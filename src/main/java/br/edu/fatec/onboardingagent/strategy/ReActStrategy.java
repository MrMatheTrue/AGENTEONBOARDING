package br.edu.fatec.onboardingagent.strategy;

import br.edu.fatec.onboardingagent.command.CommandRegistry;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Plan;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import br.edu.fatec.onboardingagent.domain.StepDecision;
import br.edu.fatec.onboardingagent.llm.LlmGateway;
import br.edu.fatec.onboardingagent.llm.PromptTemplates;
import br.edu.fatec.onboardingagent.observer.AgentEvent;
import br.edu.fatec.onboardingagent.observer.AgentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Planeja com o LLM e replaneja a cada observacao.
 *
 * <p>E a estrategia padrao. Quando nao consegue produzir um plano confiavel, ela nao
 * chuta: declara a incapacidade em {@link #escalationSignal(AgentContext)} e devolve
 * {@link StepDecision#ESCALATE}. Trocar para o HumanInTheLoop e trabalho do
 * {@link StrategySelector} — esta classe nao conhece as outras estrategias.</p>
 */
@Component
public class ReActStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(ReActStrategy.class);

    private final LlmGateway llm;
    private final CommandRegistry registry;
    private final AgentEventPublisher publisher;
    private final double confidenceThreshold;
    private final int maxReplans;

    /**
     * Motivo da ultima tentativa frustrada de planejar.
     *
     * <p>Guardado aqui porque so o planejamento enxerga o que o modelo respondeu — o
     * PlanningState chama buildPlan e, logo em seguida, escalationSignal. O agente atende
     * uma sessao por vez (console na FASE 4, janela na FASE 6), entao um campo basta.</p>
     */
    private EscalationSignal ultimoSinal;

    public ReActStrategy(LlmGateway llm,
                         CommandRegistry registry,
                         AgentEventPublisher publisher,
                         @Value("${agent.confidence-threshold:0.5}") double confidenceThreshold,
                         @Value("${agent.max-replans:2}") int maxReplans) {
        this.llm = llm;
        this.registry = registry;
        this.publisher = publisher;
        this.confidenceThreshold = confidenceThreshold;
        this.maxReplans = maxReplans;
    }

    @Override
    public String name() {
        return "ReAct";
    }

    @Override
    public Plan buildPlan(AgentContext ctx) {
        this.ultimoSinal = null;
        pensar(ctx.replanCount() == 0
                ? "Vou planejar para: %s".formatted(ctx.goal().rawText())
                : "Replanejamento #%d - o plano anterior nao deu certo".formatted(ctx.replanCount()));

        String resposta;
        try {
            resposta = llm.complete(PromptTemplates.planning(ctx, registry.catalogForPrompt()));
        } catch (Exception e) {
            // Modelo fora do ar tambem e incapacidade: pergunta ao humano em vez de quebrar.
            log.warn("Falha ao falar com o modelo local: {}", e.getMessage());
            this.ultimoSinal = EscalationSignal.of(
                    EscalationSignal.Reason.NO_VALID_PLAN,
                    "Nao consegui falar com o modelo local (%s). Confira se o llama-server esta no ar e me diga o que fazer."
                            .formatted(e.getMessage()));
            return Plan.empty();
        }

        PromptTemplates.PlanProposal proposta = PromptTemplates.parsePlanResponse(resposta);

        // 1) O proprio modelo pediu esclarecimento -> gatilho #6.
        if (proposta.needsClarification()) {
            this.ultimoSinal = EscalationSignal.of(
                    EscalationSignal.Reason.AMBIGUOUS_INPUT, proposta.clarificationNeeded());
            return Plan.empty();
        }

        // 2) Resposta ilegivel -> gatilho #1.
        if (proposta.failedToParse()) {
            log.warn("Resposta do modelo nao pode ser lida ({}). Resposta crua: {}", proposta.parseError(), resposta);
            this.ultimoSinal = EscalationSignal.of(
                    EscalationSignal.Reason.NO_VALID_PLAN,
                    "Nao consegui montar um plano para '%s'. Pode dizer com outras palavras o que voce quer fazer?"
                            .formatted(ctx.goal().rawText()));
            return Plan.empty();
        }

        Plan plan = proposta.plan();

        // 3) Plano vazio ou citando ferramenta inexistente -> gatilho #1.
        if (!plan.isValid(registry.names())) {
            this.ultimoSinal = EscalationSignal.of(
                    EscalationSignal.Reason.NO_VALID_PLAN, explicarPlanoInvalido(ctx, plan));
            return Plan.empty();
        }

        // 4) Plano legivel, mas o modelo nao confia nele -> gatilho #2.
        if (plan.confidence() < confidenceThreshold) {
            this.ultimoSinal = new EscalationSignal(
                    EscalationSignal.Reason.LOW_CONFIDENCE,
                    "Montei um plano para '%s', mas com confianca baixa (%.2f). Confere para mim: %s"
                            .formatted(ctx.goal().rawText(), plan.confidence(), descrever(plan)),
                    plan.steps().get(0));
            return Plan.empty();
        }

        log.info("Plano com {} passo(s), confianca {}", plan.size(), plan.confidence());
        pensar("Plano aceito: %d passo(s), confianca %.2f".formatted(plan.size(), plan.confidence()));
        publisher.publish(new AgentEvent.PlanCreated(name(), plan));
        return plan;
    }

    /**
     * Reagir ao que observou e a marca desta estrategia: passo que falha manda replanejar,
     * ate esgotar o orcamento de replanejamentos.
     */
    @Override
    public StepDecision decideNext(AgentContext ctx, ExecutionResult last) {
        if (last.success()) {
            pensar("Passo concluido, seguindo para o proximo");
            return StepDecision.CONTINUE;
        }
        if (ctx.replanCount() < maxReplans) {
            pensar("O passo falhou (%s). Vou repensar o plano.".formatted(last.errorMessage()));
            return StepDecision.REPLAN;
        }
        // Gatilho #3 REPLAN_LOOP: replanejar de novo seria girar em falso.
        pensar("Ja replanejei %d vezes sem sair do lugar. Preciso de ajuda.".formatted(ctx.replanCount()));
        return StepDecision.ESCALATE;
    }

    @Override
    public boolean requiresApproval(PlanStep step) {
        return registry.find(step.commandName())
                .map(command -> command.isDestructive())
                .orElse(false);
    }

    /**
     * Alem do que o planejamento apurou, cobre o gatilho #3: replanejamentos consecutivos
     * sem progresso, que so aparecem na hora de decidir, nao na de planejar.
     */
    @Override
    public Optional<EscalationSignal> escalationSignal(AgentContext ctx) {
        if (ultimoSinal != null) {
            return Optional.of(ultimoSinal);
        }
        if (ctx.replanCount() >= maxReplans) {
            return Optional.of(new EscalationSignal(
                    EscalationSignal.Reason.REPLAN_LOOP,
                    "Replanejei %d vezes e continua sem funcionar (%s). Como voce quer seguir?"
                            .formatted(ctx.replanCount(),
                                    ctx.lastResult().map(ExecutionResult::errorMessage).orElse("sem detalhe")),
                    ctx.currentStep().orElse(null)));
        }
        return Optional.empty();
    }

    /** Publica um passo do raciocinio — e o que alimenta a trilha do TraceObserver. */
    private void pensar(String pensamento) {
        publisher.publish(new AgentEvent.ReasoningStep(name(), pensamento));
    }

    private String explicarPlanoInvalido(AgentContext ctx, Plan plan) {
        if (plan.size() == 0) {
            return "Nao consegui montar um plano para '%s'. Pode detalhar o que voce quer fazer?"
                    .formatted(ctx.goal().rawText());
        }
        var desconhecidos = plan.unknownCommands(registry.names());
        if (!desconhecidos.isEmpty()) {
            return "O plano usa ferramentas que eu nao tenho (%s). Pode reformular o pedido?"
                    .formatted(String.join(", ", desconhecidos));
        }
        return "O plano que montei nao esta utilizavel. Pode detalhar o que voce quer fazer?";
    }

    private String descrever(Plan plan) {
        return plan.steps().stream().map(PlanStep::description).reduce((a, b) -> a + "; " + b).orElse("");
    }
}
