package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.CommandArgument;
import net.loyalnetwork.coffeelib.api.command.annotation.OrSelf;
import net.loyalnetwork.coffeelib.api.command.annotation.Arg;
import net.loyalnetwork.coffeelib.api.command.annotation.Command;
import net.loyalnetwork.coffeelib.api.command.annotation.Default;
import net.loyalnetwork.coffeelib.api.command.annotation.Greedy;
import net.loyalnetwork.coffeelib.api.command.annotation.Suggest;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;
import net.loyalnetwork.coffeelib.api.permission.annotation.Permission;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The platform-independent half of the command system, driven through the
 * text dispatcher Paper uses - the same definitions the Forge-like platforms
 * compile to Brigadier.
 */
class CommandSystemTest {

    enum Rarity { COMMON, RARE, LEGENDARY }

    /** A sender that records what it was told. */
    static final class FakeSource implements CommandSource {
        final List<String> messages = new ArrayList<>();
        final Set<String> permissions;
        final Object player;
        boolean available = true;

        FakeSource(Object player, String... permissions) {
            this.player = player;
            this.permissions = Set.of(permissions);
        }

        @Override
        public String name() {
            return player != null ? "Steve" : "Server";
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public boolean hasPermission(String permission) {
            return permission.isEmpty() || permissions.contains(permission);
        }

        @Override
        public Object player() {
            return player;
        }

        @Override
        public Object unwrap() {
            return this;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }
    }

    /** Stands in for a platform's player type. */
    static final class FakePlayer {
    }

    @Command(value = "crate", aliases = "crates")
    static final class CrateCommands {
        final List<String> calls = new ArrayList<>();

        @Command("")
        void root(CommandSource source) {
            calls.add("root");
        }

        @Command(value = "give", permission = "crates.give")
        void give(CommandSource source, @Arg("player") @Suggest(provider = "online") String player,
                  @Arg("amount") @Default("1") int amount,
                  @Arg("rarity") @Default("common") Rarity rarity) {
            calls.add("give " + player + " " + amount + " " + rarity);
        }

        @Command("open")
        void open(FakePlayer player, @Arg("crate") @Suggest({"daily", "vote"}) String crate) {
            calls.add("open " + crate);
        }

        @Command("announce")
        void announce(@Arg("message") @Greedy String message) {
            calls.add("announce " + message);
        }

        @Command("toggle")
        void toggle(CommandSource source, @Arg("enabled") boolean enabled) {
            calls.add("toggle " + enabled);
        }

        @Command("fail")
        void fail(CommandSource source) {
            throw new CommandException("No crate called X.");
        }

        @Command("bug")
        void bug(CommandSource source) {
            throw new IllegalStateException("oops");
        }

        Collection<String> online() {
            return List.of("Steve", "Alex");
        }
    }

    /** Commands whose executors return futures, completed by the test. */
    @Command("job")
    static final class JobCommands {
        final CompletableFuture<Void> pending = new CompletableFuture<>();

        @Command("run")
        CompletableFuture<Void> run(CommandSource source) {
            return pending;
        }

        @Command("done")
        CompletableFuture<String> done(CommandSource source) {
            return CompletableFuture.completedFuture("ok");
        }
    }

    /** Stands in for the main thread: what is handed over waits until {@link #drain()}. */
    static final class MainThread implements Executor {
        final ConcurrentLinkedQueue<Runnable> queued = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(Runnable task) {
            queued.add(task);
        }

        void drain() {
            Runnable task;
            while ((task = queued.poll()) != null) {
                task.run();
            }
        }
    }

    static final class PermissionCommands {
        @Command(value = "list", permission = "crates.list")
        @Permission(def = PermissionDefault.EVERYONE, description = "List crates")
        void list(CommandSource source) {
        }

        @Command(value = "give", permission = "crates.give", description = "Give a crate")
        void give(CommandSource source) {
        }

        @Command("help")
        void help(CommandSource source) {
        }
    }

    @Permission(def = PermissionDefault.NOBODY)
    static final class ClassWidePermission {
        @Command(value = "secret", permission = "crates.secret")
        void secret(CommandSource source) {
        }
    }

    static final class PermissionWithoutNode {
        @Command("x")
        @Permission(def = PermissionDefault.EVERYONE)
        void x() {
        }
    }

    private static TextCommandDispatcher dispatcher(Object handler) {
        TextCommandDispatcher dispatcher = new TextCommandDispatcher();
        dispatcher.addAll(CommandScanner.scan("test", handler));
        return dispatcher;
    }

    private static String[] args(String... words) {
        return words;
    }

    // ------------------------------------------------------------------ executing

    @Test
    void theAnnotatedMethodIsTheExecutor() {
        CrateCommands commands = new CrateCommands();
        TextCommandDispatcher dispatcher = dispatcher(commands);
        FakeSource admin = new FakeSource(null, "crates.give");

        assertTrue(dispatcher.execute(admin, "crate", args("give", "Steve", "3", "rare")));
        assertEquals(List.of("give Steve 3 RARE"), commands.calls);
    }

    @Test
    void optionalArgumentsGetTheirDefaults() {
        CrateCommands commands = new CrateCommands();
        dispatcher(commands).execute(new FakeSource(null, "crates.give"), "crate", args("give", "Alex"));
        assertEquals(List.of("give Alex 1 COMMON"), commands.calls);
    }

    @Test
    void theClassPrefixAloneRunsTheEmptyPathMethod() {
        CrateCommands commands = new CrateCommands();
        dispatcher(commands).execute(new FakeSource(null), "crate", args());
        assertEquals(List.of("root"), commands.calls);
    }

    @Test
    void aClassAliasWorksForEverySubcommand() {
        CrateCommands commands = new CrateCommands();
        dispatcher(commands).execute(new FakeSource(null), "crates", args("toggle", "yes"));
        assertEquals(List.of("toggle true"), commands.calls);
    }

    @Test
    void aGreedyArgumentTakesTheRestOfTheLine() {
        CrateCommands commands = new CrateCommands();
        dispatcher(commands).execute(new FakeSource(null), "crate", args("announce", "double", "drops", "now!"));
        assertEquals(List.of("announce double drops now!"), commands.calls);
    }

    @Test
    void literalsAreCaseInsensitiveAndTheNamespacedLabelWorks() {
        CrateCommands commands = new CrateCommands();
        dispatcher(commands).execute(new FakeSource(null), "myplugin:CRATE", args("Toggle", "off"));
        assertEquals(List.of("toggle false"), commands.calls);
    }

    // ------------------------------------------------------------- sender errors

    @Test
    void badInputIsToldToTheSenderAndNothingRuns() {
        CrateCommands commands = new CrateCommands();
        FakeSource admin = new FakeSource(null, "crates.give");

        assertFalse(dispatcher(commands).execute(admin, "crate", args("give", "Steve", "lots")));
        assertTrue(commands.calls.isEmpty());
        assertTrue(admin.messages.get(0).contains("'lots' is not a valid whole number"), admin.messages.toString());
    }

    @Test
    void aWrongEnumValueListsTheValidOnes() {
        FakeSource admin = new FakeSource(null, "crates.give");
        dispatcher(new CrateCommands()).execute(admin, "crate", args("give", "Steve", "1", "mythic"));
        assertTrue(admin.messages.get(0).contains("common, rare, legendary"), admin.messages.toString());
    }

    @Test
    void missingOrExtraArgumentsShowTheUsage() {
        FakeSource source = new FakeSource(null);
        TextCommandDispatcher dispatcher = dispatcher(new CrateCommands());

        dispatcher.execute(source, "crate", args("toggle"));
        dispatcher.execute(source, "crate", args("toggle", "true", "extra"));

        assertEquals("Usage: /crate toggle <enabled>", source.messages.get(0));
        assertEquals("Usage: /crate toggle <enabled>", source.messages.get(1));
    }

    @Test
    void aPermissionIsChecked() {
        CrateCommands commands = new CrateCommands();
        FakeSource guest = new FakeSource(null);

        dispatcher(commands).execute(guest, "crate", args("give", "Steve"));

        assertTrue(commands.calls.isEmpty());
        assertTrue(guest.messages.get(0).contains("do not have permission"));
    }

    @Test
    void aPlayerOnlyCommandRefusesTheConsole() {
        CrateCommands commands = new CrateCommands();
        FakeSource console = new FakeSource(null);
        FakeSource player = new FakeSource(new FakePlayer());

        TextCommandDispatcher dispatcher = dispatcher(commands);
        dispatcher.execute(console, "crate", args("open", "daily"));
        dispatcher.execute(player, "crate", args("open", "daily"));

        assertEquals("Only players can run this command.", console.messages.get(0));
        assertEquals(List.of("open daily"), commands.calls, "the player's run went through");
    }

    @Test
    void aCommandExceptionFromTheExecutorIsShownAsIs() {
        FakeSource source = new FakeSource(null);
        dispatcher(new CrateCommands()).execute(source, "crate", args("fail"));
        assertEquals(List.of("No crate called X."), source.messages);
    }

    @Test
    void aBugInTheExecutorIsLoggedAndTheSenderGetsAGenericMessage() {
        FakeSource source = new FakeSource(null);
        dispatcher(new CrateCommands()).execute(source, "crate", args("bug"));
        assertEquals(List.of(CommandDefinition.INTERNAL_ERROR), source.messages,
                "no exception text leaks to the sender");
    }

    @Test
    void anUnknownSubcommandListsWhatTheSenderCanRun() {
        FakeSource guest = new FakeSource(null);
        dispatcher(new CrateCommands()).execute(guest, "crate", args("nope"));

        // "crate" alone has an executor, so the longest match is that one with an extra
        // argument it does not take: usage of the root, not a crash.
        assertTrue(guest.messages.get(0).startsWith("Usage:"), guest.messages.toString());
    }

    // ---------------------------------------------------------------- completing

    @Test
    void subcommandsCompleteAndRespectPermissions() {
        TextCommandDispatcher dispatcher = dispatcher(new CrateCommands());

        List<String> guest = dispatcher.complete(new FakeSource(null), "crate", args(""));
        List<String> admin = dispatcher.complete(new FakeSource(null, "crates.give"), "crate", args(""));

        assertFalse(guest.contains("give"), "a command you cannot run is not offered");
        assertTrue(admin.contains("give"));
        assertTrue(admin.containsAll(List.of("announce", "open", "toggle")));
        assertEquals(List.of("toggle"), dispatcher.complete(new FakeSource(null), "crate", args("to")));
    }

    @Test
    void argumentsCompleteFromTheirSuggestions() {
        TextCommandDispatcher dispatcher = dispatcher(new CrateCommands());
        FakeSource admin = new FakeSource(null, "crates.give");

        assertEquals(List.of("Alex", "Steve"), dispatcher.complete(admin, "crate", args("give", "")));
        assertEquals(List.of("Steve"), dispatcher.complete(admin, "crate", args("give", "st")),
                "filtered by what was typed, case-insensitively");
        assertEquals(List.of("daily", "vote"), dispatcher.complete(admin, "crate", args("open", "")));
        assertEquals(List.of("legendary", "rare"), dispatcher.complete(admin, "crate", args("give", "Steve", "1", "")).subList(1, 3));
        assertEquals(List.of("false", "true"), dispatcher.complete(admin, "crate", args("toggle", "")));
    }

    @Test
    void numbersOfferNothingRatherThanNoise() {
        FakeSource admin = new FakeSource(null, "crates.give");
        assertTrue(dispatcher(new CrateCommands()).complete(admin, "crate", args("give", "Steve", "")).isEmpty());
    }

    // ------------------------------------------------------- registration errors

    static final class OptionalBeforeRequired {
        @Command("x")
        void x(@Arg("a") @Default("1") int a, @Arg("b") int b) {
        }
    }

    static final class GreedyNotLast {
        @Command("x")
        void x(@Arg("a") @Greedy String a, @Arg("b") String b) {
        }
    }

    static final class UnsupportedType {
        @Command("x")
        void x(CommandSource source, @Arg("uri") java.net.URI uri) {
        }
    }

    static final class MissingProvider {
        @Command("x")
        void x(@Arg("a") @Suggest(provider = "nowhere") String a) {
        }
    }

    static final class BadDefault {
        @Command("x")
        void x(@Arg("a") @Default("many") int a) {
        }
    }

    static final class Duplicate {
        @Command("x")
        void one() {
        }

        @Command("x")
        void two() {
        }
    }

    static final class NoCommands {
    }

    static final class FirstUuidArgument {
        java.util.UUID received;
        @Command("uuid")
        void uuid(java.util.UUID id) { received = id; }
    }

    static final class FirstCustomArgument {
        java.net.URI received;
        @Command("url")
        void url(java.net.URI uri) { received = uri; }
    }

    @Test
    void aKnownCustomTypeInFirstPositionIsNotMistakenForTheSender() {
        FirstUuidArgument handler = new FirstUuidArgument();
        TextCommandDispatcher dispatcher = dispatcher(handler);
        java.util.UUID id = java.util.UUID.randomUUID();
        assertTrue(dispatcher.execute(new FakeSource(null), "uuid", args(id.toString())));
        assertEquals(id, handler.received);
    }

    @Test
    void customFirstArgumentFromRegisteredParserUsesTheParser() {
        FirstCustomArgument handler = new FirstCustomArgument();
        ArgumentTypes types = ArgumentTypes.withDefaults();
        types.register(java.net.URI.class, (source, input) -> java.net.URI.create(input));
        TextCommandDispatcher dispatcher = new TextCommandDispatcher();
        dispatcher.addAll(CommandScanner.scan("test", handler, types));
        assertTrue(dispatcher.execute(new FakeSource(null), "url", args("https://example.com")));
        assertEquals(java.net.URI.create("https://example.com"), handler.received);
    }

    static final class InvalidSuggestionProviderType {
        @Command("wrong")
        void wrong(@Arg("item") @Suggest(provider = "notStrings") String item) {}
        Collection<Integer> notStrings() { return List.of(1, 2, 3); }
    }

    @Test
    void aSuggestionProviderReturningNumbersFailsAtRegistration() {
        assertRejected(new InvalidSuggestionProviderType(), "instead of Collection<String>");
    }

    static final class OrSelfFollowedByOptional {
        @Command("heal")
        void heal(@OrSelf FakePlayer target, @Arg("reason") @Default("none") String reason) {
        }
    }

    static final class InvalidWordCount {
        @Command("locate")
        void locate(CommandSource source, @Arg("spot") java.net.URI spot) {
        }
    }

    private static ArgumentTypes fakePlayerTypes() {
        ArgumentTypes types = ArgumentTypes.withDefaults();
        types.markPlayer(FakePlayer.class);
        types.register(FakePlayer.class, new CommandArgument<>() {
            @Override
            public FakePlayer parse(CommandSource source, String input) {
                return new FakePlayer();
            }
        });
        return types;
    }

    @Test
    void orSelfMustBeTheLastParameterEvenWhenTheNextOneIsOptional() {
        CoffeeLibException e = assertThrows(CoffeeLibException.class,
                () -> CommandScanner.scan("test", new OrSelfFollowedByOptional(), fakePlayerTypes()));
        assertTrue(e.getMessage().contains("must be the last argument"));
        assertTrue(e.getMessage().contains("#heal"));
    }

    @Test
    void invalidMultiwordParserFailsAtRegistration() {
        ArgumentTypes types = ArgumentTypes.withDefaults();
        types.register(java.net.URI.class, new CommandArgument<>() {
            @Override
            public java.net.URI parse(CommandSource source, String input) {
                return java.net.URI.create(input);
            }
            @Override
            public int words() { return 0; }
        });
        CoffeeLibException e = assertThrows(CoffeeLibException.class,
                () -> CommandScanner.scan("test", new InvalidWordCount(), types));
        assertTrue(e.getMessage().contains("invalid words() count"));
    }

    static final class GodCommand {
        FakePlayer last;
        @Command(value = "god", permission = "god.use")
        void god(CommandSource sender, @OrSelf(others = "god.others") FakePlayer player) {
            last = player;
        }
    }

    @Test
    void orSelfUsesSenderAndChecksPermissionForExplicitTargets() {
        FakePlayer self = new FakePlayer();
        FakePlayer other = new FakePlayer();
        GodCommand commands = new GodCommand();
        ArgumentTypes types = ArgumentTypes.withDefaults();
        types.markPlayer(FakePlayer.class);
        types.register(FakePlayer.class, new CommandArgument<>() {
            @Override
            public FakePlayer parse(CommandSource source, String input) {
                return "other".equals(input) ? other : self;
            }
            @Override
            public Collection<String> suggest(net.loyalnetwork.coffeelib.api.command.SuggestContext context) {
                return List.of("other", "self");
            }
        });
        TextCommandDispatcher dispatcher = new TextCommandDispatcher();
        dispatcher.addAll(CommandScanner.scan("test", commands, types));
        FakeSource user = new FakeSource(self, "god.use");
        assertTrue(dispatcher.complete(user, "god", args("")).isEmpty(),
                "cannot reveal names of players without the others permission");
        assertTrue(dispatcher.execute(user, "god", args()));
        assertSame(self, commands.last);
        assertFalse(dispatcher.execute(user, "god", args("other")));
        assertSame(self, commands.last);
        assertTrue(user.messages.get(user.messages.size() - 1).contains("other players"));
        FakeSource privileged = new FakeSource(self, "god.use", "god.others");
        assertEquals(List.of("other", "self"), dispatcher.complete(privileged, "god", args("")));
        assertTrue(dispatcher.execute(privileged, "god", args("other")));
        assertSame(other, commands.last);
    }


    @Test
    void mistakesInAHandlerFailAtRegistrationNamingTheMethod() {
        assertRejected(new OptionalBeforeRequired(), "optional arguments must be last");
        assertRejected(new GreedyNotLast(), "must be the last argument");
        assertRejected(new UnsupportedType(), "Unsupported type URI");
        assertRejected(new MissingProvider(), "names no method");
        assertRejected(new BadDefault(), "'many' is not a valid whole number");
        assertRejected(new Duplicate(), "More than one @Command method");
        assertRejected(new NoCommands(), "declares no @Command methods");
    }

    private static void assertRejected(Object handler, String expected) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> CommandScanner.scan("test", handler));
        assertTrue(e instanceof CoffeeLibException, e.getClass().getName());
        assertTrue(e.getMessage().contains(expected), handler.getClass().getSimpleName() + ": " + e.getMessage());
    }

    @Test
    void twoHandlersCannotClaimTheSamePath() {
        TextCommandDispatcher dispatcher = dispatcher(new CrateCommands());
        assertThrows(CoffeeLibException.class,
                () -> dispatcher.addAll(CommandScanner.scan("other", new CrateCommands())));
    }

    @Test
    void aFailedCommandRegistrationPreflightDoesNotChangeTheDispatcher() {
        TextCommandDispatcher dispatcher = dispatcher(new CrateCommands());
        int count = dispatcher.definitions().size();
        List<CommandDefinition> repeated = CommandScanner.scan("new-owner", new CrateCommands());

        assertThrows(CoffeeLibException.class, () -> dispatcher.validateAddAll(repeated));
        assertEquals(count, dispatcher.definitions().size());
    }

    static final class ReplacingCommands {
        @Command(value = "warp", replaces = true)
        void warp() {}
    }

    static final class OtherCommands {
        @Command("warp")
        void warp() {}
    }

    @Test
    void disabledOwnerDoesNotKeepACommandReplacementClaimForever() {
        CommandRoots.clear();
        try {
            var original = CommandScanner.scan("first", new ReplacingCommands());
            var next = CommandScanner.scan("next", new OtherCommands());
            CommandRoots.claim("first", original);
            assertThrows(CoffeeLibException.class, () -> CommandRoots.claim("next", next));

            CommandRoots.releaseOwner("first");
            CommandRoots.claim("next", next);
            assertFalse(CommandRoots.isReplaced("warp"));
        } finally {
            CommandRoots.clear();
        }
    }

    @Test
    void theUsageLineDescribesTheArguments() {
        CommandDefinition give = CommandScanner.scan("test", new CrateCommands()).stream()
                .filter(definition -> definition.path().equals(List.of("crate", "give")))
                .findFirst().orElseThrow();
        assertEquals("/crate give <player> [amount] [rarity]", give.usage());
        assertEquals(2, give.permissionLevel(), "a permission node implies operator level on Forge-likes");
    }

    // ------------------------------------------------------------------ asynchronous executors

    private static TextCommandDispatcher dispatcher(Object handler, Executor mainThread) {
        TextCommandDispatcher dispatcher = new TextCommandDispatcher(mainThread);
        dispatcher.addAll(CommandScanner.scan("test", handler));
        return dispatcher;
    }

    @Test
    void aFutureThatSucceedsSaysNothingMore() {
        JobCommands jobs = new JobCommands();
        MainThread main = new MainThread();
        FakeSource source = new FakeSource(null);

        assertTrue(dispatcher(jobs, main).execute(source, "job", args("run")), "it counts as started");
        jobs.pending.complete(null);
        main.drain();

        assertTrue(source.messages.isEmpty());
    }

    @Test
    void aCommandExceptionInsideTheFutureReachesTheSenderOnTheMainThread() {
        JobCommands jobs = new JobCommands();
        MainThread main = new MainThread();
        FakeSource source = new FakeSource(null);
        dispatcher(jobs, main).execute(source, "job", args("run"));

        // Thrown inside a later stage, the way a mod's thenAccept would: wrapped in a CompletionException.
        jobs.pending.completeExceptionally(new java.util.concurrent.CompletionException(
                new CommandException("Nothing to go back to.")));
        assertTrue(source.messages.isEmpty(), "not before the main thread runs it");

        main.drain();
        assertEquals(List.of("Nothing to go back to."), source.messages);
    }

    @Test
    void aBugInsideTheFutureIsLoggedAndTheSenderGetsTheGenericMessage() {
        JobCommands jobs = new JobCommands();
        MainThread main = new MainThread();
        FakeSource source = new FakeSource(null);
        dispatcher(jobs, main).execute(source, "job", args("run"));

        jobs.pending.completeExceptionally(new IllegalStateException("database closed"));
        main.drain();

        assertEquals(List.of(CommandDefinition.INTERNAL_ERROR), source.messages);
    }

    @Test
    void aSenderWhoLeftIsNotToldAnything() {
        JobCommands jobs = new JobCommands();
        MainThread main = new MainThread();
        FakeSource source = new FakeSource(new FakePlayer());
        dispatcher(jobs, main).execute(source, "job", args("run"));

        source.available = false;
        jobs.pending.completeExceptionally(new CommandException("Too late."));
        main.drain();

        assertTrue(source.messages.isEmpty());
    }

    @Test
    void anAlreadyCompletedFutureAndNonFutureResultsAreFine() {
        FakeSource source = new FakeSource(null);
        assertTrue(dispatcher(new JobCommands(), new MainThread()).execute(source, "job", args("done")));
        assertTrue(source.messages.isEmpty());
    }

    // ------------------------------------------------------------------ permission defaults

    private static CommandDefinition definition(Object handler, String root) {
        return CommandScanner.scan("test", handler).stream()
                .filter(definition -> definition.root().equals(root))
                .findFirst().orElseThrow();
    }

    @Test
    void permissionOnTheMethodSetsTheNodeDefaultAndDescription() {
        CommandDefinition list = definition(new PermissionCommands(), "list");
        assertEquals(PermissionDefault.EVERYONE, list.permissionDefault());
        assertEquals("List crates", list.permissionDescription());
    }

    @Test
    void withoutPermissionTheOpLevelDecidesAndTheCommandDescriptionIsUsed() {
        CommandDefinition give = definition(new PermissionCommands(), "give");
        assertEquals(PermissionDefault.OPERATORS, give.permissionDefault(), "a node implies op level 2");
        assertEquals("Give a crate", give.permissionDescription());
        assertEquals(PermissionDefault.EVERYONE, definition(new PermissionCommands(), "help").permissionDefault(),
                "no node, level 0: everyone");
    }

    @Test
    void permissionOnTheClassAppliesToItsMethods() {
        assertEquals(PermissionDefault.NOBODY, definition(new ClassWidePermission(), "secret").permissionDefault());
    }

    @Test
    void permissionWithoutANodeIsRefusedAtRegistration() {
        assertRejected(new PermissionWithoutNode(), "has no permission");
    }
}
