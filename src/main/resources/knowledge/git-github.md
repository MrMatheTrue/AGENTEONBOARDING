# Conceitos de Git e GitHub

## git status
Mostra em qual branch voce esta e o que mudou desde o ultimo commit. Separa tres grupos:
alteracoes preparadas para commit (staged), alteracoes ainda nao preparadas, e arquivos
que o Git nunca viu (untracked). E o comando que responde "onde eu estou e o que fiz".

## branch
Uma branch e uma linha de trabalho paralela. Voce cria uma para desenvolver algo sem
mexer na principal, e depois junta o resultado. `git branch nome` cria a branch mas nao
muda para ela; `git branch` sozinho lista as que existem.

## git checkout
Troca a branch ativa. Com a opcao -b (`git checkout -b nome`) ele cria a branch e ja muda
para ela na mesma operacao, que e o atalho mais usado no dia a dia.

## staging area
E a area intermediaria entre o seu diretorio de trabalho e o historico. `git add` move
alteracoes para la. So o que esta na staging area entra no proximo commit, o que permite
commitar parte do que voce mexeu.

## git add
Prepara arquivos para o commit, colocando-os na staging area. `git add .` prepara tudo o
que mudou na pasta atual; `git add arquivo` prepara so um arquivo.

## git commit
Grava um ponto no historico com uma mensagem que explica a mudanca. A mensagem importa:
ela e o que outra pessoa (ou voce daqui a seis meses) vai ler para entender o porque.
Um bom commit e pequeno e tem um proposito unico.

## git push
Envia os commits locais para o repositorio remoto, publicando seu trabalho para o time.
Antes do push o trabalho existe so na sua maquina.

## remoto e origin
O remoto e a copia do repositorio hospedada no servidor. Por convencao o remoto principal
se chama `origin`. E por ele que o time troca codigo.

## pull request
Um pull request pede que a sua branch seja revisada e incorporada a principal. E onde
acontece a revisao de codigo no GitHub: alguem le o que voce fez, comenta e aprova.
Abrir um PR nao junta o codigo; junta so depois do merge.

## merge e conflito
`git merge` junta o historico de duas branches. Quando as duas mexeram na mesma linha do
mesmo arquivo, o Git nao decide sozinho e aparece um conflito, que precisa ser resolvido
a mao antes de concluir.

## git clone
Baixa um repositorio remoto inteiro, com todo o historico, para a sua maquina. E o
primeiro comando de quem entra num projeto que ja existe.

## fluxo tipico de contribuicao
1. `git status` para ver onde voce esta
2. criar uma branch para a sua tarefa
3. mexer no codigo
4. `git add` e `git commit` para registrar
5. `git push` para publicar a branch
6. abrir um pull request pedindo revisao
