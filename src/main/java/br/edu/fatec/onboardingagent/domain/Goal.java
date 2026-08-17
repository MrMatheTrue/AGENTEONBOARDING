package br.edu.fatec.onboardingagent.domain;

import java.util.Objects;

/**
 * Objetivo do desenvolvedor: o que ele pediu e o que o agente entendeu do pedido.
 *
 * <p>O {@code rawText} e o texto cru digitado ("quero criar uma branch feature/login").
 * O {@code interpretedObjective} so e preenchido depois, quando a estrategia interpreta
 * o pedido. Enquanto ninguem interpretou, fica nulo — e um pedido ambiguo pode continuar
 * assim de proposito, virando gatilho de escalonamento.</p>
 *
 * <p>Imutavel: interpretar produz uma nova instancia, nunca altera a original.</p>
 */
public record Goal(String rawText, String interpretedObjective) {

    public Goal {
        Objects.requireNonNull(rawText, "rawText nao pode ser nulo");
        if (rawText.isBlank()) {
            throw new IllegalArgumentException("rawText nao pode ser vazio");
        }
    }

    /** Cria o objetivo a partir do texto do usuario, ainda sem interpretacao. */
    public static Goal of(String rawText) {
        return new Goal(rawText, null);
    }

    /** Devolve uma copia com o objetivo interpretado preenchido. */
    public Goal withInterpretation(String objective) {
        return new Goal(rawText, objective);
    }

    /** Indica se alguem ja interpretou o pedido. */
    public boolean isInterpreted() {
        return interpretedObjective != null && !interpretedObjective.isBlank();
    }
}
