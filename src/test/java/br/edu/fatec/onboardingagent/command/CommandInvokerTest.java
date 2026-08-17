package br.edu.fatec.onboardingagent.command;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.domain.Plan;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O Invoker e a fronteira entre o mundo que quebra (Git, IO, rede) e a maquina de estados.
 * Estes testes cobrem a garantia que sustenta essa fronteira: nada lanca para fora.
 */
class CommandInvokerTest {

    private final AgentContext ctx = new AgentContext(Goal.of("teste"));

    /** Comando de teste que quebra do jeito que for pedido. */
    private static AgentCommand comandoQueLanca(String nome, RuntimeException erro) {
        return new AgentCommand() {
            @Override
            public String name() {
                return nome;
            }

            @Override
            public String description() {
                return "comando de teste que sempre falha";
            }

            @Override
            public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
                throw erro;
            }
        };
    }

    @Test
    @DisplayName("excecao do comando vira ExecutionResult.failure, sem vazar")
    void excecaoViraFalha() {
        CommandInvoker invoker = new CommandInvoker(new CommandRegistry(
                List.of(comandoQueLanca("explode", new IllegalStateException("repositorio nao encontrado")))));

        ExecutionResult resultado = invoker.execute(ctx, "explode", Map.of());

        assertThat(resultado.isFailure()).isTrue();
        assertThat(resultado.errorMessage()).isEqualTo("repositorio nao encontrado");
    }

    @Test
    @DisplayName("excecao sem mensagem ainda produz falha legivel")
    void excecaoSemMensagemNaoViraNull() {
        CommandInvoker invoker = new CommandInvoker(new CommandRegistry(
                List.of(comandoQueLanca("mudo", new NullPointerException()))));

        ExecutionResult resultado = invoker.execute(ctx, "mudo", Map.of());

        assertThat(resultado.isFailure()).isTrue();
        assertThat(resultado.errorMessage()).isEqualTo("NullPointerException");
    }

    @Test
    void comandoInexistenteNaoLancaEExplicaOQueExiste() {
        CommandInvoker invoker = new CommandInvoker(new CommandRegistry(List.of(new ComandoDeTeste())));

        ExecutionResult resultado = invoker.execute(ctx, "gitTeleport", Map.of());

        assertThat(resultado.isFailure()).isTrue();
        assertThat(resultado.errorMessage()).contains("gitTeleport").contains("comandoDeTeste");
    }

    @Test
    void argsNulosNaoQuebramOInvoker() {
        CommandInvoker invoker = new CommandInvoker(new CommandRegistry(List.of(new ComandoDeTeste())));

        assertThatCode(() -> invoker.execute(ctx, "comandoDeTeste", null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("comando mal implementado que devolve null nao derruba o agente")
    void resultadoNuloViraFalha() {
        AgentCommand devolveNull = new AgentCommand() {
            @Override
            public String name() {
                return "nulo";
            }

            @Override
            public String description() {
                return "devolve null";
            }

            @Override
            public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
                return null;
            }
        };
        CommandInvoker invoker = new CommandInvoker(new CommandRegistry(List.of(devolveNull)));

        ExecutionResult resultado = invoker.execute(ctx, "nulo", Map.of());

        assertThat(resultado.isFailure()).isTrue();
        assertThat(resultado.errorMessage()).contains("nao devolveu resultado");
    }

    @Test
    @DisplayName("os nomes do registry sao o que valida o plano contra ferramenta inventada")
    void registryAlimentaAValidacaoDoPlano() {
        CommandRegistry registry = new CommandRegistry(List.of(new ComandoDeTeste()));

        Plan planoBom = new Plan(List.of(
                new PlanStep(1, "passo", "comandoDeTeste", Map.of())), 0.9);
        Plan planoInventado = new Plan(List.of(
                new PlanStep(1, "passo", "gitTeleport", Map.of())), 0.9);

        assertThat(planoBom.isValid(registry.names())).isTrue();
        assertThat(planoInventado.isValid(registry.names())).isFalse();
        assertThat(planoInventado.unknownCommands(registry.names())).containsExactly("gitTeleport");
    }

    @Test
    void registryRecusaDoisComandosComOMesmoNome() {
        assertThatThrownBy(() -> new CommandRegistry(List.of(new ComandoDeTeste(), new ComandoDeTeste())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("comandoDeTeste");
    }

    @Test
    void catalogoParaOPromptTrazNomeEDescricao() {
        CommandRegistry registry = new CommandRegistry(List.of(new ComandoDeTeste()));

        assertThat(registry.catalogForPrompt())
                .contains("- comandoDeTeste:")
                .contains("comando usado nos testes");
    }

    @Test
    @DisplayName("nenhum comando da FASE 2 e destrutivo - o gatilho so entra com o PR na FASE 7")
    void comandosDaFase2NaoSaoDestrutivos() {
        assertThat(new ComandoDeTeste().isDestructive()).isFalse();
    }

    private static class ComandoDeTeste implements AgentCommand {
        @Override
        public String name() {
            return "comandoDeTeste";
        }

        @Override
        public String description() {
            return "comando usado nos testes";
        }

        @Override
        public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
            return ExecutionResult.success("ok");
        }
    }
}
