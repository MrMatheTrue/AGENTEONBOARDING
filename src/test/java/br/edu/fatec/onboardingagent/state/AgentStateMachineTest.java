package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.command.CommandInvoker;
import br.edu.fatec.onboardingagent.command.CommandRegistry;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.domain.Plan;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceite da FASE 3: a maquina gira com um plano fixo, sem LLM e sem Git.
 *
 * <p>As ferramentas sao dubles controlados pelo teste — o que se verifica aqui e a
 * sequencia de estados, nao o que os comandos fazem.</p>
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

    private static AgentStateMachine maquina(List<AgentCommand> comandos, int maxRetries, int maxReplans) {
        CommandRegistry registry = new CommandRegistry(comandos);
        return new AgentStateMachine(new CommandInvoker(registry), registry, maxRetries, maxReplans);
    }

    private static Plan planoDe(int passos, String commandName) {
        List<PlanStep> lista = new ArrayList<>();
        for (int i = 1; i <= passos; i++) {
            lista.add(new PlanStep(i, "passo " + i, commandName, Map.of()));
        }
        return new Plan(lista, 0.9);
    }

    // ------------------------------------------------------------ caminho feliz

    @Test
    @DisplayName("plano fixo de 3 passos percorre INIT-PLANNING-(EXECUTING-OBSERVING)x3-COMPLETED")
    void planoDeTresPassosPercorreASequenciaEsperada() {
        ComandoOk comando = new ComandoOk();
        AgentStateMachine machine = maquina(List.of(comando), 2, 2);
        AgentContext ctx = new AgentContext(Goal.of("Quero criar uma branch feature/login"));
        ctx.installPlan(planoDe(3, "ok"));

        AgentState fim = machine.run(ctx);

        assertThat(machine.trail()).containsExactly(
                "INIT",
                "PLANNING",
                "EXECUTING", "OBSERVING",
                "EXECUTING", "OBSERVING",
                "EXECUTING", "OBSERVING",
                "COMPLETED");
        assertThat(fim).isInstanceOf(CompletedState.class);
        assertThat(fim.name()).isEqualTo("COMPLETED");
        assertThat(comando.execucoes).isEqualTo(3);
    }

    @Test
    void todosOsPassosTerminamMarcadosComoDone() {
        AgentStateMachine machine = maquina(List.of(new ComandoOk()), 2, 2);
        AgentContext ctx = new AgentContext(Goal.of("objetivo"));
        ctx.installPlan(planoDe(3, "ok"));

        machine.run(ctx);

        assertThat(ctx.plan().steps()).allMatch(p -> p.status() == PlanStep.Status.DONE);
        assertThat(ctx.history()).hasSize(3).allMatch(ExecutionResult::success);
        assertThat(ctx.isPlanFinished()).isTrue();
    }

    // -------------------------------------------------------- plano invalido

    @Test
    @DisplayName("plano vazio nao executa nada: PLANNING escala direto para WAITING_APPROVAL")
    void planoVazioEscalaSemExecutar() {
        ComandoOk comando = new ComandoOk();
        AgentStateMachine machine = maquina(List.of(comando), 2, 2);
        AgentContext ctx = new AgentContext(Goal.of("Arruma ai o meu repositorio"));

        AgentState fim = machine.run(ctx);

        assertThat(machine.trail()).containsExactly("INIT", "PLANNING", "WAITING_APPROVAL");
        assertThat(fim).isInstanceOf(WaitingApprovalState.class);
        assertThat(comando.execucoes).as("nada pode rodar sem plano valido").isZero();
        assertThat(ctx.isEscalated()).isTrue();
        assertThat(ctx.pendingEscalation()).get()
                .extracting(EscalationSignal::reason)
                .isEqualTo(EscalationSignal.Reason.NO_VALID_PLAN);
    }

    @Test
    @DisplayName("plano citando ferramenta inexistente tambem escala, e a pergunta diz qual")
    void planoComFerramentaInventadaEscala() {
        AgentStateMachine machine = maquina(List.of(new ComandoOk()), 2, 2);
        AgentContext ctx = new AgentContext(Goal.of("objetivo"));
        ctx.installPlan(planoDe(1, "gitTeleport"));

        machine.run(ctx);

        assertThat(machine.trail()).containsExactly("INIT", "PLANNING", "WAITING_APPROVAL");
        assertThat(ctx.pendingEscalation()).get()
                .extracting(EscalationSignal::questionToHuman).asString()
                .contains("gitTeleport");
    }

    // ------------------------------------------------------------- falha e retry

    @Test
    @DisplayName("falha isolada: ERROR devolve o mesmo passo para EXECUTING e o plano termina")
    void falhaUnicaEhRecuperadaPorRetry() {
        ComandoQueFalha comando = new ComandoQueFalha(1);
        AgentStateMachine machine = maquina(List.of(comando), 2, 2);
        AgentContext ctx = new AgentContext(Goal.of("objetivo"));
        ctx.installPlan(planoDe(1, "instavel"));

        AgentState fim = machine.run(ctx);

        assertThat(machine.trail()).containsExactly(
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
        AgentStateMachine machine = maquina(List.of(comando), 1, 1);
        AgentContext ctx = new AgentContext(Goal.of("objetivo"));
        ctx.installPlan(planoDe(1, "instavel"));

        AgentState fim = machine.run(ctx);

        assertThat(fim).isInstanceOf(WaitingApprovalState.class);
        assertThat(machine.trail()).endsWith("WAITING_APPROVAL");
        assertThat(machine.trail()).contains("ERROR");
        assertThat(ctx.pendingEscalation()).get()
                .extracting(EscalationSignal::reason)
                .isEqualTo(EscalationSignal.Reason.COMMAND_FAILED);
        assertThat(ctx.retryCount()).isEqualTo(1);
        assertThat(ctx.replanCount()).isEqualTo(1);
    }

    // --------------------------------------------------- resposta do humano

    @Test
    @DisplayName("respondido o humano, WAITING_APPROVAL volta a PLANNING e o plano novo roda")
    void respostaHumanaDestravaAMaquina() {
        ComandoOk comando = new ComandoOk();
        AgentStateMachine machine = maquina(List.of(comando), 2, 2);
        AgentContext ctx = new AgentContext(Goal.of("Arruma ai o meu repositorio"));

        AgentState pausada = machine.run(ctx);
        assertThat(pausada).isInstanceOf(WaitingApprovalState.class);

        // O humano esclarece e o "novo plano" chega (na FASE 4 quem o produz e a estrategia).
        ctx.submitHumanResponse("quero ver o status do repositorio");
        ctx.installPlan(planoDe(1, "ok"));

        AgentState fim = machine.resume(pausada, ctx);

        assertThat(fim).isInstanceOf(CompletedState.class);
        assertThat(machine.trail()).containsExactly(
                "INIT", "PLANNING", "WAITING_APPROVAL",
                "WAITING_APPROVAL", "PLANNING",
                "EXECUTING", "OBSERVING",
                "COMPLETED");
        assertThat(ctx.isEscalated()).as("escalonamento resolvido").isFalse();
        assertThat(ctx.escalationHistory()).as("mas a evidencia permanece").hasSize(1);
    }

    @Test
    void maquinaPausaEmVezDeGirarEmFalsoEsperandoOHumano() {
        AgentStateMachine machine = maquina(List.of(new ComandoOk()), 2, 2);
        AgentContext ctx = new AgentContext(Goal.of("pedido ambiguo"));

        // Sem resposta humana, rodar de novo nao avanca nem trava o processo.
        AgentState primeira = machine.run(ctx);
        AgentState segunda = machine.resume(primeira, ctx);

        assertThat(segunda).isInstanceOf(WaitingApprovalState.class);
    }
}
