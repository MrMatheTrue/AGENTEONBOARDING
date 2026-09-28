package br.edu.fatec.onboardingagent.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Component;

/**
 * Unica porta de saida para o LLM.
 *
 * <p>Nenhuma classe de dominio, estado, comando ou estrategia deve conhecer o
 * {@link ChatClient} ou o Spring AI. Todas passam por aqui — isso mantem o resto do
 * projeto testavel sem modelo carregado e deixa a troca de LLM a um unico ponto.</p>
 */
@Component
public class LlmGateway {

    /**
     * Forca o llama-server a restringir a amostragem a JSON valido (gramatica GBNF interna
     * do llama.cpp). Modelos pequenos como o Gemma 2B erram o formato do plano com frequencia
     * quando pedem apenas por instrucao no prompt; isso corta esse erro na origem, sem
     * depender do modelo "obedecer" o pedido em texto livre.
     */
    private static final OpenAiChatOptions JSON_OPTIONS = OpenAiChatOptions.builder()
            .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
            .build();

    private final ChatClient chatClient;

    public LlmGateway(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Envia o prompt ao modelo local e devolve a resposta em texto puro.
     *
     * @param prompt texto ja montado pelo chamador
     * @return resposta do modelo, ou string vazia se o modelo nao devolver conteudo
     */
    public String complete(String prompt) {
        String resposta = chatClient.prompt()
                .user(prompt)
                .options(JSON_OPTIONS)
                .call()
                .content();
        return resposta == null ? "" : resposta;
    }
}
