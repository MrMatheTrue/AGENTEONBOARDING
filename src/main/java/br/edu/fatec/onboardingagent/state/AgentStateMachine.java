package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.command.CommandInvoker;
import br.edu.fatec.onboardingagent.command.CommandRegistry;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CONTEXT do padrao State: guarda o estado corrente e roda o laco de transicoes.
 *
 * <p>Tambem e o dono das colaboracoes que os estados precisam (invoker, registry, limites).
 * Cada estado recebe a maquina no construtor e monta o sucessor a partir dela — por isso
 * nenhum estado precisa carregar meia duzia de parametros.</p>
 *
 * <p>O laco para em duas situacoes: {@link CompletedState} (fim) ou um estado que devolve
 * a si mesmo (sem progresso — tipicamente WAITING_APPROVAL esperando o humano). Sem essa
 * segunda saida, esperar por resposta humana viraria laco infinito.</p>
 */
@Component
public class AgentStateMachine {

    private static final Logger log = LoggerFactory.getLogger(AgentStateMachine.class);

    /** Rede de seguranca contra ciclo infinito (replan que nunca converge, por exemplo). */
    private static final int LIMITE_DE_TRANSICOES = 500;

    private final CommandInvoker invoker;
    private final CommandRegistry registry;
    private final int maxRetries;
    private final int maxReplans;

    /**
     * Trilha dos estados percorridos.
     *
     * <p>Andaime da FASE 3, para o teste conferir a sequencia. Na FASE 5 quem passa a
     * registrar a trilha e o TraceObserver, ouvindo os eventos StateChanged.</p>
     */
    private final List<String> trail = new ArrayList<>();

    public AgentStateMachine(CommandInvoker invoker,
                             CommandRegistry registry,
                             @Value("${agent.max-retries:2}") int maxRetries,
                             @Value("${agent.max-replans:2}") int maxReplans) {
        this.invoker = invoker;
        this.registry = registry;
        this.maxRetries = maxRetries;
        this.maxReplans = maxReplans;
    }

    // ------------------------------------------------------------- execucao

    /** Roda do INIT ate o fim (ou ate bloquear esperando o humano), zerando a trilha. */
    public AgentState run(AgentContext ctx) {
        trail.clear();
        return runFrom(new InitState(this), ctx);
    }

    /**
     * Retoma de um estado especifico, preservando a trilha.
     *
     * <p>E o caminho usado depois que o humano responde: o chamador preenche
     * {@code ctx.submitHumanResponse(...)} e retoma a partir do WAITING_APPROVAL.</p>
     */
    public AgentState resume(AgentState from, AgentContext ctx) {
        return runFrom(from, ctx);
    }

    private AgentState runFrom(AgentState start, AgentContext ctx) {
        AgentState current = start;
        registrar(current);

        int transicoes = 0;
        while (!(current instanceof CompletedState)) {
            AgentState proximo = current.handle(ctx);

            if (proximo == current) {
                // Sem progresso: fim de linha por bloqueio (esperando o humano).
                log.info("Maquina pausada em {}", current.name());
                return current;
            }

            current = proximo;
            registrar(current);

            if (++transicoes > LIMITE_DE_TRANSICOES) {
                throw new IllegalStateException(
                        "A maquina de estados passou de %d transicoes sem concluir - ciclo infinito. Trilha: %s"
                                .formatted(LIMITE_DE_TRANSICOES, trail));
            }
        }
        return current;
    }

    private void registrar(AgentState estado) {
        trail.add(estado.name());
        log.info("Estado: {}", estado.name());
    }

    // ------------------------------------------------------------- acessores

    /** Sequencia de estados percorridos na ultima execucao. */
    public List<String> trail() {
        return List.copyOf(trail);
    }

    CommandInvoker invoker() {
        return invoker;
    }

    CommandRegistry registry() {
        return registry;
    }

    int maxRetries() {
        return maxRetries;
    }

    int maxReplans() {
        return maxReplans;
    }
}
