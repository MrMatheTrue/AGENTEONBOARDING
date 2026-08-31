package br.edu.fatec.onboardingagent.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A busca na base de conhecimento nos dois modos: com banco vetorial e sem.
 *
 * <p>O modelo de embeddings e dublado — o teste nao pode depender de o llama.cpp estar no
 * ar. O vetor e montado por presenca de palavras-chave, o que torna a similaridade
 * previsivel e o teste deterministico.</p>
 */
class KnowledgeServiceTest {

    /** Palavras que viram dimensoes do vetor. */
    private static final List<String> DIMENSOES = List.of(
            "branch", "commit", "push", "pull request", "status", "merge", "clone", "staging");

    /**
     * Embeddings de mentira, porem coerentes: textos que falam dos mesmos assuntos
     * produzem vetores parecidos, que e tudo o que a busca por similaridade precisa.
     */
    private static class EmbeddingDeMentira implements EmbeddingModel {

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }

        @Override
        public float[] embed(String texto) {
            String minusculo = texto.toLowerCase(Locale.ROOT);
            // Uma dimensao a mais, sempre preenchida: o SimpleVectorStore recusa vetor de
            // norma zero, e um trecho pode nao conter nenhuma das palavras-chave.
            float[] vetor = new float[DIMENSOES.size() + 1];
            for (int i = 0; i < DIMENSOES.size(); i++) {
                vetor[i] = minusculo.contains(DIMENSOES.get(i)) ? 1f : 0f;
            }
            vetor[DIMENSOES.size()] = 0.1f;
            return vetor;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> saida = new ArrayList<>();
            List<String> entradas = request.getInstructions();
            for (int i = 0; i < entradas.size(); i++) {
                saida.add(new Embedding(embed(entradas.get(i)), i));
            }
            return new EmbeddingResponse(saida);
        }

        @Override
        public int dimensions() {
            return DIMENSOES.size() + 1;
        }
    }

    private static final Resource DOCUMENTO = new ClassPathResource("knowledge/git-github.md");

    @Test
    @DisplayName("com banco vetorial, a busca devolve a secao certa do documento")
    void buscaVetorialEncontraASecao() {
        VectorStore store = SimpleVectorStore.builder(new EmbeddingDeMentira()).build();
        KnowledgeService servico = new KnowledgeService(store, DOCUMENTO);

        List<String> achados = servico.search("o que e um pull request");

        assertThat(servico.usandoBancoVetorial()).isTrue();
        assertThat(achados).isNotEmpty();
        assertThat(achados.get(0)).containsIgnoringCase("pull request");
    }

    @Test
    @DisplayName("a ingestao fatia o markdown por secao e roda uma vez so")
    void ingestaoAcontecUmaVezSo() {
        SimpleVectorStore store = SimpleVectorStore.builder(new EmbeddingDeMentira()).build();
        KnowledgeService servico = new KnowledgeService(store, DOCUMENTO);

        servico.search("branch");
        servico.search("commit");

        // Se a ingestao repetisse a cada busca, o acervo teria o dobro de trechos.
        assertThat(store.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query("branch").topK(50).similarityThresholdAll().build()))
                .hasSize(12);
    }

    @Test
    @DisplayName("sem embeddings disponiveis, o agente segue com o acervo em memoria")
    void semBancoVetorialCaiNoAcervo() {
        KnowledgeService servico = new KnowledgeService(null, null);

        List<String> achados = servico.search("o que e uma branch");

        assertThat(servico.usandoBancoVetorial()).isFalse();
        assertThat(achados).isNotEmpty();
        assertThat(achados.get(0)).contains("linha de trabalho paralela");
    }

    @Test
    @DisplayName("embeddings que explodem nao derrubam a busca")
    void falhaNoEmbeddingCaiNoAcervo() {
        EmbeddingModel quebrado = new EmbeddingDeMentira() {
            @Override
            public float[] embed(Document document) {
                throw new IllegalStateException("404 em /v1/embeddings");
            }
        };
        KnowledgeService servico = new KnowledgeService(
                SimpleVectorStore.builder(quebrado).build(), DOCUMENTO);

        List<String> achados = servico.search("o que e uma branch");

        assertThat(servico.usandoBancoVetorial()).isFalse();
        assertThat(achados).isNotEmpty();
    }

    @Test
    void perguntaVaziaNaoBuscaNada() {
        assertThat(new KnowledgeService(null, null).search("  ")).isEmpty();
    }
}
