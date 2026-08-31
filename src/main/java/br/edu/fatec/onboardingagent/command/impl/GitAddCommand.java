package br.edu.fatec.onboardingagent.command.impl;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.tool.GitClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Prepara alteracoes para o commit (staging). */
@Component
public class GitAddCommand implements AgentCommand {

    private final GitClient gitClient;

    public GitAddCommand(GitClient gitClient) {
        this.gitClient = gitClient;
    }

    @Override
    public String name() {
        return "gitAdd";
    }

    @Override
    public String description() {
        return "Prepara alteracoes para o commit, colocando-as na area de staging. "
                + "Argumento opcional: path (texto, padrao '.') - use '.' para preparar tudo "
                + "ou o caminho de um arquivo especifico. Rode antes de gitCommit.";
    }

    @Override
    public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
        Object path = args.get("path");
        String alvo = path == null ? "." : path.toString();
        int preparados = gitClient.add(alvo);

        return ExecutionResult.success(preparados == 0
                ? "Nada novo para preparar em '%s'.".formatted(alvo)
                : "%d arquivo(s) preparado(s) para o commit.".formatted(preparados));
    }
}
