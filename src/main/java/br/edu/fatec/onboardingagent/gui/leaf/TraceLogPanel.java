package br.edu.fatec.onboardingagent.gui.leaf;

import br.edu.fatec.onboardingagent.gui.UIComponent;
import br.edu.fatec.onboardingagent.observer.AgentEvent;
import br.edu.fatec.onboardingagent.observer.impl.TraceObserver;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * LEAF: a trilha de raciocinio na tela.
 *
 * <p>Nao reformata nada: mostra o que o {@link TraceObserver} ja acumulou. A trilha da
 * apresentacao e a mesma no console e na janela.</p>
 */
public class TraceLogPanel extends JPanel implements UIComponent {

    private final transient TraceObserver trace;
    private final JTextArea area = new JTextArea(14, 60);

    public TraceLogPanel(TraceObserver trace) {
        super(new BorderLayout());
        this.trace = trace;
        setBorder(BorderFactory.createTitledBorder("Trilha de raciocinio"));
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(area), BorderLayout.CENTER);
    }

    @Override
    public void render() {
        area.setText("");
    }

    @Override
    public void refresh(AgentEvent event) {
        area.setText(String.join(System.lineSeparator(), trace.trilha()));
        area.setCaretPosition(area.getDocument().getLength());
    }
}
