package br.edu.fatec.onboardingagent.strategy;

import br.edu.fatec.onboardingagent.command.CommandRegistry;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Plan;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import br.edu.fatec.onboardingagent.domain.StepDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Assume quando o ReAct se declara incapaz. Nao planeja sozinha: pergunta.
 *
 * <p>Terceira diferenca real de comportamento entre as estrategias: aqui todo passo exige
 * aprovacao ({@link #requiresApproval(PlanStep)} sempre true) e a decisao padrao e
 * {@link StepDecision#ESCALATE} — nada anda sem o humano confirmar.</p>
 *
 * <p>Esta classe e a dona da conversa com o humano. Na FASE 4 isso e console; na FASE 6 o
 * ApprovalPanel substitui {@link #askHuman(String)} e mais nada precisa mudar.</p>
 */
@Component
public class HumanInTheLoopStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(HumanInTheLoopStrategy.class);

    private final CommandRegistry registry;

    /** Fonte das respostas do humano. Trocavel para o teste nao depender do teclado. */
    private final BufferedReader entrada;

    // @Autowired explicito: sao dois construtores publicos, e sem a marcacao o Spring
    // nao sabe qual usar.
    @Autowired
    public HumanInTheLoopStrategy(CommandRegistry registry) {
        this(registry, new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
    }

    /** Permite trocar a fonte das respostas — o teste roteia o humano em vez de usar o teclado. */
    public HumanInTheLoopStrategy(CommandRegistry registry, BufferedReader entrada) {
        this.registry = registry;
        this.entrada = entrada;
    }

    @Override
    public String name() {
        return "HumanInTheLoop";
    }

    /**
     * A fonte de entrada, para quem mais precisar ler do humano na mesma sessao.
     *
     * <p>Tem que ser <strong>uma so</strong>: dois BufferedReader sobre o mesmo System.in
     * competem pelo buffer, e a resposta digitada acaba sendo lida pelo outro leitor. Foi
     * exatamente o que aconteceu quando o console tinha o proprio leitor — a resposta ao
     * escalonamento era consumida como se fosse um objetivo novo.</p>
     */
    public BufferedReader input() {
        return entrada;
    }

    /**
     * Faz a pergunta e espera a resposta.
     *
     * @return o que o humano respondeu, ou vazio se nao veio nada
     */
    public String askHuman(String question) {
        System.out.println();
        System.out.println("  >> " + question);
        System.out.print("  sua resposta: ");
        try {
            String resposta = entrada.readLine();
            return resposta == null ? "" : resposta.trim();
        } catch (Exception e) {
            log.warn("Nao consegui ler a resposta do humano: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Nao inventa plano: so aproveita o que o humano respondeu para o ReAct replanejar
     * depois do deescalate. Sem resposta, devolve plano vazio de proposito.
     */
    @Override
    public Plan buildPlan(AgentContext ctx) {
        return ctx.humanResponse().isPresent() ? ctx.plan() : Plan.empty();
    }

    /** Enquanto esta no comando, nada avanca sem confirmacao. */
    @Override
    public StepDecision decideNext(AgentContext ctx, ExecutionResult last) {
        return StepDecision.ESCALATE;
    }

    @Override
    public boolean requiresApproval(PlanStep step) {
        return true;
    }

    @Override
    public Optional<EscalationSignal> escalationSignal(AgentContext ctx) {
        // Ja esta com o humano: repete o sinal em aberto, se houver.
        return ctx.pendingEscalation();
    }

    CommandRegistry registry() {
        return registry;
    }
}
