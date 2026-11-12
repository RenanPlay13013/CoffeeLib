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
- **Objetos e listas aninhados** — POJOs aninhados e `List<T>` de escalares ou
  de POJOs aninhados, com detecção de ciclos.
- **Container DI pequeno e explícito** — fiação `@Provide` / `@Receive` por
  tipo, com providers nomeados e singletons.
- **Event bus próprio** — um canal compartilhado por todos os mods e plugins da
  lib, com prioridades, cancelamento e handlers `@Subscribe`, para dois donos
  conversarem por tipos Java simples em vez de eventos do Forge ou do Bukkit.
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

O CoffeeLib é dividido em cinco módulos sob `net.loyalnetwork`:

```
coffeelib-api                    interfaces, anotações, exceções (sem dependências)
coffeelib-core                   implementações agnósticas de plataforma (Java puro)
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
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-paper:3.2")
    // Forge 1.20.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-forge-1.20.1:3.2")
    // NeoForge 1.21.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-neoforge-1.21.1:3.2")
    // Ou apenas o core agnóstico de formato (config + DI, sem backend de plataforma)
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-core:3.2")
    // Ou só as interfaces e anotações
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-api:3.2")
}
```

> O projeto raiz é um container — use as coordenadas de **módulo** acima com
> `com.github.<usuário>.<repo>:<módulo>:<tag>`.
>
> **Comece na `3.2`.** A `3.1` é a mesma lib antes de as configs poderem ficar
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

* `List<T>` de escalares é gravada como lista fluida (`tags: [survival, pvp]`).
* `List<T>` de objetos aninhados cria uma instância nova por elemento no reload
  (os elementos não têm identidade a preservar).
* `@Range`/`@OneOf` não são permitidos em listas ou objetos aninhados — são
  restrições apenas de campos folha.

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
  pulado; os handlers seguintes rodam normalmente, e nada disso vaza para quem
  publicou. O bug de um mod não quebra os handlers de outro.
* `bus.unregisterAll()` remove tudo que o seu dono registrou. Vale chamar
  quando o seu dono vai embora: handlers seguram seus objetos e, por eles, seu
  class loader. No Paper isso é automático — o plugin do CoffeeLib limpa os
  handlers de um plugin quando ele é desabilitado, então um `/reload` não
  deixa a cópia antiga das suas classes respondendo eventos.

> **Uma cópia do CoffeeLib, ou não existe canal compartilhado.** A entrega
> entre donos só funciona enquanto todos carregam as mesmas classes do
> CoffeeLib. Instale-o como mod próprio no Forge/NeoForge e como plugin próprio
> no Paper — onde o seu plugin também precisa declarar o CoffeeLib em `depend`
> ou `softdepend` no `plugin.yml` para resolver essas classes a partir dele. Um
> mod ou plugin que faz shade do CoffeeLib dentro de si fica com um bus
> privado, o que é ótimo se for intencional e silencioso se não for.
> `new DefaultEventBus("meuid")` é a forma honesta de pedir um privado.

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
# coffeelib-forge-1.20.1-3.2-SNAPSHOT.jar      <- instale este (autocontido)
# coffeelib-neoforge-1.21.1-3.2-SNAPSHOT.jar   <- instale este (autocontido)
# coffeelib-paper-3.2-SNAPSHOT.jar             <- instale este (autocontido)
# coffeelib-api-3.2-SNAPSHOT.jar               <- compile contra estes
# coffeelib-core-3.2-SNAPSHOT.jar
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