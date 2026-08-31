package br.edu.fatec.onboardingagent.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SUBJECT do padrao Observer — implementado a mao, de proposito.
 *
 * <p>O {@code ApplicationEventPublisher} do Spring faria o mesmo trabalho, mas o padrao
 * some do diagrama de classes: o Subject viraria uma caixa preta do framework. Aqui ele e
 * uma classe do projeto, com {@code subscribe}, {@code unsubscribe} e {@code publish}
 * visiveis na UML.</p>
 *
 * <p>Os observadores descobertos pelo Spring ja entram inscritos; o {@code subscribe} fica
 * disponivel para quem nasce depois do contexto — e o caso do GuiObserver, na FASE 6.</p>
 */
@Component
public class AgentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AgentEventPublisher.class);

    /** Lista copy-on-write: um observador pode se inscrever enquanto outro reage. */
    private final List<AgentObserver> observers = new CopyOnWriteArrayList<>();

    public AgentEventPublisher(List<AgentObserver> descobertos) {
        observers.addAll(descobertos);
        log.info("Observadores inscritos: {}", observers.size());
    }

    public void subscribe(AgentObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unsubscribe(AgentObserver observer) {
        observers.remove(observer);
    }

    public int subscriberCount() {
        return observers.size();
    }

    /**
     * Notifica todos os inscritos.
     *
     * <p>Observador que explode nao pode derrubar o agente: o evento e so testemunho, o
     * trabalho de verdade ja aconteceu. Por isso cada notificacao vai dentro de try/catch.</p>
     */
    public void publish(AgentEvent event) {
        for (AgentObserver observer : observers) {
            try {
                observer.onEvent(event);
            } catch (Exception e) {
                log.warn("Observador {} falhou ao tratar {}: {}",
                        observer.getClass().getSimpleName(), event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
