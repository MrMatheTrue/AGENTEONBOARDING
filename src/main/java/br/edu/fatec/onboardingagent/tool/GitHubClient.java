package br.edu.fatec.onboardingagent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * RECEIVER das operacoes no GitHub, via REST.
 *
 * <p>Separado do GitClient de proposito: o JGit mexe no repositorio local, este fala com o
 * servidor. Sao duas infraestruturas diferentes, e o Command nao precisa saber disso.</p>
 */
@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

    private final RestClient rest;
    private final String owner;
    private final String repo;
    private final String token;

    public GitHubClient(@Value("${github.api-url:https://api.github.com}") String apiUrl,
                        @Value("${github.owner:}") String owner,
                        @Value("${github.repo:}") String repo,
                        @Value("${github.token:}") String token) {
        this.owner = owner;
        this.repo = repo;
        this.token = token;
        this.rest = RestClient.builder().baseUrl(apiUrl).build();
    }

    /** Sem token, dono ou repositorio configurados, nao ha o que tentar. */
    public boolean estaConfigurado() {
        return !token.isBlank() && !owner.isBlank() && !repo.isBlank();
    }

    public String repositorio() {
        return owner + "/" + repo;
    }

    /**
     * Abre um pull request.
     *
     * @param head branch de origem (a sua)
     * @param base branch de destino (normalmente main)
     * @return URL do pull request criado
     */
    @SuppressWarnings("unchecked")
    public String createPullRequest(String title, String head, String base, String body) {
        if (!estaConfigurado()) {
            throw new IllegalStateException(
                    "GitHub nao configurado. Defina github.owner, github.repo e github.token "
                            + "(ou a variavel de ambiente GITHUB_TOKEN) para abrir pull requests.");
        }

        log.info("Abrindo PR em {}: {} -> {}", repositorio(), head, base);
        try {
            Map<String, Object> resposta = rest.post()
                    .uri("/repos/{owner}/{repo}/pulls", owner, repo)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .body(Map.of("title", title, "head", head, "base", base, "body", body == null ? "" : body))
                    .retrieve()
                    .body(Map.class);

            Object url = resposta == null ? null : resposta.get("html_url");
            return url == null ? "pull request criado" : url.toString();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Nao consegui abrir o pull request em %s: %s".formatted(repositorio(), e.getMessage()), e);
        }
    }
}
