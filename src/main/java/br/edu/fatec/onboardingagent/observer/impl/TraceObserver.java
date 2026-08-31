package br.edu.fatec.onboardingagent.observer.impl;

import br.edu.fatec.onboardingagent.observer.AgentEvent;
import br.edu.fatec.onboardingagent.observer.AgentObserver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A trilha de raciocinio — o observador que o professor pediu nominalmente.
 *
 * <p>Registra o caminho inteiro e, principalmente, <strong>o motivo</strong> de cada
 * escalonamento. E dele que sai a evidencia da apresentacao: nao basta o agente trocar de
 * estrategia, e preciso mostrar por que trocou, com um Reason mensuravel.</p>
 */
@Component
public class TraceObserver implements AgentObserver {

    private final List<String> trilha = new ArrayList<>();

    @Override
    public void onEvent(AgentEvent event) {
        String linha = switch (event) {
            case AgentEvent.StateChanged e -> e.from() == null
                    ? "ESTADO  inicio -> %s".formatted(e.to())
                    : "ESTADO  %s -> %s".formatted(e.from(), e.to());

            case AgentEvent.PlanCreated e -> "PLANO   %s montou %d passo(s), confianca %.2f: %s"
                    .formatted(e.strategy(), e.plan().size(), e.plan().confidence(),
                            e.plan().steps().stream().map(p -> p.commandName()).toList());

            case AgentEvent.ReasoningStep e -> "PENSOU  [%s] %s".formatted(e.strategy(), e.thought());

            case AgentEvent.CommandStarted e -> "EXECUTA %s %s".formatted(e.commandName(), e.args());

            case AgentEvent.CommandCompleted e -> "OK      %s em %d ms".formatted(e.commandName(), e.durationMs());

            case AgentEvent.CommandFailed e -> "FALHOU  %s (tentativa %d): %s"
                    .formatted(e.commandName(), e.retryCount(), e.errorMessage());

            // As duas linhas mais importantes da trilha:
            case AgentEvent.StrategyEscalated e -> "ESCALOU %s -> %s | MOTIVO: %s | PERGUNTA: %s"
                    .formatted(e.from(), e.to(), e.signal().reason(), e.signal().questionToHuman());

            case AgentEvent.StrategyDeescalated e -> "VOLTOU  %s -> %s (humano respondeu)"
                    .formatted(e.from(), e.to());

            case AgentEvent.UserApprovalRequired e -> "AGUARDA humano: %s".formatted(e.signal().questionToHuman());

            case AgentEvent.GoalCompleted e -> "FIM     objetivo atendido: %s".formatted(e.goal().rawText());
        };
        trilha.add(linha);
    }

    /** Trilha acumulada, uma linha por evento. */
    public List<String> trilha() {
        return List.copyOf(trilha);
    }

    /** Só os escalonamentos, que e o que se mostra na defesa do trabalho. */
    public List<String> escalonamentos() {
        return trilha.stream().filter(linha -> linha.startsWith("ESCALOU")).toList();
    }

    public void limpar() {
        trilha.clear();
    }

    /** Imprime a trilha completa no console. */
    public void imprimir() {
        System.out.println();
        System.out.println("--- trilha de raciocinio ---");
        trilha.forEach(linha -> System.out.println("  " + linha));
        System.out.println("--- fim da trilha ---");
    }
}
