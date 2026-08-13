package br.edu.fatec.onboardingagent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceite da FASE 1 — a validade do plano e o gatilho de escalonamento NO_VALID_PLAN,
 * entao cada forma de plano invalido precisa estar coberta.
 */
class PlanTest {

    private static final Set<String> COMANDOS_CONHECIDOS =
            Set.of("gitStatus", "gitBranch", "gitCheckout", "knowledgeSearch");

    private static PlanStep passo(int id, String commandName) {
        return new PlanStep(id, "passo " + id, commandName, Map.of());
    }

    @Nested
    @DisplayName("validacao estrutural — isValid()")
    class Estrutural {

        @Test
        void planoComPassosEComandosPreenchidosEValido() {
            Plan plan = new Plan(List.of(passo(1, "gitStatus"), passo(2, "gitBranch")), 0.9);

            assertThat(plan.isValid()).isTrue();
        }

        @Test
        void planoSemPassosEInvalido() {
            assertThat(new Plan(List.of(), 0.9).isValid()).isFalse();
        }

        @Test
        void planoVazioDeFabricaEInvalido() {
            assertThat(Plan.empty().isValid()).isFalse();
            assertThat(Plan.empty().confidence()).isZero();
        }

        @Test
        void passoComCommandNameEmBrancoInvalidaOPlano() {
            Plan plan = new Plan(List.of(passo(1, "gitStatus"), passo(2, "   ")), 0.9);

            assertThat(plan.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("validacao contra o registry — isValid(Set)")
    class ContraRegistry {

        @Test
        void planoComTodosOsComandosConhecidosEValido() {
            Plan plan = new Plan(List.of(passo(1, "gitStatus"), passo(2, "gitBranch")), 0.8);

            assertThat(plan.isValid(COMANDOS_CONHECIDOS)).isTrue();
            assertThat(plan.unknownCommands(COMANDOS_CONHECIDOS)).isEmpty();
        }

        @Test
        void comandoInventadoPeloLlmInvalidaOPlano() {
            Plan plan = new Plan(List.of(passo(1, "gitStatus"), passo(2, "gitTeleport")), 0.95);

            // Confianca alta nao salva plano que cita ferramenta inexistente.
            assertThat(plan.isValid()).isTrue();
            assertThat(plan.isValid(COMANDOS_CONHECIDOS)).isFalse();
            assertThat(plan.unknownCommands(COMANDOS_CONHECIDOS)).containsExactly("gitTeleport");
        }

        @Test
        void planoVazioEInvalidoMesmoComRegistryCheio() {
            assertThat(Plan.empty().isValid(COMANDOS_CONHECIDOS)).isFalse();
        }

        @Test
        void comandosDesconhecidosRepetidosAparecemUmaVezSo() {
            Plan plan = new Plan(
                    List.of(passo(1, "gitTeleport"), passo(2, "gitTeleport"), passo(3, "gitStatus")), 0.7);

            assertThat(plan.unknownCommands(COMANDOS_CONHECIDOS)).containsExactly("gitTeleport");
        }
    }

    @Test
    void passosDoPlanoSaoImutaveisParaQuemRecebe() {
        Plan plan = new Plan(List.of(passo(1, "gitStatus")), 0.9);

        assertThat(plan.steps()).isUnmodifiable();
    }

    @Test
    void argumentosDoPassoSaoCopiadosNaCriacao() {
        Map<String, Object> args = new java.util.HashMap<>(Map.of("branchName", "feature/login"));
        PlanStep step = new PlanStep(1, "criar branch", "gitBranch", args);

        args.put("branchName", "outra-coisa");

        assertThat(step.args()).containsEntry("branchName", "feature/login");
    }
}
