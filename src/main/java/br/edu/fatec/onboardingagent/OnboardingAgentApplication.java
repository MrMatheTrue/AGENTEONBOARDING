package br.edu.fatec.onboardingagent;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import br.edu.fatec.onboardingagent.observer.impl.TraceObserver;
import br.edu.fatec.onboardingagent.state.AgentState;
import br.edu.fatec.onboardingagent.state.AgentStateMachine;
import br.edu.fatec.onboardingagent.strategy.StrategySelector;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.io.BufferedReader;

/**
 * Ponto de entrada do GitHub Onboarding Agent.
 *
 * <p>O agente acompanha um desenvolvedor aprendendo Git/GitHub: ensina, planeja,
 * executa operacoes Git reais, observa o resultado e adapta o proximo passo.</p>
 */
@SpringBootApplication
public class OnboardingAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnboardingAgentApplication.class, args);
    }

    /**
     * Console da FASE 4 — recebe o objetivo, roda a maquina e mostra o resultado.
     *
     * <p>Fica aqui, e nao numa classe propria, porque a arvore de arquivos do projeto nao
     * preve um runner: a interacao definitiva e a GUI Swing da FASE 6. Desligue com
     * {@code --agent.console=false} para subir apenas a API.</p>
     */
    @Bean
    @ConditionalOnProperty(name = "agent.console", havingValue = "true", matchIfMissing = true)
    public CommandLineRunner consoleDoAgente(AgentStateMachine machine, StrategySelector selector,
                                            TraceObserver trace) {
        return args -> {
            // Uma unica fonte de entrada para a sessao inteira. Criar outro BufferedReader
            // aqui faria os dois competirem pelo System.in, e a resposta ao escalonamento
            // seria lida como se fosse um objetivo novo.
            BufferedReader teclado = selector.humanInTheLoop().input();

            System.out.println();
            System.out.println("=".repeat(70));
            System.out.println("  GitHub Onboarding Agent - digite seu objetivo (ou 'sair')");
            System.out.println("  Planejador: 'react' (padrao) ou 'plan' para PlanThenExecute");
            System.out.println("=".repeat(70));

            while (true) {
                System.out.println();
                System.out.print("objetivo> ");
                String linha = teclado.readLine();

                if (linha == null) {
                    // Sem terminal interativo (pipe fechado, execucao automatizada).
                    System.out.println("(sem entrada disponivel - encerrando o console)");
                    return;
                }
                linha = linha.trim();
                if (linha.isEmpty()) {
                    continue;
                }
                if (linha.equalsIgnoreCase("sair") || linha.equalsIgnoreCase("exit")) {
                    System.out.println("Ate a proxima.");
                    return;
                }
                if (linha.equalsIgnoreCase("react")) {
                    selector.selectReAct();
                    System.out.println("Planejador agora e ReAct.");
                    continue;
                }
                if (linha.equalsIgnoreCase("plan")) {
                    selector.selectPlanThenExecute();
                    System.out.println("Planejador agora e PlanThenExecute.");
                    continue;
                }

                executar(machine, selector, trace, linha);
            }
        };
    }

    private void executar(AgentStateMachine machine, StrategySelector selector,
                          TraceObserver trace, String objetivo) {
        // Toda sessao comeca pelo planejador escolhido; um escalonamento pode troca-lo
        // no meio do caminho, e o deescalate o devolve.
        if (selector.isEscalated()) {
            selector.selectReAct();
        }

        trace.limpar();
        AgentContext ctx = new AgentContext(Goal.of(objetivo));
        AgentState fim = machine.run(ctx);

        System.out.println();
        System.out.println("-".repeat(70));
        System.out.println("Estados percorridos: " + String.join(" -> ", machine.trail()));
        System.out.println("Estrategia ao final: " + selector.active().name());

        if (ctx.plan().size() > 0) {
            System.out.println("Plano (confianca " + ctx.plan().confidence() + "):");
            for (PlanStep passo : ctx.plan().steps()) {
                System.out.printf("  [%s] %d. %s (%s)%n",
                        passo.status(), passo.id(), passo.description(), passo.commandName());
            }
        }

        if (!ctx.history().isEmpty()) {
            System.out.println("Saida dos comandos:");
            for (ExecutionResult resultado : ctx.history()) {
                System.out.println("  " + (resultado.success()
                        ? resultado.output().replace("\n", "\n  ")
                        : "FALHOU: " + resultado.errorMessage()));
            }
        }

        ctx.escalationHistory().forEach(sinal ->
                System.out.println("Escalonamento [" + sinal.reason() + "]: " + sinal.questionToHuman()));

        System.out.printf("Trilha de aprendizado: %d%% concluida%n",
                Math.round(ctx.journey().progress() * 100));
        System.out.println("Situacao final: " + fim.name());
        System.out.println("-".repeat(70));

        // Aceite da FASE 5: a trilha de raciocinio, com o motivo de cada escalonamento.
        trace.imprimir();
    }
}
