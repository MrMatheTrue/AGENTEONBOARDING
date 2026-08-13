package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;

/** Porta de entrada: recebe o objetivo do desenvolvedor e manda planejar. */
public class InitState implements AgentState {

    private final AgentStateMachine machine;

    public InitState(AgentStateMachine machine) {
        this.machine = machine;
    }

    @Override
    public String name() {
        return "INIT";
    }

    @Override
    public AgentState handle(AgentContext ctx) {
        // O objetivo ja vem preenchido na construcao do contexto; aqui so damos a partida.
        return new PlanningState(machine);
    }
}
