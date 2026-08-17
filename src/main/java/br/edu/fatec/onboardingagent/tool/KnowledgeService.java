package br.edu.fatec.onboardingagent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RECEIVER da busca na base de conhecimento de Git/GitHub.
 *
 * <p>Na FASE 2 e um stub em memoria: um mapa de conceitos com busca por palavra-chave.
 * Na FASE 7 o mesmo contrato passa a consultar um VectorStore — quem chama
 * (KnowledgeSearchCommand) nao muda.</p>
 */
@Component
public class KnowledgeService {

    /** Conceito -> explicacao curta, em portugues, no tom de quem esta ensinando. */
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

    /**
     * Busca conceitos cujo titulo ou explicacao contenham os termos da pergunta.
     *
     * @return explicacoes encontradas, ou lista vazia quando nada casa
     */
    public List<String> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String pergunta = normalizar(query);

        List<String> achados = CONCEITOS.entrySet().stream()
                .filter(e -> pergunta.contains(e.getKey()) || normalizar(e.getValue()).contains(pergunta))
                .map(e -> "%s: %s".formatted(e.getKey(), e.getValue()))
                .toList();

        if (!achados.isEmpty()) {
            return achados;
        }

        // Nada casou pela chave inteira: tenta palavra a palavra, para perguntas em frase.
        return CONCEITOS.entrySet().stream()
                .filter(e -> temPalavraEmComum(pergunta, e.getKey()))
                .map(e -> "%s: %s".formatted(e.getKey(), e.getValue()))
                .toList();
    }

    /** Todos os conceitos da base, usado para mostrar o que o agente sabe ensinar. */
    public List<String> allConcepts() {
        return List.copyOf(CONCEITOS.keySet());
    }

    private static boolean temPalavraEmComum(String pergunta, String chave) {
        for (String palavra : pergunta.split("\\W+")) {
            if (palavra.length() > 2 && chave.contains(palavra)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizar(String texto) {
        return texto.toLowerCase(Locale.ROOT).trim();
    }
}
