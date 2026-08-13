package br.edu.fatec.onboardingagent.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Catalogo de ferramentas disponiveis: nome -> comando.
 *
 * <p>O Spring injeta todos os {@link AgentCommand} descobertos como {@code List}, entao
 * criar um comando novo e so anota-lo com {@code @Component} — nada aqui precisa mudar.</p>
 *
 * <p>Duas responsabilidades no fluxo: achar o comando que o plano pediu (ExecutingState) e
 * dizer quais nomes existem, para {@code Plan.isValid(names())} reprovar plano que cita
 * ferramenta inventada pelo LLM.</p>
 */
@Component
public class CommandRegistry {

    private final Map<String, AgentCommand> commands = new LinkedHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(CommandRegistry.class);

    public CommandRegistry(List<AgentCommand> discovered) {
        for (AgentCommand command : discovered) {
            AgentCommand anterior = commands.put(command.name(), command);
            if (anterior != null) {
                throw new IllegalStateException(
                        "Dois comandos registrados com o nome '%s': %s e %s".formatted(
                                command.name(),
                                anterior.getClass().getSimpleName(),
                                command.getClass().getSimpleName()));
            }
        }
        log.info("Ferramentas registradas ({}): {}", commands.size(), commands.keySet());
    }

    public Optional<AgentCommand> find(String name) {
        return Optional.ofNullable(commands.get(name));
    }

    public boolean contains(String name) {
        return commands.containsKey(name);
    }

    /** Nomes conhecidos — e exatamente o que {@code Plan.isValid(Set)} espera receber. */
    public Set<String> names() {
        return Set.copyOf(commands.keySet());
    }

    public Collection<AgentCommand> all() {
        return List.copyOf(commands.values());
    }

    public int size() {
        return commands.size();
    }

    /**
     * Catalogo em texto, uma ferramenta por linha, para entrar no prompt de planejamento
     * da FASE 4.
     */
    public String catalogForPrompt() {
        StringBuilder texto = new StringBuilder();
        commands.values().forEach(command -> texto
                .append("- ").append(command.name())
                .append(": ").append(command.description())
                .append(System.lineSeparator()));
        return texto.toString().stripTrailing();
    }
}
