# GitHub Onboarding Agent

Agente que acompanha um desenvolvedor aprendendo Git e GitHub: **ensina, planeja, executa
operações Git reais, observa o resultado e adapta o próximo passo**.

Projeto acadêmico da disciplina de **Padrões de Projeto** (FATEC). O código é meio; o que
está sendo avaliado é a arquitetura — cinco padrões GoF cumprindo funções diferentes
**no mesmo fluxo de execução**, não em módulos isolados.

> Strategy planeja · State avança · Command executa · Observer testemunha · Composite mostra

---

## Tabela de rastreabilidade — padrão → papel GoF → classe

| Padrão | Papel GoF | Classe no projeto | Responsabilidade única |
|---|---|---|---|
| **Strategy** | Strategy | `strategy/AgentStrategy` | contrato de planejamento e decisão |
| | ConcreteStrategy | `ReActStrategy` | planeja via LLM e **replaneja** a cada falha |
| | ConcreteStrategy | `PlanThenExecuteStrategy` | planeja **uma vez** e nunca replaneja |
| | ConcreteStrategy | `HumanInTheLoopStrategy` | exige aprovação de todo passo; conduz a conversa |
| | Context | `StrategySelector` | guarda **uma** referência ativa e a troca em runtime |
| **State** | State | `state/AgentState` | contrato: `handle(ctx)` devolve o próximo estado |
| | ConcreteState | `Init`·`Planning`·`Executing`·`Observing`·`WaitingApproval`·`Error`·`Completed` | comportamento por fase |
| | Context | `AgentStateMachine` | mantém o estado corrente e roda o laço |
| **Command** | Command | `command/AgentCommand` | encapsula uma ferramenta com seu contexto |
| | ConcreteCommand | `GitStatus`·`GitBranch`·`GitCheckout`·`GitAdd`·`GitCommit`·`GitPush`·`CreatePullRequest`·`KnowledgeSearch` | uma ferramenta cada |
| | Invoker | `CommandInvoker` | dispara, cronometra e impede exceção de vazar |
| | Receiver | `tool/GitClient`·`GitHubClient`·`KnowledgeService` | o trabalho de verdade |
| **Observer** | Subject | `observer/AgentEventPublisher` | notifica os inscritos (**implementado à mão**) |
| | Observer | `observer/AgentObserver` | contrato de reação |
| | ConcreteObserver | `TraceObserver`·`ErrorObserver`·`ProgressObserver`·`GuiObserver` | reagem sem acoplar |
| **Composite** | Component | `gui/UIComponent` | interface uniforme da UI |
| | Composite | `gui/UIComposite` | agrupa filhos e propaga `refresh()` |
| | Leaf | `GoalPanel`·`PlanPanel`·`StatePanel`·`TraceLogPanel`·`ProgressPanel`·`ApprovalPanel` | painel folha |

---

## O fluxo canônico

```
Usuário: "Quero enviar minha alteração para revisão"
   │
   ▼
[1] StrategySelector escolhe o planejador ─────────────────  STRATEGY
   ▼
[2] PlanningState → strategy.buildPlan(ctx) → LLM devolve os passos
   ▼
[3] AgentStateMachine avança passo a passo ────────────────  STATE
   ▼
[4] ExecutingState → CommandInvoker.execute(cmd) ──────────  COMMAND
   ▼
[5] cada transição e execução publica um evento ───────────  OBSERVER
   ▼
[6] ObservingState → strategy.decideNext(ctx)
   │  CONTINUE | REPLAN | ESCALATE | FAIL
   └── se ESCALATE → StrategySelector TROCA a estratégia ativa
                     ReAct ──► HumanInTheLoop  (estado vai a WAITING_APPROVAL)
   ▼
[7] a árvore de painéis Swing se redesenha ────────────────  COMPOSITE
```

---

## Escalonamento: a troca de estratégia em runtime

O `ReActStrategy` **declara a própria incapacidade** devolvendo `StepDecision.ESCALATE`.
Quem troca a estratégia é o `StrategySelector` — uma estratégia **nunca** chama outra; se
chamasse, viraria Chain of Responsibility e descaracterizaria o Strategy.

A troca acontece sob condição **mensurável**, nunca por palpite:

| # | Gatilho | `Reason` | Onde é detectado | Limite |
|---|---|---|---|---|
| 1 | LLM não produziu plano utilizável | `NO_VALID_PLAN` | `ReActStrategy.buildPlan` | 1ª tentativa |
| 2 | Confiança do plano abaixo do limiar | `LOW_CONFIDENCE` | `ReActStrategy.buildPlan` | `confidence < 0.5` |
| 3 | Replanejamentos sem progresso | `REPLAN_LOOP` | `ReActStrategy.decideNext` | `maxReplans = 2` |
| 4 | Tentativas e replanejamentos esgotados | `COMMAND_FAILED` | `ErrorState` | `maxRetries = 2` |
| 5 | Passo destrutivo (abrir PR) | `DESTRUCTIVE_STEP` | `ExecutingState` | `isDestructive()` |
| 6 | Pedido ambíguo | `AMBIGUOUS_INPUT` | `ReActStrategy.buildPlan` | sempre |

Depois que o humano responde, `deescalate()` devolve o comando ao ReAct, que **replaneja
com a resposta no contexto**. O motivo de cada escalonamento fica registrado no
`TraceObserver` e no `escalationHistory` do contexto.

---

## Como rodar

### 1. Suba o modelo local (llama.cpp)

```bash
llama-server -m gemma-2-2b-it-Q4_K_M.gguf --port 8080 -c 4096
curl http://localhost:8080/v1/models   # deve responder
```

### 2. Prepare um repositório Git de teste

```bash
mkdir -p ~/agent-workspace && cd ~/agent-workspace && git init
echo "# teste" > README.md && git add . && git commit -m "commit inicial"
```

### 3. Rode o agente

```bash
mvn spring-boot:run                                                  # console (padrão)
mvn spring-boot:run -Dspring-boot.run.arguments=--agent.ui=swing     # janela Swing
```

No console: digite o objetivo; `react` / `plan` trocam o planejador; `sair` encerra.

### Configuração (`application.yml`)

| Chave | Padrão | Para que serve |
|---|---|---|
| `agent.ui` | `console` | `console`, `swing` ou `none` |
| `agent.workspace-path` | `~/agent-workspace` | repositório Git em que o agente opera |
| `agent.journey-path` | `~/.onboarding-agent-journey.properties` | onde a trilha de aprendizado é salva |
| `agent.max-retries` | `2` | tentativas por passo antes de escalar |
| `agent.max-replans` | `2` | replanejamentos antes de escalar |
| `agent.confidence-threshold` | `0.5` | confiança mínima para executar um plano |
| `github.owner` / `github.repo` / `github.token` | vazio | necessários apenas para abrir pull requests |

> O Tomcat sobe na **8081** de propósito: a 8080 fica reservada para o `llama-server`.

---

## Os dois casos de demonstração

| Caso | Entrada | O que acontece |
|---|---|---|
| **Feliz** | "Quero criar uma branch feature/login" | ReAct planeja → State avança → Commands executam → `COMPLETED` |
| **Escalonamento** | "Arruma aí o meu repositório" | `ESCALATE` → Selector troca para HITL → `WAITING_APPROVAL` pergunta → resposta → `deescalate` → `COMPLETED` |

Trilha impressa pelo `TraceObserver` no caso de escalonamento:

```
ESTADO  inicio -> INIT
ESTADO  INIT -> PLANNING
PENSOU  [ReAct] Vou planejar para: Arruma ai o meu repositorio
ESCALOU ReAct -> HumanInTheLoop | MOTIVO: AMBIGUOUS_INPUT | PERGUNTA: O que exatamente...
ESTADO  PLANNING -> WAITING_APPROVAL
VOLTOU  HumanInTheLoop -> ReAct (humano respondeu)
ESTADO  WAITING_APPROVAL -> PLANNING
PLANO   ReAct montou 1 passo(s), confianca 0.90: [gitStatus]
EXECUTA gitStatus {}
OK      gitStatus em 270 ms
ESTADO  OBSERVING -> COMPLETED
FIM     objetivo atendido
```

---

## Documentação

| Arquivo | Conteúdo |
|---|---|
| `docs/uml-classes.puml` | classes, com os 5 padrões estereotipados |
| `docs/uml-sequence.puml` | "enviar para revisão", **incluindo o ramo de escalonamento** |
| `docs/uml-state.puml` | máquina de estados e as portas para `WAITING_APPROVAL` |
| `docs/ESTRUTURA-PROJETO-AGENTE-ONBOARDING.md` | documento de arquitetura original |

As imagens já renderizadas estão em `docs/uml-classes.png`, `docs/uml-sequence.png` e
`docs/uml-state.png`, para consulta sem precisar instalar nada. Para regerá-las depois de
alterar os `.puml`:

```bash
java -jar plantuml.jar -tpng -Playout=smetana docs/*.puml
```

O parâmetro `-Playout=smetana` usa o motor em Java puro e dispensa o Graphviz.

---

## Testes

```bash
mvn test                       # 86 testes, sem precisar do LLM
mvn test -Dllm.local=true      # inclui a ida ao modelo local (exige o llama-server no ar)
```

Os testes cobrem cada padrão isoladamente e o fluxo completo:

| Teste | O que prova |
|---|---|
| `PlanTest` · `AgentContextTest` | domínio puro: validação do plano e avanço do índice |
| `GitCommandsIntegrationTest` | Commands rodando **Git de verdade**, sem LLM |
| `CommandInvokerTest` | exceção nunca vaza do Invoker |
| `AgentStateMachineTest` | as sequências de estados, inclusive falha e retry |
| `StrategySelectorTest` | os 6 gatilhos e a troca com **uma** estratégia ativa |
| `StrategyEscalationTest` | os dois casos de demonstração, ponta a ponta |
| `ObserverTest` | trilha com o motivo, e o Subject próprio |
| `CompositeTest` | propagação do `refresh()` pela árvore de painéis |
| `EnviarParaRevisaoTest` | status → branch → add → commit → push → PR, com aprovação antes do PR |
| `KnowledgeServiceTest` | busca vetorial e a queda para o acervo em memória |

---

## Stack

Java 21 · Spring Boot 3.4.5 · Spring AI 1.0.0 (`spring-ai-starter-model-openai` +
`spring-ai-vector-store`) · JGit 6.10 · Swing · Maven.

O LLM é **local e aberto** — llama.cpp servindo Gemma 2 (2B) pela API compatível com a
OpenAI. Nenhum serviço externo é necessário para o agente funcionar; o GitHub só entra
para abrir pull requests.
