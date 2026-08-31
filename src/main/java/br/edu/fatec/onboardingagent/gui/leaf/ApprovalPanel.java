package br.edu.fatec.onboardingagent.gui.leaf;

import br.edu.fatec.onboardingagent.gui.UIComponent;
import br.edu.fatec.onboardingagent.observer.AgentEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * LEAF que substitui o {@code Scanner} da FASE 4: e por aqui que o humano responde.
 *
 * <p>Duas threads se encontram neste painel. O agente roda numa thread de fundo e chama
 * {@link #perguntar(String)}, que <strong>bloqueia</strong> ate a resposta chegar. A
 * interface segue viva na thread do Swing (EDT) e, quando o usuario confirma, entrega o
 * texto pela fila. Sem essa separacao, ou a janela congelaria, ou o agente seguiria sem
 * resposta.</p>
 */
public class ApprovalPanel extends JPanel implements UIComponent {

    private static final String SEM_PERGUNTA = "(nada pendente)";

    private final JLabel pergunta = new JLabel(SEM_PERGUNTA);
    private final JLabel motivo = new JLabel(" ");
    private final JTextField resposta = new JTextField();
    private final JButton responder = new JButton("Responder");

    /** Ponto de encontro entre a thread do agente e a do Swing. */
    private final transient BlockingQueue<String> respostas = new ArrayBlockingQueue<>(1);

    public ApprovalPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder("Aprovacao do humano"));

        motivo.setForeground(new Color(0xB8, 0x60, 0x00));
        JPanel textos = new JPanel(new BorderLayout());
        textos.add(motivo, BorderLayout.NORTH);
        textos.add(pergunta, BorderLayout.CENTER);

        JPanel entrada = new JPanel(new BorderLayout(6, 0));
        entrada.add(resposta, BorderLayout.CENTER);
        entrada.add(responder, BorderLayout.EAST);

        add(textos, BorderLayout.NORTH);
        add(entrada, BorderLayout.CENTER);

        responder.addActionListener(e -> enviar());
        resposta.addActionListener(e -> enviar());
        habilitar(false);
    }

    /**
     * Faz a pergunta e espera. Chamada pela thread do agente.
     *
     * @return o texto digitado, ou vazio se a espera for interrompida
     */
    public String perguntar(String texto) {
        respostas.clear();
        SwingUtilities.invokeLater(() -> {
            pergunta.setText("<html><body style='width:420px'>" + texto + "</body></html>");
            habilitar(true);
            resposta.requestFocusInWindow();
        });

        try {
            return respostas.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private void enviar() {
        String texto = resposta.getText().trim();
        if (texto.isEmpty()) {
            return;
        }
        respostas.offer(texto);
        resposta.setText("");
        pergunta.setText(SEM_PERGUNTA);
        motivo.setText(" ");
        habilitar(false);
    }

    private void habilitar(boolean ativo) {
        resposta.setEnabled(ativo);
        responder.setEnabled(ativo);
    }

    @Override
    public void render() {
        pergunta.setText(SEM_PERGUNTA);
        motivo.setText(" ");
        habilitar(false);
    }

    /** O motivo do escalonamento aparece junto da pergunta — e o que se explica na defesa. */
    @Override
    public void refresh(AgentEvent event) {
        if (event instanceof AgentEvent.StrategyEscalated escalonamento) {
            motivo.setText("MOTIVO: " + escalonamento.signal().reason());
        } else if (event instanceof AgentEvent.StrategyDeescalated) {
            motivo.setText(" ");
        }
    }
}
