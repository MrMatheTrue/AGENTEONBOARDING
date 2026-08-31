package br.edu.fatec.onboardingagent.command.impl;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.tool.GitClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Grava um commit com o que estiver preparado. */
@Component
public class GitCommitCommand implements AgentCommand {

    private final GitClient gitClient;

    public GitCommitCommand(GitClient gitClient) {
        this.gitClient = gitClient;
    }

    @Override
    public String name() {
        return "gitCommit";
    }

    @Override
    public String description() {
        return "Grava um commit com o que ja foi preparado por gitAdd. "
                + "Argumento obrigatorio: message (texto) - a mensagem que explica a mudanca. "
                + "Se nada estiver preparado, o commit falha; rode gitAdd antes.";
    }

    @Override
    public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
        Object mensagem = args.get("message");
        if (mensagem == null || mensagem.toString().isBlank()) {
            return ExecutionResult.failure("O argumento 'message' e obrigatorio para gitCommit.");
        }

        String id = gitClient.commit(mensagem.toString());
        return ExecutionResult.success("Commit %s criado na branch '%s'."
                .formatted(id, gitClient.currentBranch()));
    }
}
