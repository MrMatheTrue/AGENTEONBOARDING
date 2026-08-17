package br.edu.fatec.onboardingagent.tool;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * RECEIVER do padrao Command: quem realmente fala com o Git, via JGit.
 *
 * <p>Nenhum AgentCommand implementa logica de Git — eles delegam tudo aqui. E isso que
 * impede o erro classico de "Command que so chama o LLM": o trabalho de verdade mora
 * neste receiver, e ele funciona sozinho, sem modelo nenhum carregado.</p>
 *
 * <p>Toda falha de infraestrutura (repositorio inexistente, branch duplicada, IO) vira
 * {@link IllegalStateException} com mensagem em portugues. Quem transforma isso em
 * ExecutionResult e o CommandInvoker — excecao nunca chega ao usuario.</p>
 */
@Component
public class GitClient {

    private final Path workspacePath;

    // @Autowired explicito: existem dois construtores, e sem a marcacao o Spring
    // nao sabe qual usar ("No default constructor found").
    @Autowired
    public GitClient(@Value("${agent.workspace-path}") String workspacePath) {
        this(Path.of(workspacePath));
    }

    /** Usado pelos testes, que apontam para um repositorio temporario. */
    public GitClient(Path workspacePath) {
        this.workspacePath = workspacePath;
    }

    public Path workspacePath() {
        return workspacePath;
    }

    /**
     * Situacao do diretorio de trabalho, em texto legivel para o desenvolvedor.
     */
    public String status() {
        try (Git git = open()) {
            Status status = git.status().call();
            String branch = git.getRepository().getBranch();

            if (status.isClean()) {
                return "Branch atual: %s%nWorking tree limpa - nada para commitar.".formatted(branch);
            }

            StringBuilder texto = new StringBuilder("Branch atual: %s%n".formatted(branch));
            acrescenta(texto, "Alteracoes prontas para commit (staged)", ordenado(status.getAdded(), status.getChanged()));
            acrescenta(texto, "Alteracoes nao preparadas", ordenado(status.getModified(), status.getMissing()));
            acrescenta(texto, "Arquivos nao rastreados", ordenado(status.getUntracked()));
            return texto.toString().stripTrailing();
        } catch (Exception e) {
            throw falha("consultar o status do repositorio", e);
        }
    }

    /** Nomes curtos das branches locais (sem o prefixo refs/heads/). */
    public List<String> listBranches() {
        try (Git git = open()) {
            return git.branchList().call().stream()
                    .map(Ref::getName)
                    .map(nome -> nome.replace("refs/heads/", ""))
                    .toList();
        } catch (Exception e) {
            throw falha("listar as branches", e);
        }
    }

    public String currentBranch() {
        try (Git git = open()) {
            return git.getRepository().getBranch();
        } catch (Exception e) {
            throw falha("descobrir a branch atual", e);
        }
    }

    /**
     * Cria uma branch local a partir do HEAD.
     *
     * @return nome da branch criada
     */
    public String createBranch(String branchName) {
        exigeNome(branchName);
        try (Git git = open()) {
            Ref ref = git.branchCreate().setName(branchName).call();
            return ref.getName().replace("refs/heads/", "");
        } catch (Exception e) {
            throw falha("criar a branch '%s'".formatted(branchName), e);
        }
    }

    /**
     * Troca de branch, opcionalmente criando-a na mesma operacao (equivale a {@code git checkout -b}).
     *
     * @return nome da branch em que o repositorio ficou
     */
    public String checkout(String branchName, boolean createIfMissing) {
        exigeNome(branchName);
        try (Git git = open()) {
            git.checkout()
                    .setCreateBranch(createIfMissing && !branchExists(git, branchName))
                    .setName(branchName)
                    .call();
            return git.getRepository().getBranch();
        } catch (Exception e) {
            throw falha("trocar para a branch '%s'".formatted(branchName), e);
        }
    }

    // ------------------------------------------------------------------ apoio

    private Git open() {
        File dir = workspacePath.toFile();
        if (!dir.isDirectory()) {
            throw new IllegalStateException(
                    "O workspace '%s' nao existe. Crie a pasta e rode 'git init' nela antes de usar o agente."
                            .formatted(workspacePath));
        }
        try {
            return Git.open(dir);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "A pasta '%s' nao e um repositorio Git. Rode 'git init' nela antes de usar o agente."
                            .formatted(workspacePath), e);
        }
    }

    private static boolean branchExists(Git git, String branchName) throws Exception {
        return git.branchList().call().stream()
                .anyMatch(ref -> ref.getName().equals("refs/heads/" + branchName));
    }

    private static void exigeNome(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            throw new IllegalStateException("E preciso informar o nome da branch.");
        }
    }

    private static IllegalStateException falha(String acao, Exception causa) {
        // A mensagem do JGit vem em ingles; preservamos como detalhe tecnico.
        return new IllegalStateException("Nao foi possivel %s: %s".formatted(acao, causa.getMessage()), causa);
    }

    @SafeVarargs
    private static Set<String> ordenado(Set<String>... conjuntos) {
        Set<String> todos = new TreeSet<>();
        for (Set<String> conjunto : conjuntos) {
            todos.addAll(conjunto);
        }
        return todos;
    }

    private static void acrescenta(StringBuilder texto, String titulo, Set<String> arquivos) {
        if (arquivos.isEmpty()) {
            return;
        }
        texto.append(titulo).append(":").append(System.lineSeparator());
        arquivos.forEach(arquivo -> texto.append("  - ").append(arquivo).append(System.lineSeparator()));
    }
}
