package br.edu.fatec.onboardingagent.observer;

/**
 * OBSERVER: reage a um evento sem que a origem saiba quem esta ouvindo.
 *
 * <p>Quem publica (maquina de estados, invoker, selector) conhece apenas o
 * {@link AgentEventPublisher}. Trocar, somar ou remover observadores nao mexe em uma
 * linha sequer do fluxo do agente.</p>
 */
@FunctionalInterface
public interface AgentObserver {

    void onEvent(AgentEvent event);
}
