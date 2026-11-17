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
- **Field shapes that are actually supported** — every primitive and wrapper,
  `String`, enums, nested POJOs to any depth, `List`/`Set` of scalars or POJOs,
  and `Map<String, V>`. Anything else is refused by name at startup rather than
  written out as an empty value.
- **Small, explicit DI container** — `@Provide` / `@Receive` wiring by type,
  with named providers and singletons.
- **An event bus of its own** — one channel shared by every mod and plugin on
  the library, with priorities, cancellation and `@Subscribe` handlers, so two
  owners talk through plain Java types instead of Forge or Bukkit events.
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
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-paper:3.3")
    // Forge 1.20.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-forge-1.20.1:3.3")
    // NeoForge 1.21.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-neoforge-1.21.1:3.3")
    // Or just the format-agnostic core (config + DI, no platform backend)
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-core:3.3")
    // Or only the interfaces and annotations
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-api:3.3")
}
```

> The root project is a container — use the **module** coordinates above with
> `com.github.<user>.<repo>:<module>:<tag>`.
>
> **Start at `3.3`.** Before it, a `List`, `Map`, array, `byte` or `char` field
> was written out as an empty value - destroying whatever an operator had typed
> under that key - and a `Byte` or `Character` field crashed the server at
> startup with a JPMS error. `3.2` is otherwise the same library; `3.1` is `3.2`
> before configs could live
> outside `config/`, and before a `NATIVE` config on Forge/NeoForge got its
> `.toml` extension — upgrading from it renames that file, and the old
> extensionless one's values do not follow. `3.0.4` is `3.1` without the event
> bus; `3.0.3` resolves too, but its platform POMs declare
> `coffeelib-api`, `coffeelib-core` and Configurate as dependencies although the
> jar already bundles them, so you get those classes twice. Every tag before
> those, `2.0.x` included, has no artifacts at all: the build failed before
> producing any, and three of the five modules had no publication to begin with.
> The platform jars JitPack serves are the self-contained ones described under
> [Building from source](#building-from-source).

---

## Configuration

### Annotations

| Annotation       | Where     | Purpose                                                              |
| ---------------- | --------- | -------------------------------------                                |
| `@ConfigFile`    | type      | File name (no extension, `/` for subdirectories) and, optionally, the `format`. |
| `@Key`           | field     | Override the key written to the file (default: field name).          |
| `@Comment`       | field     | Comment/s written above the key in the generated file.               |
| `@Ignore`        | field     | Exclude the field from load/save entirely.                           |
| `@Range(min,max)`| field     | Numeric bounds, validated on load/reload.                            |
| `@OneOf({...})`  | field     | Restrict a String field to a fixed set, with suggestions.            |

### File names and where configs live

`@ConfigFile`'s value is a name relative to the config directory, without an
extension — the backend appends its own. A `/` puts the file in a
subdirectory, which is created on first write:

```java
@ConfigFile("server")                 // config/server.yml
@ConfigFile("mymod/database")         // config/mymod/database.yml
```

The name has to stay inside that directory: `..`, an absolute path, a `\`, an
empty segment and a name that already carries the backend's extension are all
rejected at load, with a message saying so. (`../mymod/server` used to resolve,
quietly writing outside the folder the platform handed CoffeeLib.)

Wanting the files elsewhere is legitimate, and it is the owner's call to make
at the call site rather than a string's side effect — pass the directory when
you create the manager:

```java
// Forge/NeoForge: anywhere, instead of the loader's config/
var configs = CoffeeLib.forMod(FMLPaths.GAMEDIR.get().resolve("mymod"));
var configs = CoffeeLib.detached("mymod", Path.of("/srv/shared-configs"));

// Paper: instead of the plugin's own data folder
var configs = CoffeeLib.forPlugin(this, getDataFolder().getParentFile().toPath().resolve("shared"));
```

`ConfigFormat.NATIVE` is the exception: the loader owns those files and always
writes them under `config/`, so a custom directory does not apply to them.

A managed config is named to operators by its path relative to the base
directory, which is what `/coffeelib config` lists and accepts:
`mymod/database.yml`. The bare file name works too while only one managed
config has it; with two, the command says which ones it could mean instead of
reloading whichever came first.

### Config formats

`@ConfigFile` takes a `format`, defaulting to `ConfigFormat.NATIVE`:

| Format   | File    | Who owns the file                                               |
| -------- | ------- | --------------------------------------------------------------- |
| `NATIVE` | varies  | The platform. On Paper that means YAML (so, CoffeeLib); on Forge/NeoForge it is the loader's own `ForgeConfigSpec`/`ModConfigSpec` TOML, written as `config/<name>.toml`. |
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

A `List`, `Set` or `Map<String, V>` can hold scalars or config objects, and the
element type comes from the field's generic signature — so declare it
(`List<String>`, not a raw `List`):

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

* A collection is **replaced** on load, not merged into: the file is the source
  of truth, so an element an operator deleted stays deleted.
* Elements that are config objects get a fresh instance each — they have no
  identity to preserve, unlike a nested object, which is a single field.
* A `Set` field keeps set semantics; anything else `Collection` becomes a list.
* `@Comment` is not carried into collection elements: it would have to repeat
  per element, and no format places a comment per sequence item reliably.
* `@Range`/`@OneOf` apply to scalar fields only; on anything else they are
  refused rather than silently ignored.

**Refused, with the field named, at `load` time:** arrays (declare a `List`),
raw or wildcard generics, `Map` keyed by anything but `String`, JDK types with
no codec (`Duration`, `UUID`, ...), interfaces and abstract classes,
non-static inner classes. Each throws a `ConfigException` saying what to use
instead. Nothing is written to the file when a scan fails, so a config that
cannot be represented never overwrites one that can.

> On Forge and NeoForge this applies to CoffeeLib-managed formats. A `NATIVE`
> config is described by the loader's own spec, which has no notion of
> collections and no `byte`/`char` value: those are refused too, pointing at a
> managed format.

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

## Event bus

CoffeeLib has its own event bus, for talking **between** mods and plugins
without going through Forge's `IEventBus` or Bukkit's `PluginManager`. A
platform bus ties both ends of a feature to that platform: the same thing needs
a Forge event, a NeoForge event and a Bukkit event, each registered its own
way, and an event one mod defines is awkward for another to listen to. Here,
both ends agree on a plain Java class and nothing else.

Any type can be an event — a record is usually enough:

```java
public record OrderPlaced(String item, int quantity) {}
```

Get a handle bound to your own mod or plugin, then post or listen:

```java
EventBus bus = CoffeeLib.eventBus();           // Forge, from the mod constructor
EventBus bus = CoffeeLib.eventBus(container);  // NeoForge
EventBus bus = CoffeeLib.eventBus(this);       // Paper, from your JavaPlugin

// lambda, when you want the handle back and nothing else
EventSubscription sub = bus.subscribe(OrderPlaced.class, event -> log(event.item()));
sub.unsubscribe();

bus.post(new OrderPlaced("coffee", 2));
```

Or group handlers in a class, which is where `@Subscribe` earns its keep:

```java
public final class OrderListener {

    @Subscribe                                       // defaults to NORMAL
    private void onOrder(OrderPlaced event) { ... }  // private is fine

    @Subscribe(priority = EventPriority.MONITOR)     // runs last, observe only
    void audit(OrderPlaced event) { ... }
}

bus.register(new OrderListener());
```

A handler fires for its parameter's type **and every subtype**, so subscribing
to a common supertype or interface works the way you would expect. Handlers run
in `EventPriority` order — `FIRST`, `EARLY`, `NORMAL`, `LATE`, `LAST`,
`MONITOR` — and in registration order within one priority.

### Cancelling

An event that implements `Cancellable` can be called off by a handler, and
`post` hands the event back so the decision reads at the call site:

```java
if (!bus.post(new OrderPlaced("coffee", 2)).isCancelled()) {
    placeOrder();
}
```

Honoring the cancellation is the poster's job — the bus never acts on it
itself. What it does do is stop delivering a cancelled event to handlers that
did not ask for it; a handler that wants them anyway says
`@Subscribe(receiveCancelled = true)`, which is for logging and metrics rather
than for acting on the event.

### Threads, failures and cleanup

* Handlers run **on the thread that posted**. There is no queue and no thread
  hand-off, so a slow handler blocks the poster, and anything touching server
  state has to get itself onto the right thread the way its platform expects.
* A handler that throws is logged against its owner and skipped; the handlers
  after it still run, and `post` reports nothing to whoever posted. One mod's
  bug cannot break another's handlers. That covers **anything a handler can
  throw** — an exception, an `AssertionError`, a `NoClassDefFoundError` from a
  class another mod did not ship — and **both** ways of registering one, lambda
  or `@Subscribe` (in 3.3 and earlier an `AssertionError` escaped a lambda but
  not an annotated method). The only thing let through is the JVM's own fatal
  errors, such as `OutOfMemoryError`, because nothing after them can be trusted.
* `bus.unregisterAll()` drops everything your owner registered. Worth calling
  when your owner goes away: handlers hold your objects, and through them your
  class loader. On Paper this is automatic — the CoffeeLib plugin clears a
  plugin's handlers when it is disabled, so a `/reload` cannot leave the old
  copy of your classes answering events.

### Knowing whether it worked

`post` suits notifications, where nobody downstream should be able to break the
sender. It does not suit a publisher that is **coordinating work** — "deliver
the reward, *then* mark the session complete" — because it returns normally
whether every handler succeeded or not. For that, `dispatch` is the same
delivery with the outcome handed back:

```java
EventResult<RewardDelivery> result = bus.dispatch(new RewardDelivery(player, reward));

if (result.hasFailures()) {
    rollBack(player);              // the publisher decides what a failure means
} else {
    session.complete();
}
```

`EventResult` has the event, `handlersInvoked()`, and `failures()` — each an
`EventFailure(ownerId, handlerEventType, cause)`, with `cause` exactly as the
handler threw it, never wrapped. Prefer an exception at the point you choose
over checking a flag:

```java
bus.dispatch(event).throwIfFailed();   // EventDispatchException; first failure is the
                                       // cause, the rest are suppressed
```

`dispatch(event, FailurePolicy.STOP)` halts at the first failure instead of
running every handler, for when a later handler builds on an earlier one's work
and acting on a half-done state would be worse than not acting.

> **There is no rollback.** An event is a plain object. A handler that cancelled
> it or changed a field and *then* failed leaves it cancelled or changed — the
> bus has no transaction to undo. `dispatch` is how a publisher learns it must
> clean up; the cleaning up is its own job. Failures are logged either way.

> **One copy of CoffeeLib, or no shared channel.** Delivery between owners only
> works while they all load the same CoffeeLib classes. Install it as its own
> mod jar on Forge/NeoForge, and as its own plugin on Paper — where your plugin
> also needs CoffeeLib under `depend` or `softdepend` in its `plugin.yml` to
> resolve those classes from it. A mod or plugin that shades CoffeeLib into
> itself gets a private bus instead, which is fine if intended and silent if
> not. `new DefaultEventBus("myid")` is the honest way to ask for a private one.

---

## Commands

Annotate a method and it **is** the command's executor; its parameters are the
arguments, and their types decide parsing and tab completion.

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

    @Command("open")                                     // players only: the sender type says so
    void open(ServerPlayer player, @Arg("crate") @Suggest({"daily", "vote"}) String crate) { ... }

    @Command("announce")
    void announce(@Arg("message") @Greedy String message) { ... }

    Collection<String> online() { ... }                  // suggestions, filtered as the player types
}

CoffeeLib.commands(container).register(new CrateCommands());   // NeoForge
CoffeeLib.commands().register(new CrateCommands());            // Forge, from the mod constructor
CoffeeLib.commands(this).register(new CrateCommands());        // Paper, no plugin.yml entry needed
```

* **Arguments:** `String`, `int`, `long`, `double`, `float`, `boolean` (and
  wrappers), enums. `@Default` makes one optional (optional ones go last),
  `@Greedy` takes the rest of the line, `@Arg` names it — needed unless you
  compile with `-parameters`.
* **Tab completion:** subcommands, enum constants and booleans complete on their
  own; `@Suggest` adds fixed values or a provider method. On Forge/NeoForge the
  commands compile to **Brigadier**, so the client validates numbers as they are
  typed; on Paper a completer is registered with the command.
* **Sender:** an optional first parameter — `CommandSource` on any platform, or
  the native type (`ServerPlayer`, `CommandSourceStack`, `Player`,
  `CommandSender`). A console running a player-only command is refused with a
  message.
* **Permissions:** `permission` is a node on Paper; on Forge/NeoForge
  `permissionLevel` is the operator level, derived as 2 when a `permission` is
  set and 0 otherwise. A class-level value applies to every method.
* **Errors:** a bad number, a missing argument or a missing permission is told
  to the sender, with the usage line. Throw `CommandException` from an executor
  for your own messages; any other exception is a bug — logged, and the sender
  only learns that something went wrong.
* **Namespace:** on Paper the command lives under **your plugin's** namespace
  (`/yourplugin:crate`) and is attributed to your plugin in `/help`, not to
  CoffeeLib. Brigadier commands have no namespace on Forge/NeoForge — vanilla's
  do not either — so it is simply `/crate`, logged under your mod id.

Mistakes in a handler — an optional argument before a required one, an
unsupported type, a missing provider, two methods for one path — fail at
`register`, naming the method, rather than when someone runs the command.

---

## Placeholders

Register placeholders once and any mod or plugin on CoffeeLib can fill them in.
On Paper that is what PlaceholderAPI already does; on Forge and NeoForge there
is no common system, so CoffeeLib is the intermediary.

```java
public final class CratePlaceholders {

    @Placeholder("keys")                  // %crates_keys% and %crates_keys_daily%
    int keys(ServerPlayer player, String crate) { ... }

    @Placeholder("total")                 // %crates_total%, no player needed
    long total() { ... }
}

PlaceholderManager placeholders = CoffeeLib.placeholders(container);        // namespace = mod id
PlaceholderManager placeholders = CoffeeLib.placeholders("mymod", "crates"); // or one you choose
placeholders.register(new CratePlaceholders());
placeholders.register("season", context -> currentSeason());

String line = placeholders.apply(player, "You have %crates_keys% keys - %economy_balance%");
```

* **Syntax is PlaceholderAPI's**: `%namespace_key%`, `%namespace_key_argument%`,
  so text written for Paper works unchanged elsewhere. The namespace is matched
  against registered ones longest-first rather than cut at the first `_`, so a
  mod id like `my_mod` works.
* **`apply` fills in every namespace**, not just yours — that is how one mod
  uses another's placeholders.
* **Unknown, `null` or failing stays as written**: an unregistered placeholder,
  a resolver returning `null` or throwing (logged against its owner), or one
  declaring a player type when there is no such player is left in the text,
  never replaced with something misleading. `50% off` is not a placeholder.
* **Resolver parameters**, any order, all optional: the player in the platform's
  type, a `String` for the argument, a `PlaceholderContext`.
* **Paper with PlaceholderAPI**: each namespace is published as an expansion,
  so plugins using PlaceholderAPI see yours, and `apply` also fills in theirs.
  PlaceholderAPI is a soft dependency, never bundled. It cuts identifiers at the
  first `_`, so keep `_` out of a namespace you want to publish there. A
  plugin's placeholders and expansions are dropped when it is disabled.
* Operators can check one with `/coffeelib placeholder list` and
  `/coffeelib placeholder parse <text>` on Forge/NeoForge.

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
# coffeelib-forge-1.20.1-3.3-SNAPSHOT.jar      <- install this one (self-contained)
# coffeelib-neoforge-1.21.1-3.3-SNAPSHOT.jar   <- install this one (self-contained)
# coffeelib-paper-3.3-SNAPSHOT.jar             <- install this one (self-contained)
# coffeelib-api-3.3-SNAPSHOT.jar               <- compile against these
# coffeelib-core-3.3-SNAPSHOT.jar
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