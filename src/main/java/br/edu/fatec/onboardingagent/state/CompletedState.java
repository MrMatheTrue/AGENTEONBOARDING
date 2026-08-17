package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;

/** Estado terminal: o objetivo foi atendido. Devolve a si mesmo — nao ha para onde ir. */
public class CompletedState implements AgentState {

    @SuppressWarnings("unused")
    private final AgentStateMachine machine;

    public CompletedState(AgentStateMachine machine) {
        this.machine = machine;
    }

    @Override
    public String name() {
        return "COMPLETED";
    }

    @Override
    public AgentState handle(AgentContext ctx) {
        return this;
    }
}
