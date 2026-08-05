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
- **Integração real com plataformas** — Paper usa Configurate/SnakeYAML; Forge e
  NeoForge se integram com seus sistemas nativos de config, então
  `/forge config` e o ConfigTracker enxergam de verdade.

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
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-paper:2.0")
    // Forge 1.20.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-forge-1.20.1:2.0")
    // NeoForge 1.21.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-neoforge-1.21.1:2.0")
    // Ou apenas o core agnóstico de formato (config + DI, sem backend de plataforma)
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-core:2.0")
}
```

> Troque `2.0` por qualquer release taggada. O projeto raiz é um container —
> use as coordenadas de **módulo** acima com
> `com.github.<usuário>.<repo>:<módulo>:<tag>`.

---

## Configuração

### Anotações

| Anotação          | Onde   | Propósito                                                          |
| ----------------- | ------ | -------------------------------------                             |
| `@ConfigFile`     | tipo   | Nome do arquivo (sem extensão) da config.                         |
| `@Key`            | campo  | Sobrescreve a chave gravada no arquivo (padrão: nome do campo).   |
| `@Comment`        | campo  | Comentário(s) escrito(s) acima da chave no arquivo gerado.        |
| `@Ignore`         | campo  | Exclui o campo do load/save totalmente.                           |
| `@Range(min,max)` | campo  | Limites numéricos, validados no load/reload.                      |
| `@OneOf({...})`   | campo  | Restringe um campo String a um conjunto fixo, com sugestões.      |

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

O backend Paper usa **Configurate YAML** (`configurate-yaml`) para
serialização, com estilo de bloco de dois espaços e comentários reaplicados a
partir de `@Comment`.

### Forge 1.20.1

```java
public class MyMod {
    public MyMod(FMLJavaModLoadingContext ctx) {
        ConfigManager config = CoffeeLib.forMod();        // do construtor do mod
        MyConfig cfg = config.load(MyConfig.class);
    }
}
```

O Forge usa o próprio `ForgeConfigSpec`, o que significa que cada config
carregada é um `ModConfig` real e registrado — visível para `/forge config` e
para o `ConfigTracker` do Forge. Como o Forge é dono da I/O de disco e da
validação, a checagem de limites dele redefine valores fora do intervalo para o
padrão (com um aviso no log) em vez de lançar o `ConfigValidationException` do
CoffeeLib.

### NeoForge 1.21.1

```java
public class MyMod {
    public MyMod(ModContainer container, IEventBus bus) {
        ConfigManager config = CoffeeLib.forMod(container);
        MyConfig cfg = config.load(MyConfig.class);
    }
}
```

Igual ao Forge, mas contra o `ModConfigSpec`/`ModConfigEvent` do NeoForge.

> No Forge/NeoForge, os campos só refletem o que está no disco quando o loader
> dispara o `ModConfigEvent` **depois** da construção — não de forma síncrona
> durante a chamada de `load(...)`.

---

## Compilando a partir do código-fonte

O `build` raiz depende dos builds dos módulos e copia todos os jars para
`./dist`:

```bash
./gradlew build
ls dist/
# coffeelib-api-1.0-SNAPSHOT.jar  coffeelib-core-1.0-SNAPSHOT.jar ...
```

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