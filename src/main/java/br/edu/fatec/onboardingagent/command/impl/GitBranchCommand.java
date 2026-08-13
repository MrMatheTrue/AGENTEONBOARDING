package br.edu.fatec.onboardingagent.command.impl;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.tool.GitClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Cria uma branch quando recebe {@code branchName}; lista as existentes quando nao recebe.
 *
 * <p>O comportamento duplo espelha o proprio git ({@code git branch} lista,
 * {@code git branch nome} cria) e esta explicito na descricao lida pelo LLM.</p>
 */
@Component
public class GitBranchCommand implements AgentCommand {

    private final GitClient gitClient;

    public GitBranchCommand(GitClient gitClient) {
        this.gitClient = gitClient;
    }

    @Override
    public String name() {
        return "gitBranch";
    }

    @Override
    public String description() {
        return "Cria uma branch nova ou lista as existentes. "
                + "Argumento opcional: branchName (texto, ex.: 'feature/login'). "
                + "Com branchName, cria a branch a partir do ponto atual, mas NAO muda para ela - "
                + "para mudar, use gitCheckout depois. Sem branchName, apenas lista as branches locais.";
    }

    @Override
    public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
        Object branchName = args.get("branchName");

        if (branchName == null || branchName.toString().isBlank()) {
            List<String> branches = gitClient.listBranches();
            String atual = gitClient.currentBranch();
            return ExecutionResult.success(
                    "Branch atual: %s%nBranches locais: %s".formatted(atual, String.join(", ", branches)));
        }

        String criada = gitClient.createBranch(branchName.toString().trim());
        return ExecutionResult.success(
                "Branch '%s' criada. Voce continua em '%s' - use gitCheckout para mudar."
                        .formatted(criada, gitClient.currentBranch()));
    }
}
