package br.edu.fatec.onboardingagent.command;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import br.edu.fatec.onboardingagent.observer.AgentEvent;
import br.edu.fatec.onboardingagent.observer.AgentEventPublisher;
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
 * <p>E um dos quatro pontos que publicam eventos: CommandStarted, CommandCompleted e
 * CommandFailed saem daqui. O Invoker nao sabe quem escuta.</p>
 */
@Component
public class CommandInvoker {

    private static final Logger log = LoggerFactory.getLogger(CommandInvoker.class);

    private final CommandRegistry registry;
    private final AgentEventPublisher publisher;

    public CommandInvoker(CommandRegistry registry, AgentEventPublisher publisher) {
        this.registry = registry;
        this.publisher = publisher;
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
            publisher.publish(new AgentEvent.CommandFailed(commandName, erro, 0));
            return ExecutionResult.failure(erro);
        }

        Map<String, Object> argumentos = args == null ? Map.of() : args;
        long inicio = System.nanoTime();
        log.info("Executando '{}' com args {}", commandName, argumentos);
        publisher.publish(new AgentEvent.CommandStarted(commandName, argumentos));
        try {
            ExecutionResult resultado = command.execute(ctx, argumentos);
            if (resultado == null) {
                // Comando mal implementado nao pode derrubar o agente.
                return falha(ctx, commandName,
                        "A ferramenta '%s' nao devolveu resultado.".formatted(commandName), inicio);
            }
            long duracao = decorridoMs(inicio);
            log.info("'{}' terminou em {} ms (sucesso={})", commandName, duracao, resultado.success());

            if (resultado.isFailure()) {
                publisher.publish(new AgentEvent.CommandFailed(
                        commandName, resultado.errorMessage(), ctx.retryCount()));
            }
            publisher.publish(new AgentEvent.CommandCompleted(commandName, duracao, resultado, ctx));
            return resultado;
        } catch (Exception | StackOverflowError e) {
            // Captura ampla de proposito: o Invoker e a fronteira entre o mundo que
            // quebra (Git, rede, IO) e a maquina de estados, que so entende ExecutionResult.
            String mensagem = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("'{}' falhou em {} ms: {}", commandName, decorridoMs(inicio), mensagem);
            return falha(ctx, commandName, mensagem, inicio);
        }
    }

    /** Publica os dois eventos de falha e devolve o resultado tipado. */
    private ExecutionResult falha(AgentContext ctx, String commandName, String mensagem, long inicio) {
        ExecutionResult resultado = ExecutionResult.failure(mensagem);
        publisher.publish(new AgentEvent.CommandFailed(commandName, mensagem, ctx.retryCount()));
        publisher.publish(new AgentEvent.CommandCompleted(commandName, decorridoMs(inicio), resultado, ctx));
        return resultado;
    }

    private static long decorridoMs(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
