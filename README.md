# CoffeeLib

[![](https://jitpack.io/v/RenanPlay13013/CoffeeLib.svg)](https://jitpack.io/#RenanPlay13013/CoffeeLib)

**Multiplatform configuration management library for Minecraft.**

CoffeeLib is a small, annotation-driven configuration and dependency-injection
library that runs the same way across Paper (Bukkit), Forge 1.20.1 and
NeoForge 1.21.1. It gives you typed config POJOs with automatic file
generation, default values, validation and **preserved comments** — instead of
scattering `node.get("a.b.c", def)` calls all over your code.

At its core it has zero global state: every plugin/mod gets its own
`ConfigManager` bound to its own data/config folder, and nothing is ever
shared between owners in the same JVM.

---

## Features

- **Annotation-driven config POJOs** — write a plain class, let CoffeeLib turn
  it into a file. No stringly-typed config access.
- **Auto-generation of first-run files** — defaults are written on first load.
- **Comment preservation** — `@Comment` values are written back into the file
  as `#` lines, so operators get documentation right next to the value.
- **Validation with suggestions** — `@Range` (numeric bounds) and `@OneOf`
  (enum-like string sets) fail loudly, and `@OneOf` suggests the closest match
  ("did you mean **hard**?") using a Levenshtein-distance based matcher.
- **Arbitrarily nested objects and lists** — nested POJOs and `List<T>` of
  scalars or of nested POJOs, with cycle detection.
- **Small, explicit DI container** — `@Provide` / `@Receive` wiring by type,
  with named providers and singletons.
- **Real platform integration** — Paper uses Configurate/SnakeYAML; Forge and
  NeoForge plug straight into their native config systems so `/forge config`
  and the ConfigTracker see the real thing.

---

## Architecture

CoffeeLib is split into five modules under `net.loyalnetwork`:

```
coffeelib-api                    interfaces, annotations, exceptions (no deps)
coffeelib-core                   platform-agnostic implementations (pure Java)
coffeelib-platform-paper         Paper backend (Configurate YAML)
coffeelib-platform-forge-1.20.1  Forge 1.20.1 integration (Java 17)
coffeelib-platform-neoforge-1.21.1 NeoForge 1.21.1 integration (Java 21)
```

The dependency chain is strict: **platform → core → api**. `core` never
depends on a concrete file format — it only talks to a `ConfigBackend`
interface, and each platform supplies exactly one backend.

### Build layout

Everything is one multi-module Gradle build. Each module keeps its own Java
toolchain floor (Forge uses Java 17, NeoForge 21), and the root build copies
every module's jar into `./dist` when you run `./gradlew build`.

```
java 17 floor (api/core/paper)   Java 21 (neoforge)
```

---

## Getting Started (JitPack)

Add JitPack as a repository and depend on the modules you need. You can
consume the whole config/DI stack through the platform module for your target,
or pull in `coffeelib-core` (which pulls `coffeelib-api` transitively).

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
    // Or just the format-agnostic core (config + DI, no platform backend)
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-core:2.0")
}
```

> Replace `2.0` with any tagged release. The root project is a container — use
> the **module** coordinates above with `com.github.<user>.<repo>:<module>:<tag>`.

---

## Configuration

### Annotations

| Annotation       | Where     | Purpose                                                              |
| ---------------- | --------- | -------------------------------------                                |
| `@ConfigFile`    | type      | File name (without extension) for the config.                        |
| `@Key`           | field     | Override the key written to the file (default: field name).          |
| `@Comment`       | field     | Comment/s written above the key in the generated file.               |
| `@Ignore`        | field     | Exclude the field from load/save entirely.                           |
| `@Range(min,max)`| field     | Numeric bounds, validated on load/reload.                            |
| `@OneOf({...})`  | field     | Restrict a String field to a fixed set, with suggestions.            |

### Basic example

```java
import net.loyalnetwork.coffeelib.api.config.ConfigManager;
import net.loyalnetwork.coffeelib.api.config.annotation.*;

@ConfigFile("server")                       // -> server.yml / server.toml ...
public final class ServerConfig {

    @Comment("Principal host of the server")
    public String host = "localhost";

    @Range(min = 1, max = 65535)
    public int port = 3306;

    @OneOf({"easy", "normal", "hard"})
    public String difficulty = "normal";
}
```

Loading it (platform-specific, see below for each entry point):

```java
ConfigManager manager = CoffeeLib.forPlugin(plugin);   // Paper
ServerConfig config = manager.load(ServerConfig.class);

manager.reload(config);   // re-read the file into the same instance
manager.save(config);      // write current in-memory state back to disk
```

On first load, `server.yml` is created on disk:

```yaml
# Principal host of the server
host: localhost
port: 3300
difficulty: normal
```

### Nested objects and lists

Nesting depth is unbounded; cycles are rejected at scan time.

```java
public class Credentials {
    public String username = "root";
    public String password = "changeme";
}

public class Database {
    @Comment("Database host") public String host = "db.local";
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

* `List<T>` of scalars is written as a flow list (`tags: [survival, pvp]`).
* `List<T>` of nested objects builds a fresh instance per element on reload
  (the elements have no identity to preserve).
* `@Range`/`@OneOf` are not allowed on lists or nested objects — they are
  leaf-field constraints only.

### Validation

`@Range` and `@OneOf` are evaluated on load/reload. An out-of-range number or
an unexpected string causes a `ConfigValidationException` instead of silently
clamping or defaulting. For `@OneOf`, the exception carries ranked
suggestions:

```java
catch (ConfigValidationException e) {
    e.getFieldName();       // "difficulty"
    e.getInvalidValue();    // "hrad"
    e.getSuggestions();     // ["hard"]  ("did you mean hard?")
}
```

---

## Dependency injection

`ServiceContainer` wires `@Provide` *providers* to `@Receive` *receivers* by
exact type. Provider methods run at most once — the first successful
resolution is cached and reused (singletons).

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
container.wire(new Receiver());   // resolves database + api on this instance
```

Rules:

* Matching is by **exact** type — declare the field as the same type the
  provider returns (no widening).
* When more than one provider exists for a type, use `@Provide("name")` and
  `@Receive("name")`; otherwise wiring fails with `AmbiguousProviderException`.
* No automatic dependency graph — providers must be registered *before* the
  receivers that consume them are wired. Wiring fails immediately otherwise.
* A failed `wire()` does not half-apply fields and does not cache the failure.

---

## Platform integration

Each platform exposes a tiny entry point that returns a `ConfigManager` bound
to the owner's config location.

### Paper

```java
private ConfigManager config;

public void onEnable() {
    config = CoffeeLib.forPlugin(this);              // data/ folder
    MyConfig cfg = config.load(MyConfig.class);
}
```

The Paper backend uses **Configurate YAML** (`configurate-yaml`) for
serializing, with two-space block style and comments re-injected from
`@Comment`.

### Forge 1.20.1

```java
public class MyMod {
    public MyMod(FMLJavaModLoadingContext ctx) {
        ConfigManager config = CoffeeLib.forMod();        // from the mod constructor
        MyConfig cfg = config.load(MyConfig.class);
    }
}
```

Forge uses its own `ForgeConfigSpec`, meaning every loaded config is a real,
registered `ModConfig` — visible to `/forge config` and Forge's `ConfigTracker`.
Since Forge owns disk I/O and validation, its bounds checking resets
out-of-range values to the default (with a log warning) rather than throwing
CoffeeLib's `ConfigValidationException`.

### NeoForge 1.21.1

```java
public class MyMod {
    public MyMod(ModContainer container, IEventBus bus) {
        ConfigManager config = CoffeeLib.forMod(container);
        MyConfig cfg = config.load(MyConfig.class);
    }
}
```

Same as Forge but against NeoForge's `ModConfigSpec`/`ModConfigEvent`.

> On Forge/NeoForge, fields only reflect what's on disk once the loader fires
> its `ModConfigEvent` **after** construction — not synchronously during the
> `load(...)` call.

---

## Building from source

The root `build` depends on module builds and copies every jar into `./dist`:

```bash
./gradlew build
ls dist/
# coffeelib-api-1.0-SNAPSHOT.jar  coffeelib-core-1.0-SNAPSHOT.jar ...
```

Toolchains are auto-provisioned by the foojay resolver against a configured JDK.
Check `gradle.properties` / each module's `gradle.properties` for version pinning.

---

## License

Released under the **MIT License**.

---

## AI Disclaimer

A significant part of CoffeeLib's implementation was written with the help of an
AI assistant, and I *directed it end-to-end* — but I want to be transparent about
that.

**I, RenanPlay13013**, am responsible for every architectural decision, module
layout, the `api` / `core` / platform separation, the annotation contracts, the
public API surface, the build system (multi-module Gradle), and how CoffeeLib
integrates with each platform. The AI assisted with a large portion of the
actual code generation, boilerplate and refactors, but I knew what the code was
doing at every step and reviewed **everything manually** before it was
committed. Nothing was merged as-is without my understanding.

If something looks off, treat it as mine — not the AI's. I own the result.