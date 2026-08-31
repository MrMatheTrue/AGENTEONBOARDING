package br.edu.fatec.onboardingagent.gui.leaf;

import br.edu.fatec.onboardingagent.domain.Plan;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import br.edu.fatec.onboardingagent.gui.UIComponent;
import br.edu.fatec.onboardingagent.observer.AgentEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;

/**
 * LEAF: o plano com os passos marcados conforme avancam.
 *
 * <p>Guarda o Plan recebido no PlanCreated e o redesenha a cada evento de comando —
 * o status vive no proprio PlanStep, entao basta reler.</p>
 */
public class PlanPanel extends JPanel implements UIComponent {

    private static final Color VERDE = new Color(0x1B, 0x7F, 0x3B);
    private static final Color VERMELHO = new Color(0xB0, 0x30, 0x30);
    private static final Color LARANJA = new Color(0xB8, 0x60, 0x00);

    private final JPanel lista = new JPanel();
    private final JLabel cabecalho = new JLabel("(sem plano)");
    private transient Plan plano;

    public PlanPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createTitledBorder("Plano"));
        lista.setLayout(new BoxLayout(lista, BoxLayout.Y_AXIS));
        add(cabecalho);
        add(lista);
    }

    @Override
    public void render() {
        plano = null;
        cabecalho.setText("(sem plano)");
        lista.removeAll();
    }

    @Override
    public void refresh(AgentEvent event) {
        if (event instanceof AgentEvent.PlanCreated criado) {
            plano = criado.plan();
            cabecalho.setText("%s montou %d passo(s), confianca %.2f"
                    .formatted(criado.strategy(), plano.size(), plano.confidence()));
            desenhar();
        } else if (event instanceof AgentEvent.CommandStarted || event instanceof AgentEvent.CommandCompleted) {
            desenhar();
        }
    }

    private void desenhar() {
        lista.removeAll();
        if (plano != null) {
            for (PlanStep passo : plano.steps()) {
                JLabel linha = new JLabel("%s %d. %s (%s)"
                        .formatted(marcador(passo.status()), passo.id(), passo.description(), passo.commandName()));
                linha.setForeground(cor(passo.status()));
                lista.add(linha);
            }
        }
        lista.revalidate();
        lista.repaint();
    }

    private static String marcador(PlanStep.Status status) {
        return switch (status) {
            case PENDING -> "[ ]";
            case RUNNING -> "[>]";
            case DONE -> "[x]";
            case FAILED -> "[!]";
        };
    }

    private static Color cor(PlanStep.Status status) {
        return switch (status) {
            case DONE -> VERDE;
            case FAILED -> VERMELHO;
            case RUNNING -> LARANJA;
            case PENDING -> Color.DARK_GRAY;
        };
    }
}
