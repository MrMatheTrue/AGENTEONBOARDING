package br.edu.fatec.onboardingagent.observer.impl;

import br.edu.fatec.onboardingagent.domain.LearningJourney;
import br.edu.fatec.onboardingagent.observer.AgentEvent;
import br.edu.fatec.onboardingagent.observer.AgentObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Marca a trilha de aprendizado conforme o desenvolvedor pratica cada assunto.
 *
 * <p>E o que faz deste projeto um agente de onboarding, e nao um executor de comandos: o
 * modulo so e dado como praticado quando a ferramenta correspondente roda <em>com
 * sucesso</em> — tentativa que falhou nao ensina.</p>
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
        }
    }
}
