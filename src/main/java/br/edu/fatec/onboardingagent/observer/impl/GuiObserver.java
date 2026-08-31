package br.edu.fatec.onboardingagent.observer.impl;

import br.edu.fatec.onboardingagent.gui.UIComponent;
import br.edu.fatec.onboardingagent.observer.AgentEvent;
import br.edu.fatec.onboardingagent.observer.AgentObserver;

import javax.swing.SwingUtilities;

/**
 * A ponte entre o Observer e o Composite.
 *
 * <p>Recebe o evento e chama {@code refresh} numa unica referencia — a raiz da arvore de
 * paineis. Quantos paineis existem e como estao aninhados nao interessa aqui: a
 * propagacao e responsabilidade do {@code UIComposite}. Trocar a janela inteira nao muda
 * uma linha desta classe.</p>
 *
 * <p>O agente roda numa thread de fundo, mas Swing so aceita alteracoes na sua propria
 * thread; por isso o repasse vai dentro de {@code invokeLater}.</p>
 */
public class GuiObserver implements AgentObserver {

    private final UIComponent raiz;

    public GuiObserver(UIComponent raiz) {
        this.raiz = raiz;
    }

    @Override
    public void onEvent(AgentEvent event) {
        SwingUtilities.invokeLater(() -> raiz.refresh(event));
    }
}
