package br.edu.fatec.onboardingagent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RECEIVER da busca na base de conhecimento de Git/GitHub.
 *
 * <p>Consulta o banco vetorial (RAG). Se o modelo de embeddings nao estiver disponivel —
 * o llama.cpp precisa ter sido subido com suporte a embeddings, e nem todo modelo tem —,
 * cai no acervo em memoria. O agente continua ensinando de qualquer jeito; o que muda e a
 * qualidade da busca, nao a existencia dela.</p>
 *
 * <p>A ingestao e preguicosa e acontece uma unica vez, na primeira consulta.</p>
 */
@Component
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    /** Acervo de reserva, usado quando o banco vetorial nao esta disponivel. */
    private static final Map<String, String> CONCEITOS = new LinkedHashMap<>();

    static {
        CONCEITOS.put("status",
                "git status mostra em que branch voce esta e quais arquivos foram alterados, "
                        + "separando o que ja esta preparado para commit do que ainda nao esta.");
        CONCEITOS.put("branch",
                "Uma branch e uma linha de trabalho paralela. Voce cria uma para desenvolver algo "
                        + "sem mexer na principal. 'git branch nome' cria; 'git branch' lista as existentes.");
        CONCEITOS.put("checkout",
                "git checkout troca a branch ativa. Com -b (git checkout -b nome) ele cria a branch "
                        + "e ja muda para ela na mesma operacao.");
        CONCEITOS.put("commit",
                "Um commit e um ponto salvo no historico, com uma mensagem que explica a mudanca. "
                        + "Antes de commitar e preciso preparar os arquivos com git add.");
        CONCEITOS.put("add",
                "git add move as alteracoes para a area de preparacao (staging). So o que esta "
                        + "preparado entra no proximo commit.");
        CONCEITOS.put("push",
                "git push envia os commits locais para o repositorio remoto, publicando seu trabalho.");
        CONCEITOS.put("pull request",
                "Um pull request pede que sua branch seja revisada e incorporada a principal. "
                        + "E onde acontece a revisao de codigo no GitHub.");
        CONCEITOS.put("merge",
                "git merge junta o historico de duas branches. Quando as duas mexeram na mesma linha, "
                        + "aparece um conflito, que precisa ser resolvido a mao.");
        CONCEITOS.put("remoto",
                "O remoto (origin, normalmente) e a copia do repositorio hospedada no servidor, "
                        + "por onde o time troca codigo.");
        CONCEITOS.put("clone",
                "git clone baixa um repositorio remoto inteiro, com todo o historico, para a sua maquina.");
    }

    private final VectorStore vectorStore;
    private final Resource documento;

    /** Estados possiveis da ingestao, para nao tentar de novo a cada busca. */
    private enum Ingestao {
        PENDENTE, PRONTA, INDISPONIVEL
    }

    private Ingestao ingestao = Ingestao.PENDENTE;

    public KnowledgeService(VectorStore vectorStore, Resource knowledgeDocument) {
        this.vectorStore = vectorStore;
        this.documento = knowledgeDocument;
    }

    /**
     * Busca explicacoes para a pergunta.
     *
     * @return trechos encontrados, ou lista vazia quando nada casa
     */
    public List<String> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        if (garantirIngestao()) {
            try {
                List<Document> achados = vectorStore.similaritySearch(
                        SearchRequest.builder().query(query).topK(3).build());
                if (achados != null && !achados.isEmpty()) {
                    return achados.stream().map(Document::getText).toList();
                }
            } catch (Exception e) {
                log.warn("Busca vetorial falhou ({}). Usando o acervo em memoria.", e.getMessage());
            }
        }
        return buscarNoAcervo(query);
    }

    /** Conceitos do acervo de reserva, usados para dizer o que o agente sabe ensinar. */
    public List<String> allConcepts() {
        return List.copyOf(CONCEITOS.keySet());
    }

    /** Indica se a busca esta usando o banco vetorial ou o acervo em memoria. */
    public boolean usandoBancoVetorial() {
        return ingestao == Ingestao.PRONTA;
    }

    // -------------------------------------------------------------- ingestao

    private boolean garantirIngestao() {
        if (ingestao != Ingestao.PENDENTE) {
            return ingestao == Ingestao.PRONTA;
        }
        if (vectorStore == null || documento == null) {
            // Sem banco vetorial configurado: o acervo em memoria assume desde o inicio.
            ingestao = Ingestao.INDISPONIVEL;
            return false;
        }
        try {
            List<Document> documentos = fatiarPorSecao();
            vectorStore.add(documentos);
            ingestao = Ingestao.PRONTA;
            log.info("Base de conhecimento ingerida: {} trecho(s)", documentos.size());
        } catch (Exception e) {
            // Tipico: o llama.cpp nao expoe /v1/embeddings para o modelo carregado.
            ingestao = Ingestao.INDISPONIVEL;
            log.warn("Nao foi possivel montar o banco vetorial ({}). O agente segue com o acervo em memoria.",
                    e.getMessage());
        }
        return ingestao == Ingestao.PRONTA;
    }

    /**
     * Quebra o markdown em um documento por secao ({@code ## titulo}).
     *
     * <p>Fatiar por secao, e nao por numero de caracteres, mantem cada trecho com um
     * assunto so — que e o que faz a busca devolver resposta util em vez de meio paragrafo.</p>
     */
    private List<Document> fatiarPorSecao() throws IOException {
        String texto = new String(documento.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        List<Document> documentos = new ArrayList<>();

        for (String secao : texto.split("(?m)^## ")) {
            String trecho = secao.strip();
            if (trecho.isEmpty() || trecho.startsWith("# ")) {
                continue;
            }
            String titulo = trecho.lines().findFirst().orElse("").strip();
            documentos.add(new Document(trecho, Map.of("secao", titulo)));
        }
        return documentos;
    }

    // ---------------------------------------------------------- acervo local

    private List<String> buscarNoAcervo(String query) {
        String pergunta = query.toLowerCase(Locale.ROOT).trim();

        List<String> achados = CONCEITOS.entrySet().stream()
                .filter(e -> pergunta.contains(e.getKey())
                        || e.getValue().toLowerCase(Locale.ROOT).contains(pergunta))
                .map(e -> "%s: %s".formatted(e.getKey(), e.getValue()))
                .toList();
        if (!achados.isEmpty()) {
            return achados;
        }

        return CONCEITOS.entrySet().stream()
                .filter(e -> temPalavraEmComum(pergunta, e.getKey()))
                .map(e -> "%s: %s".formatted(e.getKey(), e.getValue()))
                .toList();
    }

    private static boolean temPalavraEmComum(String pergunta, String chave) {
        for (String palavra : pergunta.split("\\W+")) {
            if (palavra.length() > 2 && chave.contains(palavra)) {
                return true;
            }
        }
        return false;
    }
}
