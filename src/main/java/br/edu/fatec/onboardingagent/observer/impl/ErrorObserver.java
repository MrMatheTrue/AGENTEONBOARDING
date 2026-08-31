package br.edu.fatec.onboardingagent.observer.impl;

import br.edu.fatec.onboardingagent.observer.AgentEvent;
import br.edu.fatec.onboardingagent.observer.AgentObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contabiliza as falhas por ferramenta.
 *
 * <p>Observa, nao decide: quem trata a falha e o ErrorState, com limites configurados. Aqui
 * so se acumula a evidencia — util para mostrar qual ferramenta e a mais problematica e
 * para o ErrorPanel da GUI, na FASE 6.</p>
 */
@Component
public class ErrorObserver implements AgentObserver {

    private static final Logger log = LoggerFactory.getLogger(ErrorObserver.class);

    private final Map<String, Integer> falhasPorComando = new LinkedHashMap<>();
    private int totalDeFalhas;

    @Override
    public void onEvent(AgentEvent event) {
        if (event instanceof AgentEvent.CommandFailed falha) {
            falhasPorComando.merge(falha.commandName(), 1, Integer::sum);
            totalDeFalhas++;
            log.warn("Falha #{} em '{}': {}", falhasPorComando.get(falha.commandName()),
                    falha.commandName(), falha.errorMessage());
        }
    }

    public int totalDeFalhas() {
        return totalDeFalhas;
    }

    public int falhasDe(String commandName) {
        return falhasPorComando.getOrDefault(commandName, 0);
    }

    public Map<String, Integer> falhasPorComando() {
        return Map.copyOf(falhasPorComando);
    }
}
