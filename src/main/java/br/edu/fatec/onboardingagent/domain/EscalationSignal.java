package br.edu.fatec.onboardingagent.domain;

import java.util.Objects;

/**
 * Pedido de socorro da estrategia ativa para o humano.
 *
 * <p>O {@link Reason} e o que o TraceObserver (FASE 5) registra e o que se mostra na
 * apresentacao: cada escalonamento tem um motivo mensuravel, nunca "achismo do LLM".</p>
 *
 * @param reason          motivo objetivo do escalonamento
 * @param questionToHuman pergunta a ser feita ao desenvolvedor
 * @param blockedStep     passo que travou; nulo quando nem plano houve
 */
public record EscalationSignal(Reason reason, String questionToHuman, PlanStep blockedStep) {

    /** Os seis gatilhos previstos na arquitetura. */
    public enum Reason {
        /** O LLM nao produziu plano utilizavel (vazio ou com comando desconhecido). */
        NO_VALID_PLAN,
        /** Confianca declarada abaixo do limiar configurado. */
        LOW_CONFIDENCE,
        /** Replanejamentos consecutivos sem progresso. */
        REPLAN_LOOP,
        /** O mesmo comando falhou vezes demais. */
        COMMAND_FAILED,
        /** Passo destrutivo ou irreversivel, que exige aprovacao explicita. */
        DESTRUCTIVE_STEP,
        /** Pedido ambiguo ou parametro obrigatorio ausente. */
        AMBIGUOUS_INPUT
    }

    public EscalationSignal {
        Objects.requireNonNull(reason, "reason nao pode ser nulo");
        Objects.requireNonNull(questionToHuman, "questionToHuman nao pode ser nula");
        if (questionToHuman.isBlank()) {
            throw new IllegalArgumentException("escalonamento sem pergunta ao humano nao faz sentido");
        }
    }

    /** Escalonamento sem passo associado — tipico de NO_VALID_PLAN e AMBIGUOUS_INPUT. */
    public static EscalationSignal of(Reason reason, String questionToHuman) {
        return new EscalationSignal(reason, questionToHuman, null);
    }

    @Override
    public String toString() {
        return "Escalonamento[%s]: %s".formatted(reason, questionToHuman);
    }
}
