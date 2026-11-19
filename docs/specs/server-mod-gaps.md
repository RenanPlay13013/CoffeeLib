# Lacunas da CoffeeLib encontradas no RavenEssentials

O RavenEssentials (mod de comandos e moderação para NeoForge 1.21.1) é o primeiro
consumidor grande da CoffeeLib. Boa parte do código dele que ficou difícil de ler
não é lógica do mod: é o mod contornando coisas que a lib ainda não faz. Este
documento lista essas lacunas, propõe a API de cada uma e define a ordem de entrega.

Cada item diz **onde o problema aparece no RavenEssentials**, a **API proposta**,
o **comportamento** em cada plataforma, os **casos de borda**, os **testes** e o
que **sai do mod** depois.

---

## Princípios que valem para todos os itens

1. **O `coffeelib-core` continua sem tipos do Minecraft.** O que depende do jogo
   (`ServerPlayer`, `ServerLevel`, `Component`, `World`...) entra por uma extensão
   que cada plataforma implementa. O core só define o contrato.
2. **As três plataformas** (NeoForge 1.21.1, Forge 1.20.1, Paper) recebem cada
   item ao mesmo tempo, com o mesmo código do lado do mod. Quando uma plataforma
   não tem como fazer algo, isso é documentado, nunca ignorado em silêncio.
3. **Erro de quem usa a lib falha no `register`**, com o nome do método, como os
   comandos já fazem hoje. Nada de descobrir o erro quando alguém roda o comando.
4. **Tudo é aditivo.** Nenhuma assinatura pública muda: são versões *minor*.
5. **Documentação nos dois READMEs** (`README.md` e `README.pt-BR.md`) e testes
   no `coffeelib-core` com uma plataforma falsa, como os testes atuais.
6. **Mensagens padrão da lib em inglês**, como hoje, e substituíveis pelo
   catálogo de mensagens (item 8).

---

## Fases

| Fase | Itens | Por quê primeiro |
|---|---|---|
| **1** ✅ | 9 Scheduler · 1 Comandos assíncronos · 10 Repositório · 16 Padrão de permissão no comando | Base para o resto; o item 1 depende do 9. Implementada; ver "Decisões da fase 1". |
| **2** | 2 Tipos de argumento · 3 Alvo `[player]` · 4 Visibilidade · 6 Sugestões com contexto · 7 Substituir vanilla | O que mais tira código dos comandos. |
| **3** | 5 Mudança de permissão · 8 Texto e mensagens · 11 DI ligada à lib | Melhoram a arquitetura dos mods. |
| **4** | 12 Localização · 13 Dados no jogador · 14 Permissão numérica · 15 Menus | Necessários para homes, kits e warps. |

---

## Fase 1

### 9. Scheduler

**No RavenEssentials:** `player.server.execute(...)` espalhado; o fly confere a
permissão com `tickCount % 20 == 0` dentro de um `PlayerTickEvent`; a troca de
modo de jogo usa `server.execute` para rodar "depois".

**API** (`coffeelib-api`, pacote `scheduler`):

```java
public interface Scheduler {
    /** Roda na thread principal do servidor: já, se estiver nela; senão, no próximo tick. */
    void onMain(Runnable task);

    /** Executor da thread principal, para CompletableFuture.thenAcceptAsync(..., scheduler.main()). */
    Executor main();

    /** Uma vez, daqui a {@code ticks} ticks. */
    Task later(long ticks, Runnable task);

    /** A cada {@code period} ticks, começando daqui a {@code delay}. */
    Task every(long delay, long period, Runnable task);

    /** O mesmo future, completando na thread principal. */
    <T> CompletableFuture<T> onMain(CompletableFuture<T> future);
}

public interface Task {
    void cancel();
    boolean isCancelled();
}
```

Ponto de entrada: `CoffeeLib.scheduler(container)`, `CoffeeLib.scheduler()` no
Forge e `CoffeeLib.scheduler(this)` no Paper.

**Comportamento:**
- **NeoForge e Forge:** uma fila por dono, drenada no `ServerTickEvent.Post`.
  `main()` é o `MinecraftServer` como `Executor`.
- **Paper:** delega ao `BukkitScheduler` do plugin dono.
- Todas as tarefas de um dono são canceladas quando o servidor para (Forge e
  NeoForge) ou quando o plugin é desabilitado (Paper).
- Uma exceção numa tarefa é logada com o id do dono e não derruba as outras.
  Num `every`, a tarefa continua agendada.
- Antes do servidor existir, como no construtor do mod, `onMain`, `later` e
  `every` guardam a tarefa e a liberam no primeiro tick.

**Testes:** com um relógio falso no core: a ordem, o `later` em N ticks, o
cancelamento, uma exceção que não para a fila, e o cancelamento ao parar.

---

### 1. Comandos assíncronos

**No RavenEssentials:** o `/back` faz o ritual inteiro na mão: busca o banco,
volta com `server.execute`, confere `hasDisconnected()`, trata o erro em
`exceptionally` e loga. E o `throw new CommandException(...)`, que todo comando
síncrono usa, **não funciona** dentro do future: o comando já terminou.

**API:** nenhuma anotação nova. Um executor pode devolver
`CompletionStage<?>`/`CompletableFuture<?>`:

```java
@Command(value = "back", permission = "ravenessentials.back")
CompletableFuture<Void> back(ServerPlayer player) {
    return players.findById(player.getStringUUID()).thenAccept(found -> {
        StoredLocation target = found.flatMap(PlayerEntity::backLocation)
                .orElseThrow(() -> new CommandException("Nenhum local para voltar."));
        Locations.teleport(player, target);
    });
}
```

**Comportamento:**
- **No `CommandDefinition.invoke`,** o valor de retorno passa a ser lido. Se for
  um `CompletionStage`, a lib registra um `whenComplete` que roda **na thread
  principal** (pelo item 9).
- **Quando o `thenAccept` do mod roda:** a lib não muda a thread das etapas do
  mod. Para que o código dentro delas possa mexer no mundo, a lib oferece o
  `scheduler.onMain(future)`, e o README recomenda isso. **Decidido:** sem
  `@Command(mainThread = true)`. Encadear as etapas do mod na thread principal
  esconderia em que thread cada uma roda, e o `scheduler.onMain(future)` resolve
  com uma linha.
- **Ao completar:**
  - com sucesso, nada a fazer;
  - com `CommandException`, mesmo dentro de `CompletionException` ou
    `ExecutionException` (a lib desembrulha a cadeia): manda `e.getMessage()`
    como falha para quem rodou, igual ao comando síncrono;
  - com outra exceção: loga em SEVERE, com dono, comando e quem rodou, e manda
    a mensagem padrão de erro interno.
- **Quem rodou já não existe** (jogador desconectado): a resposta é descartada
  sem erro. Cada plataforma diz se o remetente ainda é válido, com um
  `CommandSource.isAvailable()` novo e `default true`.
- **Brigadier:** o executor devolve `1` na hora, porque o Brigadier não espera
  futures. `/execute store result` passa a registrar "começou", não "deu certo".
  Documentar.
- **Paper:** a mesma coisa, pelo `BukkitScheduler`.

**Casos de borda:**
- Um future que nunca completa não vaza nada: só não responde. Fica fora do
  escopo um *timeout* opcional, como `@Command(timeoutSeconds = 10)`.
- Devolver `null` em vez de um future conta como "terminou", como o `void`.

**Testes:** o sucesso; uma `CommandException` dentro de `thenApply`; outra
exceção; remetente indisponível; a resposta chegando pela fila da thread
principal falsa.

**Sai do RavenEssentials:** o `exceptionally`, o `LOGGER`, o `hasDisconnected` e
o `server.execute` do `/back`, e o mesmo nas homes, no `/seen` e no `/playtime`.

---

### 10. Mais no `HibernateRepository`

**No RavenEssentials:** o `PlayerRepository` escreveu um `update(id, change)` e
um "busca ou cria" dentro de `recordLogin`. Todo repositório de um jogador vai
precisar dos dois.

**API** (em `HibernateRepository<T, ID>`):

```java
/** Muda a entidade, se existir, numa transação. true se existia. */
public CompletableFuture<Boolean> update(ID id, Consumer<? super T> change);

/** Busca ou cria com {@code create}, aplica {@code change}, tudo numa transação. Devolve a entidade. */
public CompletableFuture<T> upsert(ID id, Supplier<? extends T> create, Consumer<? super T> change);
```

**Comportamento:**
- O `change` roda dentro da transação, com a entidade *gerenciada*. Não
  precisa de `merge`: o *dirty checking* grava no commit.
- Se o `create` devolver `null` ou uma entidade sem o mesmo `id`, o future
  falha (com `DatabaseException`, como os outros erros do repositório). Evita
  gravar o registro errado em silêncio.
- **A linha é travada** (`PESSIMISTIC_WRITE`) durante a transação. Sem isso, no
  H2 ou no MySQL, duas atualizações do mesmo id podiam ler a mesma linha e uma
  apagar a mudança da outra. Dois *primeiros* `upsert` de um id sem linha
  disputam a inserção: um falha com chave duplicada, nunca em silêncio.
- **O Javadoc diz em destaque:** o `change` roda **na thread do banco**. Ele não
  pode ler o mundo nem o jogador; os valores têm de ser capturados antes.

**Testes** (no `CoffeeHibernateTest`, em SQLite e H2):
- `update` de um id que não existe devolve `false`;
- `upsert` cria e depois atualiza, sem duplicar;
- `upsert` concorrente no mesmo id não perde escrita no H2. No SQLite isso já é
  serializado pelo pool de 1 conexão.

**Sai do RavenEssentials:** o `update` próprio, e o `recordLogin` vira
`upsert(uuid, () -> new PlayerEntity(uuid), p -> p.onLogin(...))`.

---

### 16. Padrão de permissão no comando

**No RavenEssentials:** o `RavenPermissions` tem 40 nós, e uns 30 são só
`@Permission(description = "Use /x")`. Esses não fazem diferença, porque o
`NeoForgeCommandManager` já declara o nó do comando com `declareIfAbsent`. Ficam
declarados à mão só os que têm padrão diferente, como o `/list` (`EVERYONE`).

**API (decidido):** em vez de um `permissionDefault` novo no `@Command`, o
`@Permission` que já existe passa a valer também em métodos e classes de comando:

```java
@Command(value = "list", permission = "ravenessentials.list")
@Permission(def = PermissionDefault.EVERYONE, description = "Use /list")
void list(CommandSource source) { ... }
```

Um `INHERIT` no enum `PermissionDefault` teria de ser tratado em cada `switch`
da lib (o do Paper converte para o `PermissionDefault` do Bukkit) e não faria
sentido em `PermissionManager.declare(...)`. Reaproveitar a anotação não muda
nenhum enum.

**Comportamento:**
- A declaração automática usa o `def` e a `description` do `@Permission` do
  método, ou da classe. Sem ele, continua o padrão do op level, e a descrição é
  a do `@Command`.
- `@Permission` num comando sem `permission` falha no `register`.
- Declarar o mesmo nó à mão antes continua ganhando, como hoje.

**Sai do RavenEssentials:** as anotações `@Permission` dos nós de comando. As
constantes podem ficar, para evitar texto repetido.

---

## Decisões da fase 1

- **`CommandSource.isAvailable()`**, com `default true`, é o único método novo
  em interface pública que já existia. NeoForge e Forge respondem
  `!player.hasDisconnected()`, e o Paper `player.isOnline()`.
- **`CommandDefinition.execute`/`executeParsed`** passaram a devolver o
  `CompletionStage` do executor (ou `null`). É uma classe do core, usada só
  pelas plataformas.
- **O `AsyncCommands.watch`** do core concentra a resposta tardia para as três
  plataformas. Cada uma passa só o executor da thread principal e como mostrar
  a falha.
- **O `every`** conta o período a partir de cada execução, como o Bukkit, e
  `later(0)` e `later(1)` são o próximo tick nos dois.
- **O `PaperScheduler`** só usa métodos do Bukkit que existem desde a 1.8. O
  `isCancelled` da tarefa é guardado pela lib, porque o do Bukkit não existe
  antes da 1.12.

---

## Fase 2

### 2. Tipos de argumento do Minecraft

**No RavenEssentials:**
- O `Targets.resolve` busca jogador por nome, e 6 comandos declaram o mesmo
  `online()` para sugerir nomes.
- O `RegistryIds` existe porque `StringArgumentType.word()` não aceita `:`, então
  `/enchant` e `/potion` só aceitam ids vanilla, sem o `minecraft:`.
- O `Teleport` lê `x y z [dimensão]` num `@Greedy` e interpreta `~` à mão.

**Arquitetura:** os tipos do jogo não podem estar no core. O core ganha uma
extensão:

```java
// coffeelib-api
public interface ArgumentType<T> {
    /** Converte o texto digitado. Lança CommandException com a mensagem para quem rodou. */
    T parse(CommandSource source, String input);

    /** Sugestões para o texto atual do argumento. */
    Collection<String> suggest(CommandSource source, String current);

    /** Quantas palavras o argumento ocupa: 1, ou N fixo (coordenadas = 3). */
    default int words() { return 1; }
}

public interface CommandManager {
    ...
    /** Ensina a lib a ler {@code type} como parâmetro de comando, para os handlers deste dono. */
    <T> void argument(Class<T> type, ArgumentType<T> parser);
}
```

O core ganha um `ArgumentKind.CUSTOM`, que guarda o `ArgumentType`. Cada
plataforma registra os seus tipos prontos e pode trocar o nó Brigadier por um
**nativo** quando ele existe. A vantagem do nativo é que o cliente valida e
colore enquanto a pessoa digita.

**Tipos prontos:**

| Tipo do parâmetro | NeoForge / Forge | Paper | Observação |
|---|---|---|---|
| `ServerPlayer` / `Player` | palavra + nomes online | `Bukkit.getPlayerExact` | Respeita a visibilidade (item 4). Seletores (`@p`) só com `@Selector`, usando `EntityArgument`. |
| `ServerLevel` / `World` | `ResourceLocation` + aliases `overworld`, `nether`, `end` | nome ou key do mundo | Sugere os mundos carregados. |
| `ResourceLocation` / `NamespacedKey` | `ResourceLocationArgument.id()` | key | Aceita `:`. Sem namespace, usa `minecraft`. |
| `Holder<T>` com `@Registry("enchantment")` | id + busca no registro | `Registry` do Bukkit | Sugere todos os ids, de qualquer namespace. |
| `Vec3` / `BlockPos` / `Location` | 3 palavras, com `~` e `~N` relativos a quem rodou | idem | X e Z inteiros vão para o centro do bloco no `Vec3`. |
| `Duration` (core) | `1d2h30m`, `90s`, `10m` | idem | Para mute, ban e cooldowns. |
| `UUID` (core) | texto | idem | |

**Casos de borda:**
- Um tipo sem `ArgumentType` registrado continua falhando no `register`.
- `@Default` em um tipo novo passa pelo próprio `parse` (`@Default("~ ~ ~")`).
- O `@Suggest` do mod soma às sugestões do tipo, em vez de substituí-las.
  *Decidir:* um `@Suggest(replace = true)`.

**Testes:** o core com um `ArgumentType` falso, de 1 e de 3 palavras; em cada
plataforma, um teste de compilação do nó Brigadier.

**Sai do RavenEssentials:** `RegistryIds`, a maior parte do `Targets`, o parser e
o `alias` do `Teleport`, e os 6 `online()`.

---

### 3. Alvo opcional `[player]` com permissão para outros

**No RavenEssentials:** `/fly`, `/god`, `/heal`, `/vanish` e `/ping` repetem

```java
@Arg("player") @Default("") @Suggest(provider = "online") String name
...
ServerPlayer target = Targets.resolve(source, name, RavenPermissions.GOD_OTHERS);
```

**API:**

```java
@Command(value = "god", permission = "ravenessentials.god")
void god(CommandSource source, @OrSelf(others = "ravenessentials.god.others") ServerPlayer target) { ... }
```

**Comportamento:**
- **Omitido:** vale quem rodou. Se quem rodou é o console, o erro é de uso:
  "Informe um jogador".
- **Outro jogador:** exige o nó `others`, que a lib declara com padrão
  `OPERATORS` e a descrição "`/<comando>` on other players".
- As sugestões de nomes só aparecem para quem tem o nó `others`.
- Sem `others`, pode escolher qualquer jogador, como o `/ping`.
- O parâmetro é opcional por natureza: é o último, e dispensa o `@Default`.

**Sai do RavenEssentials:** o resto do `Targets`, a mensagem "sem permissão para
outros jogadores", e o `HEAL_OTHERS`/`GOD_OTHERS`/... declarados à mão.

---

### 4. Visibilidade de jogadores entre mods

**No RavenEssentials:** o `Targets.onlineNames` esconde quem está em vanish das
sugestões, mas só nos comandos do próprio mod. Qualquer outro mod que use a
CoffeeLib revela o vanish no autocompletar. Além disso, o `util` do mod importa
`vanish`.

**API:**

```java
public interface PlayerVisibility {
    /** Se {@code viewer} pode ver {@code target}. O viewer null é o console, que vê todos. */
    boolean canSee(@Nullable Object viewer, Object target);
}

public interface PlayerDirectory {
    /** Soma uma regra. Todas precisam permitir. Devolve a assinatura, para remover. */
    EventSubscription visibility(PlayerVisibility rule);

    boolean canSee(@Nullable Object viewer, Object target);

    /** Os online que {@code viewer} pode ver. */
    List<Object> visibleTo(@Nullable Object viewer);
}
```

Ponto de entrada: `CoffeeLib.players()`, com um registro compartilhado entre
todos os mods, como o `SharedPermissions`.

**Comportamento:**
- O argumento `ServerPlayer` do item 2 usa `canSee` **nas sugestões e na
  resolução**. Um jogador escondido dá "Jogador não encontrado", **a mesma
  mensagem** de um nome que não existe, para o erro não revelar nada.
- O próprio jogador sempre se vê.
- **No Paper,** também consulta o `Player.canSee` nativo.

**Sai do RavenEssentials:** a filtragem no `Targets`. O vanish registra uma regra
e passa a valer para todos os mods com CoffeeLib.

---

### 6. Sugestões que enxergam o que foi digitado

**No RavenEssentials:** o `Teleport.attachSuggestions` reconstrói o nó "coords"
do Brigadier por reflexão, só para sugerir a coordenada certa em cada posição. O
provider da lib não sabe o que já foi digitado.

**API:** o provider pode receber um contexto, além das formas atuais (sem
parâmetro, ou com `CommandSource`):

```java
public interface SuggestContext {
    CommandSource source();
    /** O texto do argumento até o cursor. Num @Greedy, tudo desde o começo dele. */
    String input();
    /** A palavra sendo digitada agora: é ela que as sugestões substituem. */
    String token();
    /** Índice da palavra dentro de um @Greedy (0, 1, 2...). 0 nos outros. */
    int tokenIndex();
    /** Um argumento anterior já interpretado, ou null se ainda não foi digitado. */
    @Nullable <T> T previous(String name, Class<T> type);
}

Collection<String> coords(SuggestContext context) { ... }
```

**Comportamento:**
- A lib aplica o *offset* do Brigadier para a sugestão substituir só o `token()`,
  e não o argumento inteiro.
- Continua filtrando por `startsWith(token())`, sem diferenciar maiúsculas.

**Sai do RavenEssentials:** o `attachSuggestions` e a lógica de índice do
`suggest`. Com o item 2, o `/tp` nem precisa disso.

---

### 7. Substituir comandos vanilla

**No RavenEssentials:** o `CommandNodes.remove` tira nós do Brigadier por
reflexão, e é usado pelo `/tp` e pelo `/title`. O `/list` só funciona porque o
Brigadier mescla os nós; uma ordem diferente de registro quebraria isso.

**API:** `@Command(value = "tp", replaces = true)`.

**Comportamento:**
- **NeoForge e Forge:** no `RegisterCommandsEvent`, antes de registrar, a lib
  remove o filho da raiz com esse nome. A reflexão nos mapas `children`,
  `literals` e `arguments` do `CommandNode` fica centralizada e testada na lib.
  Loga em INFO que substituiu o `/tp`.
- **Paper:** remove `tp` e `minecraft:tp` do `knownCommands` do `CommandMap`
  antes de registrar.
- **Conflito:** se o nó existente veio de outro dono da CoffeeLib, falha com uma
  mensagem nomeando os dois. Substituir só vale para comandos que não são da lib.
- Aliases do vanilla, como `/teleport`, continuam. Para substituí-los, o mod usa
  `aliases`.

**Sai do RavenEssentials:** o `CommandNodes` e os `@SubscribeEvent(priority =
HIGHEST)` do `Teleport` e do `Title`.

---

## Fase 3

### 5. Saber quando uma permissão muda

**No RavenEssentials:** o `FlightEvents` confere a permissão de **todos os
jogadores a cada segundo**, porque não tem como saber quando ela muda. Um fly
retirado continua funcionando por até 1 s.

**API** (em `PermissionManager`):

```java
/** Chamado na thread principal quando as permissões de um jogador podem ter mudado. */
EventSubscription onChange(Consumer<UUID> listener);
```

**Comportamento:**
- **Com LuckPerms:** assina o `UserDataRecalculateEvent` pela API dele.
- **NeoForge e Forge:** também o `PermissionsChangedEvent`, para `/op` e `/deop`.
- **Paper sem LuckPerms:** não há evento de `/op`. A lib faz **uma** verificação
  periódica central, em vez de um polling em cada mod, e documenta a latência.
- Entrega só o UUID, porque o jogador pode estar offline: quem escuta busca o que
  precisa.
- Vários eventos do mesmo jogador no mesmo tick viram uma chamada só.

**Sai do RavenEssentials:** o `onTick` do `FlightEvents`. O `Flight.sync` passa
a rodar no `onChange`, no login, no respawn e na troca de dimensão.

---

### 8. Texto com cores e mensagens

**No RavenEssentials:**
- O `TextFormat` interpreta `&0-f`, `&k-o`, `&r` e `&#RRGGBB`.
- O `Messages` tem três jeitos de mandar mensagem.
- Os textos estão espalhados pelo código, em duas línguas.

**API, em três partes:**

1. **Formatação** (`coffeelib-api` define, a plataforma implementa):
   ```java
   public interface TextFormatter<C> {
       C parse(String text);                     // & e §, &#RRGGBB, \& para um & literal
       C parse(String text, Object player);      // passa antes pelos placeholders da lib
       String strip(String text);                // sem códigos, para logs
   }
   ```
   NeoForge e Forge devolvem `MutableComponent`; o Paper devolve `Component` do
   Adventure. O `TextFormat` do RavenEssentials entra como implementação,
   com os testes.

2. **Respostas no `CommandSource`:** `success(String)`, `info(String)` e
   `error(String)`, já formatando os códigos. As cores padrão (verde, cinza e
   vermelho) são configuráveis pela lib.

3. **Catálogo de mensagens**, em cima do `ConfigManager` que já existe:
   ```java
   @ConfigFile("messages")
   public class RavenMessages {
       public String healed = "&aVocê foi curado.";
       public String healedOther = "&a{player} foi curado.";
   }

   Messages<RavenMessages> messages = CoffeeLib.messages(container, RavenMessages.class);
   messages.send(target, m -> m.healedOther, "player", name);
   ```
   - O arquivo é criado com os valores do código e recarrega com
     `/coffeelib config reload`.
   - `{nome}` é preenchido pelos pares passados.
   - Uma chave que falta no arquivo usa o padrão do código e é reportada uma vez
     no log.

**Fora do escopo:** idioma por jogador (o `locale` do cliente). Fica para depois;
o catálogo já deixa o caminho preparado.

**Sai do RavenEssentials:** o `TextFormat`, o `Messages` e as strings soltas nos
comandos.

---

### 11. Injeção de dependência ligada ao resto da lib

**No RavenEssentials:** o `RavenDatabase` é um localizador estático
(`RavenDatabase.playerRepository()`), e por isso qualquer pacote alcança o banco.
O `ServiceContainer` já existe no core, mas não tem ponto de entrada por
plataforma e não é usado pelos comandos.

**API:**
- `CoffeeLib.services(container)`: um `ServiceContainer` por dono.
- A lib registra os próprios gerenciadores como serviços: `PermissionManager`,
  `Scheduler`, `DatabaseManager`, `PlayerDirectory` e `TextFormatter`.
- `commands.register(handler)` chama `services.wire(handler)` antes de ler as
  anotações, e o `eventBus.register(listener)` também.

```java
public class BackCommand {
    @Receive PlayerRepository players;
    @Receive Scheduler scheduler;
    ...
}
```

**Comportamento:**
- Um `@Receive` sem provedor falha no `register`, com `MissingProviderException`
  nomeando o campo, como no `wire` de hoje.
- Sem `@Receive` no handler, nada muda.

**Sai do RavenEssentials:** os getters estáticos do `RavenDatabase` e do
`Permissions`.

---

## Fase 4

### 12. Localização que dá para salvar

**No RavenEssentials:** o `StoredLocation` (dimensão, xyz, yaw, pitch e quando)
e dois códigos iguais para transformar o id da dimensão em `ServerLevel` (o
`Teleport.findLevel` e o `Back.levelOf`). Homes e warps vão precisar do mesmo.

**API:**
- **`coffeelib-database`:** `CoffeeLocation`, um `@Embeddable` só com dados
  (`world`, `x`, `y`, `z`, `yaw`, `pitch`, `savedAt`).
- **Plataforma:** `Locations.of(player)`, `Locations.teleport(player, location)`
  e `Locations.world(server, location)`, que devolve `Optional`, porque a
  dimensão pode ter sido removida.

O README explica o uso de `@AttributeOverrides` para guardar duas localizações
na mesma entidade.

### 13. Dados no jogador, iguais nas três plataformas

**No RavenEssentials:** o `RavenAttachments.flag(...)` existe só no NeoForge.

**API:** `CoffeeLib.playerData(container, modEventBus)` devolve uma fábrica:
`flag("god")`, `integer("kills")`, `text("nick")` e
`json("settings", MyRecord.class)`. Cada um tem `get(player)` e
`set(player, value)`, e a fábrica tem a opção `copyOnDeath`.

**Por plataforma:**
- **NeoForge:** `AttachmentType`, registrado pela lib no `DeferredRegister` do mod.
- **Forge 1.20.1:** capability.
- **Paper:** `PersistentDataContainer`, com `NamespacedKey(plugin, chave)`.

### 14. Permissões numéricas

**Para as homes:** um limite de homes por permissão, como
`ravenessentials.home.limit.3`.

**API:** `int number(Object subject, String prefix, int fallback)` em
`PermissionManager`.

**Comportamento:**
- **Com LuckPerms:**
  - primeiro a *meta* `prefix`, se existir;
  - senão, o maior `N` entre os nós concedidos que começam com `prefix + "."`.
- **Sem LuckPerms:** o maior `N` entre os nós declarados com
  `declareRange(prefix, 1, 10)`.

### 15. Menus

**No RavenEssentials:** o `Menus.open` só abre menus vanilla (bigorna, crafting,
lixeira).

**API (rascunho):**

```java
Menu.chest(3).title("&6Kits")
    .item(13, stack, click -> click.player().closeContainer())
    .locked()               // ninguém tira nem põe itens
    .open(player);
```

- **NeoForge e Forge:** uma subclasse de `ChestMenu` que intercepta os cliques.
- **Paper:** um `InventoryHolder` próprio e o `InventoryClickEvent`.

Para detalhar quando houver o primeiro uso real: kits ou `/warp`.

---

## Fora do escopo

- **Consultas derivadas do nome do método no repositório** (`findByName`
  automático): precisam de proxies e de um parser de nomes, e é muito código
  para pouco ganho.
- **Framework de GUI completo**, com paginação e animação: o item 15 é só a base.
- **Idioma por jogador:** depois do item 8.

---

## Checklist por item

- [ ] API no `coffeelib-api`, com Javadoc
- [ ] Implementação no core, quando independe de plataforma
- [ ] NeoForge 1.21.1
- [ ] Forge 1.20.1
- [ ] Paper
- [ ] Testes no core com plataforma falsa e, quando cabe, na plataforma
- [ ] `README.md` e `README.pt-BR.md`
- [ ] Migração correspondente no RavenEssentials, removendo o código listado em "Sai do RavenEssentials"
