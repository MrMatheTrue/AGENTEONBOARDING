package br.edu.fatec.onboardingagent.command;

import br.edu.fatec.onboardingagent.command.impl.CreatePullRequestCommand;
import br.edu.fatec.onboardingagent.command.impl.GitAddCommand;
import br.edu.fatec.onboardingagent.command.impl.GitBranchCommand;
import br.edu.fatec.onboardingagent.command.impl.GitCheckoutCommand;
import br.edu.fatec.onboardingagent.command.impl.GitCommitCommand;
import br.edu.fatec.onboardingagent.command.impl.GitPushCommand;
import br.edu.fatec.onboardingagent.command.impl.GitStatusCommand;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.llm.LlmGateway;
import br.edu.fatec.onboardingagent.observer.AgentEventPublisher;
import br.edu.fatec.onboardingagent.observer.impl.TraceObserver;
import br.edu.fatec.onboardingagent.state.AgentState;
import br.edu.fatec.onboardingagent.state.AgentStateMachine;
import br.edu.fatec.onboardingagent.strategy.HumanInTheLoopStrategy;
import br.edu.fatec.onboardingagent.strategy.PlanThenExecuteStrategy;
import br.edu.fatec.onboardingagent.strategy.ReActStrategy;
import br.edu.fatec.onboardingagent.strategy.StrategySelector;
import br.edu.fatec.onboardingagent.tool.GitClient;
import br.edu.fatec.onboardingagent.tool.GitHubClient;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceite da FASE 7: o caso "quero enviar minha alteracao para revisao".
 *
 * <p>Percorre status -> branch -> add -> commit -> push -> pull request, pedindo aprovacao
 * antes do PR. O Git e real (com um remoto local de verdade, para o push acontecer); so o
 * GitHub e o LLM sao dublados.</p>
 */
class EnviarParaRevisaoTest {

    @TempDir
    Path base;

    private Path repo;
    private GitClient gitClient;
    private CommandRegistry registry;
    private final List<String> prsAbertos = new ArrayList<>();

    /** GitHub dublado: registra o PR em vez de chamar a API. */
    private class GitHubDublado extends GitHubClient {
        GitHubDublado() {
            super("http://localhost", "fatec", "onboarding", "token-de-teste");
        }

        @Override
        public String createPullRequest(String title, String head, String base, String body) {
            prsAbertos.add("%s: %s -> %s".formatted(title, head, base));
            return "https://github.com/fatec/onboarding/pull/1";
        }
    }

    private static class LlmRoteirizado extends LlmGateway {
        private final String plano;

        LlmRoteirizado(String plano) {
            super(null);
            this.plano = plano;
        }

        @Override
        public String complete(String prompt) {
            return plano;
        }
    }

    @BeforeEach
    void prepararRepositorioComRemoto() throws Exception {
        repo = base.resolve("trabalho");
        Path remoto = base.resolve("remoto.git");

        // Remoto de verdade, so que local: o push acontece sem precisar de credencial.
        Git.init().setBare(true).setDirectory(remoto.toFile()).call().close();

        Files.createDirectories(repo);
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            StoredConfig config = git.getRepository().getConfig();
            config.setBoolean("commit", null, "gpgsign", false);
            config.setString("gpg", null, "format", "openpgp");
            config.setString("remote", "origin", "url", remoto.toUri().toString());
            config.setString("user", null, "name", "Teste");
            config.setString("user", null, "email", "teste@fatec.br");
            config.save();

            Files.writeString(repo.resolve("README.md"), "# projeto");
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("commit inicial").setSign(false).call();
        }

        gitClient = new GitClient(repo);
        registry = new CommandRegistry(List.of(
                new GitStatusCommand(gitClient),
                new GitBranchCommand(gitClient),
                new GitCheckoutCommand(gitClient),
                new GitAddCommand(gitClient),
                new GitCommitCommand(gitClient),
                new GitPushCommand(gitClient),
                new CreatePullRequestCommand(new GitHubDublado(), gitClient)));
    }

    private static final String PLANO_ENVIAR_PARA_REVISAO = """
            {"confidence": 0.9, "steps": [
              {"description": "ver a situacao do repositorio", "commandName": "gitStatus", "args": {}},
              {"description": "criar e ir para a branch feature/ajuste", "commandName": "gitCheckout",
               "args": {"branchName": "feature/ajuste", "create": true}},
              {"description": "preparar as alteracoes", "commandName": "gitAdd", "args": {"path": "."}},
              {"description": "registrar o commit", "commandName": "gitCommit",
               "args": {"message": "ajuste na documentacao"}},
              {"description": "publicar a branch", "commandName": "gitPush", "args": {"remote": "origin"}},
              {"description": "abrir o pull request", "commandName": "createPullRequest",
               "args": {"title": "Ajuste na documentacao", "base": "main"}}
            ], "clarificationNeeded": null}
            """;

    private record Cenario(AgentStateMachine machine, StrategySelector selector, TraceObserver trace) {
    }

    private Cenario montar(String... respostasDoHumano) {
        TraceObserver trace = new TraceObserver();
        AgentEventPublisher publisher = new AgentEventPublisher(List.of(trace));
        LlmGateway llm = new LlmRoteirizado(PLANO_ENVIAR_PARA_REVISAO);
        StrategySelector selector = new StrategySelector(
                new ReActStrategy(llm, registry, publisher, 0.5, 2),
                new PlanThenExecuteStrategy(llm, registry),
                new HumanInTheLoopStrategy(registry,
                        new BufferedReader(new StringReader(String.join("\n", respostasDoHumano)))),
                publisher);
        return new Cenario(
                new AgentStateMachine(new CommandInvoker(registry, publisher), registry, selector, publisher, 2, 2),
                selector, trace);
    }

    // ------------------------------------------------------------------ aceite

    @Test
    @DisplayName("ACEITE: status -> branch -> add -> commit -> push -> PR, com aprovacao antes do PR")
    void enviarParaRevisaoPercorreOFluxoCompleto() throws Exception {
        Files.writeString(repo.resolve("README.md"), "# projeto\n\nagora com descricao");
        Cenario c = montar("sim");
        AgentContext ctx = new AgentContext(Goal.of("Quero enviar minha alteracao para revisao"));

        AgentState fim = c.machine().run(ctx);

        assertThat(fim.name()).isEqualTo("COMPLETED");

        // O estado real do repositorio, que e a prova que importa:
        assertThat(gitClient.currentBranch()).isEqualTo("feature/ajuste");
        assertThat(prsAbertos).containsExactly("Ajuste na documentacao: feature/ajuste -> main");

        // O commit existe e a branch chegou ao remoto.
        try (Git git = Git.open(repo.toFile())) {
            assertThat(git.log().setMaxCount(1).call().iterator().next().getFullMessage())
                    .isEqualTo("ajuste na documentacao");
            assertThat(git.lsRemote().setRemote("origin").call())
                    .anyMatch(ref -> ref.getName().equals("refs/heads/feature/ajuste"));
        }

        // A aprovacao foi pedida ANTES do PR, e por causa do passo destrutivo.
        assertThat(ctx.escalationHistory()).extracting(EscalationSignal::reason)
                .containsExactly(EscalationSignal.Reason.DESTRUCTIVE_STEP);
        assertThat(ctx.isApproved("createPullRequest")).isTrue();

        String trilha = String.join("\n", c.trace().trilha());
        assertThat(trilha.indexOf("MOTIVO: DESTRUCTIVE_STEP"))
                .as("a pergunta vem antes de o PR ser aberto")
                .isLessThan(trilha.indexOf("EXECUTA createPullRequest"));
    }

    @Test
    @DisplayName("sem autorizacao, o PR nao e aberto - o resto do trabalho fica pronto")
    void semAutorizacaoOPrNaoEhAberto() throws Exception {
        Files.writeString(repo.resolve("README.md"), "# projeto\n\nalterado");
        Cenario c = montar(); // nenhuma resposta do humano
        AgentContext ctx = new AgentContext(Goal.of("Quero enviar minha alteracao para revisao"));

        AgentState fim = c.machine().run(ctx);

        assertThat(fim.name()).isEqualTo("WAITING_APPROVAL");
        assertThat(prsAbertos).as("nada irreversivel sem autorizacao").isEmpty();
        // Mas commit e push ja aconteceram: o trabalho ate ali esta feito.
        assertThat(gitClient.currentBranch()).isEqualTo("feature/ajuste");
        assertThat(ctx.pendingEscalation()).get()
                .extracting(EscalationSignal::reason)
                .isEqualTo(EscalationSignal.Reason.DESTRUCTIVE_STEP);
    }

    @Test
    @DisplayName("abrir PR estando na main falha com orientacao, em vez de tentar")
    void prNaMainEhRecusadoComExplicacao() {
        CreatePullRequestCommand comando = new CreatePullRequestCommand(new GitHubDublado(), gitClient);

        var resultado = comando.execute(new AgentContext(Goal.of("abrir pr")),
                java.util.Map.of("title", "qualquer coisa"));

        assertThat(resultado.isFailure()).isTrue();
        assertThat(resultado.errorMessage()).contains("main").contains("Crie uma branch");
        assertThat(prsAbertos).isEmpty();
    }

    @Test
    void oCreatePullRequestEhOUnicoComandoDestrutivo() {
        assertThat(registry.all().stream().filter(AgentCommand::isDestructive).map(AgentCommand::name))
                .containsExactly("createPullRequest");
    }

    @Test
    @DisplayName("sem token configurado, a falha explica o que falta")
    void semConfiguracaoDoGitHubAFalhaEhExplicativa() {
        GitHubClient semToken = new GitHubClient("http://localhost", "", "", "");

        assertThat(semToken.estaConfigurado()).isFalse();
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> semToken.createPullRequest("t", "h", "b", "")))
                .hasMessageContaining("github.owner")
                .hasMessageContaining("GITHUB_TOKEN");
    }
}
