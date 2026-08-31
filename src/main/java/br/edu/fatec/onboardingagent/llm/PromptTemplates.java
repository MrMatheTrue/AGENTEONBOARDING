package br.edu.fatec.onboardingagent.llm;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Plan;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrato de planejamento com o LLM: monta o prompt <em>e</em> le a resposta.
 *
 * <p>Os dois lados moram juntos de proposito — sao a mesma combinacao. Mudar o formato
 * pedido no prompt sem mudar a leitura seria o jeito mais facil de quebrar o agente.</p>
 *
 * <p>Modelos pequenos (Gemma 2B) erram bastante o formato: enfeitam com ```json, escrevem
 * um paragrafo antes do objeto, devolvem confidence como texto. A leitura aqui e
 * deliberadamente tolerante nesses pontos e rigorosa no resto — o que nao der para ler
 * com seguranca vira escalonamento, nunca chute.</p>
 */
public final class PromptTemplates {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PromptTemplates() {
    }

    /**
     * Resultado da leitura da resposta do LLM.
     *
     * @param plan                plano montado (vazio quando nao deu para montar)
     * @param clarificationNeeded pergunta que o proprio modelo pediu para fazer ao humano
     * @param parseError          motivo tecnico da falha de leitura; nulo quando leu bem
     */
    public record PlanProposal(Plan plan, String clarificationNeeded, String parseError) {

        public boolean needsClarification() {
            return clarificationNeeded != null && !clarificationNeeded.isBlank();
        }

        public boolean failedToParse() {
            return parseError != null;
        }
    }

    // ----------------------------------------------------------------- prompt

    /**
     * Prompt de planejamento. Pede JSON estrito e mostra o catalogo de ferramentas.
     *
     * @param ctx     contexto, de onde saem o objetivo e o historico
     * @param catalog catalogo vindo de {@code CommandRegistry.catalogForPrompt()}
     */
    public static String planning(AgentContext ctx, String catalog) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                Voce planeja operacoes de Git para um desenvolvedor iniciante.

                Responda APENAS com um objeto JSON, sem texto antes ou depois, sem markdown.
                Formato exato:
                {"confidence": 0.0, "steps": [{"description": "...", "commandName": "...", "args": {}}], "clarificationNeeded": null}

                Regras:
                - use somente os commandName da lista de ferramentas abaixo;
                - confidence e a sua confianca no plano, de 0.0 a 1.0;
                - se o pedido for vago ou faltar informacao (por exemplo, o nome da branch),
                  devolva "steps": [] e escreva em clarificationNeeded a pergunta a fazer ao desenvolvedor;
                - nao invente ferramenta que nao esteja na lista.

                Ferramentas disponiveis:
                """);
        prompt.append(catalog).append(System.lineSeparator());

        if (!ctx.history().isEmpty()) {
            prompt.append(System.lineSeparator()).append("O que ja foi executado nesta sessao:").append(System.lineSeparator());
            for (ExecutionResult resultado : ctx.history()) {
                prompt.append("- ")
                        .append(resultado.success() ? "OK: " : "FALHOU: ")
                        .append(resultado.success() ? resumir(resultado.output()) : resultado.errorMessage())
                        .append(System.lineSeparator());
            }
        }

        ctx.humanResponse().ifPresent(resposta -> prompt
                .append(System.lineSeparator())
                .append("O desenvolvedor esclareceu: ").append(resposta).append(System.lineSeparator()));

        prompt.append(System.lineSeparator())
                .append("Pedido do desenvolvedor: ").append(ctx.goal().rawText()).append(System.lineSeparator())
                .append("JSON:");
        return prompt.toString();
    }

    // ---------------------------------------------------------------- leitura

    /** Le a resposta do modelo e devolve o plano, o pedido de esclarecimento ou o erro. */
    public static PlanProposal parsePlanResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return erro("o modelo devolveu resposta vazia");
        }

        String json = extrairObjetoJson(raw);
        if (json == null) {
            return erro("nao encontrei nenhum objeto JSON na resposta do modelo");
        }

        try {
            JsonNode raiz = MAPPER.readTree(json);

            JsonNode clarification = raiz.get("clarificationNeeded");
            String pergunta = clarification == null || clarification.isNull() ? null : clarification.asText();

            double confianca = lerConfianca(raiz.get("confidence"));

            List<PlanStep> passos = new ArrayList<>();
            JsonNode steps = raiz.get("steps");
            if (steps != null && steps.isArray()) {
                int id = 1;
                for (JsonNode passo : steps) {
                    String commandName = texto(passo.get("commandName"));
                    if (commandName == null) {
                        return erro("um passo veio sem commandName");
                    }
                    String descricao = texto(passo.get("description"));
                    passos.add(new PlanStep(
                            id++,
                            descricao == null ? commandName : descricao,
                            commandName,
                            lerArgs(passo.get("args"))));
                }
            }

            return new PlanProposal(new Plan(passos, confianca), pergunta, null);
        } catch (Exception e) {
            return erro("JSON invalido: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ apoio

    /**
     * Recorta do primeiro '{' ao ultimo '}'.
     *
     * <p>Resolve os dois enfeites mais comuns dos modelos pequenos: cerca de markdown e
     * frase de cortesia antes do objeto.</p>
     */
    private static String extrairObjetoJson(String raw) {
        int inicio = raw.indexOf('{');
        int fim = raw.lastIndexOf('}');
        if (inicio < 0 || fim <= inicio) {
            return null;
        }
        return raw.substring(inicio, fim + 1);
    }

    /** Aceita numero ou texto ("0.8"), porque os dois aparecem na pratica. */
    private static double lerConfianca(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0.0;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        try {
            return Double.parseDouble(node.asText().trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static Map<String, Object> lerArgs(JsonNode node) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return args;
        }
        node.fields().forEachRemaining(campo -> {
            JsonNode valor = campo.getValue();
            if (valor.isBoolean()) {
                args.put(campo.getKey(), valor.asBoolean());
            } else if (valor.isNumber()) {
                args.put(campo.getKey(), valor.asDouble());
            } else if (!valor.isNull()) {
                args.put(campo.getKey(), valor.asText());
            }
        });
        return args;
    }

    private static String texto(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String valor = node.asText().trim();
        return valor.isEmpty() ? null : valor;
    }

    private static PlanProposal erro(String motivo) {
        return new PlanProposal(Plan.empty(), null, motivo);
    }

    private static String resumir(String texto) {
        if (texto == null) {
            return "";
        }
        String umaLinha = texto.replaceAll("\\s+", " ").trim();
        return umaLinha.length() <= 160 ? umaLinha : umaLinha.substring(0, 157) + "...";
    }
}
