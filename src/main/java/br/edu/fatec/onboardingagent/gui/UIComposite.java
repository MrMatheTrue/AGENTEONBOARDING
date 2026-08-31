package br.edu.fatec.onboardingagent.gui;

import br.edu.fatec.onboardingagent.observer.AgentEvent;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;

/**
 * COMPOSITE: agrupa filhos e repassa a eles tudo o que recebe.
 *
 * <p>E tambem um {@link JPanel}, entao a arvore de {@link UIComponent} e a arvore de
 * componentes Swing sao a mesma coisa: adicionar um filho aqui o coloca nas duas. Por isso
 * a janela pode ser montada em qualquer profundidade sem que ninguem precise saber o
 * formato dela.</p>
 *
 * <p>Uma composicao pode conter outra composicao — e o que torna isto um Composite de
 * verdade, e nao apenas uma lista de paineis.</p>
 */
public class UIComposite extends JPanel implements UIComponent {

    private final transient List<UIComponent> children = new ArrayList<>();

    public UIComposite(LayoutManager layout) {
        super(layout);
    }

    /**
     * Acrescenta um filho a arvore.
     *
     * @return o proprio composite, para encadear
     */
    public UIComposite adicionar(UIComponent child) {
        children.add(child);
        if (child instanceof Component componenteSwing) {
            super.add(componenteSwing);
        }
        return this;
    }

    /** Acrescenta um filho numa posicao do layout (BorderLayout.CENTER, por exemplo). */
    public UIComposite adicionar(UIComponent child, Object restricaoDeLayout) {
        children.add(child);
        if (child instanceof Component componenteSwing) {
            super.add(componenteSwing, restricaoDeLayout);
        }
        return this;
    }

    public List<UIComponent> filhos() {
        return List.copyOf(children);
    }

    @Override
    public void render() {
        children.forEach(UIComponent::render);
    }

    /**
     * Propaga o evento para todos os filhos.
     *
     * <p>Filho que falhar nao pode impedir os irmaos de se atualizarem — um painel com
     * defeito nao derruba a janela inteira.</p>
     */
    @Override
    public void refresh(AgentEvent event) {
        for (UIComponent child : children) {
            try {
                child.refresh(event);
            } catch (Exception e) {
                System.err.println("Painel " + child.getClass().getSimpleName()
                        + " falhou ao tratar evento: " + e.getMessage());
            }
        }
    }
}
