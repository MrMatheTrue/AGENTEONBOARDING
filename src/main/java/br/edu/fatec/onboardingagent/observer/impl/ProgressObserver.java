package br.edu.fatec.onboardingagent.observer.impl;

import br.edu.fatec.onboardingagent.domain.LearningJourney;
import br.edu.fatec.onboardingagent.observer.AgentEvent;
import br.edu.fatec.onboardingagent.observer.AgentObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Marca a trilha de aprendizado conforme o desenvolvedor pratica cada assunto.
 *
 * <p>E o que faz deste projeto um agente de onboarding, e nao um executor de comandos: o
 * modulo so e dado como praticado quando a ferramenta correspondente roda <em>com
 * sucesso</em> — tentativa que falhou nao ensina.</p>
 *
 * <p>Tambem e quem <strong>persiste</strong> a trilha, em um arquivo de propriedades. A
 * persistencia mora aqui, e nao na LearningJourney, para manter o dominio puro: nada de
 * java.io dentro de {@code domain/}.</p>
 */
@Component
public class ProgressObserver implements AgentObserver {

    private static final Logger log = LoggerFactory.getLogger(ProgressObserver.class);

    /** Ferramenta -> modulo da trilha. Os nomes batem com os de {@link LearningJourney}. */
    private static final Map<String, String> MODULO_DA_FERRAMENTA = Map.of(
            "gitStatus", "Inspecionar o repositorio (git status)",
            "gitBranch", "Trabalhar com branches (git branch, git checkout)",
            "gitCheckout", "Trabalhar com branches (git branch, git checkout)",
            "gitAdd", "Registrar alteracoes (git add, git commit)",
            "gitCommit", "Registrar alteracoes (git add, git commit)",
            "gitPush", "Publicar no remoto (git push)",
            "createPullRequest", "Pedir revisao (pull request)");

    private final Path arquivo;

    public ProgressObserver(
            @Value("${agent.journey-path:${user.home}/.onboarding-agent-journey.properties}") String caminho) {
        this.arquivo = Path.of(caminho);
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!(event instanceof AgentEvent.CommandCompleted concluido) || !concluido.result().success()) {
            return;
        }

        String modulo = MODULO_DA_FERRAMENTA.get(concluido.commandName());
        if (modulo == null) {
            return;
        }

        LearningJourney trilha = concluido.context().journey();
        if (trilha.complete(modulo)) {
            log.info("Modulo concluido: {} ({}% da trilha)", modulo, Math.round(trilha.progress() * 100));
            salvar(trilha);
        }
    }

    /**
     * Recupera a trilha de sessoes anteriores.
     *
     * <p>Se o arquivo nao existir ou estiver ilegivel, comeca do zero: perder o progresso e
     * chato, mas impedir o agente de rodar por causa disso seria pior.</p>
     */
    public LearningJourney carregar() {
        LearningJourney trilha = new LearningJourney();
        if (!Files.isReadable(arquivo)) {
            return trilha;
        }
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            Properties props = new Properties();
            props.load(entrada);
            List<String> concluidos = new ArrayList<>();
            trilha.modules().keySet().forEach(modulo -> {
                if (Boolean.parseBoolean(props.getProperty(modulo, "false"))) {
                    concluidos.add(modulo);
                }
            });
            concluidos.forEach(trilha::complete);
            log.info("Trilha recuperada de {}: {}% concluida", arquivo, Math.round(trilha.progress() * 100));
        } catch (IOException e) {
            log.warn("Nao consegui ler a trilha em {} ({}). Comecando do zero.", arquivo, e.getMessage());
        }
        return trilha;
    }

    private void salvar(LearningJourney trilha) {
        Properties props = new Properties();
        trilha.modules().forEach((modulo, concluido) -> props.setProperty(modulo, String.valueOf(concluido)));
        try {
            if (arquivo.getParent() != null) {
                Files.createDirectories(arquivo.getParent());
            }
            try (OutputStream saida = Files.newOutputStream(arquivo)) {
                props.store(saida, "Trilha de aprendizado do GitHub Onboarding Agent");
            }
        } catch (IOException e) {
            log.warn("Nao consegui gravar a trilha em {}: {}", arquivo, e.getMessage());
        }
    }
}
