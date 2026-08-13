package br.edu.fatec.onboardingagent.domain;

/**
 * Resultado de um comando executado.
 *
 * <p>O CommandInvoker (FASE 2) nunca deixa excecao vazar: converte tudo em
 * {@link #failure(String)}. Entao este record e a unica forma de o resto do sistema
 * saber se um passo deu certo — nao existe caminho por excecao.</p>
 *
 * @param success      se o comando concluiu com sucesso
 * @param output       saida util do comando (ex.: o texto do git status)
 * @param errorMessage motivo da falha; nulo quando houve sucesso
 */
public record ExecutionResult(boolean success, String output, String errorMessage) {

    public static ExecutionResult success(String output) {
        return new ExecutionResult(true, output == null ? "" : output, null);
    }

    public static ExecutionResult failure(String errorMessage) {
        return new ExecutionResult(false, "", errorMessage);
    }

    public boolean isFailure() {
        return !success;
    }
}
