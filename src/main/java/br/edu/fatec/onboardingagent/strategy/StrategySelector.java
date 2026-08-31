package br.edu.fatec.onboardingagent.strategy;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.observer.AgentEvent;
import br.edu.fatec.onboardingagent.observer.AgentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CONTEXT do padrao Strategy: guarda <strong>uma</strong> referencia ativa e a troca em runtime.
 *
 * <p>A troca acontece sob condicao observavel — um {@link EscalationSignal} com motivo
 * mensuravel —, nunca por palpite. E o unico lugar do sistema autorizado a mudar quem
 * esta planejando; as estrategias nao se conhecem.</p>
 *
 * <p>Como {@code active} e um campo unico, e impossivel ter duas estrategias ativas ao
 * mesmo tempo: {@link #escalate} substitui a referencia, {@link #deescalate} a devolve.</p>
 */
@Component
public class StrategySelector {

    private static final Logger log = LoggerFactory.getLogger(StrategySelector.class);

    private final ReActStrategy reAct;
    private final PlanThenExecuteStrategy planThenExecute;
    private final HumanInTheLoopStrategy humanInTheLoop;
    private final AgentEventPublisher publisher;

    /** A unica estrategia ativa. Trocar esta referencia e a essencia do padrao aqui. */
    private AgentStrategy active;

    public StrategySelector(ReActStrategy reAct,
                            PlanThenExecuteStrategy planThenExecute,
                            HumanInTheLoopStrategy humanInTheLoop,
                            AgentEventPublisher publisher) {
        this.reAct = reAct;
        this.planThenExecute = planThenExecute;
        this.humanInTheLoop = humanInTheLoop;
        this.publisher = publisher;
        this.active = reAct;
    }

    public AgentStrategy active() {
        return active;
    }

    /** Escolha inicial do planejador, antes de comecar. */
    public AgentStrategy select(AgentStrategy strategy) {
        this.active = strategy;
        log.info("Estrategia ativa: {}", active.name());
        return active;
    }

    public AgentStrategy selectReAct() {
        return select(reAct);
    }

    public AgentStrategy selectPlanThenExecute() {
        return select(planThenExecute);
    }

    /**
     * Troca para o HumanInTheLoop por causa de um sinal concreto.
     *
     * <p>Chamado pelo PlanningState e pelo ObservingState/ErrorState — nunca por uma
     * estrategia.</p>
     */
    public AgentStrategy escalate(AgentContext ctx, EscalationSignal signal) {
        ctx.recordEscalation(signal);
        String anterior = active.name();
        log.info("ESCALONAMENTO [{}] {} -> HumanInTheLoop | {}",
                signal.reason(), anterior, signal.questionToHuman());
        this.active = humanInTheLoop;
        publisher.publish(new AgentEvent.StrategyEscalated(anterior, active.name(), signal));
        return this.active;
    }

    /** Humano respondeu: devolve o comando ao ReAct, com o contexto enriquecido. */
    public void deescalate(AgentContext ctx) {
        String anterior = active.name();
        log.info("DEESCALONAMENTO {} -> ReAct", anterior);
        this.active = reAct;
        ctx.clearEscalation();
        publisher.publish(new AgentEvent.StrategyDeescalated(anterior, active.name()));
    }

    public boolean isEscalated() {
        return active == humanInTheLoop;
    }

    /** Acesso ao HITL para quem precisa conversar com o humano (WaitingApprovalState). */
    public HumanInTheLoopStrategy humanInTheLoop() {
        return humanInTheLoop;
    }
}
