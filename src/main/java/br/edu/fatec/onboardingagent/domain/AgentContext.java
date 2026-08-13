package br.edu.fatec.onboardingagent.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Memoria de trabalho de uma sessao do agente.
 *
 * <p>E o objeto que atravessa os quatro padroes: a Strategy le e escreve o plano, o State
 * o recebe em {@code handle(ctx)}, o Command o recebe em {@code execute(ctx, args)} e o
 * Observer o le para narrar. Por isso ele nao pode depender de Spring nem de IA — todo o
 * resto depende dele.</p>
 *
 * <p>Os contadores {@code replanCount} e {@code retryCount} existem para tornar os
 * gatilhos de escalonamento mensuraveis: quem decide se estourou o limite e a estrategia,
 * comparando com a configuracao; o contexto so conta.</p>
 */
public class AgentContext {

    private Goal goal;
    private Plan plan;
    private int currentStepIndex;

    private final List<ExecutionResult> history = new ArrayList<>();
    private final List<EscalationSignal> escalationHistory = new ArrayList<>();

    private int replanCount;
    private int retryCount;

    /** Escalonamento ainda nao resolvido. Nulo quando o agente esta operando normalmente. */
    private EscalationSignal pendingEscalation;

    /** Resposta do humano ao escalonamento — console na FASE 4, ApprovalPanel na FASE 6. */
    private String humanResponse;

    private final LearningJourney journey;

    public AgentContext(Goal goal) {
        this(goal, new LearningJourney());
    }

    public AgentContext(Goal goal, LearningJourney journey) {
        this.goal = Objects.requireNonNull(goal, "goal nao pode ser nulo");
        this.journey = Objects.requireNonNull(journey, "journey nao pode ser nula");
        this.plan = Plan.empty();
        this.currentStepIndex = 0;
    }

    // ---------------------------------------------------------------- objetivo

    public Goal goal() {
        return goal;
    }

    public void updateGoal(Goal updated) {
        this.goal = Objects.requireNonNull(updated, "goal nao pode ser nulo");
    }

    // ------------------------------------------------------------------ plano

    public Plan plan() {
        return plan;
    }

    /**
     * Instala um novo plano e volta a execucao para o primeiro passo.
     *
     * <p>Replanejar sempre reinicia o indice: o plano novo tem passos novos, e continuar
     * de onde parou apontaria para um passo que nao existe mais.</p>
     *
     * <p>Zera tambem o retryCount, pela mesma razao de {@link #advance()}: tentativa e por
     * passo. O primeiro passo de um plano novo nao pode nascer sem direito a tentativa por
     * causa do que o plano anterior gastou. O replanCount, esse sim, continua acumulando —
     * e ele que limita o ciclo replanejar-falhar-replanejar.</p>
     */
    public void installPlan(Plan newPlan) {
        this.plan = Objects.requireNonNull(newPlan, "plan nao pode ser nulo");
        this.currentStepIndex = 0;
        this.retryCount = 0;
    }

    public int currentStepIndex() {
        return currentStepIndex;
    }

    /** Passo corrente, ou vazio quando o plano acabou (ou nem comecou). */
    public Optional<PlanStep> currentStep() {
        if (currentStepIndex < 0 || currentStepIndex >= plan.size()) {
            return Optional.empty();
        }
        return Optional.of(plan.steps().get(currentStepIndex));
    }

    /** Ainda ha passo a executar no plano atual. */
    public boolean hasMoreSteps() {
        return currentStepIndex < plan.size();
    }

    /**
     * Avanca para o proximo passo e zera o contador de tentativas.
     *
     * <p>O retryCount e por passo, nao por sessao: um passo que falhou duas vezes nao pode
     * condenar o passo seguinte.</p>
     */
    public void advance() {
        this.currentStepIndex++;
        this.retryCount = 0;
    }

    /** Todos os passos do plano foram percorridos. */
    public boolean isPlanFinished() {
        return plan.size() > 0 && currentStepIndex >= plan.size();
    }

    // -------------------------------------------------------------- historico

    public void recordResult(ExecutionResult result) {
        history.add(Objects.requireNonNull(result, "result nao pode ser nulo"));
    }

    public List<ExecutionResult> history() {
        return Collections.unmodifiableList(history);
    }

    public Optional<ExecutionResult> lastResult() {
        return history.isEmpty() ? Optional.empty() : Optional.of(history.get(history.size() - 1));
    }

    // -------------------------------------------------------------- contadores

    public int replanCount() {
        return replanCount;
    }

    public void recordReplan() {
        this.replanCount++;
    }

    public int retryCount() {
        return retryCount;
    }

    public void recordRetry() {
        this.retryCount++;
    }

    public void resetRetryCount() {
        this.retryCount = 0;
    }

    // ------------------------------------------------------------ escalonamento

    /** Registra o escalonamento: entra no historico (evidencia) e vira o pendente (bloqueio). */
    public void recordEscalation(EscalationSignal signal) {
        Objects.requireNonNull(signal, "signal nao pode ser nulo");
        escalationHistory.add(signal);
        this.pendingEscalation = signal;
    }

    /**
     * Encerra o escalonamento corrente — chamado no deescalate, quando o humano ja respondeu.
     *
     * <p>O historico permanece intacto de proposito: e dele que o TraceObserver tira o
     * "por que escalou" que se mostra na apresentacao.</p>
     */
    public void clearEscalation() {
        this.pendingEscalation = null;
    }

    public boolean isEscalated() {
        return pendingEscalation != null;
    }

    public Optional<EscalationSignal> pendingEscalation() {
        return Optional.ofNullable(pendingEscalation);
    }

    public List<EscalationSignal> escalationHistory() {
        return Collections.unmodifiableList(escalationHistory);
    }

    // ----------------------------------------------------------- resposta humana

    public Optional<String> humanResponse() {
        return Optional.ofNullable(humanResponse);
    }

    public void submitHumanResponse(String response) {
        this.humanResponse = response;
    }

    public void clearHumanResponse() {
        this.humanResponse = null;
    }

    // ------------------------------------------------------------------ trilha

    public LearningJourney journey() {
        return journey;
    }

    @Override
    public String toString() {
        return "AgentContext{objetivo='%s', passo=%d/%d, replans=%d, retries=%d, escalado=%s}"
                .formatted(goal.rawText(), currentStepIndex, plan.size(),
                        replanCount, retryCount, isEscalated());
    }
}
