package br.edu.fatec.onboardingagent.command;

import br.edu.fatec.onboardingagent.command.impl.GitBranchCommand;
import br.edu.fatec.onboardingagent.command.impl.GitCheckoutCommand;
import br.edu.fatec.onboardingagent.command.impl.GitStatusCommand;
import br.edu.fatec.onboardingagent.command.impl.KnowledgeSearchCommand;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import br.edu.fatec.onboardingagent.tool.GitClient;
import br.edu.fatec.onboardingagent.tool.KnowledgeService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceite da FASE 2: as ferramentas rodam Git de verdade num repositorio temporario.
 *
 * <p>Nao ha Spring nem LLM aqui — os comandos sao instanciados a mao com o Receiver
 * apontando para o @TempDir. Se este teste passa, o Command funciona sozinho, que e
 * exatamente o ponto do padrao.</p>
 */
class GitCommandsIntegrationTest {

    @TempDir
    Path repoDir;

    private GitClient gitClient;
    private CommandInvoker invoker;
    private AgentContext ctx;

    @BeforeEach
    void prepararRepositorio() throws Exception {
        // Repositorio com um commit inicial: sem HEAD nascido o JGit nao cria branch.
        try (Git git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
            // Isola o teste da configuracao global da maquina. Quem assina commit com SSH
            // (gpg.format=ssh) quebraria aqui, porque o JGit 6.10 so entende openpgp/x509.
            // A config do proprio repositorio tem precedencia sobre a global.
            StoredConfig config = git.getRepository().getConfig();
            config.setBoolean("commit", null, "gpgsign", false);
            config.setString("gpg", null, "format", "openpgp");
            config.save();

            Files.writeString(repoDir.resolve("README.md"), "# repo de teste");
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("commit inicial")
                    .setAuthor("Teste", "teste@fatec.br")
                    .setSign(false)
                    .call();
        }

        gitClient = new GitClient(repoDir);
        invoker = new CommandInvoker(new CommandRegistry(List.of(
                new GitStatusCommand(gitClient),
                new GitBranchCommand(gitClient),
                new GitCheckoutCommand(gitClient),
                new KnowledgeSearchCommand(new KnowledgeService()))));
        ctx = new AgentContext(Goal.of("Quero criar uma branch feature/login"));
    }

    @Test
    @DisplayName("gitStatus le o repositorio de verdade: limpo e depois sujo")
    void gitStatusRefleteOEstadoReal() throws Exception {
        ExecutionResult limpo = invoker.execute(ctx, "gitStatus", Map.of());

        assertThat(limpo.success()).isTrue();
        assertThat(limpo.output()).contains("Branch atual: main").contains("Working tree limpa");
        assertThat(limpo.errorMessage()).isNull();

        Files.writeString(repoDir.resolve("novo.txt"), "arquivo criado pelo teste");
        ExecutionResult sujo = invoker.execute(ctx, "gitStatus", Map.of());

        assertThat(sujo.success()).isTrue();
        assertThat(sujo.output()).contains("Arquivos nao rastreados").contains("novo.txt");
    }

    @Test
    @DisplayName("gitBranch cria a branch no repositorio e o git enxerga")
    void gitBranchCriaBranchDeVerdade() {
        ExecutionResult resultado = invoker.execute(ctx, "gitBranch", Map.of("branchName", "feature/login"));

        assertThat(resultado.success()).isTrue();
        assertThat(resultado.output()).contains("feature/login").contains("criada");
        // A prova real nao e o texto devolvido, e o estado do repositorio:
        assertThat(gitClient.listBranches()).contains("feature/login");
        assertThat(gitClient.currentBranch()).as("gitBranch cria mas nao muda").isEqualTo("main");
    }

    @Test
    void gitBranchSemArgumentoListaAsBranches() {
        gitClient.createBranch("feature/login");

        ExecutionResult resultado = invoker.execute(ctx, "gitBranch", Map.of());

        assertThat(resultado.success()).isTrue();
        assertThat(resultado.output()).contains("main").contains("feature/login");
    }

    @Test
    void gitCheckoutMudaDeBranch() {
        invoker.execute(ctx, "gitBranch", Map.of("branchName", "feature/login"));

        ExecutionResult resultado = invoker.execute(ctx, "gitCheckout", Map.of("branchName", "feature/login"));

        assertThat(resultado.success()).isTrue();
        assertThat(gitClient.currentBranch()).isEqualTo("feature/login");
    }

    @Test
    @DisplayName("gitCheckout com create=true cria e muda numa operacao so")
    void gitCheckoutComCreateCriaEMuda() {
        ExecutionResult resultado = invoker.execute(
                ctx, "gitCheckout", Map.of("branchName", "feature/nova", "create", true));

        assertThat(resultado.success()).isTrue();
        assertThat(gitClient.currentBranch()).isEqualTo("feature/nova");
    }

    @Test
    void executaAPartirDeUmPlanStep() {
        PlanStep passo = new PlanStep(1, "criar a branch", "gitBranch", Map.of("branchName", "feature/login"));

        ExecutionResult resultado = invoker.execute(ctx, passo);

        assertThat(resultado.success()).isTrue();
        assertThat(gitClient.listBranches()).contains("feature/login");
    }

    @Test
    void knowledgeSearchExplicaConceito() {
        ExecutionResult resultado = invoker.execute(ctx, "knowledgeSearch", Map.of("query", "o que e uma branch"));

        assertThat(resultado.success()).isTrue();
        assertThat(resultado.output()).contains("branch").contains("linha de trabalho paralela");
    }
}
