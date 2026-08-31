package br.edu.fatec.onboardingagent.command.impl;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.tool.GitClient;
import br.edu.fatec.onboardingagent.tool.GitHubClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Abre um pull request no GitHub — a unica ferramenta destrutiva do agente.
 *
 * <p>{@link #isDestructive()} devolve {@code true}, e e isso que dispara o gatilho #5
 * (DESTRUCTIVE_STEP) no ExecutingState: o passo nao roda sem o desenvolvedor autorizar.
 * A regra faz sentido aqui porque um PR e visivel para o time inteiro e dispara
 * notificacoes — nao da para desfazer discretamente, como se faz com uma branch local.</p>
 */
@Component
public class CreatePullRequestCommand implements AgentCommand {

    private final GitHubClient gitHubClient;
    private final GitClient gitClient;

    public CreatePullRequestCommand(GitHubClient gitHubClient, GitClient gitClient) {
        this.gitHubClient = gitHubClient;
        this.gitClient = gitClient;
    }

    @Override
    public String name() {
        return "createPullRequest";
    }

    @Override
    public String description() {
        return "Abre um pull request no GitHub pedindo revisao da branch atual. "
                + "Argumento obrigatorio: title (texto). Opcionais: base (branch de destino, padrao 'main') "
                + "e body (descricao). Operacao visivel para o time inteiro - exige aprovacao do "
                + "desenvolvedor antes de rodar. Use apenas depois de gitPush.";
    }

    /** Irreversivel do ponto de vista do time: forca a aprovacao humana. */
    @Override
    public boolean isDestructive() {
        return true;
    }

    @Override
    public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
        Object titulo = args.get("title");
        if (titulo == null || titulo.toString().isBlank()) {
            return ExecutionResult.failure("O argumento 'title' e obrigatorio para createPullRequest.");
        }

        Object base = args.get("base");
        Object corpo = args.get("body");
        String head = gitClient.currentBranch();

        if ("main".equals(head) || "master".equals(head)) {
            return ExecutionResult.failure(
                    "Voce esta na '%s'. Crie uma branch e publique-a antes de abrir um pull request."
                            .formatted(head));
        }

        String url = gitHubClient.createPullRequest(
                titulo.toString(),
                head,
                base == null || base.toString().isBlank() ? "main" : base.toString(),
                corpo == null ? "" : corpo.toString());

        return ExecutionResult.success("Pull request aberto: " + url);
    }
}
