# Estrutura do Projeto — GitHub Onboarding Agent (Spring AI + 4 Patterns + Composite)

> Base: fala do professor (2 áudios) + enunciado. Tudo aqui é decisão **travada**, não sugestão.

---

## 1. O que o professor travou (não é negociável)

| Regra | Citação do áudio |
|---|---|
| **Um único projeto**, não 4 exemplos | "Tudo num projeto só, no mesmo projeto vai ter a aplicação desses quatro patterns" |
| Cada padrão cumpre **função diferente** na mesma execução | "cada um dos quatro padrões se aplicam a partes diferentes do projeto... cumprindo funções diferentes" |
| **Strategy** = escolher qual dos 3 planejadores | "a estratégia é que você pode escolher uma dessas três: o ReAct, o Plan-Execute e o Human-in-the-Loop" |
| **State** = gerenciar o **pulo de tarefa** | "a hora que ele está gerenciando o pular de tarefa... exatamente, ele está controlando o estado" |
| **Command** = **ferramenta** (método Java que o LLM chama) | "ferramentas são métodos que eu tenho no meu java... é uma aplicação do Command" |
| **Observer** = observar a **trilha de raciocínio** e erros | "preciso observar qual foi a trilha de raciocínio que ele usou... você está usando o Observer" |
| LLM **local e aberto** | "o menor modelo possível que rodar na tua máquina" — Gemma 2B/4B, Qwen 3 |
| Runtime preferido: **llama.cpp**, não Ollama | "o Ollama tá com uns problemas... o llama.cpp tá mais estável" |
| GUI opcional: **Jan.ai** para testar o modelo | "é o Jan.ai no site" |
| **Entrega 1 — 14/09**: MVP rodando | "a coisa mais boba que funcionar" |
| No 14/09: **Strategy + State obrigatórios. Observer NÃO** | "o que não tem no 14/09 é o Observer, é certeza absoluta" |
| Validar o tema com ele antes | "pede para eu validar o tema" |

**Consequência direta:** a arquitetura **nasce do fluxo do agente**, não das classes. Só se desenha classe depois que o fluxo estiver fechado.

---

## 2. Stack

| Camada | Escolha | Motivo |
|---|---|---|
| Runtime | Java 21 + Spring Boot 3.3+ | padrão da disciplina |
| IA | **Spring AI 1.0** (`spring-ai-starter-model-openai`) | llama.cpp expõe API OpenAI-compatível |
| LLM | **llama.cpp server** + Gemma 2 (2B/4B) ou Qwen 3 | exigência: opensource + roda na máquina da FATEC |
| Base de conhecimento | **Vector Store** (`SimpleVectorStore` p/ MVP → pgvector depois) | professor sugeriu explicitamente banco vetorial |
| Git real | **JGit** (`org.eclipse.jgit`) — ou `ProcessBuilder` no MVP | Command precisa de um Receiver real |
| GitHub | REST via `RestClient` | comando `CreatePullRequest` |
| GUI | **Swing** | onde entra o **Composite** |
| Build | Maven | — |

`application.yml` (aponta pro llama.cpp local):

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:8080      # llama.cpp --server
      api-key: not-needed
      chat.options:
        model: gemma-2-2b-it
        temperature: 0.2
```

---

## 3. O fluxo canônico (o coração do projeto)

Este é o diagrama que responde "onde está cada padrão". **Decorem este fluxo — é o que cai na avaliação.**

```
Usuário: "Quero enviar minha alteração para revisão"
        │
        ▼
 [1] StrategySelector  ────────────────────────────────  STRATEGY
        │  escolhe: ReAct | PlanThenExecute | HumanInTheLoop
        ▼
 [2] PlanningState → strategy.buildPlan(ctx) → LLM devolve passos
        │  1.status  2.branch  3.commit  4.push  5.criar PR
        ▼
 [3] AgentStateMachine avança passo a passo  ──────────  STATE
        │  INIT → PLANNING → EXECUTING → OBSERVING → (loop) → COMPLETED
        ▼
 [4] ExecutingState → CommandInvoker.execute(cmd)  ────  COMMAND
        │  GitStatusCommand, GitCommitCommand, KnowledgeSearchCommand...
        ▼
 [5] cada transição/execução publica evento  ──────────  OBSERVER
        │  TraceObserver, ErrorObserver, GuiObserver, ProgressObserver
        ▼
 [6] ObservingState → strategy.decideNext(ctx)
        │  CONTINUE | REPLAN | ESCALATE | FAIL
        │
        └── se ESCALATE ─► StrategySelector TROCA a estratégia ativa
                           ReActStrategy ──► HumanInTheLoopStrategy
                           (STATE vai para WAITING_APPROVAL)
        ▼
 [7] GUI Swing redesenha a árvore de painéis  ─────────  COMPOSITE
```

**A frase de defesa na apresentação:**
> "Strategy planeja. State avança. Command executa. Observer testemunha. Composite mostra."

---

## 4. Estrutura de pastas — exata

```
src/main/java/br/edu/fatec/onboardingagent/
│
├── OnboardingAgentApplication.java
│
├── config/
│   ├── LlmConfig.java                 # ChatClient → llama.cpp
│   ├── VectorStoreConfig.java         # ingestão da base de conhecimento Git
│   └── AgentBeansConfig.java          # registra strategies, commands, observers
│
├── domain/                            # POJOs puros, ZERO Spring, ZERO IA
│   ├── AgentContext.java              # goal, plan, currentStepIndex, history, journey
│   ├── Goal.java
│   ├── Plan.java
│   ├── PlanStep.java                  # commandName + args + status
│   ├── ExecutionResult.java           # success, output, errorMessage
│   ├── StepDecision.java              # enum CONTINUE|REPLAN|ESCALATE|FAIL
│   ├── EscalationSignal.java          # motivo + pergunta ao humano + passo bloqueado
│   └── LearningJourney.java           # módulos + progresso do dev
│
├── strategy/                          # ◆ STRATEGY
│   ├── AgentStrategy.java             # INTERFACE
│   ├── ReActStrategy.java             # replaneja a cada observação
│   ├── PlanThenExecuteStrategy.java   # planeja 1x, executa tudo
│   ├── HumanInTheLoopStrategy.java    # assume quando o ReAct escala
│   └── StrategySelector.java          # Context: escolhe E TROCA em runtime
│
├── state/                             # ◆ STATE
│   ├── AgentState.java                # INTERFACE: AgentState handle(AgentContext)
│   ├── InitState.java
│   ├── PlanningState.java             # chama strategy.buildPlan()
│   ├── ExecutingState.java            # chama CommandInvoker
│   ├── ObservingState.java            # chama strategy.decideNext()
│   ├── WaitingApprovalState.java      # destino do ESCALATE — espera o humano
│   ├── ErrorState.java                # retry / replan
│   ├── CompletedState.java
│   └── AgentStateMachine.java         # Context do State
│
├── command/                           # ◆ COMMAND
│   ├── AgentCommand.java              # INTERFACE: name(), description(), execute()
│   ├── CommandRegistry.java           # nome → comando
│   ├── CommandInvoker.java            # Invoker: executa + publica eventos
│   ├── SpringAiToolAdapter.java       # expõe cada Command como tool do Spring AI
│   └── impl/
│       ├── GitStatusCommand.java
│       ├── GitBranchCommand.java
│       ├── GitCheckoutCommand.java
│       ├── GitAddCommand.java
│       ├── GitCommitCommand.java
│       ├── GitPushCommand.java
│       ├── CreatePullRequestCommand.java
│       └── KnowledgeSearchCommand.java   # RAG no banco vetorial
│
├── tool/                              # RECEIVERS (quem faz o trabalho de fato)
│   ├── GitClient.java                 # JGit
│   ├── GitHubClient.java              # REST
│   └── KnowledgeService.java          # VectorStore
│
├── observer/                          # ◆ OBSERVER
│   ├── AgentEvent.java                # sealed / hierarquia
│   ├── AgentObserver.java             # INTERFACE: onEvent(AgentEvent)
│   ├── AgentEventPublisher.java       # SUBJECT: subscribe/notify
│   └── impl/
│       ├── TraceObserver.java         # trilha de raciocínio ← pedido do professor
│       ├── ErrorObserver.java         # detecta falha → dispara ErrorState
│       ├── ProgressObserver.java      # atualiza LearningJourney
│       └── GuiObserver.java           # ponte Observer → Composite
│
├── llm/
│   ├── LlmGateway.java                # única porta pro ChatClient
│   └── PromptTemplates.java           # prompts de planejamento e de resposta
│
├── gui/                               # ◆ COMPOSITE
│   ├── UIComponent.java               # COMPONENT: render(), refresh(AgentEvent)
│   ├── UIComposite.java               # COMPOSITE: List<UIComponent> children
│   ├── leaf/
│   │   ├── GoalPanel.java
│   │   ├── PlanPanel.java
│   │   ├── StatePanel.java
│   │   ├── TraceLogPanel.java
│   │   └── ProgressPanel.java
│   └── MainWindow.java
│
└── api/                               # opcional
    └── AgentController.java
```

**Tipos de evento (`AgentEvent`) — mínimo:**
`PlanCreated`, `StateChanged`, `CommandStarted`, `CommandCompleted`, `CommandFailed`, `UserApprovalRequired`, `ReasoningStep`, `GoalCompleted`.

---

## 4.1 ◆ Escalonamento ReAct → Human-in-the-Loop

O `ReActStrategy` **declara a própria incapacidade**; quem troca a estratégia é o `StrategySelector` (o Context). Uma estratégia **nunca** chama a outra — se chamasse, viraria Chain of Responsibility e descaracterizaria o Strategy.

### Gatilhos de escalonamento (objetivos e mensuráveis)

| # | Gatilho | Detectado em | Limite |
|---|---|---|---|
| 1 | LLM não produz plano válido (vazio, sem comando conhecido) | `PlanningState` | 1ª tentativa |
| 2 | Confiança do plano abaixo do limiar | `ReActStrategy.buildPlan` | `confidence < 0.5` |
| 3 | Replanejamentos consecutivos sem progresso | `ObservingState` | `maxReplans = 2` |
| 4 | Mesmo comando falhou N vezes | `CommandInvoker` | `maxRetries = 2` |
| 5 | Passo destrutivo/irreversível | `requiresApproval(step)` | `merge main`, `push --force`, `branch -D` |
| 6 | Parâmetro obrigatório ausente / pedido ambíguo | `ReActStrategy` | sempre |

### Como fica no código

```java
public enum StepDecision { CONTINUE, REPLAN, ESCALATE, FAIL }

public record EscalationSignal(Reason reason, String question, PlanStep blockedStep) {
    public enum Reason { NO_VALID_PLAN, LOW_CONFIDENCE, REPLAN_LOOP,
                         COMMAND_FAILED, DESTRUCTIVE_STEP, AMBIGUOUS_INPUT }
}
```

```java
// StrategySelector — Context do Strategy
public AgentStrategy escalate(AgentContext ctx, EscalationSignal signal) {
    ctx.recordEscalation(signal);
    publisher.publish(new StrategyEscalated(active.name(), "HumanInTheLoop", signal));
    this.active = humanInTheLoop;          // troca em runtime, 1 ativa por vez
    return this.active;
}

public void deescalate(AgentContext ctx) {  // humano respondeu → volta pro ReAct
    this.active = reAct;
    ctx.clearEscalation();
}
```

**Ciclo completo:**

```
ReActStrategy planeja  →  ObservingState detecta gatilho
        ↓ StepDecision.ESCALATE
StrategySelector.escalate()  →  estratégia ativa = HumanInTheLoop
        ↓
AgentState = WAITING_APPROVAL   (evento UserApprovalRequired → GUI)
        ↓
Humano responde / aprova
        ↓
StrategySelector.deescalate()  →  volta pro ReAct com o contexto enriquecido
        ↓
PlanningState replaneja com a resposta do humano no histórico
```

> **Por que isso fortalece a nota:** o professor definiu Strategy como *"você pode escolher uma dessas três formas de executar um agente"*. Escolher **em runtime, sob condição observável**, é a demonstração mais forte possível de intercambialidade — que é justamente o propósito do padrão. Só cuide de nunca ter duas estratégias ativas ao mesmo tempo.

### Impacto nas outras classes

| Classe | Mudança |
|---|---|
| `StrategySelector` | ganha `escalate()` / `deescalate()`; guarda `active` |
| `ObservingState` | passa a devolver `ESCALATE` e transicionar para `WaitingApprovalState` |
| `WaitingApprovalState` | bloqueia até `ctx.humanResponse()` chegar; depois → `PlanningState` |
| `AgentContext` | `escalationHistory`, `replanCount`, `humanResponse` |
| `AgentEvent` | novos: `StrategyEscalated`, `StrategyDeescalated`, `UserApprovalRequired` |
| `TraceObserver` | registra **por que** escalou (o `Reason`) — vira evidência na apresentação |
| GUI (Composite) | `ApprovalPanel` como novo Leaf |

---

## 5. Contratos-chave (só as assinaturas)

```java
// ◆ STRATEGY
public interface AgentStrategy {
    String name();
    Plan buildPlan(AgentContext ctx);
    StepDecision decideNext(AgentContext ctx, ExecutionResult last);
    boolean requiresApproval(PlanStep step);
    Optional<EscalationSignal> escalationSignal(AgentContext ctx);  // ← incapacidade auto-declarada
}

// ◆ STATE  — o estado devolve o PRÓXIMO estado (nada de enum + switch)
public interface AgentState {
    String name();
    AgentState handle(AgentContext ctx);
}

// ◆ COMMAND
public interface AgentCommand {
    String name();
    String description();               // vira descrição da tool pro LLM
    ExecutionResult execute(AgentContext ctx, Map<String,Object> args);
}

// ◆ OBSERVER
public interface AgentObserver { void onEvent(AgentEvent event); }

public class AgentEventPublisher {      // Subject
    public void subscribe(AgentObserver o) {...}
    public void publish(AgentEvent e)     {...}
}

// ◆ COMPOSITE
public interface UIComponent {
    void render();
    void refresh(AgentEvent event);     // propaga pra árvore inteira
}
```

---

## 6. Tabela de rastreabilidade UML (colar na documentação)

| Padrão | Papel GoF | Classe no projeto | Responsabilidade única |
|---|---|---|---|
| **Strategy** | Strategy | `AgentStrategy` | contrato de planejamento |
| | ConcreteStrategy | `ReActStrategy`, `PlanThenExecuteStrategy`, `HumanInTheLoopStrategy` | 3 formas de planejar/decidir |
| | Context | `StrategySelector` | escolhe **e troca em runtime** (ReAct → HITL no escalonamento) |
| **State** | State | `AgentState` | contrato de estado |
| | ConcreteState | `Planning/Executing/Observing/Error/Completed/WaitingApproval` | comportamento por fase |
| | Context | `AgentStateMachine` | mantém estado atual e transiciona |
| **Command** | Command | `AgentCommand` | encapsula uma ferramenta + contexto |
| | ConcreteCommand | `GitCommitCommand`, `KnowledgeSearchCommand`, … | 1 ferramenta cada |
| | Invoker | `CommandInvoker` | dispara e mede |
| | Receiver | `GitClient`, `GitHubClient`, `KnowledgeService` | trabalho real |
| **Observer** | Subject | `AgentEventPublisher` | notifica |
| | Observer | `AgentObserver` | contrato |
| | ConcreteObserver | `Trace/Error/Progress/Gui Observer` | reage sem acoplar |
| **Composite** | Component | `UIComponent` | interface uniforme da UI |
| | Composite | `UIComposite` | agrupa filhos e propaga |
| | Leaf | `PlanPanel`, `TraceLogPanel`, … | painel folha |

---

## 7. UML — diagramas a entregar

### 7.1 Classes (PlantUML)

```plantuml
@startuml
package strategy {
  interface AgentStrategy { +buildPlan() +decideNext() +escalationSignal() }
  class ReActStrategy
  class PlanThenExecuteStrategy
  class HumanInTheLoopStrategy
  class StrategySelector { -active: AgentStrategy\n+escalate(signal)\n+deescalate() }
  AgentStrategy <|.. ReActStrategy
  AgentStrategy <|.. PlanThenExecuteStrategy
  AgentStrategy <|.. HumanInTheLoopStrategy
  StrategySelector --> AgentStrategy : active
  ReActStrategy ..> StrategySelector : ESCALATE\n(via StepDecision)
}
package state {
  interface AgentState { +handle(ctx): AgentState }
  class PlanningState
  class ExecutingState
  class ObservingState
  class WaitingApprovalState
  class ErrorState
  class CompletedState
  class AgentStateMachine
  AgentState <|.. PlanningState
  AgentState <|.. ExecutingState
  AgentState <|.. ObservingState
  AgentState <|.. WaitingApprovalState
  AgentState <|.. ErrorState
  AgentState <|.. CompletedState
  AgentStateMachine --> AgentState
}
package command {
  interface AgentCommand { +execute(ctx,args) }
  class GitCommitCommand
  class KnowledgeSearchCommand
  class CommandInvoker
  AgentCommand <|.. GitCommitCommand
  AgentCommand <|.. KnowledgeSearchCommand
  CommandInvoker --> AgentCommand
  GitCommitCommand --> GitClient
}
package observer {
  interface AgentObserver
  class AgentEventPublisher
  class TraceObserver
  class GuiObserver
  AgentObserver <|.. TraceObserver
  AgentObserver <|.. GuiObserver
  AgentEventPublisher o--> AgentObserver
}
package gui {
  interface UIComponent
  class UIComposite
  class PlanPanel
  UIComponent <|.. UIComposite
  UIComponent <|.. PlanPanel
  UIComposite o--> UIComponent
}
PlanningState --> AgentStrategy
ExecutingState --> CommandInvoker
ObservingState --> AgentStrategy
ObservingState --> StrategySelector : escalate()
WaitingApprovalState --> StrategySelector : deescalate()
CommandInvoker --> AgentEventPublisher
AgentStateMachine --> AgentEventPublisher
GuiObserver --> UIComposite
@enduml
```

### 7.2 Sequência (o caso "enviar para revisão")

`Usuário → StrategySelector → AgentStateMachine → PlanningState → AgentStrategy → LlmGateway → ExecutingState → CommandInvoker → GitCommitCommand → GitClient → AgentEventPublisher → TraceObserver/GuiObserver → ObservingState → (loop) → CompletedState`

### 7.3 Máquina de estados

```
INIT ──► PLANNING ──► EXECUTING ──► OBSERVING ──► COMPLETED
            ▲             ▲            │
            │             └── CONTINUE ┤
            └──── REPLAN ──────────────┤
                                       │ ESCALATE
                                       ▼
                            WAITING_APPROVAL ──► (humano responde)
                                       │              │
                                       │ FAIL         └──► PLANNING  [deescalate]
                                       ▼
                                    ERROR ──► PLANNING (replan) | EXECUTING (retry)
                                       │
                                       └── retries esgotados ──► WAITING_APPROVAL
```

> Note as **duas portas** para `WAITING_APPROVAL`: uma vinda do `OBSERVING` (ReAct se declarou incapaz) e outra do `ERROR` (retries esgotados). Ambas trocam a estratégia ativa para `HumanInTheLoop`.

---

## 8. Plano de entrega

### Entrega 1 — **14/09** (MVP, "a coisa mais boba que funcionar")

**Tem que ter:**
- Spring Boot subindo + Spring AI conectado ao llama.cpp respondendo
- `AgentStrategy` com **as 3** implementações (pelo menos ReAct funcional)
- `AgentStateMachine` com `INIT → PLANNING → EXECUTING → OBSERVING → COMPLETED`
- 2–3 `AgentCommand` reais (`GitStatusCommand`, `GitBranchCommand`, `KnowledgeSearchCommand`)
- Console/log mostrando o plano e o avanço dos passos

- **Escalonamento em versão mínima:** só o gatilho #1 (plano inválido) → `WaitingApprovalState` pedindo input no console. Sem GUI, sem Observer — mas já prova a troca de estratégia em runtime, que é o diferencial da apresentação.

**NÃO precisa ter:** Observer, GUI Swing/Composite, GitHub PR, banco vetorial completo, tratamento de erro sofisticado, os 6 gatilhos.

### Entrega 2 — Observer + escalonamento completo
`AgentEventPublisher`, `TraceObserver` registrando o **motivo** de cada escalonamento, `ErrorObserver`, `ErrorState` com retry/replan, os 6 gatilhos, `deescalate()` devolvendo o controle ao ReAct.

### Entrega 3 — Produto
GUI Swing com Composite, `LearningJourney` persistida, PR no GitHub, RAG completo, UML final.

### **Antes de tudo:** validar o tema com o professor
> "GitHub Onboarding Agent — Customer Service Agent for Developer Onboarding"

---

## 9. Erros que derrubam a nota (evitar desde o commit 1)

| ❌ Erro | ✅ Correto |
|---|---|
| `enum AgentState` + `switch` | classes de estado polimórficas devolvendo o próximo estado |
| Command que só chama `chatClient.call()` | Command com **Receiver** próprio (`GitClient`) e resultado tipado |
| Usar só `ApplicationEventPublisher` do Spring como "Observer" | implementar `AgentEventPublisher` **próprio** — o padrão precisa aparecer na UML |
| Strategy com 3 classes idênticas | comportamento **realmente diferente**: replan / plano fixo / aprovação |
| Duas estratégias ativas ao mesmo tempo | **uma ativa por vez**; o `StrategySelector` troca a referência |
| `ReActStrategy` instanciar/chamar `HumanInTheLoopStrategy` | ReAct só devolve `ESCALATE`; **quem troca é o Context** |
| Escalonar por "achismo" do LLM | gatilhos **numéricos e verificáveis** (`maxReplans`, `maxRetries`, `confidence`) |
| Padrões em módulos separados | **um fluxo só** que passa pelos quatro |
| Implementar 20 comandos Git | poucos comandos, comportamento rico |
| Composite fora da GUI | Composite é na árvore de painéis Swing |

---

## 10. Ordem de execução (próximos 7 dias)

1. Subir `llama.cpp server` + Gemma 2 2B e validar via curl
2. `mvn` scaffold + `LlmConfig` + um "ping" no ChatClient
3. `domain/` (POJOs) — sem Spring
4. `AgentCommand` + `GitStatusCommand` + `GitClient` → **testar isolado**
5. `AgentState` + `AgentStateMachine` com 3 estados → **testar sem LLM (plano mockado)**
6. `AgentStrategy` + `ReActStrategy` com prompt de planejamento → plugar no `PlanningState`
7. `StrategySelector.escalate()` + `WaitingApprovalState` com gatilho #1 (plano inválido)
8. Rodar o caso ponta a ponta, **incluindo um caso que escala** → isso é o MVP do 14/09

### Os 2 casos de teste do MVP (leve os dois para a aula)

| Caso | Entrada | Resultado esperado |
|---|---|---|
| **Feliz** | "Quero criar uma branch feature/login" | ReAct planeja 2 passos → State avança → Commands executam → COMPLETED |
| **Escalonamento** | "Arruma aí o meu repositório" (ambíguo) | ReAct devolve `ESCALATE(AMBIGUOUS_INPUT)` → Selector troca p/ HITL → `WAITING_APPROVAL` pergunta ao usuário → resposta → volta pro ReAct → COMPLETED |
