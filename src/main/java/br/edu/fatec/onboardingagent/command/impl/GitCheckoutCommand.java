package br.edu.fatec.onboardingagent.command.impl;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.tool.GitClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Muda a branch ativa, criando-a se pedido ({@code git checkout -b}). */
@Component
public class GitCheckoutCommand implements AgentCommand {

    private final GitClient gitClient;

    public GitCheckoutCommand(GitClient gitClient) {
        this.gitClient = gitClient;
    }

    @Override
    public String name() {
        return "gitCheckout";
    }

    @Override
    public String description() {
        return "Muda para outra branch. "
                + "Argumento obrigatorio: branchName (texto, ex.: 'feature/login'). "
                + "Argumento opcional: create (true/false, padrao false) - com true, cria a branch "
                + "caso ela ainda nao exista e ja muda para ela.";
    }

    @Override
    public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
        Object branchName = args.get("branchName");
        if (branchName == null || branchName.toString().isBlank()) {
            // Parametro obrigatorio ausente: falha explicita, que na FASE 4 vira
            // gatilho de escalonamento em vez de execucao no escuro.
            return ExecutionResult.failure(
                    "O argumento 'branchName' e obrigatorio para gitCheckout.");
        }

        boolean create = interpretaBooleano(args.get("create"));
        String atual = gitClient.checkout(branchName.toString().trim(), create);
        return ExecutionResult.success("Agora voce esta na branch '%s'.".formatted(atual));
    }

    /** O LLM tanto manda {@code true} quanto {@code "true"}; aceitamos os dois. */
    private static boolean interpretaBooleano(Object valor) {
        if (valor instanceof Boolean booleano) {
            return booleano;
        }
        return valor != null && Boolean.parseBoolean(valor.toString().trim());
    }
}
