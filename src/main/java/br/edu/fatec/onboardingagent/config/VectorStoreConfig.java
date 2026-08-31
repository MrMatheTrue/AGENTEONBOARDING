package br.edu.fatec.onboardingagent.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Banco vetorial em memoria com a base de conhecimento de Git/GitHub.
 *
 * <p>O bean e criado vazio; a ingestao acontece na primeira busca, dentro do
 * KnowledgeService. E deliberado: gerar os embeddings exige o modelo local no ar, e nao
 * faz sentido impedir a aplicacao de subir por causa disso. Se o modelo nao responder, a
 * busca cai no acervo em memoria e o agente continua ensinando.</p>
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore knowledgeVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /** O arquivo que alimenta o banco vetorial. */
    @Bean
    public Resource knowledgeDocument(
            @org.springframework.beans.factory.annotation.Value("classpath:knowledge/git-github.md") Resource recurso) {
        return recurso;
    }
}
