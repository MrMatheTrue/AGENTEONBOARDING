package br.edu.fatec.onboardingagent.command.impl;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.tool.KnowledgeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Consulta a base de conhecimento de Git/GitHub — o lado "ensinar" do agente.
 *
 * <p>Na FASE 7 o Receiver passa a consultar um VectorStore; este comando nao muda.</p>
 */
@Component
public class KnowledgeSearchCommand implements AgentCommand {

    private final KnowledgeService knowledgeService;

    public KnowledgeSearchCommand(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String name() {
        return "knowledgeSearch";
    }

    @Override
    public String description() {
        return "Busca a explicacao de um conceito de Git ou GitHub na base de conhecimento, "
                + "para ensinar o desenvolvedor. "
                + "Argumento obrigatorio: query (texto, ex.: 'o que e uma branch'). "
                + "Use quando o pedido for duvida ou pedido de explicacao, e nao uma operacao no repositorio.";
    }

    @Override
    public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
        Object query = args.get("query");
        if (query == null || query.toString().isBlank()) {
            return ExecutionResult.failure("O argumento 'query' e obrigatorio para knowledgeSearch.");
        }

        List<String> achados = knowledgeService.search(query.toString());
        if (achados.isEmpty()) {
            return ExecutionResult.success(
                    "Nao encontrei esse conceito na base. Conceitos disponiveis: %s"
                            .formatted(String.join(", ", knowledgeService.allConcepts())));
        }
        return ExecutionResult.success(String.join(System.lineSeparator(), achados));
    }
}
