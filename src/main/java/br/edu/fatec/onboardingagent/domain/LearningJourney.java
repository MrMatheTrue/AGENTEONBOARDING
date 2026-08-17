package br.edu.fatec.onboardingagent.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Trilha de aprendizado do desenvolvedor: quais modulos de Git/GitHub ele ja praticou.
 *
 * <p>E o que diferencia este agente de um executor de comandos — ele acompanha alguem que
 * esta aprendendo. O ProgressObserver (FASE 5) marca os modulos conforme os comandos
 * correspondentes rodam com sucesso.</p>
 */
public class LearningJourney {

    /** Modulos padrao da trilha, na ordem em que um iniciante costuma encontra-los. */
    private static final List<String> DEFAULT_MODULES = List.of(
            "Inspecionar o repositorio (git status)",
            "Trabalhar com branches (git branch, git checkout)",
            "Registrar alteracoes (git add, git commit)",
            "Publicar no remoto (git push)",
            "Pedir revisao (pull request)"
    );

    /** Modulo -> concluido. LinkedHashMap para preservar a ordem didatica. */
    private final Map<String, Boolean> modules = new LinkedHashMap<>();

    /** Cria a trilha com os modulos padrao, todos pendentes. */
    public LearningJourney() {
        this(DEFAULT_MODULES);
    }

    public LearningJourney(List<String> moduleNames) {
        Objects.requireNonNull(moduleNames, "moduleNames nao pode ser nulo");
        moduleNames.forEach(name -> modules.put(name, false));
    }

    /**
     * Marca um modulo como concluido.
     *
     * @return true se o modulo existia e mudou de pendente para concluido
     */
    public boolean complete(String moduleName) {
        if (!modules.containsKey(moduleName) || modules.get(moduleName)) {
            return false;
        }
        modules.put(moduleName, true);
        return true;
    }

    public boolean isCompleted(String moduleName) {
        return Boolean.TRUE.equals(modules.get(moduleName));
    }

    public Map<String, Boolean> modules() {
        return Collections.unmodifiableMap(modules);
    }

    public List<String> completedModules() {
        return modules.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
    }

    public List<String> pendingModules() {
        return modules.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();
    }

    /** Progresso de 0.0 a 1.0. Trilha sem modulos conta como zero, nao como completa. */
    public double progress() {
        if (modules.isEmpty()) {
            return 0.0;
        }
        return (double) completedModules().size() / modules.size();
    }

    @Override
    public String toString() {
        return "Trilha{%d de %d modulos concluidos}".formatted(completedModules().size(), modules.size());
    }
}
