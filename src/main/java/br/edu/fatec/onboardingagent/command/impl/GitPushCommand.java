package br.edu.fatec.onboardingagent.command.impl;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.tool.GitClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Publica a branch atual no repositorio remoto. */
@Component
public class GitPushCommand implements AgentCommand {

    private final GitClient gitClient;

    public GitPushCommand(GitClient gitClient) {
        this.gitClient = gitClient;
    }

    @Override
    public String name() {
        return "gitPush";
    }

    @Override
    public String description() {
        return "Publica a branch atual no repositorio remoto, tornando o trabalho visivel para o time. "
                + "Argumento opcional: remote (texto, padrao 'origin'). "
                + "Rode depois de gitCommit e antes de abrir um pull request.";
    }

    @Override
    public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
        Object remoto = args.get("remote");
        String destino = remoto == null ? "origin" : remoto.toString();

        if (!gitClient.temRemoto(destino)) {
            return ExecutionResult.failure(
                    "O repositorio nao tem um remoto chamado '%s'. Configure com 'git remote add %s <url>'."
                            .formatted(destino, destino));
        }
        return ExecutionResult.success(gitClient.push(destino));
    }
}
