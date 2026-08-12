# CLAUDE.md — Build Spec: GitHub Onboarding Agent

> **Como usar:** salve este arquivo como `CLAUDE.md` na raiz do repositório vazio e coloque
> `ESTRUTURA-PROJETO-AGENTE-ONBOARDING.md` na pasta `docs/`. Abra o Claude Code na raiz e diga:
> **"Leia CLAUDE.md e execute a FASE 0."**
> Depois avance uma fase por vez: *"execute a FASE 1"*, e assim por diante.
> **Nunca peça "construa o projeto inteiro"** — o agente pula os checkpoints e você perde o controle
> da arquitetura, que é justamente o que está sendo avaliado.

---

## 0. Contexto imutável

Projeto acadêmico de **Padrões de Projeto**. O código é meio; **a nota está na arquitetura**.
Documento de arquitetura completo: `docs/ESTRUTURA-PROJETO-AGENTE-ONBOARDING.md` — leia antes de codar.

**Tema:** *GitHub Onboarding Agent* — Customer Service Agent for Developer Onboarding.
Um agente que acompanha um desenvolvedor aprendendo Git/GitHub: ensina, planeja, executa
operações Git reais, observa o resultado e adapta o próximo passo.

**Cinco padrões, um único fluxo:**

| Padrão | Papel exato neste projeto |
|---|---|
| **Strategy** | Escolhe e **troca em runtime** o planejador: ReAct ⇄ HumanInTheLoop, + PlanThenExecute |
| **State** | Controla o avanço de tarefa: INIT→PLANNING→EXECUTING→OBSERVING→COMPLETED |
| **Command** | Cada ferramenta (comando Git / busca na base) encapsulada com seu contexto |
| **Observer** | Registra a trilha de raciocínio, falhas e mudanças de estado |
| **Composite** | Árvore de painéis da GUI Swing |

---

## 1. Regras que o agente NÃO pode quebrar

1. **Uma fase por vez.** Ao terminar uma fase, PARE, resuma o que fez e aguarde aprovação.
2. **Não crie classe que não esteja na árvore de arquivos deste documento.** Se achar que falta algo, pergunte.
3. **Proibido `enum` + `switch` para estado.** State é polimórfico: cada estado é uma classe que devolve o próximo.
4. **Proibido `AgentCommand` que só chame o LLM.** Todo Command delega a um Receiver real (`GitClient`, `GitHubClient`, `KnowledgeService`).
5. **Proibido usar `ApplicationEventPublisher` do Spring como Observer.** Implemente `AgentEventPublisher` próprio — o padrão precisa aparecer na UML.
6. **Proibido uma Strategy instanciar ou chamar outra.** Ela só devolve `StepDecision.ESCALATE`; quem troca é o `StrategySelector`.
7. **Nunca duas estratégias ativas ao mesmo tempo.** `StrategySelector` guarda uma única referência `active`.
8. **Não adicione dependências** além das listadas na FASE 0. Sem Lombok, sem MapStruct, sem libs de IA fora o Spring AI.
9. **Sem GUI antes da FASE 6.** Sem Observer antes da FASE 5.
10. **Português nos comentários e nas mensagens ao usuário. Inglês nos nomes de classe e método.**
11. Ao final de cada fase, rode `mvn -q compile` e garanta build limpo.

---

## 2. Ambiente (o aluno prepara, o agente só valida)

LLM local via **llama.cpp** (não Ollama — instável no momento), modelo pequeno aberto:

```bash
llama-server -m gemma-2-2b-it-Q4_K_M.gguf --port 8080 -c 4096
curl http://localhost:8080/v1/models   # deve responder
```

Alternativas de modelo: Gemma 2 (2B/4B), Qwen 3. **Regra: o menor que rodar na máquina.**

---

## FASE 0 — Scaffold

**Entregável:** projeto Maven compilando e respondendo ao LLM local.

- Java 21, Spring Boot 3.3+, packaging jar
- `groupId`: `br.edu.fatec` · `artifactId`: `onboarding-agent` · pacote base `br.edu.fatec.onboardingagent`

Dependências — **exatamente estas**:

```xml
<!-- spring-ai-bom 1.0.0 no dependencyManagement -->
<dependency> org.springframework.boot : spring-boot-starter-web </dependency>
<dependency> org.springframework.ai  : spring-ai-starter-model-openai </dependency>
<dependency> org.eclipse.jgit        : org.eclipse.jgit : 6.10.0.202406032230-r </dependency>
<dependency> org.springframework.boot: spring-boot-starter-test : test </dependency>
```

`src/main/resources/application.yml`:

```yaml
spring:
  application.name: onboarding-agent
  ai:
    openai:
      base-url: http://localhost:8080
      api-key: not-needed
      chat:
        options:
          model: gemma-2-2b-it
          temperature: 0.2
agent:
  workspace-path: ${user.home}/agent-workspace   # repo Git de teste
  max-replans: 2
  max-retries: 2
  confidence-threshold: 0.5
```

Crie `config/LlmConfig.java` (bean `ChatClient`) e `llm/LlmGateway.java` (única porta para o LLM,
método `String complete(String prompt)`).

**✅ Aceite:** `mvn spring-boot:run` sobe e um teste manual do `LlmGateway` devolve texto do modelo local.
**⛔ PARE e reporte.**

---

## FASE 1 — Domínio puro

**Entregável:** POJOs sem nenhuma anotação Spring e sem nenhuma referência a IA.

```
domain/
├── Goal.java              // texto do usuário + objetivo interpretado
├── PlanStep.java          // id, description, commandName, Map<String,Object> args, StepStatus
├── Plan.java              // List<PlanStep>, confidence (double), boolean isValid()
├── ExecutionResult.java   // boolean success, String output, String errorMessage
├── StepDecision.java      // enum CONTINUE, REPLAN, ESCALATE, FAIL
├── EscalationSignal.java  // record(Reason, String questionToHuman, PlanStep blockedStep)
│                          // Reason: NO_VALID_PLAN, LOW_CONFIDENCE, REPLAN_LOOP,
│                          //         COMMAND_FAILED, DESTRUCTIVE_STEP, AMBIGUOUS_INPUT
├── LearningJourney.java   // módulos Git/GitHub + progresso do dev
└── AgentContext.java      // goal, plan, currentStepIndex, List<ExecutionResult> history,
                           // replanCount, retryCount, escalationHistory, humanResponse, journey
```

`Plan.isValid()` = tem ≥1 passo **e** todo `commandName` existe no registry. Usado como gatilho de escalonamento.

**✅ Aceite:** testes unitários de `Plan.isValid()` e do avanço de `currentStepIndex` passando. Zero import de Spring.
**⛔ PARE.**

---

## FASE 2 — Command (+ Receivers)

**Entregável:** ferramentas executando Git de verdade, **testáveis sem LLM**.

```
command/
├── AgentCommand.java          // interface: name(), description(), execute(ctx, args)
├── CommandRegistry.java       // Map<String, AgentCommand>, injetado via List<AgentCommand>
├── CommandInvoker.java        // executa, cronometra, captura exceção → ExecutionResult
└── impl/
    ├── GitStatusCommand.java
    ├── GitBranchCommand.java
    ├── GitCheckoutCommand.java
    └── KnowledgeSearchCommand.java   // FASE 2: stub em memória (Map de conceitos Git)
tool/
├── GitClient.java             // JGit sobre agent.workspace-path — RECEIVER
└── KnowledgeService.java      // stub agora, VectorStore na FASE 7
```

- `description()` é o texto que o LLM lê para escolher a ferramenta. Escreva com cuidado.
- `CommandInvoker` **nunca** deixa exceção vazar: converte em `ExecutionResult.failure(msg)`.
- Marque comandos destrutivos com `default boolean isDestructive() { return false; }`.

**✅ Aceite:** teste de integração cria um repo temporário, roda `GitStatusCommand` e `GitBranchCommand`, e valida o `ExecutionResult`. **Sem nenhuma chamada ao LLM.**
**⛔ PARE.**

---

## FASE 3 — State

**Entregável:** máquina de estados girando com um **plano mockado** (ainda sem LLM).

```
state/
├── AgentState.java           // interface: String name(); AgentState handle(AgentContext ctx);
├── InitState.java
├── PlanningState.java
├── ExecutingState.java
├── ObservingState.java
├── WaitingApprovalState.java
├── ErrorState.java
├── CompletedState.java
└── AgentStateMachine.java    // loop: while(!(current instanceof CompletedState)) current = current.handle(ctx)
```

Transições obrigatórias:

```
INIT → PLANNING → EXECUTING → OBSERVING ─┬─ CONTINUE  → EXECUTING
                                          ├─ REPLAN    → PLANNING
                                          ├─ ESCALATE  → WAITING_APPROVAL
                                          └─ FAIL      → ERROR
ERROR → EXECUTING (retry < maxRetries) | PLANNING (replan) | WAITING_APPROVAL (retries esgotados)
WAITING_APPROVAL → PLANNING (após humanResponse)
```

**✅ Aceite:** teste que injeta um `Plan` fixo com 3 passos e verifica a sequência de `name()` dos estados percorridos. Sem LLM.
**⛔ PARE.**

---

## FASE 4 — Strategy + escalonamento

**Entregável:** o agente ponta a ponta, com LLM, incluindo troca de estratégia em runtime. **Este é o MVP do 14/09.**

```
strategy/
├── AgentStrategy.java            // name(), buildPlan(ctx), decideNext(ctx, last),
│                                 // requiresApproval(step), escalationSignal(ctx)
├── ReActStrategy.java            // planeja via LLM; replaneja após cada observação
├── PlanThenExecuteStrategy.java  // planeja 1x, executa tudo, nunca replaneja
├── HumanInTheLoopStrategy.java   // assume quando o ReAct escala; formula a pergunta ao humano
└── StrategySelector.java         // Context: active, select(), escalate(signal), deescalate()
llm/PromptTemplates.java          // prompt de planejamento em JSON estrito
```

Contrato do prompt de planejamento — o LLM deve devolver **só JSON**:

```json
{ "confidence": 0.0,
  "steps": [ { "description": "...", "commandName": "gitStatus", "args": {} } ],
  "clarificationNeeded": null }
```

Se vier `clarificationNeeded` preenchido, ou o parse falhar, ou `confidence < 0.5`, ou `!plan.isValid()`
→ `EscalationSignal`. Nunca deixe o agente "chutar" um plano.

Escalonamento — MVP entrega **apenas o gatilho #1** (`NO_VALID_PLAN`) e o `#6` (`AMBIGUOUS_INPUT`).
Os demais entram na FASE 5.

```java
// StrategySelector
public AgentStrategy escalate(AgentContext ctx, EscalationSignal s) {
    ctx.recordEscalation(s);
    this.active = humanInTheLoop;   // troca a referência — nunca duas ativas
    return this.active;
}
public void deescalate(AgentContext ctx) { this.active = reAct; ctx.clearEscalation(); }
```

Na FASE 4 a interação humana é **por console** (`Scanner`) — GUI só na FASE 6.

**✅ Aceite — os dois casos rodando:**

| Caso | Entrada | Esperado |
|---|---|---|
| Feliz | "Quero criar uma branch feature/login" | ReAct planeja → State avança → Commands executam → COMPLETED |
| Escalonamento | "Arruma aí o meu repositório" | `ESCALATE` → Selector troca p/ HITL → `WAITING_APPROVAL` pergunta → resposta → `deescalate` → COMPLETED |

**⛔ PARE. Esta é a entrega de 14/09.**

---

## FASE 5 — Observer + robustez

```
observer/
├── AgentEvent.java              // sealed interface + records:
│                                // PlanCreated, StateChanged, CommandStarted, CommandCompleted,
│                                // CommandFailed, ReasoningStep, StrategyEscalated,
│                                // StrategyDeescalated, UserApprovalRequired, GoalCompleted
├── AgentObserver.java           // void onEvent(AgentEvent)
├── AgentEventPublisher.java     // SUBJECT próprio: subscribe / unsubscribe / publish
└── impl/
    ├── TraceObserver.java       // trilha de raciocínio + MOTIVO de cada escalonamento
    ├── ErrorObserver.java
    └── ProgressObserver.java    // atualiza LearningJourney
```

Publique eventos em: `AgentStateMachine` (toda transição), `CommandInvoker` (início/fim/falha),
`StrategySelector` (escalate/deescalate), `ReActStrategy` (cada passo de raciocínio).

Implemente aqui os gatilhos restantes: `LOW_CONFIDENCE`, `REPLAN_LOOP`, `COMMAND_FAILED`, `DESTRUCTIVE_STEP`.

**✅ Aceite:** ao rodar o caso de escalonamento, o `TraceObserver` imprime a trilha completa e o `Reason`. Nenhuma classe de `state/`, `command/` ou `strategy/` importa uma classe de `observer/impl/`.
**⛔ PARE.**

---

## FASE 6 — GUI Swing com Composite

```
gui/
├── UIComponent.java     // COMPONENT: void render(); void refresh(AgentEvent e);
├── UIComposite.java     // COMPOSITE: List<UIComponent> children; refresh() propaga a todos
├── leaf/GoalPanel · PlanPanel · StatePanel · TraceLogPanel · ProgressPanel · ApprovalPanel
└── MainWindow.java
observer/impl/GuiObserver.java   // ponte Observer → raiz do Composite
```

O `GuiObserver` recebe o evento e chama `root.refresh(event)`; a árvore propaga sozinha.
**`ApprovalPanel`** substitui o `Scanner` da FASE 4 na interação de escalonamento.

**✅ Aceite:** janela mostra objetivo, plano com passos marcados, estado atual, log da trilha e — no escalonamento — o painel de aprovação.
**⛔ PARE.**

---

## FASE 7 — RAG + GitHub

- `VectorStoreConfig` com `SimpleVectorStore`; ingerir um `.md` com conceitos de Git/GitHub
- `KnowledgeService` real; `KnowledgeSearchCommand` passa a consultar o vector store
- `GitAddCommand`, `GitCommitCommand`, `GitPushCommand`
- `GitHubClient` (RestClient) + `CreatePullRequestCommand` — **marcado como destrutivo** → força HITL
- `LearningJourney` persistida

**✅ Aceite:** o caso "quero enviar minha alteração para revisão" percorre status → branch → commit → push → PR, pedindo aprovação antes do PR.

---

## FASE 8 — Documentação (UML)

Gerar em `docs/`:
- `uml-classes.puml` — diagrama de classes com os 5 padrões estereotipados
- `uml-sequence.puml` — sequência do caso "enviar para revisão", **incluindo o ramo de escalonamento**
- `uml-state.puml` — máquina de estados com as duas portas para `WAITING_APPROVAL`
- `README.md` — tabela de rastreabilidade padrão → papel GoF → classe

---

## 3. Checklist final (o que o professor vai perguntar)

- [ ] Os 5 padrões estão no **mesmo fluxo de execução**, não em módulos isolados
- [ ] Consigo apontar, no código, a classe exata de cada papel GoF
- [ ] O State é polimórfico — não existe `switch` de estado
- [ ] Todo Command tem um Receiver real
- [ ] O `AgentEventPublisher` é nosso, não do Spring
- [ ] As 3 strategies têm comportamento genuinamente diferente
- [ ] A troca ReAct → HITL acontece em runtime, com **uma** estratégia ativa por vez, disparada por gatilho mensurável
- [ ] O modelo é aberto e roda local
- [ ] A UML bate com o código
