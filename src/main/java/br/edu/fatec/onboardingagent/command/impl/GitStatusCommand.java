package br.edu.fatec.onboardingagent.command.impl;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.tool.GitClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Mostra a situacao do repositorio. Delega ao GitClient (Receiver). */
@Component
public class GitStatusCommand implements AgentCommand {

    private final GitClient gitClient;

    public GitStatusCommand(GitClient gitClient) {
        this.gitClient = gitClient;
    }

    @Override
    public String name() {
        return "gitStatus";
    }

    @Override
    public String description() {
        return "Mostra a situacao atual do repositorio: em qual branch o desenvolvedor esta e "
                + "quais arquivos foram alterados, preparados ou nao rastreados. "
                + "Nao recebe argumentos. Use sempre que precisar saber o estado antes de agir.";
    }

    @Override
    public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
        return ExecutionResult.success(gitClient.status());
    }
}
