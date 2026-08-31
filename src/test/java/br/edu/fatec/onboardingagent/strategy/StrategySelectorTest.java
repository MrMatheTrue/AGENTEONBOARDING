package br.edu.fatec.onboardingagent.strategy;

import br.edu.fatec.onboardingagent.command.AgentCommand;
import br.edu.fatec.onboardingagent.command.CommandRegistry;
import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.domain.StepDecision;
import br.edu.fatec.onboardingagent.llm.LlmGateway;
import br.edu.fatec.onboardingagent.observer.AgentEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Os gatilhos de escalonamento e a troca de estrategia em runtime. */
class StrategySelectorTest {

    private static final CommandRegistry REGISTRY = new CommandRegistry(List.of(comando("gitStatus"), comando("gitBranch")));

    private static AgentCommand comando(String nome) {
        return new AgentCommand() {
            @Override
            public String name() {
                return nome;
            }

            @Override
            public String description() {
                return "ferramenta " + nome;
            }

            @Override
            public ExecutionResult execute(AgentContext ctx, Map<String, Object> args) {
                return ExecutionResult.success("ok");
            }
        };
    }

    private static LlmGateway llmQueResponde(String resposta) {
        return new LlmGateway(null) {
            @Override
            public String complete(String prompt) {
                return resposta;
            }
        };
    }

    private static AgentEventPublisher publisher() {
        return new AgentEventPublisher(List.of());
    }

    private static ReActStrategy reAct(String respostaDoModelo) {
        return new ReActStrategy(llmQueResponde(respostaDoModelo), REGISTRY, publisher(), 0.5, 2);
    }

    private static StrategySelector selector(ReActStrategy reAct) {
        return new StrategySelector(
                reAct,
                new PlanThenExecuteStrategy(llmQueResponde("{}"), REGISTRY),
                new HumanInTheLoopStrategy(REGISTRY, new BufferedReader(new StringReader(""))),
                publisher());
    }

    // ------------------------------------------------------- leitura da resposta

    @Test
    @DisplayName("JSON limpo vira plano valido")
    void jsonLimpoViraPlano() {
        ReActStrategy strategy = reAct("""
                {"confidence": 0.9, "steps": [{"description": "ver status", "commandName": "gitStatus", "args": {}}], "clarificationNeeded": null}
                """);

        var plan = strategy.buildPlan(new AgentContext(Goal.of("ver o status")));

        assertThat(plan.isValid(REGISTRY.names())).isTrue();
        assertThat(plan.size()).isEqualTo(1);
        assertThat(plan.confidence()).isEqualTo(0.9);
        assertThat(strategy.escalationSignal(new AgentContext(Goal.of("x")))).isEmpty();
    }

    @Test
    @DisplayName("modelo pequeno enfeita com cerca de markdown e conversa - ainda assim lemos o plano")
    void respostaEnfeitadaAindaEhLida() {
        ReActStrategy strategy = reAct("""
                Claro! Aqui esta o plano:
                ```json
                {"confidence": "0.8", "steps": [{"description": "criar branch", "commandName": "gitBranch", "args": {"branchName": "feature/login"}}]}
                ```
                Espero ter ajudado!
                """);

        var plan = strategy.buildPlan(new AgentContext(Goal.of("criar branch")));

        assertThat(plan.isValid(REGISTRY.names())).isTrue();
        assertThat(plan.confidence()).as("confianca como texto tambem e aceita").isEqualTo(0.8);
        assertThat(plan.steps().get(0).args()).containsEntry("branchName", "feature/login");
    }

    // ----------------------------------------------------------- os gatilhos

    @Test
    @DisplayName("gatilho #6 AMBIGUOUS_INPUT: o modelo pede esclarecimento")
    void clarificationViraAmbiguousInput() {
        ReActStrategy strategy = reAct(
                "{\"confidence\": 0.2, \"steps\": [], \"clarificationNeeded\": \"Qual o nome da branch?\"}");
        AgentContext ctx = new AgentContext(Goal.of("cria uma branch"));

        var plan = strategy.buildPlan(ctx);

        assertThat(plan.size()).isZero();
        assertThat(strategy.escalationSignal(ctx)).get()
                .satisfies(sinal -> {
                    assertThat(sinal.reason()).isEqualTo(EscalationSignal.Reason.AMBIGUOUS_INPUT);
                    assertThat(sinal.questionToHuman()).isEqualTo("Qual o nome da branch?");
                });
    }

    @Test
    @DisplayName("gatilho #1 NO_VALID_PLAN: resposta ilegivel")
    void respostaIlegivelViraNoValidPlan() {
        ReActStrategy strategy = reAct("desculpe, nao entendi o pedido");
        AgentContext ctx = new AgentContext(Goal.of("faz algo"));

        strategy.buildPlan(ctx);

        assertThat(strategy.escalationSignal(ctx)).get()
                .extracting(EscalationSignal::reason)
                .isEqualTo(EscalationSignal.Reason.NO_VALID_PLAN);
    }

    @Test
    @DisplayName("gatilho #1 NO_VALID_PLAN: o modelo inventou uma ferramenta")
    void ferramentaInventadaViraNoValidPlan() {
        ReActStrategy strategy = reAct(
                "{\"confidence\": 0.95, \"steps\": [{\"description\": \"teletransporte\", \"commandName\": \"gitTeleport\", \"args\": {}}]}");
        AgentContext ctx = new AgentContext(Goal.of("faz magica"));

        strategy.buildPlan(ctx);

        assertThat(strategy.escalationSignal(ctx)).get()
                .satisfies(sinal -> {
                    assertThat(sinal.reason()).isEqualTo(EscalationSignal.Reason.NO_VALID_PLAN);
                    assertThat(sinal.questionToHuman()).contains("gitTeleport");
                });
    }

    @Test
    @DisplayName("gatilho #2 LOW_CONFIDENCE: plano legivel, mas o modelo nao confia nele")
    void confiancaBaixaViraLowConfidence() {
        ReActStrategy strategy = reAct(
                "{\"confidence\": 0.3, \"steps\": [{\"description\": \"ver status\", \"commandName\": \"gitStatus\", \"args\": {}}]}");
        AgentContext ctx = new AgentContext(Goal.of("sei la, ve ai"));

        var plan = strategy.buildPlan(ctx);

        assertThat(plan.size()).as("plano abaixo do limiar nao e executado").isZero();
        assertThat(strategy.escalationSignal(ctx)).get()
                .extracting(EscalationSignal::reason)
                .isEqualTo(EscalationSignal.Reason.LOW_CONFIDENCE);
    }

    // -------------------------------------------------- troca em runtime

    @Test
    @DisplayName("escalate troca a referencia ativa; deescalate a devolve - nunca duas ativas")
    void trocaEmRuntimeMantemUmaAtiva() {
        ReActStrategy reAct = reAct("{}");
        StrategySelector selector = selector(reAct);
        AgentContext ctx = new AgentContext(Goal.of("objetivo"));

        assertThat(selector.active().name()).isEqualTo("ReAct");
        assertThat(selector.isEscalated()).isFalse();

        var ativa = selector.escalate(ctx, EscalationSignal.of(
                EscalationSignal.Reason.NO_VALID_PLAN, "o que voce quer fazer?"));

        assertThat(ativa.name()).isEqualTo("HumanInTheLoop");
        assertThat(selector.active()).as("uma unica referencia ativa").isSameAs(ativa);
        assertThat(selector.isEscalated()).isTrue();
        assertThat(ctx.isEscalated()).isTrue();

        selector.deescalate(ctx);

        assertThat(selector.active().name()).isEqualTo("ReAct");
        assertThat(ctx.isEscalated()).isFalse();
        assertThat(ctx.escalationHistory()).as("a evidencia permanece").hasSize(1);
    }

    @Test
    void selecaoDoPlanejadorInicialTroca() {
        StrategySelector selector = selector(reAct("{}"));

        assertThat(selector.selectPlanThenExecute().name()).isEqualTo("PlanThenExecute");
        assertThat(selector.active().name()).isEqualTo("PlanThenExecute");
        assertThat(selector.selectReAct().name()).isEqualTo("ReAct");
    }

    // ------------------------------------- as tres se comportam diferente

    @Test
    @DisplayName("diante da MESMA falha: ReAct replaneja, PlanThenExecute falha, HITL escala")
    void asTresEstrategiasReagemDiferenteAoMesmoResultado() {
        AgentContext ctx = new AgentContext(Goal.of("objetivo"));
        ExecutionResult falha = ExecutionResult.failure("deu ruim");

        AgentStrategy react = reAct("{}");
        AgentStrategy planThenExecute = new PlanThenExecuteStrategy(llmQueResponde("{}"), REGISTRY);
        AgentStrategy hitl = new HumanInTheLoopStrategy(REGISTRY, new BufferedReader(new StringReader("")));

        assertThat(react.decideNext(ctx, falha)).isEqualTo(StepDecision.REPLAN);
        assertThat(planThenExecute.decideNext(ctx, falha)).isEqualTo(StepDecision.FAIL);
        assertThat(hitl.decideNext(ctx, falha)).isEqualTo(StepDecision.ESCALATE);
    }

    @Test
    @DisplayName("esgotado o orcamento de replanejamentos, o ReAct escala por REPLAN_LOOP (gatilho #3)")
    void reActRespeitaOLimiteDeReplanejamentos() {
        AgentContext ctx = new AgentContext(Goal.of("objetivo"));
        AgentStrategy react = reAct("{}");
        ExecutionResult falha = ExecutionResult.failure("deu ruim");

        assertThat(react.decideNext(ctx, falha)).isEqualTo(StepDecision.REPLAN);

        ctx.recordReplan();
        ctx.recordReplan();

        // Antes da FASE 5 isto era FAIL. Agora o ReAct reconhece que esta girando em falso
        // e pede ajuda, em vez de empurrar a falha para o tratamento de erro.
        assertThat(react.decideNext(ctx, falha)).isEqualTo(StepDecision.ESCALATE);
        assertThat(react.escalationSignal(ctx)).get()
                .extracting(EscalationSignal::reason)
                .isEqualTo(EscalationSignal.Reason.REPLAN_LOOP);
    }

    @Test
    void hitlExigeAprovacaoDeTodoPasso() {
        AgentStrategy hitl = new HumanInTheLoopStrategy(REGISTRY, new BufferedReader(new StringReader("")));
        var passo = new br.edu.fatec.onboardingagent.domain.PlanStep(1, "qualquer", "gitStatus", Map.of());

        assertThat(hitl.requiresApproval(passo)).isTrue();
        assertThat(reAct("{}").requiresApproval(passo)).as("comando nao destrutivo").isFalse();
    }
}
