# CoffeeLib

[![](https://jitpack.io/v/RenanPlay13013/CoffeeLib.svg)](https://jitpack.io/#RenanPlay13013/CoffeeLib)

**Biblioteca multiplataforma de gerenciamento de configuração para Minecraft.**

CoffeeLib é uma pequena biblioteca de configuração e injeção de dependência,
orientada a anotações, que funciona da mesma forma em Paper (Bukkit), Forge
1.20.1 e NeoForge 1.21.1. Ela oferece POJOs de configuração tipados com geração
automática de arquivos, valores padrão, validação e **comentários preservados** —
em vez de espalhar chamadas `node.get("a.b.c", def)` por todo o seu código.

No seu núcleo ela não tem estado global: cada plugin/mod tem seu próprio
`ConfigManager` vinculado à sua própria pasta de dados/config, e nada é
compartilhado entre donos na mesma JVM.

---

## Recursos

- **POJOs de configuração por anotações** — escreva uma classe simples e deixe
  o CoffeeLib transformá-la em arquivo. Sem acesso a config via string.
- **Geração automática na primeira execução** — os padrões são gravados logo no
  primeiro load.
- **Preservação de comentários** — os valores de `@Comment` são escritos no
  arquivo como linhas `#`, então os operadores têm a documentação bem ao lado
  do valor.
- **Validação com sugestões** — `@Range` (limites numéricos) e `@OneOf`
  (conjuntos de strings tipo enum) falham de forma visível, e `@OneOf` sugere o
  valor mais próximo ("você quis dizer **hard**?") usando um matcher baseado em
  distância de Levenshtein.
- **Formas de campo realmente suportadas** — todo primitivo e wrapper,
  `String`, enums, POJOs aninhados em qualquer profundidade, `List`/`Set` de
  escalares ou POJOs, e `Map<String, V>`. Qualquer outra coisa é recusada pelo
  nome no startup, em vez de gravada como valor vazio.
- **Container DI pequeno e explícito** — fiação `@Provide` / `@Receive` por
  tipo, com providers nomeados e singletons.
- **Event bus próprio** — um canal compartilhado por todos os mods e plugins da
  lib, com prioridades, cancelamento e handlers `@Subscribe`, para dois donos
  conversarem por tipos Java simples em vez de eventos do Forge ou do Bukkit.
- **Um scheduler** — roda trabalho na thread principal agora, depois de alguns
  ticks ou a cada tantos, e traz o resultado de um future de volta para ela;
  cancelado junto com o servidor. O Forge e o NeoForge não têm um próprio.
- **Um canal de economia** — mods e plugins provêm moedas (`coins`, `cash`) e
  outros as usam pelo id, como o Vault, mas em todas as plataformas e com
  quantas moedas forem; com ponte para o Vault nos dois sentidos no Paper.
- **MySQL, MariaDB, PostgreSQL, SQLite e H2** — uma conexão em pool
  (HikariCP) por mod ou plugin, num servidor ou num arquivo local, configurada por um `database.yml`, com helpers JDBC assíncronos e um
  `DataSource` comum, com o Hibernate pronto para usar por cima.
- **Integração real com plataformas** — Forge e NeoForge se integram com seus
  sistemas nativos de config, então `/forge config` e o ConfigTracker enxergam
  de verdade.
- **Formato escolhido por config** — `@ConfigFile(format = YAML | JSON | HOCON)`
  funciona em todas as plataformas, inclusive nas forge-like. Uma config em um
  desses formatos é do CoffeeLib de ponta a ponta: lida, validada e gravada
  pelo `core`.
- **Sem ciclo de vida do mod load nos formatos gerenciados** — uma config
  gerenciada é lida dentro do `load(...)` e a instância volta já preenchida,
  em vez de esperar o `ModConfigEvent` do loader.
- **Reload manual e hot reload** — `/coffeelib config reload` aplica a edição do
  operador na hora, e `/coffeelib config hotreload start|stop` liga e desliga um
  watcher que faz isso a cada salvamento.

---

## Arquitetura

O CoffeeLib é dividido em sete módulos sob `net.loyalnetwork`:

```
coffeelib-api                    interfaces, anotações, exceções (sem dependências)
coffeelib-core                   implementações agnósticas de plataforma (Java puro)
coffeelib-database               pools de conexão; HikariCP, MariaDB e PostgreSQL relocados dentro
coffeelib-hibernate              hibernate-core reempacotado para os loaders forge-like (interno)
coffeelib-platform-paper         backend Paper (Configurate YAML)
coffeelib-platform-forge-1.20.1  integração Forge 1.20.1 (Java 17)
coffeelib-platform-neoforge-1.21.1 integração NeoForge 1.21.1 (Java 21)
```

A cadeia de dependência é estrita: **plataforma → core → api**. O `core` nunca
depende de um formato de arquivo concreto — ele só fala com a interface
`ConfigBackend`, e cada plataforma fornece exatamente um backend.

### Estrutura do build

Tudo é um único build multi-módulo do Gradle. Cada módulo mantém seu próprio
piso de toolchain Java (Forge usa Java 17, NeoForge 21), e o build raiz copia
todos os jars dos módulos para `./dist` quando você roda `./gradlew build`.

---

## Primeiros passos (JitPack)

Adicione o JitPack como repositório e declare os módulos que você precisa. Você
pode consumir a stack completa de config/DI pelo módulo de plataforma do seu
alvo, ou puxar o `coffeelib-core` (que puxa `coffeelib-api` transitivamente).

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Paper
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-paper:3.9")
    // Forge 1.20.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-forge-1.20.1:3.9")
    // NeoForge 1.21.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-neoforge-1.21.1:3.9")
    // Ou apenas o core agnóstico de formato (config + DI, sem backend de plataforma)
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-core:3.9")
    // Ou só as interfaces e anotações
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-api:3.9")
}
```

> O projeto raiz é um container — use as coordenadas de **módulo** acima com
> `com.github.<usuário>.<repo>:<módulo>:<tag>`.
>
> **Comece na `3.3`.** Antes dela, um campo `List`, `Map`, array, `byte` ou
> `char` era gravado como valor vazio - destruindo o que o operador tivesse
> digitado naquela chave - e um campo `Byte` ou `Character` derrubava o servidor
> no startup com erro de JPMS. A `3.2` é no mais a mesma lib; a `3.1` é a `3.2`
> antes de as configs poderem ficar
> fora do `config/` e antes de um config `NATIVE` no Forge/NeoForge ganhar a
> extensão `.toml` — ao subir dela esse arquivo é renomeado, e os valores do
> antigo sem extensão não vão junto. A `3.0.4` é a `3.1` sem o event bus; a `3.0.3` também resolve, mas os POMs de plataforma dela declaram
> `coffeelib-api`, `coffeelib-core` e o Configurate como dependências mesmo com
> o jar já embutindo tudo, então você recebe essas classes duas vezes. Qualquer
> tag anterior a essas, incluindo as `2.0.x`, não tem artefato nenhum: o build
> falhava antes de gerar qualquer coisa, e três dos cinco módulos não tinham
> publicação. Os jars de plataforma que o JitPack entrega são os autocontidos
> descritos em
> [Compilando a partir do código-fonte](#compilando-a-partir-do-código-fonte).

---

## Configuração

### Anotações

| Anotação          | Onde   | Propósito                                                          |
| ----------------- | ------ | -------------------------------------                             |
| `@ConfigFile`     | tipo   | Nome do arquivo (sem extensão, `/` para subpastas) e, opcionalmente, o `format`. |
| `@Key`            | campo  | Sobrescreve a chave gravada no arquivo (padrão: nome do campo).   |
| `@Comment`        | campo  | Comentário(s) escrito(s) acima da chave no arquivo gerado.        |
| `@Ignore`         | campo  | Exclui o campo do load/save totalmente.                           |
| `@Range(min,max)` | campo  | Limites numéricos, validados no load/reload.                      |
| `@OneOf({...})`   | campo  | Restringe um campo String a um conjunto fixo, com sugestões.      |

### Nomes de arquivo e onde as configs ficam

O valor do `@ConfigFile` é um nome relativo ao diretório de config, sem
extensão — o backend acrescenta a dele. Uma `/` coloca o arquivo numa subpasta,
que é criada na primeira escrita:

```java
@ConfigFile("server")                 // config/server.yml
@ConfigFile("mymod/database")         // config/mymod/database.yml
```

O nome precisa ficar dentro desse diretório: `..`, caminho absoluto, `\`,
segmento vazio e nome que já carrega a extensão do backend são todos recusados
no load, com mensagem dizendo o motivo. (`../mymod/server` resolvia antes,
gravando silenciosamente fora da pasta que a plataforma entregou ao CoffeeLib.)

Querer os arquivos em outro lugar é legítimo, e é uma decisão do dono, tomada
na chamada em vez de ser efeito colateral de uma string — passe o diretório ao
criar o manager:

```java
// Forge/NeoForge: onde você quiser, em vez do config/ do loader
var configs = CoffeeLib.forMod(FMLPaths.GAMEDIR.get().resolve("mymod"));
var configs = CoffeeLib.detached("mymod", Path.of("/srv/configs-compartilhadas"));

// Paper: em vez da data folder do próprio plugin
var configs = CoffeeLib.forPlugin(this, getDataFolder().getParentFile().toPath().resolve("shared"));
```

O `ConfigFormat.NATIVE` é a exceção: o loader é dono daqueles arquivos e sempre
os grava sob `config/`, então diretório customizado não se aplica a eles.

Uma config gerenciada é identificada para o operador pelo caminho relativo ao
diretório base, que é o que o `/coffeelib config` lista e aceita:
`mymod/database.yml`. O nome puro do arquivo também funciona enquanto só uma
config gerenciada o tiver; com duas, o comando diz quais poderiam ser em vez de
recarregar a primeira que apareceu.

### Formatos de config

`@ConfigFile` aceita um `format`, cujo padrão é `ConfigFormat.NATIVE`:

| Formato  | Arquivo | Quem é dono do arquivo                                          |
| -------- | ------- | --------------------------------------------------------------- |
| `NATIVE` | varia   | A plataforma. No Paper isso significa YAML (ou seja, o CoffeeLib); no Forge/NeoForge é o TOML do próprio `ForgeConfigSpec`/`ModConfigSpec`, gravado como `config/<nome>.toml`. |
| `YAML`   | `.yml`  | CoffeeLib (Configurate/SnakeYAML). Comentários preservados.     |
| `JSON`   | `.json` | CoffeeLib (Configurate/Gson). Comentários descartados — JSON não tem. |
| `HOCON`  | `.conf` | CoffeeLib (Configurate/HOCON). Comentários preservados.         |

```java
@ConfigFile(value = "server", format = ConfigFormat.YAML)   // -> config/server.yml no Forge
public final class ServerConfig { /* ... */ }
```

Tudo que não é `NATIVE` é uma config **gerenciada**, e no Forge e no NeoForge
essa escolha muda três coisas:

1. **O load é síncrono.** `load(...)` lê o arquivo (criando com os padrões na
   primeira execução) e devolve uma instância preenchida. Nada espera o
   `ModConfigEvent`, então os valores já servem dentro do próprio construtor.
2. **A validação é do CoffeeLib.** `@Range`/`@OneOf` lançam
   `ConfigValidationException` em vez de o loader redefinir silenciosamente um
   valor fora do intervalo para o padrão.
3. **O reload é explícito** — veja abaixo.

### Recarregando uma config gerenciada

Todo entry point de plataforma retorna um `ReloadableConfigManager`
(`ConfigManager` + `ConfigReloadService`), que cobre as configs gerenciadas que
aquele manager carregou:

```java
ReloadableConfigManager configs = CoffeeLib.forMod();
ServerConfig cfg = configs.load(ServerConfig.class);          // format = YAML

configs.addReloadListener(config -> reabrirConexao());        // reagir aos novos valores
configs.managedFiles();        // ["server.yml"]
configs.reload("server.yml");  // um arquivo, agora
configs.reloadAll();           // retorna quantos recarregaram sem erro
configs.startHotReload();      // observa os arquivos e recarrega quando mudam
configs.stopHotReload();
```

Os callbacks de hot reload rodam na thread do watcher, não na thread do
servidor — agende para a thread da plataforma você mesmo se o que você faz
precisar disso. Um arquivo que falha ao parsear ou validar mantém os valores
anteriores em memória e registra o motivo no log, em vez de matar o watcher ou
aplicar a edição pela metade.

### Exemplo básico

```java
import net.loyalnetwork.coffeelib.api.config.ConfigManager;
import net.loyalnetwork.coffeelib.api.config.annotation.*;

@ConfigFile("server")                       // -> server.yml / server.toml ...
public final class ServerConfig {

    @Comment("Host principal do servidor")
    public String host = "localhost";

    @Range(min = 1, max = 65535)
    public int port = 3306;

    @OneOf({"easy", "normal", "hard"})
    public String difficulty = "normal";
}
```

Carregando (específico da plataforma, veja os entry points abaixo):

```java
ConfigManager manager = CoffeeLib.forPlugin(plugin);   // Paper
ServerConfig config = manager.load(ServerConfig.class);

manager.reload(config);   // relê o arquivo na mesma instância
manager.save(config);      // grava o estado atual em memória no disco
```

No primeiro load, o `server.yml` é criado no disco:

```yaml
# Host principal do servidor
host: localhost
port: 3300
difficulty: normal
```

### Objetos e listas aninhados

A profundidade de aninhamento é livre; ciclos são rejeitados no scan.

```java
public class Credentials {
    public String username = "root";
    public String password = "changeme";
}

public class Database {
    @Comment("Host do banco") public String host = "db.local";
    @Range(min = 1, max = 65535) public int port = 5432;
    public Credentials credentials = new Credentials();
}

@ConfigFile("app")
public class AppConfig {
    public String name = "coffee";
    public Database database = new Database();
    public List<String> tags = new ArrayList<>(List.of("survival", "pvp"));
}
```

Uma `List`, `Set` ou `Map<String, V>` pode conter escalares ou objetos de
config, e o tipo do elemento vem da assinatura genérica do campo — então
declare-o (`List<String>`, não uma `List` crua):

```java
@ConfigFile(value = "crates", format = ConfigFormat.YAML)
public class CratesConfig {
    public List<String> tags = new ArrayList<>(List.of("survival", "pvp"));
    public List<Crate> crates = new ArrayList<>(List.of(new Crate()));
    public Map<String, Integer> weights = new LinkedHashMap<>(Map.of("common", 70));
}
```

```yaml
tags:
- survival
- pvp
crates:
- name: diamond
  value: 1
weights:
  common: 70
```

* Uma coleção é **substituída** no load, não mesclada: o arquivo é a fonte da
  verdade, então um elemento que o operador apagou continua apagado.
* Elementos que são objetos de config recebem instância nova a cada load — eles
  não têm identidade a preservar, diferente de um objeto aninhado, que é um
  campo só.
* Um campo `Set` mantém semântica de conjunto; qualquer outra `Collection` vira
  lista.
* `@Comment` não é levado para dentro de elementos de coleção: teria que se
  repetir por elemento, e nenhum formato posiciona comentário por item de
  sequência de forma confiável.
* `@Range`/`@OneOf` valem só para campos escalares; em qualquer outra coisa são
  recusados, em vez de ignorados em silêncio.

**Recusado, com o campo nomeado, no `load`:** arrays (declare uma `List`),
genéricos crus ou com wildcard, `Map` com chave que não seja `String`, tipos do
JDK sem codec (`Duration`, `UUID`, ...), interfaces e classes abstratas,
classes internas não estáticas. Cada caso lança `ConfigException` dizendo o que
usar no lugar. Nada é gravado no arquivo quando o scan falha, então uma config
que não pode ser representada nunca sobrescreve uma que pode.

> No Forge e no NeoForge isso vale para os formatos gerenciados pelo CoffeeLib.
> Uma config `NATIVE` é descrita pelo spec do próprio loader, que não tem noção
> de coleção nem valor `byte`/`char`: esses também são recusados, apontando para
> um formato gerenciado.

### Validação

`@Range` e `@OneOf` são avaliados no load/reload. Um número fora do intervalo ou
uma string inesperada causa um `ConfigValidationException` em vez de silenciar
ou aplicar default. Para `@OneOf`, a exceção carrega sugestões ranqueadas:

```java
catch (ConfigValidationException e) {
    e.getFieldName();       // "difficulty"
    e.getInvalidValue();    // "hrad"
    e.getSuggestions();     // ["hard"] ("você quis dizer hard?")
}
```

---

## Injeção de dependência

O `ServiceContainer` fia `@Provide` (providers) a `@Receive` (receivers) por
tipo exato. Métodos provider rodam no máximo uma vez — a primeira resolução com
sucesso é cacheada e reutilizada (singletons).

```java
import net.loyalnetwork.coffeelib.api.di.ServiceContainer;
import net.loyalnetwork.coffeelib.core.di.DefaultServiceContainer;

class ProviderHost {
    @Provide Database database() { return new Database(); }

    @Provide("minecraftApi") Api api() { return new ApiImpl(); }
}

class Receiver {
    @Receive public Database database;
    @Receive("minecraftApi") public Api api;
}

ServiceContainer container = new DefaultServiceContainer();
container.register(new ProviderHost());
container.wire(new Receiver());   // resolve database + api nesta instância
```

Regras:

* A correspondência é por tipo **exato** — declare o campo como o mesmo tipo que
  o provider retorna (sem widening).
* Quando existe mais de um provider para um tipo, use `@Provide("nome")` e
  `@Receive("nome")`; caso contrário a fiação falha com
  `AmbiguousProviderException`.
* Sem grafo de dependências automático — providers devem ser registrados
  *antes* de os receivers que os consomem serem fiados. Caso contrário, a
  fiação falha de imediato.
* Uma `wire()` que falha não aplica campos pela metade e não cacheia a falha.

### Serviços vinculados ao mod/plugin (Fase 3)

O uso manual de `new DefaultServiceContainer()` continua disponível. Para
comandos e listeners CoffeeLib, existe também um **container por dono**, com a
mesma API nas três plataformas:

```java
// Paper (JavaPlugin):
ServiceContainer services = CoffeeLib.services(this);
// Forge (id do mod):
ServiceContainer services = CoffeeLib.services("seumod");
// NeoForge (ModContainer):
ServiceContainer services = CoffeeLib.services(container);
```

No primeiro acesso, a CoffeeLib registra `PermissionManager`, `Scheduler`,
`DatabaseManager` e `PlayerDirectory`. Chamadas posteriores retornam o mesmo
container. Para serviços próprios, registre o provedor **antes** do comando ou
evento que vai consumi-lo:

```java
class Providers {
    @Provide PlayerRepository players() { return repository; }
}

CoffeeLib.services(owner).register(new Providers());

class HomeCommand {
    @Receive private PlayerRepository players;
    @Receive private Scheduler scheduler;

    @Command("home")
    void home(CommandSource source) { /* serviços já injetados */ }
}

CoffeeLib.commands(owner).register(new HomeCommand());
CoffeeLib.eventBus(owner).register(new HomeListener());
```

No Paper, `owner` é o `JavaPlugin`; no Forge é o id `String`; no NeoForge,
`ModContainer` ou id `String`. O registro do comando ou listener injeta os
campos `@Receive` antes de publicar os handlers, inclusive campos herdados.
Erros de provedor inexistente/ambíguo e campos estáticos/finais falham no
registro; se algo faltar, nenhum campo do receiver é alterado. Providers não
podem devolver `null`.

No Paper, o container é limpo quando o plugin é desabilitado. Forge e NeoForge
mantêm seus containers enquanto o processo do mod existir; **não** guarde
jogadores, mundos ou entidades vivas como providers persistentes.

**Pendente:** eventos automáticos de alteração de permissão, `TextFormatter`,
catálogo de mensagens e testes de integração nos servidores reais.

---

## Scheduler

Trabalho na thread principal do servidor — agora, depois, ou de tempos em
tempos — do mesmo jeito em todas as plataformas. O Forge e o NeoForge não têm um
scheduler próprio; no Paper, este é o do Bukkit, ligado ao seu plugin.

```java
Scheduler scheduler = CoffeeLib.scheduler(container);   // Forge: scheduler(), Paper: scheduler(this)

scheduler.later(20, () -> player.sendSystemMessage(...));    // daqui a um segundo
Task autosave = scheduler.every(0, 20 * 60, this::saveAll); // a cada minuto, a partir do próximo tick
autosave.cancel();

scheduler.onMain(() -> world.setBlock(...));                 // já, se estiver na thread principal; senão, no próximo tick

repository.findById(id)                                      // completa numa thread do banco...
        .thenAcceptAsync(found -> teleport(player, found), scheduler.main());   // ...e continua na principal
CompletableFuture<Optional<Home>> home = scheduler.onMain(repository.findById(id)); // o mesmo, como future
```

* **Ticks**, 20 por segundo quando o servidor acompanha. `later(0)` e `later(1)`
  rodam os dois no próximo tick, como no Bukkit; o período do `every` conta a
  partir de cada execução, então um tick lento empurra as seguintes em vez de
  amontoá-las.
* **Falhas** numa tarefa vão para o log com o id do seu mod ou plugin e não
  param nem as outras tarefas nem a próxima execução de uma repetida.
* **Canceladas para você** quando o servidor para (Forge, NeoForge) ou o plugin
  é desabilitado (Paper). O que é agendado sem servidor rodando — no construtor
  do mod, ou entre dois mundos de um cliente — espera o primeiro tick do próximo.
* **Futures e desligamento:** `scheduler.onMain(future)` termina com
  `CancellationException` se o servidor Forge/NeoForge encerrar antes da
  entrega (ou o plugin dono for desabilitado no Paper). Isso impede que uma
  resposta de banco de um mundo antigo mexa no mundo seguinte. Trate o
  cancelamento explicitamente se o comando precisar ignorar o desligamento.

---

## Event bus

O CoffeeLib tem o próprio event bus, para conversar **entre** mods e plugins
sem passar pelo `IEventBus` do Forge nem pelo `PluginManager` do Bukkit. Um bus
de plataforma amarra as duas pontas de um recurso àquela plataforma: a mesma
coisa precisa de um evento do Forge, um do NeoForge e um do Bukkit, cada um
registrado de um jeito, e um evento que um mod define fica estranho de outro
escutar. Aqui as duas pontas combinam uma classe Java simples e mais nada.

Qualquer tipo serve como evento — um record geralmente basta:

```java
public record OrderPlaced(String item, int quantity) {}
```

Pegue um handle ligado ao seu mod ou plugin e então publique ou escute:

```java
EventBus bus = CoffeeLib.eventBus();           // Forge, do construtor do mod
EventBus bus = CoffeeLib.eventBus(container);  // NeoForge
EventBus bus = CoffeeLib.eventBus(this);       // Paper, do seu JavaPlugin

// lambda, quando você só quer o handle de volta
EventSubscription sub = bus.subscribe(OrderPlaced.class, event -> log(event.item()));
sub.unsubscribe();

bus.post(new OrderPlaced("coffee", 2));
```

Ou agrupe handlers numa classe, que é onde o `@Subscribe` se paga:

```java
public final class OrderListener {

    @Subscribe                                       // padrão é NORMAL
    private void onOrder(OrderPlaced event) { ... }  // private funciona

    @Subscribe(priority = EventPriority.MONITOR)     // roda por último, só observa
    void audit(OrderPlaced event) { ... }
}

bus.register(new OrderListener());
```

Um handler dispara para o tipo do parâmetro **e todos os subtipos**, então
assinar um supertipo comum ou uma interface funciona como se espera. Os
handlers rodam na ordem de `EventPriority` — `FIRST`, `EARLY`, `NORMAL`,
`LATE`, `LAST`, `MONITOR` — e, dentro de uma mesma prioridade, na ordem de
registro.

### Cancelamento

Um evento que implementa `Cancellable` pode ser cancelado por um handler, e o
`post` devolve o evento para a decisão ficar legível na chamada:

```java
if (!bus.post(new OrderPlaced("coffee", 2)).isCancelled()) {
    fazerPedido();
}
```

Respeitar o cancelamento é trabalho de quem publicou — o bus nunca age por
conta. O que ele faz é parar de entregar um evento cancelado aos handlers que
não pediram por isso; quem quiser recebê-los mesmo assim declara
`@Subscribe(receiveCancelled = true)`, o que serve para log e métricas, não
para agir sobre o evento.

### Threads, falhas e limpeza

* Os handlers rodam **na thread que publicou**. Não existe fila nem troca de
  thread, então um handler lento bloqueia quem publicou, e qualquer coisa que
  toque o estado do servidor precisa ir para a thread certa do jeito que
  aquela plataforma espera.
* Um handler que lança exceção é registrado no log em nome do dono dele e
  pulado; os handlers seguintes rodam normalmente, e o `post` não informa nada a
  quem publicou. O bug de um mod não quebra os handlers de outro. Isso vale para
  **qualquer coisa que um handler possa lançar** — exceção, `AssertionError`,
  `NoClassDefFoundError` de uma classe que outro mod não trouxe — e para
  **as duas** formas de registrar, lambda ou `@Subscribe` (na 3.3 e anteriores
  um `AssertionError` escapava de uma lambda mas não de um método anotado). A
  única coisa que passa são os erros fatais da própria JVM, como
  `OutOfMemoryError`, porque nada depois deles é confiável.
* `bus.unregisterAll()` remove tudo que o seu dono registrou. Vale chamar
  quando o seu dono vai embora: handlers seguram seus objetos e, por eles, seu
  class loader. No Paper isso é automático — o plugin do CoffeeLib limpa os
  handlers de um plugin quando ele é desabilitado, então um `/reload` não
  deixa a cópia antiga das suas classes respondendo eventos.

### Saber se deu certo

O `post` serve para notificações, em que ninguém a jusante pode quebrar quem
publica. Ele não serve para um publicador que está **coordenando trabalho** —
"entregue a recompensa, *depois* marque a sessão como concluída" — porque volta
normalmente tenham todos os handlers funcionado ou não. Para isso o `dispatch` é
a mesma entrega com o resultado devolvido:

```java
EventResult<RewardDelivery> result = bus.dispatch(new RewardDelivery(player, reward));

if (result.hasFailures()) {
    desfazer(player);              // quem publica decide o que uma falha significa
} else {
    session.complete();
}
```

O `EventResult` traz o evento, `handlersInvoked()` e `failures()` — cada uma um
`EventFailure(ownerId, handlerEventType, cause)`, com `cause` exatamente como o
handler lançou, nunca embrulhada. Prefira uma exceção no ponto que você escolher
a conferir um flag:

```java
bus.dispatch(event).throwIfFailed();   // EventDispatchException; a primeira falha é a
                                       // causa, as demais ficam como suppressed
```

`dispatch(event, FailurePolicy.STOP)` para no primeiro erro em vez de rodar todos
os handlers, para quando um handler posterior depende do trabalho de um anterior
e agir sobre um estado pela metade seria pior do que não agir.

> **Não existe rollback.** Um evento é um objeto comum. Um handler que cancelou
> ou alterou um campo e *depois* falhou deixa o evento cancelado ou alterado — o
> bus não tem transação para desfazer. O `dispatch` é como o publicador descobre
> que precisa limpar; a limpeza é trabalho dele. As falhas são registradas no
> log de qualquer forma.

> **Uma cópia do CoffeeLib, ou não existe canal compartilhado.** A entrega
> entre donos só funciona enquanto todos carregam as mesmas classes do
> CoffeeLib. Instale-o como mod próprio no Forge/NeoForge e como plugin próprio
> no Paper — onde o seu plugin também precisa declarar o CoffeeLib em `depend`
> ou `softdepend` no `plugin.yml` para resolver essas classes a partir dele. Um
> mod ou plugin que faz shade do CoffeeLib dentro de si fica com um bus
> privado, o que é ótimo se for intencional e silencioso se não for.
> `new DefaultEventBus("meuid")` é a forma honesta de pedir um privado.

---

## Comandos

Anote um método e ele **é** o executor do comando; os parâmetros são os
argumentos, e o tipo de cada um decide o parse e o tab completion.

```java
@Command(value = "crate", aliases = "crates")
public final class CrateCommands {

    @Command("")                                         // /crate
    void help(CommandSource source) { ... }

    @Command(value = "give", permission = "crates.give") // /crate give <player> [amount] [rarity]
    void give(CommandSource source,
              @Arg("player") @Suggest(provider = "online") String player,
              @Arg("amount") @Default("1") int amount,
              @Arg("rarity") @Default("common") Rarity rarity) { ... }

    @Command("open")                                     // só jogadores: o tipo do sender diz isso
    void open(ServerPlayer player, @Arg("crate") @Suggest({"daily", "vote"}) String crate) { ... }

    @Command("announce")
    void announce(@Arg("message") @Greedy String message) { ... }

    Collection<String> online() { ... }                  // sugestões, filtradas enquanto digita
}

CoffeeLib.commands(container).register(new CrateCommands());   // NeoForge
CoffeeLib.commands().register(new CrateCommands());            // Forge, do construtor do mod
CoffeeLib.commands(this).register(new CrateCommands());        // Paper, sem entrada no plugin.yml
```

* **Argumentos:** `String`, `int`, `long`, `double`, `float`, `boolean` (e
  wrappers), enums. `@Default` torna um opcional (opcionais vão por último),
  `@Greedy` pega o resto da linha, `@Arg` dá o nome — necessário a menos que você
  compile com `-parameters`.
* **Tab completion:** subcomandos, constantes de enum e booleanos completam
  sozinhos; `@Suggest` acrescenta valores fixos ou um método provedor. No
  Forge/NeoForge os comandos viram **Brigadier**, então o cliente valida números
  enquanto o jogador digita; no Paper um completer é registrado junto.
* **Sender:** primeiro parâmetro opcional — `CommandSource` em qualquer
  plataforma, ou o tipo nativo (`ServerPlayer`, `CommandSourceStack`, `Player`,
  `CommandSender`). O console rodando um comando só de jogador é recusado com
  mensagem.
* **Permissões:** `permission` é um node no Paper; no Forge/NeoForge o
  `permissionLevel` é o nível de operador, derivado como 2 quando há `permission`
  e 0 quando não há. Um valor na classe vale para todos os métodos.
* **Erros:** número inválido, argumento faltando ou permissão faltando são
  informados ao sender, com a linha de uso. Lance `CommandException` do executor
  para mensagens próprias; qualquer outra exceção é bug — vai para o log, e o
  sender só fica sabendo que algo deu errado.
* **Executores assíncronos:** um executor pode devolver um `CompletableFuture`
  (qualquer `CompletionStage`). O comando conta como iniciado na hora, e quando
  o future falha o sender é avisado do mesmo jeito que acima — a mensagem da
  `CommandException`, ou a genérica com a causa no log — a partir da thread
  principal, e só se ainda estiver online. Lançada dentro de uma etapa seguinte,
  ela chega embrulhada numa `CompletionException`; o CoffeeLib a desembrulha.
  As etapas em si rodam onde o future delas completa (uma thread do banco, por
  exemplo), então continue na thread principal com o scheduler antes de mexer
  no mundo:

  ```java
  @Command(value = "back", permission = "crates.back")
  CompletableFuture<Void> back(ServerPlayer player) {
      return scheduler.onMain(homes.findById(player.getStringUUID())).thenAccept(found -> {
          Home home = found.orElseThrow(() -> new CommandException("Nenhuma home para voltar."));
          home.teleport(player);                                 // thread principal: seguro
      });
  }
  ```

  O Brigadier não espera futures, então o `/execute store result` registra `1`
  ("iniciado") para um comando assim.
* **Namespace:** no Paper o comando fica no namespace **do seu plugin**
  (`/seuplugin:crate`) e é atribuído ao seu plugin no `/help`, não ao CoffeeLib.
  Comandos Brigadier não têm namespace no Forge/NeoForge — os do vanilla também
  não —, então é simplesmente `/crate`, com o log no id do seu mod.

Erros num handler — opcional antes de obrigatório, tipo não suportado, provedor
inexistente, dois métodos para o mesmo caminho — falham no `register`, com o
método nomeado, e não quando alguém roda o comando.

### Fase 2 — visibilidade e argumentos tipados

O módulo `coffeelib-core` permanece **sem tipos do Minecraft**. Cada plataforma
fornece seus parsers nativos e os plugins podem registrar os próprios:

```java
commands.argument(Kit.class, (source, name) -> kits.require(name));
```

`UUID` e `Duration` (`1d2h30m`, `90s`) já existem no core. Um tipo de argumento
registrado na primeira posição é tratado como argumento, não como remetente.
Um jogador nativo na primeira posição, **sem anotação**, continua sendo o
remetente; use `@Arg` ou `@OrSelf` para indicá-lo como alvo. `@OrSelf` deve ser
o **último** parâmetro; com `others = "myplugin.command.others"`, sugestões
para outros jogadores exigem permissão.

No Paper, `Location` aceita **três palavras** (`x y z`, `~ ~ ~`, `~1 ~-2 ~`),
relativas ao jogador ou bloco de comando, e recusa o console sem localização.
O módulo Paper mantém a base Spigot 1.8.8: `NamespacedKey` não pode virar
argumento desse mesmo JAR sem elevar a versão mínima; use `String` nessa
plataforma. Forge/NeoForge suportam `ResourceLocation`, `ServerLevel`, `Vec3`,
`BlockPos`, `Holder<T>` com `@Registry` e `ServerPlayer`.

Para vanish, registre a regra **com o nome do dono** (o nome exato de
`JavaPlugin#getName()` no Paper). Assim, o CoffeeLib remove o listener quando
o plugin é desabilitado:

```java
PlayerDirectory players = CoffeeLib.players();
EventSubscription vanish = players.visibility("MyVanish", (viewer, target) ->
        !isVanished(target) || maySeeVanished(viewer));
List<Object> visible = players.visibleTo(viewer);
```

`visibleTo` devolve uma lista imutável dos jogadores online visíveis, somando
as regras da CoffeeLib e, no Paper, o `Player.canSee` nativo. **A verificação
falha de forma segura:** se uma regra ou verificação nativa lança exceção, o
jogador afetado fica oculto para quem consulta; a primeira falha de cada regra
é registrada no log com o contexto do dono, sem revelar jogadores ocultos.
Console (`viewer` null) vê todos; jogador vê a si mesmo. Consulte o diretório **na thread do
servidor**, pois ele trabalha com jogadores reais. A forma anterior
`visibility(rule)` continua válida, mas quem a usa deve cancelar o
`EventSubscription` manualmente. No Forge/NeoForge, como o mod não é
descarregado a cada troca de mundo, remova as regras por dono explicitamente
quando seu serviço parar, com `SharedPlayers.releaseOwner(modId)`.

---

## Placeholders

Registre placeholders uma vez e qualquer mod ou plugin do CoffeeLib consegue
preenchê-los. No Paper é isso que o PlaceholderAPI já faz; no Forge e no
NeoForge não existe sistema comum, então o CoffeeLib faz o intermédio.

```java
public final class CratePlaceholders {

    @Placeholder("keys")                  // %crates_keys% e %crates_keys_daily%
    int keys(ServerPlayer player, String crate) { ... }

    @Placeholder("total")                 // %crates_total%, sem precisar de jogador
    long total() { ... }
}

PlaceholderManager placeholders = CoffeeLib.placeholders(container);        // namespace = id do mod
PlaceholderManager placeholders = CoffeeLib.placeholders("mymod", "crates"); // ou um escolhido
placeholders.register(new CratePlaceholders());
placeholders.register("season", context -> temporadaAtual());

String line = placeholders.apply(player, "Você tem %crates_keys% chaves - %economy_balance%");
```

* **Sintaxe do PlaceholderAPI**: `%namespace_chave%`, `%namespace_chave_argumento%`,
  então um texto escrito para o Paper funciona igual nas outras plataformas. O
  namespace é casado com os registrados, do mais longo para o mais curto, em vez
  de cortado no primeiro `_` — assim um id de mod como `my_mod` funciona.
* **`apply` preenche todos os namespaces**, não só o seu — é assim que um mod usa
  os placeholders de outro.
* **Desconhecido, `null` ou com falha fica como está**: placeholder não
  registrado, resolver que devolve `null` ou lança exceção (registrada no log em
  nome do dono), ou que declara tipo de jogador sem haver um, continua no texto,
  nunca trocado por algo enganoso. `50% off` não é placeholder.
* **Parâmetros do resolver**, em qualquer ordem e todos opcionais: o jogador no
  tipo da plataforma, uma `String` para o argumento, um `PlaceholderContext`.
* **Paper com PlaceholderAPI**: cada namespace é publicado como expansion, então
  plugins que usam PlaceholderAPI veem os seus, e o `apply` também preenche os
  deles. O PlaceholderAPI é dependência opcional, nunca embutida. Ele corta
  identificadores no primeiro `_`, então evite `_` num namespace que deva
  aparecer lá. Placeholders e expansions de um plugin somem quando ele é
  desabilitado.
* Operadores conferem com `/coffeelib placeholder list` e
  `/coffeelib placeholder parse <texto>` no Forge/NeoForge.

---

## Permissões

Nós de permissão com default, checados do mesmo jeito em todas as plataformas e
**integrados ao LuckPerms** sem configuração. Declare uma vez, como constantes:

```java
public final class CratePermissions {

    @Permission(description = "Dar chaves de caixa")                  // operadores por padrão
    public static final String GIVE = "crates.give";

    @Permission(def = PermissionDefault.EVERYONE, description = "Abrir caixas")
    public static final String OPEN = "crates.open";
}

PermissionManager permissions = CoffeeLib.permissions(container);     // Forge: permissions(), Paper: permissions(this)
permissions.declare(CratePermissions.class);

if (permissions.has(player, CratePermissions.GIVE)) { ... }

@Command(value = "give", permission = CratePermissions.GIVE)          // e os comandos usam o mesmo nó
```

Como um nó é decidido:

* **Forge/NeoForge**: primeiro o **PermissionAPI** da plataforma, quando o
  handler de permissão do servidor é um mod de permissão de verdade (LuckPerms ou
  outro) e não o handler padrão da própria plataforma; depois o LuckPerms direto,
  quando está instalado e tem resposta (concedido *ou* negado); senão, o default
  do nó — `EVERYONE`,
  `OPERATORS` (op nível 2, ou o nível do próprio comando) ou `NOBODY` até ser
  concedido. Assim as permissões de um mod funcionam num servidor sem plugin de
  permissão, e no momento em que o LuckPerms entra ele assume. O console tem
  tudo. A API do LuckPerms é dependência só de compilação: nada dela vai no jar,
  e nenhuma classe do LuckPerms é carregada se o mod não estiver presente.
* **Paper**: o sistema de permissões do próprio Bukkit, no qual o LuckPerms e
  qualquer outro plugin de permissão já se integram. Um nó declarado é
  registrado no servidor com o default mapeado (`EVERYONE` → true,
  `OPERATORS` → op, `NOBODY` → false), então aparece nos plugins de permissão.
  As regras do Bukkit valem como são, inclusive a de que `NOBODY` não é
  concedido ao console.
* Um nó que ninguém declarou é de operadores; um nó vazio é de todos.

> **Nós declarados com o servidor rodando.** O PermissionAPI só aceita nós
> durante a subida do servidor, então um nó declarado depois — um comando
> registrado com o servidor já no ar, por exemplo — só entra nele no próximo
> reinício. Até lá ele é decidido pelo resto da cadeia (LuckPerms direto, depois
> o default) e é gravado em `config/coffeelib/permission-nodes.cache`, cujo
> header explica tudo isso; na próxima subida, todo nó desse arquivo é
> registrado no PermissionAPI como qualquer outro. Entradas de mods que não
> estão mais instalados são descartadas, e o arquivo pode ser apagado sem risco.
> Para entrar no PermissionAPI o nó precisa de pelo menos dois segmentos
> (`crates.give`, não `crates`) e nenhum curinga.

Onde o CoffeeLib passou a checar nós:

* **Comandos** no Forge/NeoForge antes checavam só nível de op; o `permission`
  de um comando agora passa pela cadeia acima, com o nível dele como o que
  "operadores" significa. O nó é declarado automaticamente no `register`, com o
  default que o nível implica, a menos que você tenha declarado.
* **O default do próprio comando:** coloque `@Permission` no método do
  `@Command` (ou na classe dele) para declarar o nó com outro default ou outra
  descrição, sem precisar de uma classe de constantes:

  ```java
  @Command(value = "list", permission = "crates.list")
  @Permission(def = PermissionDefault.EVERYONE, description = "Listar as crates")
  void list(CommandSource source) { ... }
  ```

  A primeira declaração de um nó vale, então uma feita com
  `permissions.declare(...)` antes de registrar o comando mantém a dela.
* **Os subcomandos do próprio `/coffeelib`** têm nós em vez de op nível 2 fixo:
  `coffeelib.config.list`, `coffeelib.config.reload`,
  `coffeelib.config.hotreload` e `coffeelib.placeholder`, todos com default de
  operador — então um servidor pode dar reload de config à staff sem dar op.

### Notificações de mudança de permissão (Fase 3)

Use `onChange` em vez de consultar todos os jogadores continuamente em cada mod:

```java
PermissionManager permissions = CoffeeLib.permissions(container); // Forge: permissions(), Paper: permissions(this)
EventSubscription assinatura = permissions.onChange(uuid -> {
    // Executa na THREAD DE TICK DO SERVIDOR, não na thread assíncrona do LuckPerms.
    // O UUID pode ser de alguém que acabou de desconectar.
    // Procure o jogador pelo UUID e consulte novamente a permissão necessária.
    // Ex.: sincronize o modo fly de quem estiver online.
});
// Quando não precisar mais: assinatura.unsubscribe();
```

O callback é um **aviso de possível mudança**, não a comparação dos nós:
vários eventos para o mesmo UUID antes do tick viram uma chamada. Uma exceção
no listener é registrada com a identificação do dono sem interromper os outros.
As assinaturas pertencem ao mod/plugin: no Paper são removidas ao desabilitar
o plugin; no Forge/NeoForge permanecem durante a vida do mod.

* **Nas três plataformas com LuckPerms**: assinatura no
  `UserDataRecalculateEvent`; eventos assíncronos entram numa fila e o UUID é
  entregue no tick seguinte. API do LuckPerms é opcional, não embutida.
* **Forge/NeoForge**: `PermissionsChangedEvent` também avisa sobre op/deop;
  eventos já cancelados são ignorados quando detectáveis. As fontes passam pela
  mesma deduplicação por tick. Um evento pode anunciar uma possível alteração
  mesmo que outro listener a cancele depois.
* **Paper sem LuckPerms**: uma verificação **central a cada 20 ticks** compara
  o nível de op e as permissões efetivas dos nós registrados na CoffeeLib.
  Não detecta garantidamente nós não registrados, mudanças externas sem evento
  ou jogadores offline. Com TPS baixo, a latência em segundos aumenta.
* No desligamento, eventos atrasados são descartados para não vazarem entre
  mundos ao reiniciar o servidor integrado.

Implementações próprias antigas de `PermissionManager` continuam compatíveis,
mas o método padrão lança `UnsupportedOperationException` até que implementem
a nova funcionalidade.

---

## Economia

Um canal entre os mods que **provêm** moedas e os mods que as **usam**, no
espírito do Vault: um mod de essentials provê `coins` e `cash`, um mod de loja
cobra `coins`, um mod de missões paga `cash`, e nenhum deles conhece os outros.
A CoffeeLib **não guarda saldo nenhum**: o mod provedor mantém as contas, onde
quiser, e responde a cada chamada.

Provendo uma moeda:

```java
public final class CoinsProvider implements CurrencyProvider {
    @Override public String id() { return "coins"; }
    @Override public int fractionalDigits() { return 0; }           // moedas inteiras
    @Override public String format(BigDecimal amount) { return amount.toPlainString() + " coins"; }

    @Override public CompletableFuture<BigDecimal> balance(UUID player) { ... }
    @Override public CompletableFuture<TransactionResult> deposit(UUID player, BigDecimal amount) { ... }
    @Override public CompletableFuture<TransactionResult> withdraw(UUID player, BigDecimal amount) { ... }
    // opcionais: hasAccount, set, displayName
}

CoffeeLib.economy(container).register(new CoinsProvider(...));   // Forge: economy(), Paper: economy(this)
```

Usando uma:

```java
Currency coins = CoffeeLib.economy(container).currency("coins");   // pode pegar na subida

coins.withdraw(player.getUUID(), new BigDecimal("50")).thenAccept(result -> server.execute(() -> {
    if (result.succeeded()) { /* entrega o item */ }
    else if (result.status() == TransactionResult.Status.INSUFFICIENT_FUNDS) { /* "coins insuficientes" */ }
}));
coins.transfer(pagador, recebedor, valor);                           // /pay
```

* **O handle funciona antes do provedor existir.** Os mods carregam em qualquer
  ordem, então `currency("coins")` sempre devolve um handle; até um mod prover a
  moeda, `isAvailable()` é falso e as transações respondem `NO_PROVIDER`.
  `onAvailable("coins", currency -> ...)` roda quando ela chega (ou na hora).
* **Um provedor por moeda.** Um segundo mod registrando `coins` é recusado com
  uma exceção que diz quem já provê.
* **Os valores são conferidos antes de chegar ao provedor**: negativo, zero ou
  com mais casas decimais do que a moeda aceita voltam como `INVALID_AMOUNT`.
  Use `BigDecimal`, nunca `double`, para dinheiro.
* **Tudo é assíncrono**: um provedor com banco de dados nunca trava o servidor,
  e um em memória completa na hora. Os futures completam na thread do provedor;
  volte para a thread do servidor antes de mexer no mundo.
* **`transfer` agora exige um `AtomicTransferProvider`.** Essa extensão
  opcional executa débito e crédito **dentro de uma única transação do provedor**.
  A CoffeeLib valida o valor e invoca o mesmo provedor uma única vez, sem
  realizar `withdraw` e `deposit` separadamente. Sem essa capacidade, devolve
  `NOT_SUPPORTED` **sem movimentar dinheiro**. É uma mudança intencional de
  comportamento em relação ao fluxo inseguro de saque/depósito/estorno.
  Provedores existentes precisam implementar explicitamente a interface para
  continuar oferecendo `/pay`.
* **Contrato do provedor:** bloqueie as duas contas em ordem determinística
  (ou use transação ACID no banco), valide saldo e destinatário, modifique as
  duas contas e confirme ou desfaça as duas alterações juntas. As demais
  operações sobre essas contas também precisam usar sincronização compatível.
  Nunca construa uma transferência atômica encadeando dois futures. O Vault
  importado não tem garantia transacional e retorna `NOT_SUPPORTED`.
  Mesmo com armazenamento atômico, uma falha de rede após o commit pode gerar
  resposta ambígua; novas tentativas exigem chaves de idempotência.
* Um provedor é um objeto comum chamado de qualquer thread: ele precisa ser
  thread-safe. No Paper, as moedas de um plugin saem quando ele é desabilitado.

### Vault (Paper)

Com o Vault instalado, a CoffeeLib liga as duas economias, conforme o
`plugins/CoffeeLib/economy.yml` da CoffeeLib:

```yaml
# The CoffeeLib currency offered to Vault plugins as the server's economy, such as coins. Empty: none
vaultExport: ''
# The id another plugin's Vault economy (EssentialsX, CMI...) gets as a CoffeeLib currency. Empty: do not bring it in
vaultImport: vault
```

* **Importação** (ligada por padrão): a economia do Vault de outro plugin vira a
  moeda `vault` da CoffeeLib, então um mod ou plugin na CoffeeLib pode cobrar o
  dinheiro do EssentialsX com `currency("vault")`. As chamadas rodam na thread
  do servidor, como as economias do Vault esperam. O Vault não tem como definir
  um saldo, então `set` responde `NOT_SUPPORTED`.
* **Exportação** (desligada até você nomear uma moeda): a moeda da CoffeeLib
  escolhida é registrada no Vault com prioridade alta, e todo plugin de Vault
  passa a usá-la. A API do Vault é síncrona, então cada chamada espera até 5
  segundos pelo provedor: de graça para um provedor em memória, a thread do
  servidor travada para um lento. Bancos respondem `NOT_IMPLEMENTED`.

---

## Banco de dados

Um pool de conexões por mod ou plugin, para servidores **MySQL**, **MariaDB** e
**PostgreSQL**, ou **SQLite** e **H2** num arquivo local, sem servidor nenhum. A parte da CoffeeLib termina num `DataSource` JDBC comum: use os
helpers pequenos dela para SQL, ou coloque Hibernate, jOOQ ou JDBI por cima.
Nada para adicionar ao seu build: o HikariCP e os drivers vêm dentro dos jars de
plataforma, relocados em `net.loyalnetwork.coffeelib.libs`, então não conflitam
com a cópia que outro mod ou plugin traga.

```java
DatabaseManager databases = CoffeeLib.database(container);   // Forge: database(), Paper: database(this)
Database db = databases.open();                                // a partir do database.yml

db.update("CREATE TABLE IF NOT EXISTS crates (player VARCHAR(36) PRIMARY KEY, opened INT)");

db.queryFirst("SELECT opened FROM crates WHERE player = ?", rs -> rs.getInt(1), uuid.toString())
  .thenAccept(opened -> ...);                                  // Optional<Integer>, fora da thread do servidor

db.transaction(connection -> { ... });                         // commit ao retornar, rollback ao lançar
```

* **Todo helper roda nas threads do próprio banco** (`CoffeeLib-DB-<dono>-N`,
  uma por conexão do pool) e devolve um `CompletableFuture`, nunca bloqueando a
  thread do servidor. Volte para ela pelo scheduler da sua plataforma antes de
  mexer no mundo. Falhas completam o future com uma `DatabaseException` que traz
  a mensagem do driver.
* **`open()` conecta na hora**, então configuração errada falha na subida com o
  motivo ("Access denied...", "Connection refused...") e o arquivo a corrigir.
* **Fechado para você**: no Paper quando o plugin é desabilitado (depois do
  `onDisable` dele, que ainda pode salvar ali); no Forge e no NeoForge quando o
  jogo fecha. `db.close()` fecha um antes.

O arquivo, criado com defaults no primeiro uso (`config/<modid>/database.yml`
no Forge/NeoForge, a pasta de dados do plugin no Paper):

```yaml
# MYSQL, MARIADB or POSTGRESQL (a server), or SQLITE or H2 (a local file, no server needed)
type: MYSQL
# SQLITE/H2 only: the file, relative to this one's folder (H2 adds .mv.db)
file: data
host: localhost
# 0 = the type's usual port (3306 for MySQL/MariaDB, 5432 for PostgreSQL)
port: 0
database: minecraft
username: minecraft
# Any value here may be ${NAME} to read the environment variable NAME instead
password: ${CRATES_DB_PASSWORD}
poolSize: 10
connectionTimeoutMillis: 10000
properties: {}
jdbcUrl: ''
```

O `database.yml` pode ficar em outro lugar, como qualquer config: passe um
diretório, como em `CoffeeLib.database(container, Path.of("databases/crates"))`
(Forge: `database(dir)`, Paper: `database(this, dir)`); `null` mantém o padrão.

Configurações próprias (guardadas na sua config, ou montadas em código) vão por
`databases.open(DatabaseSettings.builder(DatabaseType.POSTGRESQL).host(...)...build())`.
`properties` vão para o driver como estão: `sslMode: verify-full` no
MySQL/MariaDB, `sslmode: verify-full` no PostgreSQL, e assim por diante.

### Embutidos: SQLite e H2

Para dados que crescem além de uma config mas não precisam de servidor (homes,
a posição do `/back`, estatísticas por jogador), use `type: SQLITE` ou
`type: H2`: o banco é um arquivo ao lado do `database.yml` (`file: data`, ou um
caminho seu; caminhos relativos são resolvidos a partir dessa pasta). Host,
porta e credenciais são ignorados. A mesma API `Database` e o mesmo SQL
funcionam como num servidor, então migrar para MySQL depois é uma mudança no
`database.yml`, mais o SQL que for diferente entre eles.

* **SQLite** escreve com uma conexão por vez, então o pool dele tem uma única
  conexão, seja qual for o `poolSize`, em modo WAL, esperando até 5 s quando o
  arquivo está ocupado em vez de falhar. O driver dele traz código nativo ligado
  ao nome do próprio pacote, então não dá para relocá-lo como o resto: no Paper a
  CoffeeLib usa a cópia que o servidor já traz; no Forge e no NeoForge ele vai
  aninhado no jar da CoffeeLib (`META-INF/jarjar`), e o loader mantém uma cópia
  só quando outros mods também o aninham.
* **H2** guarda tudo em `<file>.mv.db`, um arquivo H2 comum que qualquer
  ferramenta de banco abre (DBeaver, IntelliJ, o console do próprio H2; usuário
  vazio, sem senha). É o H2 publicado, não relocado: o H2 grava o nome das
  próprias classes no arquivo, então uma cópia relocada geraria arquivos que só a
  CoffeeLib lê. Ele é distribuído como o SQLite (aninhado no Forge/NeoForge,
  biblioteca do plugin no Paper).

> **Arquivos H2 da CoffeeLib 3.6 e 3.7** foram gravados por uma cópia relocada
> dessas, e outras ferramentas dizem que eles estão corrompidos. Na primeira vez
> que um deles é aberto, ele é convertido sozinho para um arquivo H2 padrão (não
> precisa fazer nada) e o original fica ao lado como
> `<file>.mv.db.coffeelib-old`, para apagar quando tudo estiver funcionando.

Os dois são para um único processo de servidor: dois servidores compartilhando
um banco precisam de MySQL, MariaDB ou PostgreSQL.

> **MySQL 8 sem TLS.** O MySQL 8 autentica com `caching_sha2_password`, que numa
> conexão sem TLS precisa da chave RSA do servidor. A menos que você configure
> `sslMode` (ou o próprio `allowPublicKeyRetrieval`), a CoffeeLib deixa o driver
> buscá-la, então uma instalação padrão conecta. Para um banco numa rede que
> não é confiável, configure `sslMode`.

### Hibernate

O Hibernate ORM 6.6 vem com a CoffeeLib, então um mod ou plugin o usa sem
empacotar nada: compile contra ele e monte um `SessionFactory` sobre um banco da
CoffeeLib.

```groovy
compileOnly 'org.hibernate.orm:hibernate-core:6.6.4.Final'   // só isso: a CoffeeLib traz o Hibernate em tempo de execução
// compileOnly 'org.hibernate.orm:hibernate-community-dialects:6.6.4.Final' só se você citar o SQLiteDialect por conta própria
```

```java
@Entity
public class Home {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String player;
    private String name;
    private double x, y, z;
    // um construtor sem argumentos, getters...
}

Database db = CoffeeLib.database(container).open();
SessionFactory sessions = CoffeeHibernate.sessionFactory(db, Home.class);   // tabelas criadas/atualizadas para bater

CompletableFuture.runAsync(() -> sessions.inTransaction(session -> session.persist(home)), db.executor())
        .whenComplete((ok, error) -> server.execute(() -> { /* de volta à thread do servidor */ }));
```

* `CoffeeHibernate.sessionFactory` entrega ao Hibernate o pool da CoffeeLib
  como conexões, encontra suas entidades pelo seu próprio class loader e fecha o
  `SessionFactory` junto com o banco. A sobrecarga que recebe um
  `Consumer<Configuration>` muda o resto (`hbm2ddl.auto`, cache, nomes); o
  padrão é `update`.
* Todo tipo de banco funciona. O dialeto é detectado pela conexão no MySQL,
  MariaDB, PostgreSQL e H2; no SQLite, a CoffeeLib escolhe o `SQLiteDialect`
  dos dialetos da comunidade do próprio Hibernate (`hibernate-community-dialects`,
  distribuído junto). Os dialetos da comunidade têm menos cobertura que os
  principais, e o SQLite aceita uma escrita por vez, então para um banco embutido
  com muitas entidades relacionadas o H2 é a escolha mais segura.
* Rode as sessions em `db.executor()` (ou em threads suas), nunca na thread do
  servidor.
* Uma associação lazy vira um proxy que carrega numa **chamada de método**: leia
  os dados dela por getters, não por campos públicos.

#### Repositórios

O `HibernateRepository<T, ID>` é o CRUD de sempre para uma entidade, com os
mesmos futures e as mesmas threads de todo o resto aqui. Estenda-o e acrescente
as suas consultas com `read(...)` (sem transação) e `write(...)` (uma transação,
com commit ao retornar e rollback se lançar exceção):

```java
public class PlayerRepository extends HibernateRepository<PlayerEntity, String> {
    public PlayerRepository(SessionFactory sessions, Database database) {
        super(sessions, database, PlayerEntity.class);
    }

    public CompletableFuture<Optional<PlayerEntity>> findByName(String name) {
        return read(session -> session
                .createSelectionQuery("from PlayerEntity where name = :name", PlayerEntity.class)
                .setParameter("name", name)
                .uniqueResultOptional());
    }
}
```

| Método | Devolve | Observações |
|---|---|---|
| `save(entity)` | `T` | Insere ou atualiza (`merge`). Use a instância **devolvida**: ela é a cópia gerenciada, e é nela que vem um id gerado. |
| `saveAll(entities)` | `List<T>` | Uma transação: salva todos, ou nenhum. |
| `findById(id)` | `Optional<T>` | |
| `findAllById(ids)` | `List<T>` | Uma consulta só; ids sem linha ficam de fora. |
| `findAll()` | `List<T>` | |
| `existsById(id)` | `Boolean` | |
| `count()` | `Long` | |
| `deleteById(id)` | `Boolean` | Se havia algo para apagar. |
| `delete(entity)` | `Void` | Funciona com entidades *detached*, como as que um repositório devolve. |
| `deleteAll()` | `Integer` | Um `delete` em massa: callbacks e cascatas do JPA **não** rodam. |
| `update(id, change)` | `Boolean` | Busca, muda e grava numa transação, com a linha travada; se havia uma. Não precisa de `save`. |
| `upsert(id, create, change)` | `T` | O `update`, criando a entidade com `create` antes quando não existe. |

O `update` e o `upsert` travam a linha (`PESSIMISTIC_WRITE`), então dois deles no
mesmo id rodam um depois do outro, em vez de um sobrescrever a mudança do outro.
As funções deles rodam **na thread do banco**: leia do mundo ou do jogador o que
elas precisam antes da chamada. Dois *primeiros* `upsert` de um id que ainda não
tem linha disputam a inserção — um ganha e o outro falha com a chave duplicada,
nunca em silêncio.

```java
players.upsert(uuid, () -> new PlayerEntity(uuid), player -> player.onLogin(name, ip, now));
```

O construtor confere se a classe é uma entidade daquele `SessionFactory`, então
esquecê-la no `CoffeeHibernate.sessionFactory(db, ...)` falha ali mesmo, e não na
primeira consulta. Falhas completam o future com uma `DatabaseException` que traz
a mensagem do Hibernate.

Quatro coisas para ter em mente:

1. **As entidades devolvidas são *detached*.** A sessão delas já fechou quando o
   future completa, então ler uma associação `LAZY` depois disso lança
   `LazyInitializationException`. Busque o que precisa dentro do `read(...)`,
   com uma consulta `join fetch`.
2. **Os futures completam numa thread do banco**, não na do servidor. Volte para
   ela antes de mexer no mundo, com o [scheduler](#scheduler):
   `scheduler.onMain(future)` ou `thenAcceptAsync(..., scheduler.main())`.
3. **O `save` usa `merge`.** O objeto que você passa não fica gerenciado; guarde
   o devolvido. (Para quem vem do Spring: aqui ele nunca é o mesmo objeto.)
4. **Uma chamada é uma transação.** `findById` seguido de `save` são duas; use
   `update`/`upsert`, ou coloque as duas dentro de um `write(...)`, quando
   precisarem acontecer juntas.

**Não existem consultas derivadas do nome do método**: um `findByName` nunca é
gerado para você. Escreva-o com `read(...)`, como acima.

Como ele é distribuído:

* **Forge e NeoForge**: aninhado no jar da CoffeeLib (`META-INF/jarjar`) como
  *game libraries*, para carregar junto dos mods cujas entidades ele usa em
  proxies. Os dev runs de um mod também recebem, já que o jar da CoffeeLib é
  carregado ali como mod. Dois ajustes fazem isso funcionar com o sistema de
  módulos, ambos invisíveis para o mod: o ANTLR vai relocado dentro do
  `hibernate-core` (o Forge 1.20.1 traz um ANTLR 4.9 que o parser de consultas do
  Hibernate não consegue usar), e o `jakarta.transaction-api` é o 2.0.0, a mesma
  API sem as exigências do 2.0.1 sobre módulos de CDI que ninguém fornece.
* **Paper/Spigot 1.17+**: uma entrada `libraries` do plugin da CoffeeLib, que o
  servidor baixa do Maven Central na primeira subida. Declare
  `depend: [CoffeeLib]` no seu `plugin.yml` para enxergá-lo.

O Hibernate acrescenta cerca de 18 MB aos jars do Forge e do NeoForge.

---

## Integração por plataforma

Cada plataforma expõe um entry point pequeno que retorna um `ConfigManager`
ligado à pasta de config do dono.

### Paper

```java
private ConfigManager config;

public void onEnable() {
    config = CoffeeLib.forPlugin(this);              // pasta data/
    MyConfig cfg = config.load(MyConfig.class);
}
```

O Paper não tem um sistema de config próprio para o CoffeeLib delegar, então
toda config aqui é gerenciada: `NATIVE` resolve para **Configurate YAML**
(estilo de bloco de dois espaços, comentários reaplicados a partir de
`@Comment`), e uma classe pode optar por JSON ou HOCON via
`@ConfigFile(format = ...)`. Reload manual e hot reload ficam disponíveis no
manager retornado — plugins Paper já trazem o próprio `/meuplugin reload`,
então este módulo não registra comando algum.

### Forge 1.20.1

```java
public class MyMod {
    public MyMod(FMLJavaModLoadingContext ctx) {
        ConfigManager config = CoffeeLib.forMod();        // do construtor do mod
        MyConfig cfg = config.load(MyConfig.class);
    }
}
```

Uma config `NATIVE` usa o próprio `ForgeConfigSpec`, ou seja, é um `ModConfig`
real e registrado — visível para `/forge config` e para o `ConfigTracker` do
Forge. Como ali o Forge é dono da I/O de disco e da validação, a checagem de
limites dele redefine valores fora do intervalo para o padrão (com um aviso no
log) em vez de lançar o `ConfigValidationException` do CoffeeLib.

Uma config em qualquer outro formato vai para `config/<nome>.<ext>` e é do
CoffeeLib:

```java
public class MyMod {
    public MyMod(FMLJavaModLoadingContext ctx) {
        ReloadableConfigManager configs = CoffeeLib.forMod();
        MyConfig cfg = configs.load(MyConfig.class);   // format = YAML -> já preenchida aqui
        configs.startHotReload();                      // opcional: observar desde o início
    }
}
```

`CoffeeLib.forMod()` ainda precisa ser chamado no construtor do mod, porque é o
único lugar onde o Forge deixa alguém registrar um `ModConfig`. Se você usa só
formatos gerenciados, `CoffeeLib.detached("meumod")` funciona em qualquer lugar
— num handler de início de servidor, num comando, num hook de reload — e falha
com um `ConfigException` claro se receber uma classe `NATIVE`.

### NeoForge 1.21.1

```java
public class MyMod {
    public MyMod(ModContainer container, IEventBus bus) {
        ConfigManager config = CoffeeLib.forMod(container);
        MyConfig cfg = config.load(MyConfig.class);
    }
}
```

Igual ao Forge, mas contra o `ModConfigSpec`/`ModConfigEvent` do NeoForge, e
`CoffeeLib.detached("meumod")` também existe aqui.

> Isso vale só para configs `NATIVE`: os campos delas refletem o que está no
> disco quando o loader dispara o `ModConfigEvent` **depois** da construção,
> não de forma síncrona durante a chamada de `load(...)`. Uma config gerenciada
> (`format = YAML | JSON | HOCON`) é lida durante a chamada e nunca envolve o
> loader.

### `/coffeelib config` (Forge e NeoForge)

O jar do CoffeeLib registra um comando, o lado do operador das configs
gerenciadas — o loader não sabe que esses arquivos existem, então sem ele uma
edição exigiria reiniciar o servidor. Requer permissão de nível 2.

```
/coffeelib config list                      o que é gerenciado e o que está sendo observado
/coffeelib config reload                    todos os arquivos gerenciados de todos os mods
/coffeelib config reload <mod> [<arquivo>]  um mod, ou um arquivo dele
/coffeelib config hotreload status
/coffeelib config hotreload start [<mod>]   inicia o watcher
/coffeelib config hotreload stop  [<mod>]   para o watcher
```

`<mod>` é o id do mod com que o manager foi criado, e os arquivos vêm pelo nome
listado (`server.yml`). Configs nativas não são tocadas por nada disso —
continuam sendo assunto do `/forge config` e do `/neoforge config`.

---

## Compilando a partir do código-fonte

O `build` compila todos os módulos e copia o jar de cada um para `./dist`:

```bash
./gradlew build
ls dist/
# coffeelib-forge-1.20.1-3.3-SNAPSHOT.jar      <- instale este (autocontido)
# coffeelib-neoforge-1.21.1-3.3-SNAPSHOT.jar   <- instale este (autocontido)
# coffeelib-paper-3.3-SNAPSHOT.jar             <- instale este (autocontido)
# coffeelib-api-3.3-SNAPSHOT.jar               <- compile contra estes
# coffeelib-core-3.3-SNAPSHOT.jar
```

São dois tipos de jar, para dois usos diferentes.

**Os jars de plataforma são autocontidos**: cada um embute `coffeelib-api`,
`coffeelib-core` e os backends do Configurate (`configurate-yaml`,
`configurate-gson`, `configurate-hocon`), porque o jogo carrega esse jar como
mod/plugin em vez de resolver as dependências Maven dele — o
`/coffeelib config` roda dentro desse jar e precisa dessas classes. O `gson`
fica de fora: o jogo já fornece. Um arquivo em `mods/` ou `plugins/` e mais
nada para copiar ao lado. Um mod ou plugin que prefere fazer shade do CoffeeLib
em vez de usar o jar instalado precisa fazer shade do mesmo conjunto.

**O `coffeelib-api` e o `coffeelib-core` são os jars de biblioteca**, para
depender do CoffeeLib em tempo de build sem arrastar uma plataforma que você não
usa — `core` para a implementação de config/DI, `api` sozinho se você só precisa
das interfaces e anotações. Serem "magros" é de propósito: não levam cópia do
Configurate, então a ferramenta de build resolve os formatos pelos metadados
deles (veja as coordenadas do JitPack acima) e nada fica duplicado em runtime.
Eles não são para jogar em `mods/`.

O jar do Forge é pego depois do `reobfJar`, que o reescreve para os nomes SRG
que um runtime Forge real espera — um jar copiado antes dessa etapa só funciona
dentro de um dev run.

### Dev runs

`runClient`/`runServer` carregam o mod de `build/classes`, não desse jar, então
o bundling acima não serve para eles e cada loader precisa da sua própria
fiação:

* **Forge** — o `coffeelib-core` entra no `minecraftLibrary` do ForgeGradle, que
  cai no classpath de runtime do Minecraft (o mesmo que carrega gson e guava).
  As dependências de runtime comuns do projeto ficam num classpath que o class
  loader do mod não vê — era daí o crash no tab-complete do
  `ConfigReloadRegistry`.
* **NeoForge** — a mesma coisa pelo `additionalRuntimeClasspath` do
  ModDevGradle.

Os dois excluem o `gson`: o Minecraft põe a cópia dele no module path, e uma
segunda faz o run falhar ao resolver. E os dois são só classpath de propósito —
declarar as classes também como mod sources faz dois módulos JPMS exportarem o
mesmo pacote, o que falha do mesmo jeito.

Os dois loaders sobem a partir de arquivos de argumento gerados. Depois de
mexer nisso, suba o run pelo Gradle
(`./gradlew :coffeelib-platform-neoforge-1.21.1:runClient`) ou refaça o sync do
Gradle na IDE; senão o run reaproveita o classpath gerado antes e você depura
uma configuração que já não está no disco.

Toolchains são provisionadas automaticamente pelo resolver do foojay contra um
JDK configurado. Veja `gradle.properties` / o `gradle.properties` de cada módulo
para o pinning de versão.

---

## Licença

Licenciado sob a **MIT License**.

---

## Aviso de IA

Uma parte significativa da implementação do CoffeeLib foi escrita com a ajuda de
um assistente de IA, e eu a *direcionei de ponta a ponta* — mas quero ser
transparente sobre isso.

**Eu, RenanPlay13013**, sou responsável por todas as decisões de arquitetura,
layout de módulos, a separação `api` / `core` / plataformas, os contratos das
anotações, a superfície da API pública, o sistema de build (Gradle
multi-módulo) e a forma como o CoffeeLib se integra com cada plataforma. A IA
ajudou com uma grande parte da geração de código, boilerplate e refatorações,
mas eu sabia o que o código fazia em cada passo e revisei **tudo manualmente**
antes de commitar. Nada foi mesclado como estava, sem o meu entendimento.

Se algo parecer errado, trate como meu — não da IA. Eu sou dono do resultado.

### Texto e catálogos de mensagens (Fase 3 / item 8)

A API expõe `TextFormatter<C>` e `Messages<T>`. O **core Java puro** reconhece
códigos legados `&`/`§`, cores `&#RRGGBB`, `\&` e templates com campos
`{nome}`. Cada plataforma faz sua renderização: **Forge/NeoForge** retornam
`MutableComponent` com RGB; **Paper** retorna `String`, pois este módulo ainda
é compilado para Spigot 1.8.8. Nesse caso, cores RGB são aproximadas para a
paleta legada. O parser não acessa o mundo.

```java
@ConfigFile("messages")
public class MyMessages {
    public String welcome = "&aBem-vindo, {player}!";
}

// NeoForge 1.21.1
Messages<MyMessages> messages = CoffeeLib.messages(container, MyMessages.class);
messages.send(player, m -> m.welcome, "player", player.getName().getString());
messages.reload();

// Paper: CoffeeLib.messages(plugin, MyMessages.class)
// Forge: CoffeeLib.messages("modid", MyMessages.class)
```

Os catálogos usam **YAML gerenciado** por padrão, inclusive no Forge/NeoForge,
onde um `@ConfigFile` normal sem `format` usa TOML nativo. `format = JSON`
ou `HOCON` explícito continua válido. O arquivo é carregado de maneira síncrona
na pasta do dono (`plugins/<plugin>/messages.yml` no Paper;
`config/<modid>/messages.yml` no Forge/NeoForge). Pode ser recarregado pelo
`messages.reload()` nas três plataformas, ou por `/coffeelib config reload`
no Forge/NeoForge. O objeto carregado é atualizado no lugar. Inicialize um
catálogo uma vez por dono; instâncias criadas separadamente não se sincronizam
automaticamente.

`messages.send` agenda a entrega na thread do servidor e ignora destinatários
que se desconectaram. `CommandSource.success`, `info` e `error` usam verde,
cinza e vermelho nos adaptadores nativos; implementações personalizadas usam
o fallback de texto simples, preservando compatibilidade. A substituição
`{chave}` não é recursiva.

**Limitações:** cores das respostas ainda não são configuráveis; os campos
ausentes seguem o mecanismo padrão do ConfigManager, sem aviso individual
único. Faltam compilação Gradle e testes de integração reais nos loaders.

**Arquitetura:** aplicamos Object Calisthenics e Domain-Driven Design como
diretrizes, mantendo os trechos formatados como value objects imutáveis, o
catálogo como domínio de aplicação e a renderização dependente de Minecraft
nos adaptadores. Essas diretrizes não substituem compatibilidade e correção.

## Fase 4 (incremental): permissões numéricas e localizações salvas

### Limites numéricos

O `PermissionManager` expõe `declareRange(prefix, primeiro, último)` e
`number(subject, prefix, fallback)`:

```java
PermissionManager permissions = CoffeeLib.permissions(container); // NeoForge
permissions.declareRange("ravenessentials.home.limit", 1, 10);
int limiteHomes = permissions.number(player, "ravenessentials.home.limit", 1);
```

Com LuckPerms, o valor inteiro da meta `ravenessentials.home.limit` tem
prioridade. Na ausência dessa meta, vale o maior nó efetivamente **concedido**
`ravenessentials.home.limit.N`, incluindo permissões herdadas e contextuais.
Sem LuckPerms, somente os nós declarados por esse dono são examinados.
Sufixos negativos, inválidos ou com overflow são ignorados. A declaração do
intervalo não concede permissões automaticamente (padrão `NOBODY`) e aceita
até 10.000 entradas por chamada. Nenhuma consulta ao banco de usuários
offline é realizada.

### Localização persistível

`coffeelib-database` fornece `CoffeeLocation`, um `@Embeddable` JPA com
`world, x, y, z, yaw, pitch, savedAt`, sem qualquer tipo do Minecraft.
Para persistir várias localizações na mesma entidade, use
`@AttributeOverrides` nos atributos JPA.

Cada implementação (`paper.location.Locations`, `forge.location.Locations`
ou `neoforge.location.Locations`) expõe `of`, `world` e `teleport`:

```java
CoffeeLocation salvo = Locations.of(player);
Optional<ServerLevel> destino = Locations.world(server, salvo);
boolean aceito = Locations.teleport(player, salvo);
```

No Paper, o identificador é o **nome do mundo Bukkit**; em Forge/NeoForge,
é a dimensão namespaced. Não intercambie esses valores sem mapeamento.
As três implementações exigem a thread do servidor para capturar, resolver
mundos e teleportar. Para resultados de banco ou rede, retorne primeiro à
thread de tick usando o `Scheduler`. Um mundo ausente ou jogador desconectado
faz `teleport` retornar `false`, sem inventar dimensão padrão. Após mudar de
dimensão, recupere o `ServerPlayer` pelo UUID: a instância pode ter mudado.

**Status:** implementação incremental, ainda sem homologação em servidores reais.
Menus e a compilação oficial das plataformas continuam pendentes.


## Fase 4 (incremental): dados persistentes de jogadores (item 13)

`coffeelib-api` agora fornece `PlayerDataFactory` e `PlayerData<T>` sem
importar Minecraft para o core. Ponto de entrada em cada plataforma:

```java
// NeoForge 1.21.1: declarar no construtor do mod, antes do registro congelar.
PlayerDataFactory data = CoffeeLib.playerData(container);
PlayerData<Boolean> god = data.copyOnDeath(true).flag("god");
PlayerData<Integer> kills = data.integer("kills");
PlayerData<String> nick = data.text("nick");
PlayerData<MySettings> settings = data.json("settings", MySettings.class);

god.set(serverPlayer, true); // somente na thread do servidor
boolean habilitado = god.get(serverPlayer);
```

No **Forge 1.20.1**, use `CoffeeLib.playerData("seumodid")` e o `ServerPlayer`
atual; no **Paper/Spigot 1.14+**, use `CoffeeLib.playerData(seuPlugin)` com
um `Player` Bukkit. Cada chave é registrada **uma única vez** por dono e
aceita apenas caracteres minúsculos `[a-z0-9._-]` (1–64 caracteres).
Os valores ausentes são `false`, `0`, `""` e `null` (JSON). JSON usa Gson e
uma classe concreta; `set(player, null)` remove/reseta o JSON. Trocar o tipo
de uma chave já gravada exige migração explícita.

### Regras importantes

- **NeoForge:** `AttachmentType` persistido via codec, sob namespace do mod,
  com `copyOnDeath()` nativo. As chaves devem ser declaradas antes de fechar o
  registro de attachments.
- **Forge:** capability serializada anexada a cada jogador; chaves `dono:campo`
  e cópia seletiva pelo `PlayerEvent.Clone` apenas em morte. A biblioteca não
  mantém referências permanentes a jogadores.
- **Paper:** utiliza `PersistentDataContainer` quando existente; em Bukkit
  **anterior ao 1.14**, `playerData` falha de modo explícito, preservando a
  compatibilidade das demais funções com Spigot 1.8.8. Os dados que não devem
  sobreviver à morte são apagados no evento de **morte**, não no respawn,
  porque voltar do End também pode provocar `PlayerRespawnEvent`.
- Leituras e escritas exigem a **thread do servidor**; o consumidor deve usar
  o scheduler da CoffeeLib ao voltar de um future. Jogadores obsoletos ou
  desconectados são rejeitados. A persistência segue os salvamentos normais do
  servidor; não há sincronização automática com o cliente.
- `copyOnDeath(true/false)` é uma opção de **registro** para as próximas
  chaves, não altera campos registrados anteriormente. Padrão: `false`.

**Testes:** 140 testes Java direcionados passaram com executor JUnit
simplificado, além de cenários simulados dos três adaptadores. Ainda faltam
builds oficiais contra Forge/NeoForge, integração em Paper moderno e ensaios
reais de reinício, morte e troca de dimensão. Os stubs não homologam o código.

**Próximo item da Fase 4:** menus/inventários (item 15) e homologação real.


## Menus de baú somente leitura (Fase 4 / item 15)

A CoffeeLib disponibiliza uma API pequena para menus **bloqueados**, com
`ItemStack` e jogador nativos de cada plataforma. O cliente usa o menu de baú
vanilla, sem precisar registrar tela personalizada. O `coffeelib-api` não importa
nenhum tipo Minecraft. São permitidas de 1 a 6 linhas; slots inválidos falham
ao configurar o menu.

### Paper / Spigot (inclusive 1.8.8)

```java
var kits = CoffeeLib.menus(plugin).chest(3)
        .title("&6Kits")
        .item(13, new org.bukkit.inventory.ItemStack(org.bukkit.Material.CHEST),
                click -> click.player().closeInventory())
        .locked();
kits.open(player);
```

### Forge 1.20.1

```java
var kits = CoffeeLib.menus("yourmod").chest(3)
        .title("&6Kits")
        .item(13, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST),
                click -> click.player().closeContainer())
        .locked();
kits.open(serverPlayer);
```

### NeoForge 1.21.1

```java
var kits = CoffeeLib.menus(modContainer).chest(3)
        .title("&6Kits")
        .item(13, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST),
                click -> click.player().closeContainer())
        .locked();
kits.open(serverPlayer);
```

**Garantias e limites:**

- Apenas menus **bloqueados**: abrir sem `.locked()` lança erro imediatamente.
  Ainda não há slots editáveis nem movimentação de itens. O conteúdo é copiado
  para um inventário novo por jogador; itens de exposição não são itens reais.
- Apenas clique esquerdo/direito normal nos itens superiores dispara ações,
  **no tick seguinte**. Shift, arrasto, número do teclado, drop e clique duplo
  são bloqueados. Durante o menu também são bloqueados cliques no inventário
  inferior do jogador para impedir transferências acidentais.
- Apenas um callback pendente por menu; vários pacotes de clique no mesmo tick
  não duplicam a execução da ação.
- Se o jogador fechar a tela antes do callback, a ação não executa. Exceções
  do callback são registradas no log com o dono.
- Abra o menu apenas na thread do servidor. No Forge/NeoForge as ações usam o
  scheduler da CoffeeLib e são canceladas ao parar o servidor. No Paper, menus
  e referências são liberados quando o plugin é desativado.
- Na compatibilidade com Spigot 1.8.8, o título renderizado do inventário
  aceita no máximo 32 caracteres, contando códigos de formatação.
- Menus com slots de entrada, transferências reais e telas personalizadas
  continuam fora deste primeiro contrato por exigirem tratamento de transações.

**Arquitetura:** [Diretrizes de DDD e Object Calisthenics](docs/architecture/ddd-object-calisthenics.pt-BR.md).
