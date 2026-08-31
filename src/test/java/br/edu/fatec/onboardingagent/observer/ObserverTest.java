package br.edu.fatec.onboardingagent.observer;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.command.CommandInvoker;
import br.edu.fatec.onboardingagent.command.CommandRegistry;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.llm.LlmGateway;
import br.edu.fatec.onboardingagent.observer.impl.ErrorObserver;
import br.edu.fatec.onboardingagent.observer.impl.ProgressObserver;
import br.edu.fatec.onboardingagent.observer.impl.TraceObserver;
import br.edu.fatec.onboardingagent.state.AgentState;
import br.edu.fatec.onboardingagent.state.AgentStateMachine;
import br.edu.fatec.onboardingagent.strategy.HumanInTheLoopStrategy;
import br.edu.fatec.onboardingagent.strategy.PlanThenExecuteStrategy;
import br.edu.fatec.onboardingagent.strategy.ReActStrategy;
import br.edu.fatec.onboardingagent.strategy.StrategySelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceite da FASE 5: rodando o caso de escalonamento, o TraceObserver mostra a trilha
 * completa <strong>e o motivo</strong>, e os novos gatilhos disparam.
 */
class ObserverTest {

    /** Ferramenta dublada, com destrutividade e resultado configuraveis. */
    private static class Ferramenta implements AgentCommand {
        private final String nome;
        private final boolean destrutiva;
        private final boolean sempreFalha;
        int execucoes;

        Ferramenta(String nome, boolean destrutiva, boolean sempreFalha) {
            this.nome = nome;
            this.destrutiva = destrutiva;
            this.sempreFalha = sempreFalha;
        }

        @Override
        public String name() {
            return nome;
        }

        @Override
        public String description() {
            return "ferramenta " + nome;
        }

        @Override
        public boolean isDestructive() {
            return destrutiva;
        }

        @Override
        public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
            execucoes++;
            return sempreFalha ? ExecutionResult.failure("falhou de novo") : ExecutionResult.success("feito");
        }
    }

    private static class LlmRoteirizado extends LlmGateway {
        private final Deque<String> respostas = new ArrayDeque<>();

        LlmRoteirizado(String... respostas) {
            super(null);
            this.respostas.addAll(List.of(respostas));
        }

        @Override
        public String complete(String prompt) {
            return respostas.isEmpty() ? respostaVazia() : respostas.poll();
        }

        /** Depois do roteiro, repete o ultimo formato valido para nao virar plano invalido. */
        private String respostaVazia() {
            return "{\"confidence\": 0.9, \"steps\": [], \"clarificationNeeded\": null}";
        }
    }

    private record Cenario(AgentStateMachine machine, StrategySelector selector,
                           TraceObserver trace, ErrorObserver erros) {
    }

    private static Cenario montar(List<AgentCommand> comandos, int maxRetries, int maxReplans,
                                  LlmGateway llm, String... respostasDoHumano) {
        TraceObserver trace = new TraceObserver();
        ErrorObserver erros = new ErrorObserver();
        AgentEventPublisher publisher = new AgentEventPublisher(List.of(trace, erros, new ProgressObserver()));

        CommandRegistry registry = new CommandRegistry(comandos);
        StrategySelector selector = new StrategySelector(
                new ReActStrategy(llm, registry, publisher, 0.5, maxReplans),
                new PlanThenExecuteStrategy(llm, registry),
                new HumanInTheLoopStrategy(registry,
                        new BufferedReader(new StringReader(String.join("\n", respostasDoHumano)))),
                publisher);
        AgentStateMachine machine = new AgentStateMachine(
                new CommandInvoker(registry, publisher), registry, selector, publisher, maxRetries, maxReplans);
        return new Cenario(machine, selector, trace, erros);
    }

    private static String plano(String commandName) {
        return ("{\"confidence\": 0.9, \"steps\": [{\"description\": \"usar %s\", \"commandName\": \"%s\","
                + " \"args\": {}}], \"clarificationNeeded\": null}").formatted(commandName, commandName);
    }

    // ------------------------------------------------------- o Subject proprio

    @Test
    @DisplayName("o publisher e nosso: subscribe, unsubscribe e publish")
    void publisherProprioGerenciaInscricoes() {
        AgentEventPublisher publisher = new AgentEventPublisher(List.of());
        StringBuilder recebidos = new StringBuilder();
        AgentObserver ouvinte = evento -> recebidos.append(evento.getClass().getSimpleName()).append(" ");

        publisher.subscribe(ouvinte);
        publisher.publish(new AgentEvent.StateChanged(null, "INIT"));
        assertThat(publisher.subscriberCount()).isEqualTo(1);

        publisher.unsubscribe(ouvinte);
        publisher.publish(new AgentEvent.StateChanged("INIT", "PLANNING"));

        assertThat(recebidos.toString().trim()).isEqualTo("StateChanged");
        assertThat(publisher.subscriberCount()).isZero();
    }

    @Test
    @DisplayName("observador que quebra nao derruba o agente")
    void observadorComDefeitoNaoQuebraOFluxo() {
        AgentObserver quebrado = evento -> {
            throw new IllegalStateException("observador com bug");
        };
        StringBuilder saOutro = new StringBuilder();
        AgentEventPublisher publisher = new AgentEventPublisher(List.of(quebrado, (AgentObserver) e -> saOutro.append("ok")));

        publisher.publish(new AgentEvent.StateChanged(null, "INIT"));

        assertThat(saOutro.toString()).as("o observador seguinte ainda recebe").isEqualTo("ok");
    }

    // --------------------------------------------------- a trilha com o motivo

    @Test
    @DisplayName("no caso de escalonamento, a trilha traz o raciocinio e o MOTIVO")
    void trilhaMostraOMotivoDoEscalonamento() {
        Ferramenta ok = new Ferramenta("gitStatus", false, false);
        Cenario c = montar(List.of(ok), 2, 2,
                new LlmRoteirizado(
                        "{\"confidence\": 0.2, \"steps\": [], \"clarificationNeeded\": \"O que voce quer arrumar?\"}",
                        plano("gitStatus")),
                "quero ver o status");

        AgentState fim = c.machine().run(new AgentContext(Goal.of("Arruma ai o meu repositorio")));

        assertThat(fim.name()).isEqualTo("COMPLETED");

        String trilha = String.join("\n", c.trace().trilha());
        assertThat(trilha)
                .contains("ESTADO  inicio -> INIT")
                .contains("ESTADO  PLANNING -> WAITING_APPROVAL")
                .contains("PENSOU  [ReAct]")
                .contains("EXECUTA gitStatus")
                .contains("FIM     objetivo atendido");

        // O que se mostra na defesa do trabalho:
        assertThat(c.trace().escalonamentos()).hasSize(1);
        assertThat(c.trace().escalonamentos().get(0))
                .contains("ReAct -> HumanInTheLoop")
                .contains("MOTIVO: AMBIGUOUS_INPUT")
                .contains("O que voce quer arrumar?");
        assertThat(trilha).contains("VOLTOU  HumanInTheLoop -> ReAct");
    }

    @Test
    void progressObserverMarcaATrilhaDeAprendizado() {
        Ferramenta ok = new Ferramenta("gitStatus", false, false);
        Cenario c = montar(List.of(ok), 2, 2, new LlmRoteirizado(plano("gitStatus")));
        AgentContext ctx = new AgentContext(Goal.of("ver o status"));

        c.machine().run(ctx);

        assertThat(ctx.journey().isCompleted("Inspecionar o repositorio (git status)")).isTrue();
        assertThat(ctx.journey().progress()).isGreaterThan(0.0);
    }

    @Test
    void errorObserverContabilizaAsFalhas() {
        Ferramenta ruim = new Ferramenta("gitStatus", false, true);
        Cenario c = montar(List.of(ruim), 1, 1, new LlmRoteirizado(plano("gitStatus"), plano("gitStatus")));

        c.machine().run(new AgentContext(Goal.of("ver o status")));

        assertThat(c.erros().totalDeFalhas()).isPositive();
        assertThat(c.erros().falhasDe("gitStatus")).isPositive();
    }

    // ----------------------------------------------------- os novos gatilhos

    @Test
    @DisplayName("gatilho #3 REPLAN_LOOP: replanejar de novo seria girar em falso")
    void replanLoopEscala() {
        Ferramenta ruim = new Ferramenta("gitStatus", false, true);
        Cenario c = montar(List.of(ruim), 0, 1,
                new LlmRoteirizado(plano("gitStatus"), plano("gitStatus"), plano("gitStatus")));
        AgentContext ctx = new AgentContext(Goal.of("ver o status"));

        AgentState fim = c.machine().run(ctx);

        assertThat(fim.name()).isEqualTo("WAITING_APPROVAL");
        assertThat(ctx.escalationHistory()).extracting(EscalationSignal::reason)
                .contains(EscalationSignal.Reason.REPLAN_LOOP);
        assertThat(c.trace().escalonamentos().toString()).contains("MOTIVO: REPLAN_LOOP");
    }

    @Test
    @DisplayName("gatilho #5 DESTRUCTIVE_STEP: passo irreversivel nao roda sem autorizacao")
    void passoDestrutivoPedeAutorizacao() {
        Ferramenta perigosa = new Ferramenta("createPullRequest", true, false);
        Cenario c = montar(List.of(perigosa), 2, 2,
                new LlmRoteirizado(plano("createPullRequest")));
        AgentContext ctx = new AgentContext(Goal.of("abre o PR"));

        // Sem resposta do humano, o passo nao pode ter rodado.
        AgentState fim = c.machine().run(ctx);

        assertThat(fim.name()).isEqualTo("WAITING_APPROVAL");
        assertThat(perigosa.execucoes).as("nada irreversivel sem autorizacao").isZero();
        assertThat(ctx.pendingEscalation()).get()
                .extracting(EscalationSignal::reason)
                .isEqualTo(EscalationSignal.Reason.DESTRUCTIVE_STEP);
    }

    @Test
    @DisplayName("autorizado uma vez, o passo destrutivo roda e nao pergunta de novo")
    void autorizacaoDoHumanoLiberaOPassoDestrutivo() {
        Ferramenta perigosa = new Ferramenta("createPullRequest", true, false);
        Cenario c = montar(List.of(perigosa), 2, 2,
                new LlmRoteirizado(plano("createPullRequest"), plano("createPullRequest")),
                "sim");
        AgentContext ctx = new AgentContext(Goal.of("abre o PR"));

        AgentState fim = c.machine().run(ctx);

        assertThat(fim.name()).isEqualTo("COMPLETED");
        assertThat(perigosa.execucoes).isEqualTo(1);
        assertThat(ctx.isApproved("createPullRequest")).isTrue();
        assertThat(c.trace().escalonamentos().toString()).contains("MOTIVO: DESTRUCTIVE_STEP");
    }
}
