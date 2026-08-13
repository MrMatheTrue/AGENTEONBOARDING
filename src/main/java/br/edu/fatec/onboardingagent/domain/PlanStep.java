package br.edu.fatec.onboardingagent.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Um passo do plano: a intencao em linguagem natural mais o comando que a realiza.
 *
 * <p>O {@code commandName} e a chave que o CommandRegistry (FASE 2) usa para achar o
 * AgentCommand correspondente. Os {@code args} sao os parametros que o LLM preencheu
 * para aquela execucao — por exemplo {@code {"branchName": "feature/login"}}.</p>
 *
 * <p>Diferente das demais classes do dominio, esta tem estado mutavel: o {@link Status}
 * muda conforme o passo avanca. Os args, porem, sao congelados na criacao.</p>
 */
public class PlanStep {

    /** Ciclo de vida de um passo dentro do plano. */
    public enum Status {
        /** Ainda nao executado. */
        PENDING,
        /** Em execucao neste momento. */
        RUNNING,
        /** Executado com sucesso. */
        DONE,
        /** Executado e falhou. */
        FAILED
    }

    private final int id;
    private final String description;
    private final String commandName;
    private final Map<String, Object> args;
    private Status status;

    public PlanStep(int id, String description, String commandName, Map<String, Object> args) {
        this.id = id;
        this.description = Objects.requireNonNull(description, "description nao pode ser nula");
        this.commandName = Objects.requireNonNull(commandName, "commandName nao pode ser nulo");
        // Copia defensiva: o plano nao pode ser alterado por quem passou o mapa.
        this.args = args == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(args));
        this.status = Status.PENDING;
    }

    public int id() {
        return id;
    }

    public String description() {
        return description;
    }

    public String commandName() {
        return commandName;
    }

    public Map<String, Object> args() {
        return args;
    }

    public Status status() {
        return status;
    }

    public void markRunning() {
        this.status = Status.RUNNING;
    }

    public void markDone() {
        this.status = Status.DONE;
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    /** Um passo esta encerrado quando concluiu com sucesso ou falhou de vez. */
    public boolean isFinished() {
        return status == Status.DONE || status == Status.FAILED;
    }

    /**
     * Um passo so e estruturalmente utilizavel se aponta para algum comando.
     * Se o comando existe de fato, quem sabe e o registry — ver {@link Plan#isValid(java.util.Set)}.
     */
    public boolean hasCommand() {
        return !commandName.isBlank();
    }

    @Override
    public String toString() {
        return "[%d] %s (%s) -> %s".formatted(id, description, commandName, status);
    }
}
