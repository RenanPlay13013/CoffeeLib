# CoffeeLib architecture — DDD and Object Calisthenics

CoffeeLib is a **cross-platform integration library**, not a single business application. We use Domain-Driven Design (DDD) to separate *contexts, contracts and invariants*; Object Calisthenics provides refactoring heuristics, not hard public-API compatibility requirements.

## Contexts and boundaries

| Layer | Responsibility | Must not own |
| --- | --- | --- |
| `coffeelib-api` | Stable consumer-facing contracts and callbacks | Minecraft platform classes, repository implementation details |
| `coffeelib-core` | Command, scheduler, economy, visibility, permission, message and DI rules | Bukkit/Forge/NeoForge lifecycle or runtime types |
| `coffeelib-database` | Pool/transaction and Hibernate infrastructure | Gameplay scheduling, events and player-facing output |
| `coffeelib-platform-*` | Platform adapters; threading, lifecycle, networking and data conversion | New divergent business rules that belong in the core |

## Internal model

- **Commands:** `CommandScanner` coordinates reflection discovery; `CommandArguments` validates parameter contracts; `CommandSuggestions` resolves completion providers; `CommandPaths` normalizes route aliases and refuses duplicates. New rules belong beside the responsible collaborator, not in a monolithic scanner.
- **DI:** `DefaultServiceContainer` is a stable facade; `ProviderCatalog` discovers and registers providers; `InjectionPlan` stages receiver assignments. Register-time validation must precede publication.
- **Scheduling:** `TickScheduler` handles thread handoff and lifecycle; `TickAgenda` owns tick/period invariants. Scheduled work is tied to the server lifecycle; never carry a pending completion into another world.
- **Visibility:** `SharedPlayers` enumerates online players; `VisibilityPolicies` owns rule lifetimes, owner cleanup and fail-closed evaluation. No exceptions from vanish rules may expose a hidden target.
- **Economy:** `RegistryCurrency` dispatches to the current provider; `MoneyAmountPolicy` validates decimal scale/positivity. Atomic transfer is a provider capability, not a client-side withdraw/deposit compensation protocol.
- **Messages:** `DefaultMessages` owns delivery and catalog lifetime; `PlaceholderBindings` validates input; `MessageTemplate` performs non-recursive substitution.
- **Permissions:** `SharedPermissionChanges` owns subscriptions and delivery; `PermissionChangeBuffer` owns asynchronous invalidation and server lifecycle buffering.

## Object Calisthenics guidance

1. Favor small, intention-revealing methods, early returns, encapsulated collections, and immutable internal value objects.
2. Keep invariants close to the model that owns them; avoid spreading validation across Paper/Forge/NeoForge adapters.
3. Prefer a domain-specific type for a real invariant, not a wrapper for every `String` or `int`. Existing public contracts retain their signatures.
4. Keep reflection, persistence, I/O, classloader and platform-specific API calls in infrastructure / adapter boundaries.
5. Do not apply “one level of indentation,” “one dot,” or “no else” mechanically if doing so reduces correctness or clarity.
6. Preserve atomicity and failure semantics, especially for config reload, currency transfer, permissions and data copied on death.
7. For every new behavior, test the pure policy in core and validate each actual platform version before claiming production readiness.

## Known limitations and review backlog

- Large existing components (`ManagedConfigService`, `DefaultConfigManager`, `PooledDatabase`, and platform bootstrap/command adapter classes) need dedicated refactors with integration tests; their size alone is **not** evidence of a functional bug.
- Forge and NeoForge have extensive similar adapter code, but platform mappings/event APIs differ. Only extract behavior after validating both target versions.
- Process-wide registries (`CommandRoots`, `SharedServices`, permission and economy registries) need an explicit strategy for hybrid runtimes, plugin reload and multiple integrated servers. Replacing them without lifecycle tests risks new leaks or differences in behavior.
- All changes here are additive/internal. They do **not** mean the official Gradle build, Forge, NeoForge or Paper runtime integration has passed. Do not merge without those checks.
