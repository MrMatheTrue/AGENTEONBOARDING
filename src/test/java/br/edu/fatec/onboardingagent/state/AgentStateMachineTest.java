package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.command.CommandInvoker;
import br.edu.fatec.onboardingagent.command.CommandRegistry;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import br.edu.fatec.onboardingagent.llm.LlmGateway;
import br.edu.fatec.onboardingagent.observer.AgentEventPublisher;
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
 * Sequencias de estados da maquina, com ferramentas e LLM dublados.
 *
 * <p>O foco aqui e a ordem das transicoes, nao o que os comandos fazem nem a qualidade do
 * plano. O Git de verdade fica no GitCommandsIntegrationTest; o caminho ponta a ponta com
 * as estrategias, no StrategyEscalationTest.</p>
 */
class AgentStateMachineTest {

    /** Duble que sempre da certo e anota quantas vezes rodou. */
    private static class ComandoOk implements AgentCommand {
        int execucoes;

        @Override
        public String name() {
            return "ok";
        }

        @Override
        public String description() {
            return "duble que sempre da certo";
        }

        @Override
        public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
            execucoes++;
            return ExecutionResult.success("feito");
        }
    }

    /** Duble que falha as N primeiras vezes e depois passa a dar certo. */
    private static class ComandoQueFalha implements AgentCommand {
        private final int falhasAntesDeFuncionar;
        int execucoes;

        ComandoQueFalha(int falhasAntesDeFuncionar) {
            this.falhasAntesDeFuncionar = falhasAntesDeFuncionar;
        }

        @Override
        public String name() {
            return "instavel";
        }

        @Override
        public String description() {
            return "duble que falha algumas vezes";
        }

        @Override
        public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
            execucoes++;
            return execucoes <= falhasAntesDeFuncionar
                    ? ExecutionResult.failure("falha simulada #" + execucoes)
                    : ExecutionResult.success("finalmente deu certo");
        }
    }

    /** LLM roteirizado: devolve as respostas na ordem pedida. */
    private static class LlmRoteirizado extends LlmGateway {
        private final Deque<String> respostas = new ArrayDeque<>();

        LlmRoteirizado(String... respostas) {
            super(null);
            this.respostas.addAll(List.of(respostas));
        }

        @Override
        public String complete(String prompt) {
            return respostas.isEmpty() ? "{}" : respostas.poll();
        }
    }

    /** JSON de um plano com N passos apontando para o mesmo comando. */
    private static String planoJson(int passos, String commandName, double confianca) {
        StringBuilder json = new StringBuilder("{\"confidence\": " + confianca + ", \"steps\": [");
        for (int i = 1; i <= passos; i++) {
            if (i > 1) {
                json.append(",");
            }
            json.append("{\"description\": \"passo %d\", \"commandName\": \"%s\", \"args\": {}}"
                    .formatted(i, commandName));
        }
        return json.append("], \"clarificationNeeded\": null}").toString();
    }

    private record Montagem(AgentStateMachine machine, StrategySelector selector) {
    }

    private static Montagem montar(List<AgentCommand> comandos,
                                   int maxRetries,
                                   int maxReplans,
                                   LlmGateway llm,
                                   String... respostasDoHumano) {
        CommandRegistry registry = new CommandRegistry(comandos);
        AgentEventPublisher publisher = new AgentEventPublisher(List.of());
        StrategySelector selector = new StrategySelector(
                new ReActStrategy(llm, registry, publisher, 0.5, maxReplans),
                new PlanThenExecuteStrategy(llm, registry),
                new HumanInTheLoopStrategy(registry,
                        new BufferedReader(new StringReader(String.join("\n", respostasDoHumano)))),
                publisher);
        return new Montagem(
                new AgentStateMachine(new CommandInvoker(registry, publisher), registry, selector,
                        publisher, maxRetries, maxReplans),
                selector);
    }

    // ------------------------------------------------------------ caminho feliz

    @Test
    @DisplayName("plano de 3 passos percorre INIT-PLANNING-(EXECUTING-OBSERVING)x3-COMPLETED")
    void planoDeTresPassosPercorreASequenciaEsperada() {
        ComandoOk comando = new ComandoOk();
        Montagem m = montar(List.of(comando), 2, 2, new LlmRoteirizado(planoJson(3, "ok", 0.9)));
        AgentContext ctx = new AgentContext(Goal.of("Quero criar uma branch feature/login"));

        AgentState fim = m.machine().run(ctx);

        assertThat(m.machine().trail()).containsExactly(
                "INIT",
                "PLANNING",
                "EXECUTING", "OBSERVING",
                "EXECUTING", "OBSERVING",
                "EXECUTING", "OBSERVING",
                "COMPLETED");
        assertThat(fim).isInstanceOf(CompletedState.class);
        assertThat(comando.execucoes).isEqualTo(3);
    }

    @Test
    void todosOsPassosTerminamMarcadosComoDone() {
        Montagem m = montar(List.of(new ComandoOk()), 2, 2, new LlmRoteirizado(planoJson(3, "ok", 0.9)));
        AgentContext ctx = new AgentContext(Goal.of("objetivo"));

        m.machine().run(ctx);

        assertThat(ctx.plan().steps()).allMatch(p -> p.status() == PlanStep.Status.DONE);
        assertThat(ctx.history()).hasSize(3).allMatch(ExecutionResult::success);
        assertThat(ctx.isPlanFinished()).isTrue();
    }

    // ---------------------------------------------------------- plano invalido

    @Test
    @DisplayName("plano vazio nao executa nada: PLANNING escala direto para WAITING_APPROVAL")
    void planoVazioEscalaSemExecutar() {
        ComandoOk comando = new ComandoOk();
        Montagem m = montar(List.of(comando), 2, 2, new LlmRoteirizado("nao entendi o pedido"));
        AgentContext ctx = new AgentContext(Goal.of("Arruma ai o meu repositorio"));

        AgentState fim = m.machine().run(ctx);

        assertThat(m.machine().trail()).containsExactly("INIT", "PLANNING", "WAITING_APPROVAL");
        assertThat(fim).isInstanceOf(WaitingApprovalState.class);
        assertThat(comando.execucoes).as("nada pode rodar sem plano valido").isZero();
        assertThat(ctx.pendingEscalation()).get()
                .extracting(EscalationSignal::reason)
                .isEqualTo(EscalationSignal.Reason.NO_VALID_PLAN);
        assertThat(m.selector().active().name()).as("o escalonamento trocou a estrategia").isEqualTo("HumanInTheLoop");
    }

    // ----------------------------------------------------------- ReAct replaneja

    @Test
    @DisplayName("ReAct diante de falha volta a PLANNING - replanejar e a marca da estrategia")
    void reActReplanejaAposFalha() {
        ComandoQueFalha comando = new ComandoQueFalha(1);
        Montagem m = montar(List.of(comando), 2, 2, new LlmRoteirizado(
                planoJson(1, "instavel", 0.9),
                planoJson(1, "instavel", 0.9)));
        AgentContext ctx = new AgentContext(Goal.of("objetivo"));

        AgentState fim = m.machine().run(ctx);

        assertThat(m.machine().trail()).containsExactly(
                "INIT", "PLANNING",
                "EXECUTING", "OBSERVING",
                "PLANNING",
                "EXECUTING", "OBSERVING",
                "COMPLETED");
        assertThat(fim).isInstanceOf(CompletedState.class);
        assertThat(ctx.replanCount()).isEqualTo(1);
        assertThat(comando.execucoes).isEqualTo(2);
    }

    // ------------------------------------------- PlanThenExecute usa o ErrorState

    @Test
    @DisplayName("PlanThenExecute nao replaneja: a falha vai para ERROR e o retry resolve")
    void planThenExecuteRecuperaPorRetry() {
        ComandoQueFalha comando = new ComandoQueFalha(1);
        Montagem m = montar(List.of(comando), 2, 2, new LlmRoteirizado(planoJson(1, "instavel", 0.9)));
        m.selector().selectPlanThenExecute();
        AgentContext ctx = new AgentContext(Goal.of("objetivo"));

        AgentState fim = m.machine().run(ctx);

        assertThat(m.machine().trail()).containsExactly(
                "INIT", "PLANNING",
                "EXECUTING", "OBSERVING", "ERROR",
                "EXECUTING", "OBSERVING",
                "COMPLETED");
        assertThat(fim).isInstanceOf(CompletedState.class);
        assertThat(comando.execucoes).isEqualTo(2);
    }

    @Test
    @DisplayName("falha teimosa esgota retries e replans e termina escalando (2a porta do WAITING_APPROVAL)")
    void falhaPersistenteEscalaDepoisDeEsgotarOsLimites() {
        ComandoQueFalha comando = new ComandoQueFalha(Integer.MAX_VALUE);
        Montagem m = montar(List.of(comando), 1, 1, new LlmRoteirizado(planoJson(1, "instavel", 0.9)));
        m.selector().selectPlanThenExecute();
        AgentContext ctx = new AgentContext(Goal.of("objetivo"));

        AgentState fim = m.machine().run(ctx);

        assertThat(fim).isInstanceOf(WaitingApprovalState.class);
        assertThat(m.machine().trail()).endsWith("WAITING_APPROVAL").contains("ERROR");
        assertThat(ctx.pendingEscalation()).get()
                .extracting(EscalationSignal::reason)
                .isEqualTo(EscalationSignal.Reason.COMMAND_FAILED);
        assertThat(ctx.replanCount()).isEqualTo(1);
        assertThat(m.selector().active().name())
                .as("a 2a porta tambem troca a estrategia").isEqualTo("HumanInTheLoop");
    }

    // ------------------------------------------------------ resposta do humano

    @Test
    @DisplayName("respondido o humano, WAITING_APPROVAL deescala e o plano novo roda")
    void respostaHumanaDestravaAMaquina() {
        ComandoOk comando = new ComandoOk();
        Montagem m = montar(List.of(comando), 2, 2,
                new LlmRoteirizado(
                        "{\"confidence\": 0.2, \"steps\": [], \"clarificationNeeded\": \"O que voce quer arrumar?\"}",
                        planoJson(1, "ok", 0.9)),
                "quero ver o status do repositorio");
        AgentContext ctx = new AgentContext(Goal.of("Arruma ai o meu repositorio"));

        AgentState fim = m.machine().run(ctx);

        assertThat(m.machine().trail()).containsExactly(
                "INIT", "PLANNING",
                "WAITING_APPROVAL",
                "PLANNING",
                "EXECUTING", "OBSERVING",
                "COMPLETED");
        assertThat(fim).isInstanceOf(CompletedState.class);
        assertThat(ctx.isEscalated()).as("escalonamento resolvido").isFalse();
        assertThat(ctx.escalationHistory()).as("mas a evidencia permanece").hasSize(1);
        assertThat(m.selector().active().name()).isEqualTo("ReAct");
        assertThat(comando.execucoes).isEqualTo(1);
    }

    @Test
    void maquinaPausaEmVezDeGirarEmFalsoEsperandoOHumano() {
        Montagem m = montar(List.of(new ComandoOk()), 2, 2,
                new LlmRoteirizado("{\"confidence\": 0.1, \"steps\": [], \"clarificationNeeded\": \"o que voce quer?\"}"));
        AgentContext ctx = new AgentContext(Goal.of("pedido ambiguo"));

        AgentState primeira = m.machine().run(ctx);
        AgentState segunda = m.machine().resume(primeira, ctx);

        assertThat(primeira).isInstanceOf(WaitingApprovalState.class);
        assertThat(segunda).isInstanceOf(WaitingApprovalState.class);
    }
}
