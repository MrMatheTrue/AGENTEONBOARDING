package br.edu.fatec.onboardingagent.gui.leaf;

import br.edu.fatec.onboardingagent.gui.UIComponent;
import br.edu.fatec.onboardingagent.observer.AgentEvent;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;

/** LEAF: mostra o objetivo em curso e sinaliza quando ele foi atendido. */
public class GoalPanel extends JPanel implements UIComponent {

    private final JLabel texto = new JLabel("(nenhum objetivo ainda)");

    public GoalPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Objetivo"));
        texto.setFont(texto.getFont().deriveFont(Font.PLAIN, 13f));
        add(texto, BorderLayout.CENTER);
    }

    /** Chamado pela janela ao disparar um objetivo novo. */
    public void definirObjetivo(String objetivo) {
        texto.setText(objetivo);
    }

    @Override
    public void render() {
        texto.setText("(nenhum objetivo ainda)");
    }

    @Override
    public void refresh(AgentEvent event) {
        if (event instanceof AgentEvent.GoalCompleted concluido) {
            texto.setText("[concluido] " + concluido.goal().rawText());
        }
    }
}
