package br.edu.fatec.onboardingagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do acesso ao LLM local.
 *
 * <p>O modelo roda em llama.cpp (servidor com API compativel com a OpenAI), apontado
 * por {@code spring.ai.openai.base-url} no application.yml. Aqui apenas montamos o
 * {@link ChatClient} com o system prompt do agente; nenhuma outra classe do projeto
 * deve falar com o LLM diretamente — a unica porta e o LlmGateway.</p>
 */
@Configuration
public class LlmConfig {

    /** Papel do agente. Mantido curto de proposito: modelos pequenos se perdem em prompts longos. */
    private static final String SYSTEM_PROMPT = """
            Voce e um agente de onboarding que ensina Git e GitHub para desenvolvedores iniciantes.
            Responda sempre em portugues, de forma direta e objetiva.
            Quando o formato da resposta for especificado, siga-o exatamente, sem texto extra.
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
