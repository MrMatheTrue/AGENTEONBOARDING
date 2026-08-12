package br.edu.fatec.onboardingagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
}
