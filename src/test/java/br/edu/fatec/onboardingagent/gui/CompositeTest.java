package br.edu.fatec.onboardingagent.gui;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.domain.Plan;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import br.edu.fatec.onboardingagent.gui.leaf.ApprovalPanel;
import br.edu.fatec.onboardingagent.gui.leaf.GoalPanel;
import br.edu.fatec.onboardingagent.gui.leaf.PlanPanel;
import br.edu.fatec.onboardingagent.gui.leaf.ProgressPanel;
import br.edu.fatec.onboardingagent.gui.leaf.StatePanel;
import br.edu.fatec.onboardingagent.observer.AgentEvent;
import br.edu.fatec.onboardingagent.observer.AgentEventPublisher;
import br.edu.fatec.onboardingagent.observer.AgentObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O Composite propagando de verdade. Nao abre janela: os paineis Swing sao construidos
 * sem tela, e o que se verifica e a arvore, nao o desenho.
 */
class CompositeTest {

    /** Folha de teste que so anota o que recebeu. */
    private static class FolhaEspia implements UIComponent {
        final List<String> eventos = new ArrayList<>();
        int renderizacoes;

        @Override
        public void render() {
            renderizacoes++;
        }

        @Override
        public void refresh(AgentEvent event) {
            eventos.add(event.getClass().getSimpleName());
        }
    }

    private static AgentEvent.StateChanged evento() {
        return new AgentEvent.StateChanged("INIT", "PLANNING");
    }

    @Test
    @DisplayName("refresh na raiz chega a folha mais profunda - composicao dentro de composicao")
    void refreshPropagaAteAFolhaMaisProfunda() {
        FolhaEspia rasa = new FolhaEspia();
        FolhaEspia profunda = new FolhaEspia();

        UIComposite nivel3 = new UIComposite(new BorderLayout());
        nivel3.adicionar(profunda);
        UIComposite nivel2 = new UIComposite(new BorderLayout());
        nivel2.adicionar(nivel3);
        UIComposite raiz = new UIComposite(new BorderLayout());
        raiz.adicionar(rasa).adicionar(nivel2);

        raiz.refresh(evento());

        assertThat(rasa.eventos).containsExactly("StateChanged");
        assertThat(profunda.eventos).as("tres niveis abaixo da raiz").containsExactly("StateChanged");
    }

    @Test
    void renderTambemPercorreAArvoreInteira() {
        FolhaEspia folha = new FolhaEspia();
        UIComposite interno = new UIComposite(new BorderLayout());
        interno.adicionar(folha);
        UIComposite raiz = new UIComposite(new BorderLayout());
        raiz.adicionar(interno);

        raiz.render();

        assertThat(folha.renderizacoes).isEqualTo(1);
    }

    @Test
    @DisplayName("folha com defeito nao impede os irmaos de se atualizarem")
    void folhaComDefeitoNaoDerrubaAArvore() {
        UIComponent quebrada = new UIComponent() {
            @Override
            public void render() {
                // sem efeito
            }

            @Override
            public void refresh(AgentEvent event) {
                throw new IllegalStateException("painel com bug");
            }
        };
        FolhaEspia sadia = new FolhaEspia();
        UIComposite raiz = new UIComposite(new BorderLayout());
        raiz.adicionar(quebrada).adicionar(sadia);

        raiz.refresh(evento());

        assertThat(sadia.eventos).containsExactly("StateChanged");
    }

    @Test
    @DisplayName("um filho adicionado ao Composite entra tambem na arvore Swing")
    void composicaoEhTambemUmContainerSwing() {
        UIComposite raiz = new UIComposite(new BorderLayout());
        GoalPanel folha = new GoalPanel();

        raiz.adicionar(folha, BorderLayout.CENTER);

        assertThat(raiz.filhos()).containsExactly(folha);
        assertThat(raiz.getComponentCount()).as("mesma arvore nos dois mundos").isEqualTo(1);
    }

    // ------------------------------------------------- a ponte Observer -> Composite

    @Test
    @DisplayName("o publisher fala com UMA referencia (a raiz) e a arvore se vira")
    void observerEntregaNaRaizEAArvorePropaga() {
        FolhaEspia folha = new FolhaEspia();
        UIComposite raiz = new UIComposite(new BorderLayout());
        raiz.adicionar(folha);

        // Mesmo papel do GuiObserver, sem depender da thread do Swing no teste.
        AgentObserver ponte = raiz::refresh;
        AgentEventPublisher publisher = new AgentEventPublisher(List.of());
        publisher.subscribe(ponte);

        publisher.publish(evento());
        publisher.publish(new AgentEvent.GoalCompleted(Goal.of("objetivo"), new AgentContext(Goal.of("objetivo"))));

        assertThat(folha.eventos).containsExactly("StateChanged", "GoalCompleted");
    }

    // ------------------------------------------------------ as folhas de verdade

    @Test
    void paineisReaisAbsorvemOsEventosSemQuebrar() {
        AgentContext ctx = new AgentContext(Goal.of("Quero criar uma branch feature/login"));
        PlanStep passo = new PlanStep(1, "ver status", "gitStatus", Map.of());
        Plan plano = new Plan(List.of(passo), 0.9);

        UIComposite raiz = new UIComposite(new BorderLayout());
        raiz.adicionar(new GoalPanel())
                .adicionar(new StatePanel())
                .adicionar(new PlanPanel())
                .adicionar(new ProgressPanel())
                .adicionar(new ApprovalPanel());
        raiz.render();

        raiz.refresh(new AgentEvent.StateChanged(null, "INIT"));
        raiz.refresh(new AgentEvent.PlanCreated("ReAct", plano));
        raiz.refresh(new AgentEvent.CommandStarted("gitStatus", Map.of()));
        passo.markDone();
        raiz.refresh(new AgentEvent.CommandCompleted("gitStatus", 12,
                ExecutionResult.success("Branch atual: main"), ctx));
        raiz.refresh(new AgentEvent.StrategyEscalated("ReAct", "HumanInTheLoop",
                EscalationSignal.of(EscalationSignal.Reason.AMBIGUOUS_INPUT, "o que voce quer?")));
        raiz.refresh(new AgentEvent.StrategyDeescalated("HumanInTheLoop", "ReAct"));
        raiz.refresh(new AgentEvent.GoalCompleted(ctx.goal(), ctx));

        assertThat(raiz.filhos()).hasSize(5);
    }

    @Test
    @DisplayName("o ApprovalPanel devolve a resposta a thread do agente, que fica bloqueada ate la")
    void approvalPanelDesbloqueiaAThreadDoAgente() throws Exception {
        ApprovalPanel painel = new ApprovalPanel();
        List<String> recebido = new ArrayList<>();

        // A thread do agente pergunta e fica esperando.
        Thread agente = new Thread(() -> recebido.add(painel.perguntar("Qual branch?")));
        agente.start();

        // A thread do Swing responde, como faria o clique no botao.
        javax.swing.SwingUtilities.invokeAndWait(() -> {
        });
        Thread.sleep(50);
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            for (java.awt.Component c : componentesDe(painel)) {
                if (c instanceof javax.swing.JTextField campo) {
                    campo.setText("feature/login");
                }
            }
            for (java.awt.Component c : componentesDe(painel)) {
                if (c instanceof javax.swing.JButton botao) {
                    botao.doClick();
                }
            }
        });

        agente.join(2000);

        assertThat(recebido).containsExactly("feature/login");
    }

    private static List<java.awt.Component> componentesDe(java.awt.Container raiz) {
        List<java.awt.Component> todos = new ArrayList<>();
        for (java.awt.Component c : raiz.getComponents()) {
            todos.add(c);
            if (c instanceof java.awt.Container container) {
                todos.addAll(componentesDe(container));
            }
        }
        return todos;
    }
}
