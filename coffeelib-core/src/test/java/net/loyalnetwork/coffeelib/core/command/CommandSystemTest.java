package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.annotation.Arg;
import net.loyalnetwork.coffeelib.api.command.annotation.Command;
import net.loyalnetwork.coffeelib.api.command.annotation.Default;
import net.loyalnetwork.coffeelib.api.command.annotation.Greedy;
import net.loyalnetwork.coffeelib.api.command.annotation.Suggest;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        void x(CommandSource source, @Arg("when") java.time.Duration when) {
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

    @Test
    void mistakesInAHandlerFailAtRegistrationNamingTheMethod() {
        assertRejected(new OptionalBeforeRequired(), "optional arguments must be last");
        assertRejected(new GreedyNotLast(), "must be the last argument");
        assertRejected(new UnsupportedType(), "Unsupported type Duration");
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
    void theUsageLineDescribesTheArguments() {
        CommandDefinition give = CommandScanner.scan("test", new CrateCommands()).stream()
                .filter(definition -> definition.path().equals(List.of("crate", "give")))
                .findFirst().orElseThrow();
        assertEquals("/crate give <player> [amount] [rarity]", give.usage());
        assertEquals(2, give.permissionLevel(), "a permission node implies operator level on Forge-likes");
    }
}
