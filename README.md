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
- **Real platform integration** — Forge and NeoForge plug straight into their
  native config systems so `/forge config` and the ConfigTracker see the real
  thing.
- **Pick your format per config** — `@ConfigFile(format = YAML | JSON | HOCON)`
  works on every platform, Forge-like ones included. A config in one of these
  is CoffeeLib's from end to end: read, validated and written by `core`.
- **No mod-loading lifecycle for managed formats** — a managed config is read
  during `load(...)` and the instance comes back populated, instead of waiting
  for the loader's `ModConfigEvent`.
- **Manual reload and hot reload** — `/coffeelib config reload` applies an
  operator's edit immediately, and `/coffeelib config hotreload start|stop`
  toggles a file watcher that does it on every save.

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
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-paper:3.0.4")
    // Forge 1.20.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-forge-1.20.1:3.0.4")
    // NeoForge 1.21.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-neoforge-1.21.1:3.0.4")
    // Or just the format-agnostic core (config + DI, no platform backend)
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-core:3.0.4")
    // Or only the interfaces and annotations
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-api:3.0.4")
}
```

> The root project is a container — use the **module** coordinates above with
> `com.github.<user>.<repo>:<module>:<tag>`.
>
> **Start at `3.0.4`.** `3.0.3` resolves too, but its platform POMs declare
> `coffeelib-api`, `coffeelib-core` and Configurate as dependencies although the
> jar already bundles them, so you get those classes twice. Every tag before
> that, `2.0.x` included, has no artifacts at all: the build failed before
> producing any, and three of the five modules had no publication to begin with.
> The platform jars JitPack serves are the self-contained ones described under
> [Building from source](#building-from-source).

---

## Configuration

### Annotations

| Annotation       | Where     | Purpose                                                              |
| ---------------- | --------- | -------------------------------------                                |
| `@ConfigFile`    | type      | File name (without extension), and optionally the `format`.          |
| `@Key`           | field     | Override the key written to the file (default: field name).          |
| `@Comment`       | field     | Comment/s written above the key in the generated file.               |
| `@Ignore`        | field     | Exclude the field from load/save entirely.                           |
| `@Range(min,max)`| field     | Numeric bounds, validated on load/reload.                            |
| `@OneOf({...})`  | field     | Restrict a String field to a fixed set, with suggestions.            |

### Config formats

`@ConfigFile` takes a `format`, defaulting to `ConfigFormat.NATIVE`:

| Format   | File    | Who owns the file                                               |
| -------- | ------- | --------------------------------------------------------------- |
| `NATIVE` | varies  | The platform. On Paper that means YAML (so, CoffeeLib); on Forge/NeoForge it is the loader's own `ForgeConfigSpec`/`ModConfigSpec` TOML. |
| `YAML`   | `.yml`  | CoffeeLib (Configurate/SnakeYAML). Comments preserved.          |
| `JSON`   | `.json` | CoffeeLib (Configurate/Gson). Comments dropped — JSON has none.  |
| `HOCON`  | `.conf` | CoffeeLib (Configurate/HOCON). Comments preserved.              |

```java
@ConfigFile(value = "server", format = ConfigFormat.YAML)   // -> config/server.yml on Forge
public final class ServerConfig { /* ... */ }
```

Everything but `NATIVE` is a **managed** config, and on Forge and NeoForge that
choice changes three things:

1. **Loading is synchronous.** `load(...)` reads the file (creating it with
   defaults on first run) and hands back a populated instance. Nothing waits on
   `ModConfigEvent`, so the values are usable in the same constructor.
2. **Validation is CoffeeLib's.** `@Range`/`@OneOf` throw
   `ConfigValidationException` instead of the loader silently resetting an
   out-of-range value to its default.
3. **Reloading is explicit** — see below.

### Reloading a managed config

Every platform entry point returns a `ReloadableConfigManager`
(`ConfigManager` + `ConfigReloadService`), covering the managed configs that
manager loaded:

```java
ReloadableConfigManager configs = CoffeeLib.forMod();
ServerConfig cfg = configs.load(ServerConfig.class);          // format = YAML

configs.addReloadListener(config -> reopenConnection());      // react to new values
configs.managedFiles();        // ["server.yml"]
configs.reload("server.yml");  // one file, now
configs.reloadAll();           // returns how many reloaded cleanly
configs.startHotReload();      // watch the files, reload on change
configs.stopHotReload();
```

Hot-reload callbacks run on the watcher thread, not the server thread —
schedule onto the platform's thread yourself if what you do needs it. A file
that fails to parse or validate leaves the previous values in memory and logs
why, rather than killing the watcher or half-applying an edit.

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

Paper has no config system of its own to defer to, so every config here is a
managed one: `NATIVE` resolves to **Configurate YAML** (two-space block style,
comments re-injected from `@Comment`), and a class can opt into JSON or HOCON
per `@ConfigFile(format = ...)`. Manual reload and hot reload are available on
the returned manager — Paper plugins ship their own `/myplugin reload`, so this
module registers no command of its own.

### Forge 1.20.1

```java
public class MyMod {
    public MyMod(FMLJavaModLoadingContext ctx) {
        ConfigManager config = CoffeeLib.forMod();        // from the mod constructor
        MyConfig cfg = config.load(MyConfig.class);
    }
}
```

A `NATIVE` config uses Forge's own `ForgeConfigSpec`, so it is a real,
registered `ModConfig` — visible to `/forge config` and Forge's
`ConfigTracker`. Since Forge owns disk I/O and validation there, its bounds
checking resets out-of-range values to the default (with a log warning) rather
than throwing CoffeeLib's `ConfigValidationException`.

A config in any other format goes to `config/<name>.<ext>` and is CoffeeLib's:

```java
public class MyMod {
    public MyMod(FMLJavaModLoadingContext ctx) {
        ReloadableConfigManager configs = CoffeeLib.forMod();
        MyConfig cfg = configs.load(MyConfig.class);   // format = YAML -> populated right here
        configs.startHotReload();                      // optional: watch it from the start
    }
}
```

`CoffeeLib.forMod()` must still be called from the mod constructor, because
that is the only place Forge lets anyone register a `ModConfig`. If you only
use managed formats, `CoffeeLib.detached("mymod")` works anywhere — a
server-start handler, a command, a reload hook — and fails with a clear
`ConfigException` if handed a `NATIVE` class.

### NeoForge 1.21.1

```java
public class MyMod {
    public MyMod(ModContainer container, IEventBus bus) {
        ConfigManager config = CoffeeLib.forMod(container);
        MyConfig cfg = config.load(MyConfig.class);
    }
}
```

Same as Forge but against NeoForge's `ModConfigSpec`/`ModConfigEvent`, and
`CoffeeLib.detached("mymod")` is here too.

> This applies to `NATIVE` configs only: their fields reflect what is on disk
> once the loader fires its `ModConfigEvent` **after** construction, not
> synchronously during the `load(...)` call. A managed config
> (`format = YAML | JSON | HOCON`) is read during the call and never involves
> the loader.

### `/coffeelib config` (Forge and NeoForge)

The CoffeeLib jar registers one command, the operator side of managed configs —
the loader knows nothing about those files, so without it an edit would need a
server restart. Requires permission level 2.

```
/coffeelib config list                      what is managed, and what is being watched
/coffeelib config reload                    every managed file of every mod
/coffeelib config reload <mod> [<file>]     one mod, or one file of it
/coffeelib config hotreload status
/coffeelib config hotreload start [<mod>]   start the file watcher
/coffeelib config hotreload stop  [<mod>]   stop it
```

`<mod>` is the mod id the manager was created with, and files are named as
listed (`server.yml`). Native configs are untouched by all of this — they stay
`/forge config`'s and `/neoforge config`'s business.

---

## Building from source

`build` builds every module and copies its jar into `./dist`:

```bash
./gradlew build
ls dist/
# coffeelib-forge-1.20.1-3.0-SNAPSHOT.jar      <- install this one (self-contained)
# coffeelib-neoforge-1.21.1-3.0-SNAPSHOT.jar   <- install this one (self-contained)
# coffeelib-paper-3.0-SNAPSHOT.jar             <- install this one (self-contained)
# coffeelib-api-3.0-SNAPSHOT.jar               <- compile against these
# coffeelib-core-3.0-SNAPSHOT.jar
```

Two kinds of jar, for two different jobs.

**The platform jars are self-contained**: each bundles `coffeelib-api`,
`coffeelib-core` and the Configurate backends (`configurate-yaml`,
`configurate-gson`, `configurate-hocon`), because the game loads it as a
mod/plugin rather than resolving its Maven dependencies — `/coffeelib config`
runs inside that jar and needs them. `gson` is left out: the game already
provides it. One file into `mods/` or `plugins/` and nothing to copy beside it.
A mod or plugin that shades CoffeeLib instead of relying on the installed jar
needs to shade the same set.

**`coffeelib-api` and `coffeelib-core` are the plain library jars**, for
depending on CoffeeLib at build time without pulling in a platform you do not
target — `core` for the config/DI implementation, `api` alone if you only need
the interfaces and annotations. Being plain is the point: they carry no copy of
Configurate, so a build tool resolves the formats from their own metadata (see
the JitPack coordinates above) and nothing is duplicated at runtime. They are
not meant to be dropped into `mods/`.

The Forge jar is taken after `reobfJar`, which rewrites it to the SRG names a
real Forge runtime patches — a jar copied before that step runs only inside a
dev run.

### Dev runs

`runClient`/`runServer` load the mod from `build/classes`, not from that jar, so
the bundling above does nothing for them and each loader needs its own wiring:

* **Forge** — `coffeelib-core` goes into ForgeGradle's `minecraftLibrary`, which
  lands on the Minecraft runtime classpath (the one that also carries gson and
  guava). A project's plain runtime dependencies sit on a classpath the mod's own
  class loader cannot see — that was the tab-complete crash on
  `ConfigReloadRegistry`.
* **NeoForge** — the same thing through ModDevGradle's
  `additionalRuntimeClasspath`.

Both exclude `gson`: Minecraft puts its own copy on the module path, and a second
one makes the run fail to resolve. Both are also classpath-only on purpose —
adding the classes as mod sources *as well* makes two JPMS modules export the
same package, which fails the same way.

Both loaders launch from generated argument files. After changing any of this,
start the run through Gradle (`./gradlew :coffeelib-platform-neoforge-1.21.1:runClient`)
or re-sync Gradle in the IDE, otherwise the run reuses the previously generated
classpath and you debug a configuration that is no longer on disk.

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