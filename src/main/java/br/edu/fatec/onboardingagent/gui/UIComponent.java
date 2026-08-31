package br.edu.fatec.onboardingagent.gui;

import br.edu.fatec.onboardingagent.observer.AgentEvent;

/**
 * COMPONENT do padrao Composite: a interface que folha e composicao compartilham.
 *
 * <p>E o que permite ao GuiObserver falar com a janela inteira como se fosse um objeto so:
 * ele chama {@code refresh(evento)} na raiz e a arvore se encarrega de propagar. O
 * observador nao sabe quantos paineis existem nem como estao aninhados.</p>
 */
public interface UIComponent {

    /** Monta o conteudo inicial do componente. */
    void render();

    /** Reage a um evento do agente. A composicao repassa aos filhos; a folha se redesenha. */
    void refresh(AgentEvent event);
}
