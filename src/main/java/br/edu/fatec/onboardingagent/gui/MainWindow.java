package br.edu.fatec.onboardingagent.gui;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.gui.leaf.ApprovalPanel;
import br.edu.fatec.onboardingagent.gui.leaf.GoalPanel;
import br.edu.fatec.onboardingagent.gui.leaf.PlanPanel;
import br.edu.fatec.onboardingagent.gui.leaf.ProgressPanel;
import br.edu.fatec.onboardingagent.gui.leaf.StatePanel;
import br.edu.fatec.onboardingagent.gui.leaf.TraceLogPanel;
import br.edu.fatec.onboardingagent.observer.AgentEventPublisher;
import br.edu.fatec.onboardingagent.observer.impl.GuiObserver;
import br.edu.fatec.onboardingagent.observer.impl.ProgressObserver;
import br.edu.fatec.onboardingagent.observer.impl.TraceObserver;
import br.edu.fatec.onboardingagent.state.AgentStateMachine;
import br.edu.fatec.onboardingagent.strategy.StrategySelector;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * A janela do agente: monta a arvore de paineis e liga o Observer nela.
 *
 * <p>A arvore tem profundidade de verdade — uma composicao dentro de outra —, que e o que
 * distingue um Composite de uma lista de paineis:</p>
 *
 * <pre>
 * raiz (UIComposite)
 * +-- topo (UIComposite)      -> GoalPanel, StatePanel
 * +-- centro (UIComposite)    -> PlanPanel, ProgressPanel, TraceLogPanel
 * +-- ApprovalPanel
 * </pre>
 *
 * <p>O {@link GuiObserver} chama {@code refresh} apenas na raiz; a propagacao ate a ultima
 * folha e do proprio Composite.</p>
 */
public class MainWindow extends JFrame {

    private final AgentStateMachine machine;
    private final StrategySelector selector;
    private final TraceObserver trace;
    private final ProgressObserver progresso;

    private final UIComposite raiz = new UIComposite(new BorderLayout(8, 8));
    private final GoalPanel goalPanel = new GoalPanel();
    private final PlanPanel planPanel = new PlanPanel();
    private final StatePanel statePanel = new StatePanel();
    private final ProgressPanel progressPanel = new ProgressPanel();
    private final ApprovalPanel approvalPanel = new ApprovalPanel();
    private final TraceLogPanel traceLogPanel;

    private final JTextField campoObjetivo = new JTextField();
    private final JButton executar = new JButton("Executar");

    public MainWindow(AgentStateMachine machine,
                      StrategySelector selector,
                      AgentEventPublisher publisher,
                      TraceObserver trace,
                      ProgressObserver progresso) {
        super("GitHub Onboarding Agent");
        this.machine = machine;
        this.selector = selector;
        this.trace = trace;
        this.progresso = progresso;
        this.traceLogPanel = new TraceLogPanel(trace);

        montarArvore();
        raiz.render();

        // A ponte Observer -> Composite. O publisher e o Subject da FASE 5; o GuiObserver
        // nasce depois do contexto, por isso se inscreve pelo subscribe().
        publisher.subscribe(new GuiObserver(raiz));

        // O ApprovalPanel toma o lugar do console na conversa com o humano.
        selector.humanInTheLoop().useHumanSource(approvalPanel::perguntar);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(raiz);
        setPreferredSize(new Dimension(980, 720));
        pack();
        setLocationRelativeTo(null);
    }

    private void montarArvore() {
        UIComposite topo = new UIComposite(new BorderLayout(8, 8));
        // add (e nao adicionar): a barra de objetivo nao e folha do Composite, porque nao
        // reage a evento nenhum - ela apenas dispara o agente.
        topo.add(barraDeObjetivo(), BorderLayout.NORTH);
        topo.adicionar(goalPanel, BorderLayout.CENTER);
        topo.adicionar(statePanel, BorderLayout.EAST);

        UIComposite centro = new UIComposite(new BorderLayout(8, 8));
        UIComposite coluna = new UIComposite(new BorderLayout(8, 8));
        coluna.adicionar(planPanel, BorderLayout.NORTH);
        coluna.adicionar(progressPanel, BorderLayout.CENTER);
        centro.adicionar(coluna, BorderLayout.WEST);
        centro.adicionar(traceLogPanel, BorderLayout.CENTER);

        UIComposite rodape = new UIComposite(new BorderLayout(8, 8));
        rodape.adicionar(approvalPanel, BorderLayout.CENTER);

        raiz.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        raiz.adicionar(topo, BorderLayout.NORTH);
        raiz.adicionar(centro, BorderLayout.CENTER);
        raiz.adicionar(rodape, BorderLayout.SOUTH);
    }

    /**
     * Barra de entrada do objetivo. Nao e folha do Composite: nao reage a eventos.
     *
     * <p>Cuidado com o layout: no BorderLayout, PAGE_START e NORTH sao a mesma posicao.
     * Colocar esta barra em PAGE_START na raiz expulsava o topo (objetivo + estado) do
     * layout, e os dois paineis sumiam da tela. Por isso ela mora dentro do topo.</p>
     */
    private JPanel barraDeObjetivo() {
        JPanel barra = new JPanel(new BorderLayout(6, 6));
        barra.setBorder(BorderFactory.createTitledBorder("O que voce quer fazer?"));
        campoObjetivo.setName("campoObjetivo");
        barra.add(campoObjetivo, BorderLayout.CENTER);
        barra.add(executar, BorderLayout.EAST);
        executar.addActionListener(e -> dispararObjetivo());
        campoObjetivo.addActionListener(e -> dispararObjetivo());

        JPanel topo = new JPanel();
        topo.setLayout(new BoxLayout(topo, BoxLayout.Y_AXIS));
        topo.add(barra);
        return topo;
    }

    private void dispararObjetivo() {
        String objetivo = campoObjetivo.getText().trim();
        if (objetivo.isEmpty()) {
            return;
        }
        campoObjetivo.setText("");
        executar.setEnabled(false);
        goalPanel.definirObjetivo(objetivo);
        trace.limpar();

        if (selector.isEscalated()) {
            selector.selectReAct();
        }

        // O agente NAO pode rodar na thread do Swing: ele bloqueia esperando o humano
        // responder no ApprovalPanel, e a janela congelaria antes de mostrar a pergunta.
        Thread agente = new Thread(() -> {
            try {
                machine.run(new AgentContext(Goal.of(objetivo), progresso.carregar()));
            } catch (Exception e) {
                System.err.println("Falha ao executar o objetivo: " + e.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> executar.setEnabled(true));
            }
        }, "agente");
        agente.setDaemon(true);
        agente.start();
    }

    /** Raiz da arvore, exposta para o teste conferir a propagacao. */
    public UIComponent raiz() {
        return raiz;
    }

    public ApprovalPanel approvalPanel() {
        return approvalPanel;
    }
}
