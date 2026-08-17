package br.edu.fatec.onboardingagent.command;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * INVOKER do padrao Command: dispara a ferramenta, cronometra e blinda o resto do sistema.
 *
 * <p>Contrato central: <strong>excecao nunca vaza daqui</strong>. Qualquer falha — comando
 * inexistente, repositorio quebrado, bug na ferramenta — sai como
 * {@link ExecutionResult#failure(String)}. E por isso que a maquina de estados pode
 * decidir com um simples {@code if (result.success())}, sem try/catch espalhado.</p>
 *
 * <p>Na FASE 5 este e um dos pontos que passam a publicar eventos (CommandStarted,
 * CommandCompleted, CommandFailed). Por ora, so registra em log.</p>
 */
@Component
public class CommandInvoker {

    private static final Logger log = LoggerFactory.getLogger(CommandInvoker.class);

    private final CommandRegistry registry;

    public CommandInvoker(CommandRegistry registry) {
        this.registry = registry;
    }

    /** Executa o passo do plano, usando o commandName e os args que ele carrega. */
    public ExecutionResult execute(AgentContext ctx, PlanStep step) {
        return execute(ctx, step.commandName(), step.args());
    }

    /**
     * Executa a ferramenta pelo nome.
     *
     * @return resultado tipado, sempre — nunca lanca
     */
    public ExecutionResult execute(AgentContext ctx, String commandName, Map<String, Object> args) {
        AgentCommand command = registry.find(commandName).orElse(null);
        if (command == null) {
            String erro = "Ferramenta desconhecida: '%s'. Disponiveis: %s"
                    .formatted(commandName, registry.names());
            log.warn(erro);
            return ExecutionResult.failure(erro);
        }

        long inicio = System.nanoTime();
        log.info("Executando '{}' com args {}", commandName, args);
        try {
            ExecutionResult resultado = command.execute(ctx, args == null ? Map.of() : args);
            if (resultado == null) {
                // Comando mal implementado nao pode derrubar o agente.
                return ExecutionResult.failure(
                        "A ferramenta '%s' nao devolveu resultado.".formatted(commandName));
            }
            log.info("'{}' terminou em {} ms (sucesso={})", commandName, decorridoMs(inicio), resultado.success());
            return resultado;
        } catch (Exception | StackOverflowError e) {
            // Captura ampla de proposito: o Invoker e a fronteira entre o mundo que
            // quebra (Git, rede, IO) e a maquina de estados, que so entende ExecutionResult.
            String mensagem = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("'{}' falhou em {} ms: {}", commandName, decorridoMs(inicio), mensagem);
            return ExecutionResult.failure(mensagem);
        }
    }

    private static long decorridoMs(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
