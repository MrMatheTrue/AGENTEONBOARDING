package br.edu.fatec.onboardingagent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceite da FASE 1 — avanco do currentStepIndex e contabilidade do escalonamento.
 * Nenhum LLM, nenhum Spring: e so memoria de trabalho.
 */
class AgentContextTest {

    private static PlanStep passo(int id, String commandName) {
        return new PlanStep(id, "passo " + id, commandName, Map.of());
    }

    private static AgentContext contextoComPlanoDeTresPassos() {
        AgentContext ctx = new AgentContext(Goal.of("Quero criar uma branch feature/login"));
        ctx.installPlan(new Plan(
                List.of(passo(1, "gitStatus"), passo(2, "gitBranch"), passo(3, "gitCheckout")), 0.9));
        return ctx;
    }

    @Test
    @DisplayName("o indice caminha do primeiro ao ultimo passo e depois encerra o plano")
    void indiceAvancaAteOFimDoPlano() {
        AgentContext ctx = contextoComPlanoDeTresPassos();

        assertThat(ctx.currentStepIndex()).isZero();
        assertThat(ctx.currentStep()).map(PlanStep::commandName).contains("gitStatus");
        assertThat(ctx.hasMoreSteps()).isTrue();
        assertThat(ctx.isPlanFinished()).isFalse();

        ctx.advance();
        assertThat(ctx.currentStepIndex()).isEqualTo(1);
        assertThat(ctx.currentStep()).map(PlanStep::commandName).contains("gitBranch");

        ctx.advance();
        assertThat(ctx.currentStep()).map(PlanStep::commandName).contains("gitCheckout");
        assertThat(ctx.hasMoreSteps()).isTrue();

        ctx.advance();
        assertThat(ctx.currentStepIndex()).isEqualTo(3);
        assertThat(ctx.currentStep()).isEmpty();
        assertThat(ctx.hasMoreSteps()).isFalse();
        assertThat(ctx.isPlanFinished()).isTrue();
    }

    @Test
    void contextoNovoNaoTemPassoNemPlano() {
        AgentContext ctx = new AgentContext(Goal.of("Arruma ai o meu repositorio"));

        assertThat(ctx.plan().size()).isZero();
        assertThat(ctx.currentStep()).isEmpty();
        assertThat(ctx.hasMoreSteps()).isFalse();
        assertThat(ctx.isPlanFinished()).isFalse();
    }

    @Test
    @DisplayName("replanejar reinicia o indice — o plano novo comeca do zero")
    void instalarNovoPlanoReiniciaOIndice() {
        AgentContext ctx = contextoComPlanoDeTresPassos();
        ctx.advance();
        ctx.advance();
        assertThat(ctx.currentStepIndex()).isEqualTo(2);

        ctx.installPlan(new Plan(List.of(passo(1, "knowledgeSearch")), 0.6));

        assertThat(ctx.currentStepIndex()).isZero();
        assertThat(ctx.currentStep()).map(PlanStep::commandName).contains("knowledgeSearch");
    }

    @Test
    @DisplayName("retryCount e por passo: avancar zera as tentativas")
    void avancarZeraORetryCount() {
        AgentContext ctx = contextoComPlanoDeTresPassos();

        ctx.recordRetry();
        ctx.recordRetry();
        assertThat(ctx.retryCount()).isEqualTo(2);

        ctx.advance();

        assertThat(ctx.retryCount()).isZero();
    }

    @Test
    @DisplayName("plano novo tambem zera as tentativas, mas nao os replanejamentos")
    void instalarNovoPlanoZeraRetryMasNaoReplan() {
        AgentContext ctx = contextoComPlanoDeTresPassos();
        ctx.recordRetry();
        ctx.recordReplan();

        ctx.installPlan(new Plan(List.of(passo(1, "gitStatus")), 0.8));

        assertThat(ctx.retryCount()).as("passo novo, orcamento novo").isZero();
        assertThat(ctx.replanCount()).as("e o replan que limita o ciclo").isEqualTo(1);
    }

    @Test
    void replanCountAcumulaAoLongoDaSessao() {
        AgentContext ctx = contextoComPlanoDeTresPassos();

        ctx.recordReplan();
        ctx.recordReplan();

        assertThat(ctx.replanCount()).isEqualTo(2);
    }

    @Test
    void historicoGuardaOsResultadosNaOrdem() {
        AgentContext ctx = contextoComPlanoDeTresPassos();

        ctx.recordResult(ExecutionResult.success("On branch main"));
        ctx.recordResult(ExecutionResult.failure("branch ja existe"));

        assertThat(ctx.history()).hasSize(2);
        assertThat(ctx.lastResult()).get().extracting(ExecutionResult::isFailure).isEqualTo(true);
        assertThat(ctx.history()).isUnmodifiable();
    }

    @Test
    @DisplayName("clearEscalation libera o bloqueio mas preserva a evidencia no historico")
    void escalonamentoBloqueiaEDepoisLibera() {
        AgentContext ctx = contextoComPlanoDeTresPassos();
        EscalationSignal sinal = EscalationSignal.of(
                EscalationSignal.Reason.AMBIGUOUS_INPUT, "Qual repositorio voce quer arrumar?");

        ctx.recordEscalation(sinal);

        assertThat(ctx.isEscalated()).isTrue();
        assertThat(ctx.pendingEscalation()).contains(sinal);

        ctx.submitHumanResponse("o repositorio do trabalho de padroes");
        ctx.clearEscalation();

        assertThat(ctx.isEscalated()).isFalse();
        assertThat(ctx.pendingEscalation()).isEmpty();
        assertThat(ctx.escalationHistory()).containsExactly(sinal);
        assertThat(ctx.humanResponse()).contains("o repositorio do trabalho de padroes");
    }

    @Test
    void trilhaDeAprendizadoAcompanhaOProgresso() {
        AgentContext ctx = contextoComPlanoDeTresPassos();
        LearningJourney trilha = ctx.journey();
        String primeiro = trilha.pendingModules().get(0);

        assertThat(trilha.progress()).isZero();
        assertThat(trilha.complete(primeiro)).isTrue();
        assertThat(trilha.complete(primeiro)).as("concluir duas vezes nao conta duas vezes").isFalse();
        assertThat(trilha.isCompleted(primeiro)).isTrue();
        assertThat(trilha.progress()).isGreaterThan(0.0);
    }
}
