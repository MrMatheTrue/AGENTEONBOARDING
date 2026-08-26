package br.edu.fatec.onboardingagent.strategy;

import br.edu.fatec.onboardingagent.command.CommandInvoker;
import br.edu.fatec.onboardingagent.command.CommandRegistry;
import br.edu.fatec.onboardingagent.command.impl.GitBranchCommand;
import br.edu.fatec.onboardingagent.command.impl.GitCheckoutCommand;
import br.edu.fatec.onboardingagent.command.impl.GitStatusCommand;
import br.edu.fatec.onboardingagent.command.impl.KnowledgeSearchCommand;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.llm.LlmGateway;
import br.edu.fatec.onboardingagent.state.AgentState;
import br.edu.fatec.onboardingagent.state.AgentStateMachine;
import br.edu.fatec.onboardingagent.tool.GitClient;
import br.edu.fatec.onboardingagent.tool.KnowledgeService;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceite da FASE 4 — os dois casos do CLAUDE.md, ponta a ponta.
 *
 * <p>O LLM e um duble que devolve JSON roteirizado; o Git e de verdade, num repositorio
 * temporario. Assim o teste roda no CI sem llama.cpp e ainda assim exercita o caminho
 * completo: Strategy planeja, State avanca, Command executa.</p>
 */
class StrategyEscalationTest {

    @TempDir
    Path repoDir;

    private CommandRegistry registry;
    private GitClient gitClient;

    /** LLM roteirizado: devolve as respostas na ordem em que forem pedidas. */
    private static class LlmRoteirizado extends LlmGateway {
        private final Deque<String> respostas = new ArrayDeque<>();
        int chamadas;

        LlmRoteirizado(String... respostas) {
            super(null);
            this.respostas.addAll(List.of(respostas));
        }

        @Override
        public String complete(String prompt) {
            chamadas++;
            return respostas.isEmpty() ? "{}" : respostas.poll();
        }
    }

    @BeforeEach
    void prepararRepositorio() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
            StoredConfig config = git.getRepository().getConfig();
            config.setBoolean("commit", null, "gpgsign", false);
            config.setString("gpg", null, "format", "openpgp");
            config.save();

            Files.writeString(repoDir.resolve("README.md"), "# repo de teste");
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("commit inicial").setAuthor("Teste", "teste@fatec.br").setSign(false).call();
        }

        gitClient = new GitClient(repoDir);
        registry = new CommandRegistry(List.of(
                new GitStatusCommand(gitClient),
                new GitBranchCommand(gitClient),
                new GitCheckoutCommand(gitClient),
                new KnowledgeSearchCommand(new KnowledgeService())));
    }

    private AgentStateMachine montar(LlmGateway llm, StrategySelector[] saida, String... respostasDoHumano) {
        BufferedReader humano = new BufferedReader(new StringReader(String.join("\n", respostasDoHumano)));
        StrategySelector selector = new StrategySelector(
                new ReActStrategy(llm, registry, 0.5, 2),
                new PlanThenExecuteStrategy(llm, registry),
                new HumanInTheLoopStrategy(registry, humano));
        saida[0] = selector;
        return new AgentStateMachine(new CommandInvoker(registry), registry, selector, 2, 2);
    }

    // ------------------------------------------------------------- caso feliz

    @Test
    @DisplayName("CASO FELIZ: 'criar branch feature/login' -> ReAct planeja, State avanca, Git executa")
    void casoFeliz() {
        LlmRoteirizado llm = new LlmRoteirizado("""
                {"confidence": 0.9,
                 "steps": [
                   {"description": "ver a situacao do repositorio", "commandName": "gitStatus", "args": {}},
                   {"description": "criar a branch feature/login", "commandName": "gitBranch", "args": {"branchName": "feature/login"}}
                 ],
                 "clarificationNeeded": null}
                """);
        StrategySelector[] selector = new StrategySelector[1];
        AgentStateMachine machine = montar(llm, selector);
        AgentContext ctx = new AgentContext(Goal.of("Quero criar uma branch feature/login"));

        AgentState fim = machine.run(ctx);

        assertThat(fim.name()).isEqualTo("COMPLETED");
        assertThat(machine.trail()).containsExactly(
                "INIT", "PLANNING",
                "EXECUTING", "OBSERVING",
                "EXECUTING", "OBSERVING",
                "COMPLETED");
        // A prova real: a branch existe no repositorio.
        assertThat(gitClient.listBranches()).contains("feature/login");
        assertThat(ctx.isEscalated()).isFalse();
        assertThat(selector[0].active().name()).isEqualTo("ReAct");
    }

    // --------------------------------------------------------- escalonamento

    @Test
    @DisplayName("CASO ESCALONAMENTO: pedido ambiguo -> ESCALATE -> HITL pergunta -> resposta -> deescalate -> COMPLETED")
    void casoEscalonamento() {
        LlmRoteirizado llm = new LlmRoteirizado(
                // 1a chamada: o modelo percebe a ambiguidade e pede esclarecimento
                """
                {"confidence": 0.2, "steps": [], "clarificationNeeded": "O que exatamente voce quer arrumar no repositorio?"}
                """,
                // 2a chamada: ja com a resposta do humano no contexto, planeja
                """
                {"confidence": 0.9,
                 "steps": [{"description": "ver a situacao do repositorio", "commandName": "gitStatus", "args": {}}],
                 "clarificationNeeded": null}
                """);
        StrategySelector[] selector = new StrategySelector[1];
        AgentStateMachine machine = montar(llm, selector, "quero ver o status do repositorio");
        AgentContext ctx = new AgentContext(Goal.of("Arruma ai o meu repositorio"));

        AgentState fim = machine.run(ctx);

        assertThat(machine.trail()).containsExactly(
                "INIT", "PLANNING",
                "WAITING_APPROVAL",
                "PLANNING",
                "EXECUTING", "OBSERVING",
                "COMPLETED");
        assertThat(fim.name()).isEqualTo("COMPLETED");

        // O motivo do escalonamento ficou registrado, com a pergunta que o modelo formulou.
        assertThat(ctx.escalationHistory()).hasSize(1);
        assertThat(ctx.escalationHistory().get(0).reason())
                .isEqualTo(EscalationSignal.Reason.AMBIGUOUS_INPUT);
        assertThat(ctx.escalationHistory().get(0).questionToHuman())
                .contains("O que exatamente voce quer arrumar");

        // Trocou para HITL e voltou para o ReAct — uma ativa por vez.
        assertThat(selector[0].active().name()).as("deescalate devolveu o comando").isEqualTo("ReAct");
        assertThat(ctx.isEscalated()).isFalse();
        assertThat(llm.chamadas).as("replanejou com a resposta do humano").isEqualTo(2);
    }

    @Test
    @DisplayName("a resposta do humano entra no prompt do replanejamento")
    void respostaDoHumanoChegaAoPrompt() {
        StringBuilder segundoPrompt = new StringBuilder();
        LlmGateway llm = new LlmGateway(null) {
            private int chamadas;

            @Override
            public String complete(String prompt) {
                chamadas++;
                if (chamadas == 1) {
                    return "{\"confidence\": 0.1, \"steps\": [], \"clarificationNeeded\": \"Qual branch?\"}";
                }
                segundoPrompt.append(prompt);
                return "{\"confidence\": 0.9, \"steps\": [{\"description\": \"status\", \"commandName\": \"gitStatus\", \"args\": {}}]}";
            }
        };
        StrategySelector[] selector = new StrategySelector[1];
        AgentStateMachine machine = montar(llm, selector, "a branch feature/login");

        machine.run(new AgentContext(Goal.of("cria a branch pra mim")));

        assertThat(segundoPrompt.toString()).contains("O desenvolvedor esclareceu: a branch feature/login");
    }

    @Test
    @DisplayName("sem resposta do humano a maquina pausa em WAITING_APPROVAL, sem executar nada")
    void semRespostaAMaquinaPausa() {
        LlmRoteirizado llm = new LlmRoteirizado(
                "{\"confidence\": 0.2, \"steps\": [], \"clarificationNeeded\": \"O que voce quer fazer?\"}");
        StrategySelector[] selector = new StrategySelector[1];
        AgentStateMachine machine = montar(llm, selector); // nenhuma resposta roteirizada
        AgentContext ctx = new AgentContext(Goal.of("faz algo ai"));

        AgentState fim = machine.run(ctx);

        assertThat(fim.name()).isEqualTo("WAITING_APPROVAL");
        assertThat(ctx.history()).as("nenhum comando rodou").isEmpty();
        assertThat(selector[0].active().name()).as("segue com o humano").isEqualTo("HumanInTheLoop");
        assertThat(selector[0].isEscalated()).isTrue();
    }

    @Test
    @DisplayName("modelo fora do ar vira pergunta ao humano, nao excecao")
    void modeloForaDoArEscala() {
        LlmGateway llmQuebrado = new LlmGateway(null) {
            @Override
            public String complete(String prompt) {
                throw new IllegalStateException("Connection refused: localhost/127.0.0.1:8080");
            }
        };
        StrategySelector[] selector = new StrategySelector[1];
        AgentStateMachine machine = montar(llmQuebrado, selector);
        AgentContext ctx = new AgentContext(Goal.of("Quero criar uma branch"));

        AgentState fim = machine.run(ctx);

        assertThat(fim.name()).isEqualTo("WAITING_APPROVAL");
        assertThat(ctx.pendingEscalation()).get()
                .extracting(EscalationSignal::questionToHuman).asString()
                .contains("llama-server");
    }
}
