package br.edu.fatec.onboardingagent.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Plano produzido por uma estrategia: a sequencia de passos mais a confianca declarada
 * pelo LLM (0.0 a 1.0).
 *
 * <p>A validade do plano e o gatilho de escalonamento numero 1 (NO_VALID_PLAN): plano
 * invalido nunca vira execucao, vira pergunta ao humano. Por isso a checagem mora aqui,
 * no dominio, e nao espalhada pelos estados.</p>
 */
public class Plan {

    private final List<PlanStep> steps;
    private final double confidence;

    public Plan(List<PlanStep> steps, double confidence) {
        Objects.requireNonNull(steps, "steps nao pode ser nulo");
        this.steps = Collections.unmodifiableList(List.copyOf(steps));
        this.confidence = confidence;
    }

    /** Plano vazio — o que sobra quando o LLM nao conseguiu planejar. Sempre invalido. */
    public static Plan empty() {
        return new Plan(List.of(), 0.0);
    }

    public List<PlanStep> steps() {
        return steps;
    }

    public double confidence() {
        return confidence;
    }

    public int size() {
        return steps.size();
    }

    /**
     * Validacao estrutural: tem pelo menos um passo e todo passo aponta para algum comando.
     *
     * <p>Esta versao nao consegue saber se o comando <em>existe</em>, porque o dominio e puro
     * e nao conhece o CommandRegistry (que so nasce na FASE 2, no pacote command). Quem tem o
     * registry em maos deve preferir {@link #isValid(Set)}, que faz a checagem completa
     * exigida pelo CLAUDE.md.</p>
     */
    public boolean isValid() {
        return !steps.isEmpty() && steps.stream().allMatch(PlanStep::hasCommand);
    }

    /**
     * Validacao completa: tem pelo menos um passo <strong>e</strong> todo {@code commandName}
     * existe entre os comandos conhecidos.
     *
     * @param availableCommands nomes registrados no CommandRegistry
     */
    public boolean isValid(Set<String> availableCommands) {
        Objects.requireNonNull(availableCommands, "availableCommands nao pode ser nulo");
        return isValid() && steps.stream()
                .allMatch(step -> availableCommands.contains(step.commandName()));
    }

    /** Nomes de comando citados no plano que o registry nao conhece. Vira a explicacao do escalonamento. */
    public List<String> unknownCommands(Set<String> availableCommands) {
        Objects.requireNonNull(availableCommands, "availableCommands nao pode ser nulo");
        return steps.stream()
                .map(PlanStep::commandName)
                .filter(name -> !availableCommands.contains(name))
                .distinct()
                .toList();
    }

    @Override
    public String toString() {
        return "Plan{passos=%d, confianca=%.2f}".formatted(steps.size(), confidence);
    }
}
