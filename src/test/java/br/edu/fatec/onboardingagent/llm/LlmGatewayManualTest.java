package br.edu.fatec.onboardingagent.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de aceite da FASE 0.
 *
 * <p>O contexto Spring sobe sempre. A ida ao modelo so roda quando o llama.cpp estiver
 * de pe, porque depende de servico externo:</p>
 *
 * <pre>
 * llama-server -m gemma-2-2b-it-Q4_K_M.gguf --port 8080 -c 4096
 * mvn test -Dllm.local=true
 * </pre>
 */
@SpringBootTest
class LlmGatewayManualTest {

    @Autowired
    private LlmGateway llmGateway;

    @Test
    void contextoSobeComOGatewayRegistrado() {
        assertThat(llmGateway).isNotNull();
    }

    @Test
    @EnabledIfSystemProperty(named = "llm.local", matches = "true")
    void modeloLocalRespondeEmTexto() {
        String resposta = llmGateway.complete("Em uma frase: para que serve o comando git status?");

        System.out.println("[LLM] " + resposta);
        assertThat(resposta).isNotBlank();
    }
}
