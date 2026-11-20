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
- **A scheduler** — run work on the main thread now, after some ticks or
  every so many, and bring a future's result back to it; cancelled with the
  server. Forge and NeoForge have none of their own.
- **An economy channel** — mods and plugins provide currencies (`coins`,
  `cash`) and others use them by id, like Vault but for every platform and any
  number of currencies; bridged to Vault both ways on Paper.
- **MySQL, MariaDB, PostgreSQL, SQLite and H2** — a pooled connection
  (HikariCP) per mod or plugin, to a server or to a local file, configured from a `database.yml`, with async JDBC helpers and a
  plain `DataSource`, and Hibernate ready to use on top.
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

CoffeeLib is split into seven modules under `net.loyalnetwork`:

```
coffeelib-api                    interfaces, annotations, exceptions (no deps)
coffeelib-core                   platform-agnostic implementations (pure Java)
coffeelib-database               connection pools; HikariCP, MariaDB and PostgreSQL relocated inside
coffeelib-hibernate              hibernate-core repacked for Forge-like loaders (internal)
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
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-paper:3.9")
    // Forge 1.20.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-forge-1.20.1:3.9")
    // NeoForge 1.21.1
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-platform-neoforge-1.21.1:3.9")
    // Or just the format-agnostic core (config + DI, no platform backend)
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-core:3.9")
    // Or only the interfaces and annotations
    implementation("com.github.RenanPlay13013.CoffeeLib:coffeelib-api:3.9")
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

### Platform-owned services (Phase 3)

The existing manual `new DefaultServiceContainer()` workflow remains supported.
For CoffeeLib-owned command handlers and event listeners, however, use the
**owner-scoped** registry. It is available on all three platforms:

```java
// Paper, from your plugin:
ServiceContainer services = CoffeeLib.services(this);
// Forge, from your mod's constructor or anywhere with a mod id:
ServiceContainer services = CoffeeLib.services("yourmod");
// NeoForge, using the ModContainer:
ServiceContainer services = CoffeeLib.services(container);
```

The first call for an owner creates a single container with four ready-to-use
providers: `PermissionManager`, `Scheduler`, `DatabaseManager` and
`PlayerDirectory`. Repeated calls return that same container. To provide your
own application services, register a provider host **before** its consumers:

```java
class AppProviders {
    private final PlayerRepository repo;
    AppProviders(PlayerRepository repo) { this.repo = repo; }
    @Provide PlayerRepository players() { return repo; }
}

CoffeeLib.services(owner).register(new AppProviders(repo));

class HomeCommand {
    @Receive private PlayerRepository players;
    @Receive private Scheduler scheduler;

    @Command("home")
    void home(CommandSource source) { /* use injected services */ }
}

CoffeeLib.commands(owner).register(new HomeCommand());
CoffeeLib.eventBus(owner).register(new HomeListener());
```

For Paper, `owner` means the `JavaPlugin` instance; for Forge it means the
`String` mod id; NeoForge accepts `ModContainer` or a `String` mod id.
`commands(...).register(handler)` and `eventBus(...).register(listener)` now
wire `@Receive` **before registration**. A missing, ambiguous, static or final
field fails immediately. Injection traverses superclasses, not just the
receiver's concrete class. If any provider is missing, no receiver field is
written. Providers must not return `null`.

On Paper, CoffeeLib releases the owner registry when its plugin is disabled,
preventing a classloader leak across normal reloads. Forge and NeoForge keep
owner registries for the mod's JVM lifetime; do not store world/player entities
as persistent providers. This release does **not** yet add automatic permission
change events or the cross-platform `TextFormatter` / message catalog. Those
remain future work, as do live-server integration tests.

---

## Scheduler

Work on the server's main thread — now, later, or every so often — the same way
on every platform. Forge and NeoForge have no scheduler of their own; on Paper
this is Bukkit's, bound to your plugin.

```java
Scheduler scheduler = CoffeeLib.scheduler(container);   // Forge: scheduler(), Paper: scheduler(this)

scheduler.later(20, () -> player.sendSystemMessage(...));    // in one second
Task autosave = scheduler.every(0, 20 * 60, this::saveAll); // every minute, starting next tick
autosave.cancel();

scheduler.onMain(() -> world.setBlock(...));                 // now if on the main thread, else next tick

repository.findById(id)                                      // completes on a database thread...
        .thenAcceptAsync(found -> teleport(player, found), scheduler.main());   // ...continues on the main one
CompletableFuture<Optional<Home>> home = scheduler.onMain(repository.findById(id)); // same, as a future
```

* **Ticks**, 20 a second when the server keeps up. `later(0)` and `later(1)`
  both run on the next tick, as on Bukkit; the period of `every` counts from
  each run, so a slow tick shifts the following runs instead of bunching them.
* **Failures** in a task are logged under your mod or plugin id and stop
  neither the other tasks nor the next run of a repeating one.
* **Cancelled for you** when the server stops (Forge, NeoForge) or the plugin is
  disabled (Paper). Work scheduled while no server runs — from a mod
  constructor, or between two worlds of a client — waits for the next server's
  first tick.
* **Server-thread futures and shutdown:** `scheduler.onMain(future)` completes
  exceptionally with `CancellationException` if the Forge/NeoForge server stops
  before delivery (or the owning Paper plugin is disabled). This prevents a
  database callback from a previous integrated world from running in the next.
  Handle cancellation explicitly if a command should suppress shutdown errors.

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
* **Asynchronous executors:** an executor may return a `CompletableFuture`
  (any `CompletionStage`). The command counts as started right away, and when
  the future fails the sender is told the same way as above — a
  `CommandException`'s message, or the generic one with the cause logged — from
  the main thread, and only if they are still online. Thrown inside a later
  stage it arrives wrapped in a `CompletionException`; CoffeeLib unwraps it.
  The stages themselves run wherever their future completes (a database
  thread, say), so continue on the main thread with the scheduler before
  touching the world:

  ```java
  @Command(value = "back", permission = "crates.back")
  CompletableFuture<Void> back(ServerPlayer player) {
      return scheduler.onMain(homes.findById(player.getStringUUID())).thenAccept(found -> {
          Home home = found.orElseThrow(() -> new CommandException("No home to go back to."));
          home.teleport(player);                                 // main thread: safe
      });
  }
  ```

  Brigadier cannot wait for a future, so `/execute store result` records `1`
  ("started") for such a command.
* **Namespace:** on Paper the command lives under **your plugin's** namespace
  (`/yourplugin:crate`) and is attributed to your plugin in `/help`, not to
  CoffeeLib. Brigadier commands have no namespace on Forge/NeoForge — vanilla's
  do not either — so it is simply `/crate`, logged under your mod id.

Mistakes in a handler — an optional argument before a required one, an
unsupported type, a missing provider, two methods for one path — fail at
`register`, naming the method, rather than when someone runs the command.

### Phase 2 — player visibility and typed arguments

The `coffeelib-core` module has **no Minecraft types**. Platforms supply the
native player/world/coordinate parsers, and plugins may register their own:

```java
commands.argument(Kit.class, (source, name) -> kits.require(name));
```

`UUID` and `Duration` (`1d2h30m`, `90s`) are available in core. A registered
argument type in first position is an argument, not a sender; a leading,
unannotated platform player type is still interpreted as the sender. Add
`@Arg` or `@OrSelf` to make a leading player a target. `@OrSelf` must be the
**last** parameter and, with `others = "myplugin.command.others"`, only offers
other players to authorized senders.

Paper additionally supports `Location` as a **three-word argument** (`x y z`,
`~ ~ ~` and `~1 ~-2 ~`), taking the player's or command block's current world;
console without a location gets a command error. Paper targets Spigot 1.8.8,
so `NamespacedKey` cannot be added as an argument to this universal jar without
raising that minimum API requirement; use the `String` name for that platform.
Forge/NeoForge have `ResourceLocation`, `ServerLevel`, `Vec3`, `BlockPos`,
`Holder<T>` with `@Registry`, and `ServerPlayer`.

For a vanish plugin, register a rule with **its owner id** (the exact
`JavaPlugin#getName()` on Paper), so CoffeeLib removes it when the plugin is
disabled:

```java
PlayerDirectory players = CoffeeLib.players();
EventSubscription vanish = players.visibility("MyVanish", (viewer, target) ->
        !isVanished(target) || maySeeVanished(viewer));
List<Object> visible = players.visibleTo(viewer);
```

`visibleTo` is a snapshot of currently online players, filtered by every
CoffeeLib rule; on Paper it also honors Bukkit `Player.canSee`. A null viewer
(console) sees all, and a player always sees themselves. Query live online
players and visibility **only on the server thread**. When an owner id is not
supplied, existing `visibility(rule)` remains supported but its returned
subscription **must** be unsubscribed manually. On Forge/NeoForge, release
owner-specific rules manually when the mod's own service stops using
`SharedPlayers.releaseOwner(modId)`; mod loaders do not unload mods per world.

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

## Permissions

Permission nodes with a default, checked the same way on every platform and
**integrated with LuckPerms** out of the box. Declare them once, as constants:

```java
public final class CratePermissions {

    @Permission(description = "Give crate keys")                      // operators by default
    public static final String GIVE = "crates.give";

    @Permission(def = PermissionDefault.EVERYONE, description = "Open crates")
    public static final String OPEN = "crates.open";
}

PermissionManager permissions = CoffeeLib.permissions(container);     // Forge: permissions(), Paper: permissions(this)
permissions.declare(CratePermissions.class);

if (permissions.has(player, CratePermissions.GIVE)) { ... }

@Command(value = "give", permission = CratePermissions.GIVE)          // and commands use the same node
```

How a node is decided:

* **Forge/NeoForge**: first the platform's **PermissionAPI**, when the
  server's permission handler is a real permission mod (LuckPerms or another)
  rather than the platform's own default handler; then LuckPerms directly, when
  it is installed and has an answer (granted *or* denied); otherwise the node's
  default — `EVERYONE`,
  `OPERATORS` (op level 2, or the command's own level), or `NOBODY` until
  granted. So a mod's permissions work on a server with no permission plugin,
  and the moment LuckPerms is added it takes over. The console has everything.
  LuckPerms' API is a compile-only dependency: nothing of it is bundled, and no
  LuckPerms class loads unless the mod is present.
* **Paper**: Bukkit's own permission system, which LuckPerms and every other
  permission plugin already hook into. A declared node is registered with the
  server, default mapped (`EVERYONE` → true, `OPERATORS` → op, `NOBODY` →
  false), so it shows up in permission plugins. Bukkit's rules apply as-is,
  including that `NOBODY` is not granted to the console.
* A node nobody declared is for operators; an empty node is for everyone.

> **Nodes declared on a running server.** The PermissionAPI only accepts nodes
> while the server starts, so a node declared later — a command registered on a
> running server, say — cannot join it until a restart. Until then it is decided
> by the rest of the chain (LuckPerms directly, then its default), and it is
> written to `config/coffeelib/permission-nodes.cache`, whose header explains
> all this; on the next start every node in that file is registered with the
> PermissionAPI like any other. Entries of mods no longer installed are dropped,
> and the file is safe to delete. A node must have at least two segments
> (`crates.give`, not `crates`) and no wildcard to be registered there.

Where CoffeeLib now checks nodes:

* **Commands** on Forge/NeoForge used to check only an op level; a command's
  `permission` now goes through the chain above, with its level as what
  "operators" means. The node is declared automatically on `register` with the
  default its level implies, unless you declared it yourself.
* **A command's own default:** put `@Permission` on the `@Command` method (or
  its class) to declare its node with another default or description, with no
  constants class needed:

  ```java
  @Command(value = "list", permission = "crates.list")
  @Permission(def = PermissionDefault.EVERYONE, description = "List the crates")
  void list(CommandSource source) { ... }
  ```

  The first declaration of a node wins, so one made with
  `permissions.declare(...)` before the command is registered keeps its own.
* **`/coffeelib`'s own subcommands** have nodes instead of a flat op level 2:
  `coffeelib.config.list`, `coffeelib.config.reload`,
  `coffeelib.config.hotreload` and `coffeelib.placeholder`, all defaulting to
  operators — so a server can hand config reloads to staff without giving them op.

### Permission-change notifications (Phase 3)

Use `onChange` instead of polling every player inside each mod/plugin:

```java
PermissionManager permissions = CoffeeLib.permissions(container); // Forge: permissions(), Paper: permissions(this)
EventSubscription subscription = permissions.onChange(uuid -> {
    // This callback runs on the SERVER TICK THREAD, not LuckPerms' async thread.
    // The UUID can refer to a player who has just disconnected.
    // Resolve the player by UUID and re-check the relevant permission here.
    // Example: re-apply fly mode if the player is currently online.
});
// Optional: subscription.unsubscribe();
```

This is an **invalidation hint**, not a guaranteed diff: it means permissions
*may* have changed. Multiple updates to the same UUID before a tick are
coalesced into one callback. Uncaught failures in one listener are logged with
the owner and cannot stop other listeners. Subscriptions are kept by owner;
Paper removes them on plugin disable, and Forge/NeoForge keep them for the
mod's lifetime. The `EventSubscription` type is from `coffeelib-api`.

* **All three platforms, with LuckPerms**: subscribe to
  `UserDataRecalculateEvent`. The event can fire asynchronously; CoffeeLib
  queues only the UUID and delivers on the next server tick. The API is
  optional and never shaded into the jar.
* **Forge/NeoForge**: also listen to `PermissionsChangedEvent` for op/deop;
  canceled events are ignored when observed canceled. The two sources share
  the tick-level deduplication queue. Events can be invalidation hints even
  if a subsequent handler cancels the underlying change.
* **Paper without LuckPerms**: one central poll **every 20 ticks** compares
  online players' op status and effective values of all CoffeeLib-declared
  nodes. Undeclared nodes, external plugin mutations without an observable
  signal, and offline players cannot be watched reliably. The polling delay
  grows if the server TPS drops; it is not a fixed one-second SLA.
* Server shutdown stops accepting late callbacks; an integrated server opening
  a new world resumes processing without replaying changes from the old world.

Custom `PermissionManager` implementations written before this method existed
remain binary-compatible: the interface's default implementation throws a
clear `UnsupportedOperationException`; the built-in managers support it.

---

## Economy

A channel between the mods that **provide** currencies and the mods that
**use** them, in the spirit of Vault: an essentials mod provides `coins` and
`cash`, a shop mod charges `coins`, a quest mod pays `cash`, and none of them
knows the others. CoffeeLib holds **no balances** - the providing mod keeps
the accounts, wherever it likes, and answers every call.

Providing a currency:

```java
public final class CoinsProvider implements CurrencyProvider {
    @Override public String id() { return "coins"; }
    @Override public int fractionalDigits() { return 0; }           // whole coins
    @Override public String format(BigDecimal amount) { return amount.toPlainString() + " coins"; }

    @Override public CompletableFuture<BigDecimal> balance(UUID player) { ... }
    @Override public CompletableFuture<TransactionResult> deposit(UUID player, BigDecimal amount) { ... }
    @Override public CompletableFuture<TransactionResult> withdraw(UUID player, BigDecimal amount) { ... }
    // optional: hasAccount, set, displayName
}

CoffeeLib.economy(container).register(new CoinsProvider(...));   // Forge: economy(), Paper: economy(this)
```

Using one:

```java
Currency coins = CoffeeLib.economy(container).currency("coins");   // fine to take at startup

coins.withdraw(player.getUUID(), new BigDecimal("50")).thenAccept(result -> server.execute(() -> {
    if (result.succeeded()) { /* give the item */ }
    else if (result.status() == TransactionResult.Status.INSUFFICIENT_FUNDS) { /* "not enough coins" */ }
}));
coins.transfer(payer, payee, amount);                                // /pay
```

* **A handle works before its provider exists.** Mods load in any order, so
  `currency("coins")` always returns a handle; until a mod provides it,
  `isAvailable()` is false and transactions report `NO_PROVIDER`.
  `onAvailable("coins", currency -> ...)` runs when it arrives (or right away).
* **One provider per currency.** A second mod registering `coins` is refused
  with an exception naming the first.
* **Amounts are checked before the provider sees them**: negative, zero, or with
  more decimals than the currency allows come back as `INVALID_AMOUNT`. Use
  `BigDecimal`, never `double`, for money.
* **Everything is asynchronous**: a provider on a database never blocks the
  server, and one in memory completes at once. Futures complete on the
  provider's thread - go back to the server thread before touching the world.
* **`transfer` is a withdrawal then a deposit**; if the recipient deposit
  fails, CoffeeLib attempts a refund. A failed refund is logged and reported as
  `FAILED` with a manual-reconciliation warning, not disguised as an ordinary
  recipient failure. **This is not atomic:** a crash or database outage can
  still lose money between operations. Use a transactional provider if
  transfers must be atomic.
* A provider is a plain object called from any thread: it must be thread-safe.
  On Paper, a plugin's currencies are withdrawn when it is disabled.

### Vault (Paper)

With Vault installed, CoffeeLib joins the two economies, set in CoffeeLib's
`plugins/CoffeeLib/economy.yml`:

```yaml
# The CoffeeLib currency offered to Vault plugins as the server's economy, such as coins. Empty: none
vaultExport: ''
# The id another plugin's Vault economy (EssentialsX, CMI...) gets as a CoffeeLib currency. Empty: do not bring it in
vaultImport: vault
```

* **Import** (on by default): the Vault economy of another plugin becomes the
  CoffeeLib currency `vault`, so a mod or plugin on CoffeeLib can charge
  EssentialsX money with `currency("vault")`. Its calls run on the server
  thread, as Vault economies expect. Vault cannot set a balance, so `set`
  reports `NOT_SUPPORTED`.
* **Export** (off until you name a currency): the chosen CoffeeLib currency is
  registered with Vault at high priority, so every Vault plugin uses it. Vault's
  API is synchronous, so each call waits up to 5 seconds for the provider - free
  for a provider in memory, a blocked server thread for a slow one. Banks report
  `NOT_IMPLEMENTED`.

---

## Database

A connection pool per mod or plugin, for **MySQL**, **MariaDB** and
**PostgreSQL** servers, or **SQLite** and **H2** in a local file, with no
server at all. CoffeeLib's part ends at a plain JDBC `DataSource`: use its
small helpers for SQL, or put Hibernate, jOOQ or JDBI on top. Nothing to add
to your build: HikariCP and the drivers ship inside the platform jars,
relocated under `net.loyalnetwork.coffeelib.libs`, so they cannot clash with a
copy another mod or plugin brings.

```java
DatabaseManager databases = CoffeeLib.database(container);   // Forge: database(), Paper: database(this)
Database db = databases.open();                                // from database.yml

db.update("CREATE TABLE IF NOT EXISTS crates (player VARCHAR(36) PRIMARY KEY, opened INT)");

db.queryFirst("SELECT opened FROM crates WHERE player = ?", rs -> rs.getInt(1), uuid.toString())
  .thenAccept(opened -> ...);                                  // Optional<Integer>, off the server thread

db.transaction(connection -> { ... });                         // commit on return, rollback on throw
```

* **Every helper runs on the database's own threads** (`CoffeeLib-DB-<owner>-N`,
  one per pooled connection) and returns a `CompletableFuture`, never blocking
  the server thread. Get back onto it with your platform's scheduler before
  touching the world. Failures complete the future with a `DatabaseException`
  carrying the driver's message.
* **`open()` connects immediately**, so wrong settings fail at startup with the
  reason ("Access denied...", "Connection refused...") and the file to fix.
* **Closed for you**: on Paper when the plugin is disabled (after its
  `onDisable`, so it can still save there); on Forge and NeoForge when the game
  exits. `db.close()` closes one earlier.

The file, created with defaults on first use (`config/<modid>/database.yml` on
Forge/NeoForge, the plugin's data folder on Paper):

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

`database.yml` can live elsewhere, like any config: pass a directory, as in
`CoffeeLib.database(container, Path.of("databases/crates"))` (Forge:
`database(dir)`, Paper: `database(this, dir)`); `null` keeps the default.

Settings of your own (kept in your config, or built in code) go through
`databases.open(DatabaseSettings.builder(DatabaseType.POSTGRESQL).host(...)...build())`.
`properties` are passed to the driver as they are: `sslMode: verify-full` for
MySQL/MariaDB, `sslmode: verify-full` for PostgreSQL, and so on.

### Embedded: SQLite and H2

For data that outgrows a config but does not need a server (homes, a
`/back` location, per-player stats), set `type: SQLITE` or `type: H2`: the
database is a file next to `database.yml` (`file: data`, or a path of your own;
relative paths are resolved against that folder). Host, port and credentials
are ignored. The same `Database` API and the same SQL work as with a server, so
moving to MySQL later is a change in `database.yml`, plus any SQL that differs
between them.

* **SQLite** writes with one connection at a time, so its pool holds a single
  connection whatever `poolSize` says, in WAL mode, waiting up to 5 s on a busy
  file instead of failing. Its driver carries native code bound to its own
  package name, so it cannot be relocated like the rest: on Paper CoffeeLib uses
  the copy the server ships; on Forge and NeoForge it is nested in CoffeeLib's
  jar (`META-INF/jarjar`), and the loader keeps one copy when other mods nest it
  too.
* **H2** keeps everything in `<file>.mv.db`, an ordinary H2 file that any
  database tool opens (DBeaver, IntelliJ, H2's own console; user empty, no
  password). It is the published H2, not relocated: H2 writes its own class
  names into the file, so a relocated copy would make files only CoffeeLib can
  read. It ships like SQLite (nested on Forge/NeoForge, a plugin library on
  Paper).

> **H2 files from CoffeeLib 3.6 and 3.7** were written by such a relocated
> copy, and other tools call them corrupted. The first time one is opened, it is
> converted to a standard H2 file automatically - nothing to do - and the
> original is kept beside it as `<file>.mv.db.coffeelib-old`, to delete once
> everything works.

Either is for one server process: two servers sharing a database need MySQL,
MariaDB or PostgreSQL.

> **MySQL 8 without TLS.** MySQL 8 authenticates with `caching_sha2_password`,
> which over a plain connection needs the server's RSA key. Unless you
> configure `sslMode` (or `allowPublicKeyRetrieval` yourself), CoffeeLib lets
> the driver fetch it, so a default install connects. For a database across an
> untrusted network, set `sslMode` instead.

### Hibernate

Hibernate ORM 6.6 ships with CoffeeLib, so a mod or plugin uses it with no
packaging of its own: compile against it, and build a `SessionFactory` on a
CoffeeLib database.

```groovy
compileOnly 'org.hibernate.orm:hibernate-core:6.6.4.Final'   // nothing else: CoffeeLib brings it at runtime
// compileOnly 'org.hibernate.orm:hibernate-community-dialects:6.6.4.Final' only if you name SQLiteDialect yourself
```

```java
@Entity
public class Home {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String player;
    private String name;
    private double x, y, z;
    // a no-argument constructor, getters...
}

Database db = CoffeeLib.database(container).open();
SessionFactory sessions = CoffeeHibernate.sessionFactory(db, Home.class);   // tables created/updated to match

CompletableFuture.runAsync(() -> sessions.inTransaction(session -> session.persist(home)), db.executor())
        .whenComplete((ok, error) -> server.execute(() -> { /* back on the server thread */ }));
```

* `CoffeeHibernate.sessionFactory` gives Hibernate CoffeeLib's pool as its
  connections, finds your entities through your own class loader, and closes
  the `SessionFactory` together with the database. The overload taking a
  `Consumer<Configuration>` changes anything else (`hbm2ddl.auto`, caching,
  naming); the default is `update`.
* Every database type works. The dialect is detected from the connection for
  MySQL, MariaDB, PostgreSQL and H2; for SQLite, CoffeeLib picks the
  `SQLiteDialect` from Hibernate's own community dialects
  (`hibernate-community-dialects`, shipped alongside). Community dialects are
  maintained with less coverage than the core ones, and SQLite itself takes one
  writer at a time, so for an embedded database with many related entities H2
  is the safer choice.
* Run sessions on `db.executor()` (or threads of your own), never on the server
  thread.
* A lazy association becomes a proxy that loads on a **method call**: read its
  data through getters, not public fields.

#### Repositories

`HibernateRepository<T, ID>` is the usual CRUD for one entity, on the same
futures and threads as everything else here. Extend it, and add your own
queries with `read(...)` (no transaction) and `write(...)` (one transaction,
committed on return, rolled back on a throw):

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

| Method | Returns | Notes |
|---|---|---|
| `save(entity)` | `T` | Insert or update (`merge`). Use the **returned** instance: it is the managed copy, and the one carrying a generated id. |
| `saveAll(entities)` | `List<T>` | One transaction: all saved, or none. |
| `findById(id)` | `Optional<T>` | |
| `findAllById(ids)` | `List<T>` | One query; ids with no row are left out. |
| `findAll()` | `List<T>` | |
| `existsById(id)` | `Boolean` | |
| `count()` | `Long` | |
| `deleteById(id)` | `Boolean` | Whether there was something to delete. |
| `delete(entity)` | `Void` | Works with detached entities, such as the ones a repository returns. |
| `deleteAll()` | `Integer` | One bulk `delete`: JPA callbacks and cascades do **not** run. |
| `update(id, change)` | `Boolean` | Finds, changes and commits in one transaction, the row locked; whether there was one. No `save` needed. |
| `upsert(id, create, change)` | `T` | `update`, creating the entity with `create` first when there is none. |

`update` and `upsert` lock the row (`PESSIMISTIC_WRITE`), so two of them on the
same id run one after the other instead of one overwriting the other's change.
Their functions run **on the database thread**: read what they need from the
world or the player before the call. Two *first* `upsert`s of an id that has no
row yet race to insert it — one wins and the other fails with the duplicate key,
never silently.

```java
players.upsert(uuid, () -> new PlayerEntity(uuid), player -> player.onLogin(name, ip, now));
```

The constructor checks that the class is an entity of that `SessionFactory`, so
forgetting it in `CoffeeHibernate.sessionFactory(db, ...)` fails right there
rather than on the first query. Failures complete the future with a
`DatabaseException` carrying Hibernate's message.

Four things to keep in mind:

1. **Returned entities are detached.** Their session is closed by the time the
   future completes, so reading a `LAZY` association afterwards throws
   `LazyInitializationException`. Fetch what you need inside `read(...)`, with
   a `join fetch` query.
2. **Futures complete on a database thread**, not the server's. Get back to it
   before touching the world, with the [scheduler](#scheduler):
   `scheduler.onMain(future)` or `thenAcceptAsync(..., scheduler.main())`.
3. **`save` merges.** The object you pass does not become managed; keep the
   returned one. (Spring users: here it is never the same object.)
4. **One call is one transaction.** `findById` followed by `save` is two; use
   `update`/`upsert`, or put both inside one `write(...)`, when they must happen
   together.

There are **no queries derived from method names**: a `findByName` is never
generated for you. Write it with `read(...)`, as above.

How it ships:

* **Forge and NeoForge**: nested in CoffeeLib's jar (`META-INF/jarjar`) as
  *game libraries*, so they load next to the mods whose entities they proxy. A
  mod's own dev runs get them too, since CoffeeLib's jar is loaded there as a
  mod. Two adjustments make this work under the module system, both invisible to
  a mod: ANTLR is relocated inside `hibernate-core` (Forge 1.20.1 ships an ANTLR
  4.9 that Hibernate's query parser cannot use), and `jakarta.transaction-api`
  is 2.0.0, the same API without 2.0.1's requirements on CDI modules nothing
  provides.
* **Paper/Spigot 1.17+**: a `libraries` entry of the CoffeeLib plugin, which the
  server downloads from Maven Central on first start. Declare
  `depend: [CoffeeLib]` in your `plugin.yml` to see it.

Hibernate adds about 18 MB to the Forge and NeoForge jars.

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

### Text and message catalogs (Phase 3 / item 8)

The API defines `TextFormatter<C>` and `Messages<T>`. The **Java-only core** parses
legacy `&`/`§` color and style codes, `&#RRGGBB`, `\&`, and templates with named
`{placeholders}`. The renderer is platform-specific: **Forge/NeoForge** use
`MutableComponent` (with RGB), while **Paper** exposes `String` because the
module still targets the Spigot 1.8.8 API. On Paper, RGB is reduced to the
closest legacy color. Parsing does not touch the world.

```java
@ConfigFile("messages")
public class MyMessages {
    public String welcome = "&aWelcome, {player}!";
}

// NeoForge 1.21.1
Messages<MyMessages> messages = CoffeeLib.messages(container, MyMessages.class);
messages.send(player, m -> m.welcome, "player", player.getName().getString());
messages.reload();

// Paper: CoffeeLib.messages(plugin, MyMessages.class)
// Forge: CoffeeLib.messages("modid", MyMessages.class)
```

Message catalogs use **managed YAML** by default, even on Forge and NeoForge,
where an ordinary `@ConfigFile` without an explicit format normally chooses
native TOML. An explicit `format = JSON` or `HOCON` is respected. The catalog
is loaded synchronously from the owner's folder (`plugins/<plugin>/messages.yml`
on Paper; `config/<modid>/messages.yml` on Forge/NeoForge). It remains registered
for `/coffeelib config reload` on Forge/NeoForge, and `messages.reload()` works
on all platforms. The catalog retains its loaded model; reload updates fields
in place. Initialize a given catalog once per owner; multiple independent
instances are not automatically unified.

`messages.send` schedules delivery on the owning platform's server scheduler;
invalid/disconnected recipients are skipped. For command sources, the new
`success`, `info`, and `error` helpers render default green, gray, and red
responses in the native platform adapters. A custom `CommandSource` retains
plain-text defaults for binary compatibility. Template replacement happens
once: values containing `{something}` are not recursively expanded.

**Current limitations:** the standard response colors are not configurable yet;
missing YAML fields fall back according to the existing config loader, but
one-time missing-key warnings have not been added. Real loader builds and
server integration tests remain required before production use.

**Design guidance:** Object Calisthenics and Domain-Driven Design are used as
heuristics: immutable styled runs are value objects, message catalogs belong
to the application domain, and Bukkit/Minecraft rendering remains in platform
adapters. Do not sacrifice API compatibility, correctness, or runtime
performance merely to satisfy a stylistic rule.

## Phase 4 (incremental): numeric permissions and saved locations

### Numeric limits

`PermissionManager` now exposes `declareRange(prefix, first, last)` and
`number(subject, prefix, fallback)`. Example:

```java
PermissionManager permissions = CoffeeLib.permissions(container); // NeoForge
permissions.declareRange("ravenessentials.home.limit", 1, 10);
int allowedHomes = permissions.number(player, "ravenessentials.home.limit", 1);
```

With LuckPerms, an integer meta value under `ravenessentials.home.limit` wins.
Otherwise the highest effective **granted** `ravenessentials.home.limit.N` node is
returned (including inherited contextual grants). Without LuckPerms, the library
checks only nodes declared by this owner. Negative, malformed and overflowing
numeric suffixes are ignored. Ranges grant nothing by default (`NOBODY`), and
can declare up to 10,000 entries per call. No offline database calls occur.

### Persisted location snapshot

The `coffeelib-database` module provides
`net.loyalnetwork.coffeelib.database.location.CoffeeLocation`, a JPA
`@Embeddable` of `world, x, y, z, yaw, pitch, savedAt`. An entity holding more
than one location can use JPA `@AttributeOverrides`. The class intentionally
contains no Minecraft types and holds no live entity/world reference.

Each platform has a `location.Locations` adapter with the same method names:

```java
// Import Locations from your chosen platform package, e.g. .neoforge.location.Locations
CoffeeLocation saved = Locations.of(player);
Optional<ServerLevel> destination = Locations.world(server, saved);
boolean accepted = Locations.teleport(player, saved);
```

Paper uses a `Player`, Bukkit `Server` and `World`, with world **names** as ids;
Forge/NeoForge use `ServerPlayer`, `MinecraftServer`, `ServerLevel` and
namespaced dimension ids. Do not transfer a Paper world-name snapshot to a
modded server without an explicit mapping. All three adapters require the
server's primary/tick thread for reading, world lookup and teleportation. For
work originating from database or I/O threads, first reschedule via the CoffeeLib
`Scheduler`. Missing worlds or disconnected players return `false` from
`teleport` rather than silently defaulting to the overworld. A cross-dimension
teleport may replace the `ServerPlayer`; resolve its UUID again afterward.

This is not a runtime-homologated Phase 4: platform integration still needs compilation against real loader dependencies and live-server tests.


## Phase 4 (incremental): persistent player data (item 13)

`coffeelib-api` now defines `PlayerDataFactory` and `PlayerData<T>`; these
contracts do not import any Minecraft classes. Platform entry points create
native storage adapters:

```java
// NeoForge 1.21.1 — call from the mod constructor, BEFORE attachment registration:
PlayerDataFactory data = CoffeeLib.playerData(container);
PlayerData<Boolean> god = data.copyOnDeath(true).flag("god");
PlayerData<Integer> kills = data.integer("kills");
PlayerData<String> nickname = data.text("nick");
PlayerData<MySettings> settings = data.json("settings", MySettings.class);

god.set(serverPlayer, true); // tick/server thread only
boolean enabled = god.get(serverPlayer);
```

On **Forge 1.20.1**, use `CoffeeLib.playerData("yourmodid")` and a current
`ServerPlayer`. On **Paper/Spigot 1.14+**, use
`CoffeeLib.playerData(yourJavaPlugin)` and a current Bukkit `Player`.
Registration is once per key per mod/plugin owner; names are lowercase
`[a-z0-9._-]`, 1–64 characters. JSON uses Gson and a concrete Java class;
absent JSON is `null`, and setting `null` removes/resets its value. Defaults
are `false`, `0` and `""` for the three primitive kinds. A different field
type under an existing key needs an explicit data migration.

### Semantics and compatibility

- **NeoForge 1.21.1:** one owner-namespaced `AttachmentType<String>` per key,
  serialized using a codec; `copyOnDeath(true)` delegates to the native
  attachment behavior. Declare all keys before registry freeze.
- **Forge 1.20.1:** CoffeeLib registers one namespaced serialized capability on
  players. Fields use `owner:key`; `PlayerEvent.Clone` copies only opted-in
  keys on death, leaving End return alone. No player object is cached.
- **Paper:** uses `PersistentDataContainer` through a version-gated reflective
  adapter because this module must *still link against Spigot 1.8.8*. Calling
  `playerData` on Bukkit < 1.14 throws `UnsupportedOperationException` clearly;
  other CoffeeLib features retain the old baseline. An owner-only
  `PlayerDeathEvent` listener clears non-copying keys after a death. It does
  not clear keys for End portal returns (which can trigger respawn events).
  Plugin disable clears owner descriptors, releasing class-loader references.
- All operations on players require the **server thread**. Use CoffeeLib's
  scheduler to return from futures before reading/writing a player. A stale or
  disconnected player is rejected, not silently redirected. Storage saves
  according to the platform's normal player-save lifecycle; changes are not
  automatically synchronized to client mods.
- A `copyOnDeath` view applies only to subsequently declared keys, not to
  keys already registered. Death persistence defaults to `false`.

**Validation performed:** 140 targeted Java tests (with a lightweight JUnit
executor) passed; platform storage adapters compiled and ran isolated stubs.
The Forge/NeoForge **real** loader builds, Paper 1.14+/modern PDC integration,
and restart/respawn persistence on live servers are **not yet verified**. No
assumption of production readiness should be made from a stubbed compile.

**Still pending in Phase 4:** inventory/menus (item 15) and cross-platform
runtime homologation.


## Read-only chest menus (Phase 4 / item 15)

CoffeeLib provides a small, **locked** inventory menu API. It uses the native
`ItemStack` and player type for each platform and a normal vanilla chest screen;
`coffeelib-api` contains no Minecraft dependencies. Registering a custom client
screen is **not** necessary. The library enforces a 1–6 row range and validates
slot indices at configuration time.

### Paper / Spigot (including 1.8.8)

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

**Safety and lifecycle:**

- Only **locked menus** are supported; attempting to open a menu without
  `.locked()` throws immediately. Unlocked menus and transferring items are not
  implemented yet. Items are display templates, cloned/copied into a fresh
  container for each player. No player inventory is used as menu storage.
- Clicking the top inventory with the left or right mouse button runs the callback
  **on the following server tick**. Shift-click, drag, number-key, drop,
  double-click, and all item-moving operations are blocked. Player inventory
  clicks are also blocked while a locked menu is open.
- Only one callback may be pending per menu, preventing duplicate actions when
  several click packets arrive in the same server tick.
- If the player leaves the menu before the callback executes, the callback is
  discarded. Callback exceptions are logged with the owning plugin/mod and
  never permitted to modify the locked container.
- Call `.open(player)` only from the server thread. Forge and NeoForge callbacks
  are scheduled using the owner's CoffeeLib scheduler; pending tasks are
  canceled when the server stops. Paper menus and handlers are released when
  the owning plugin disables.
- Paper uses legacy inventory titles: on Spigot 1.8.8, the rendered title must
  be at most 32 characters, including formatting codes. It rejects oversized
  titles explicitly.
- The menu intentionally uses the native chest screen. Adding input slots,
  real item transfers, special client-side animations, or fully custom menus
  requires a separate transactional design and is outside this initial API.
