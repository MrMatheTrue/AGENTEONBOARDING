package br.edu.fatec.onboardingagent.command;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;

import java.util.Map;

/**
 * COMMAND: uma ferramenta do agente, encapsulada com o contexto de execucao.
 *
 * <p>Cada implementacao delega o trabalho a um Receiver real (GitClient, KnowledgeService,
 * GitHubClient). Nenhuma implementacao pode se limitar a chamar o LLM — isso
 * descaracterizaria o padrao.</p>
 */
public interface AgentCommand {

    /** Identificador usado no plano e no registry (ex.: {@code gitStatus}). */
    String name();

    /**
     * Descricao lida pelo LLM na hora de escolher a ferramenta.
     *
     * <p>E o texto que entra no prompt de planejamento, entao precisa dizer o que a
     * ferramenta faz <em>e</em> quais argumentos ela espera. Descricao vaga aqui vira
     * plano invalido la na frente.</p>
     */
    String description();

    /**
     * Executa a ferramenta.
     *
     * <p>Pode lancar excecao a vontade: o CommandInvoker captura e converte em
     * {@link ExecutionResult#failure(String)}.</p>
     *
     * @param ctx  memoria de trabalho da sessao
     * @param args parametros preenchidos pela estrategia
     */
    ExecutionResult execute(AgentContext ctx, Map<String, Object> args);

    /**
     * Marca operacoes irreversiveis (merge na principal, push --force, branch -D, abrir PR).
     *
     * <p>Comando destrutivo dispara o gatilho DESTRUCTIVE_STEP e forca aprovacao humana
     * antes de rodar.</p>
     */
    default boolean isDestructive() {
        return false;
    }
}
