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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Planeja uma unica vez e executa o plano ate o fim. Nunca replaneja.
 *
 * <p>Diferenca de comportamento em relacao ao ReAct, que e o que o padrao Strategy tem que
 * demonstrar: diante de uma falha, o ReAct devolve REPLAN e repensa; esta devolve FAIL e
 * deixa o ErrorState tentar de novo ou escalar. Planejar de novo esta fora de questao —
 * planejou uma vez, e aquilo.</p>
 *
 * <p>Serve para pedidos ja bem definidos, em que replanejar so gastaria tempo do modelo.</p>
 */
@Component
public class PlanThenExecuteStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(PlanThenExecuteStrategy.class);

    private final LlmGateway llm;
    private final CommandRegistry registry;

    private EscalationSignal ultimoSinal;

    public PlanThenExecuteStrategy(LlmGateway llm, CommandRegistry registry) {
        this.llm = llm;
        this.registry = registry;
    }

    @Override
    public String name() {
        return "PlanThenExecute";
    }

    @Override
    public Plan buildPlan(AgentContext ctx) {
        this.ultimoSinal = null;

        // Ja planejou nesta sessao: mantem o plano. E o traco da estrategia.
        if (ctx.plan().isValid(registry.names()) && ctx.plan().size() > 0 && !ctx.history().isEmpty()) {
            log.info("Plano ja existe e nao sera refeito ({} passos)", ctx.plan().size());
            return ctx.plan();
        }

        String resposta;
        try {
            resposta = llm.complete(PromptTemplates.planning(ctx, registry.catalogForPrompt()));
        } catch (Exception e) {
            this.ultimoSinal = EscalationSignal.of(
                    EscalationSignal.Reason.NO_VALID_PLAN,
                    "Nao consegui falar com o modelo local (%s). Confira se o llama-server esta no ar."
                            .formatted(e.getMessage()));
            return Plan.empty();
        }

        PromptTemplates.PlanProposal proposta = PromptTemplates.parsePlanResponse(resposta);

        if (proposta.needsClarification()) {
            this.ultimoSinal = EscalationSignal.of(
                    EscalationSignal.Reason.AMBIGUOUS_INPUT, proposta.clarificationNeeded());
            return Plan.empty();
        }
        if (proposta.failedToParse() || !proposta.plan().isValid(registry.names())) {
            this.ultimoSinal = EscalationSignal.of(
                    EscalationSignal.Reason.NO_VALID_PLAN,
                    "Nao consegui montar um plano para '%s'. Pode detalhar o pedido?"
                            .formatted(ctx.goal().rawText()));
            return Plan.empty();
        }
        return proposta.plan();
    }

    /** Sem replanejamento: ou segue, ou falha e deixa o tratamento de erro agir. */
    @Override
    public StepDecision decideNext(AgentContext ctx, ExecutionResult last) {
        return last.success() ? StepDecision.CONTINUE : StepDecision.FAIL;
    }

    @Override
    public boolean requiresApproval(PlanStep step) {
        return registry.find(step.commandName())
                .map(command -> command.isDestructive())
                .orElse(false);
    }

    @Override
    public Optional<EscalationSignal> escalationSignal(AgentContext ctx) {
        return Optional.ofNullable(ultimoSinal);
    }
}
