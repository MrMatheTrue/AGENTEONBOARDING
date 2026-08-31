package br.edu.fatec.onboardingagent.gui.leaf;

import br.edu.fatec.onboardingagent.domain.LearningJourney;
import br.edu.fatec.onboardingagent.gui.UIComponent;
import br.edu.fatec.onboardingagent.observer.AgentEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/** LEAF: a trilha de aprendizado do desenvolvedor, modulo a modulo. */
public class ProgressPanel extends JPanel implements UIComponent {

    private final JProgressBar barra = new JProgressBar(0, 100);
    private final JPanel modulos = new JPanel();

    public ProgressPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createTitledBorder("Trilha de aprendizado"));
        barra.setStringPainted(true);
        modulos.setLayout(new BoxLayout(modulos, BoxLayout.Y_AXIS));
        add(barra);
        add(modulos);
    }

    @Override
    public void render() {
        barra.setValue(0);
        modulos.removeAll();
        desenhar(new LearningJourney());
    }

    @Override
    public void refresh(AgentEvent event) {
        LearningJourney trilha = switch (event) {
            case AgentEvent.CommandCompleted concluido -> concluido.context().journey();
            case AgentEvent.GoalCompleted fim -> fim.context().journey();
            default -> null;
        };
        if (trilha != null) {
            desenhar(trilha);
        }
    }

    private void desenhar(LearningJourney trilha) {
        barra.setValue((int) Math.round(trilha.progress() * 100));
        modulos.removeAll();
        trilha.modules().forEach((nome, concluido) ->
                modulos.add(new JLabel((concluido ? "[x] " : "[ ] ") + nome)));
        modulos.revalidate();
        modulos.repaint();
    }
}
