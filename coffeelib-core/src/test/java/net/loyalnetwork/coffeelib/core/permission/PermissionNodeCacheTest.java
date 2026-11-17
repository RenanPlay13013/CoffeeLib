package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The file that carries late permission nodes across a restart, and the chain step that uses them. */
class PermissionNodeCacheTest {

    private static PermissionNodes.NodeInfo node(String name, String owner) {
        return new PermissionNodes.NodeInfo(name, owner, PermissionDefault.OPERATORS, 4, "Reset the crates");
    }

    @Test
    void theFileStartsWithAHeaderSayingWhyItExists(@TempDir Path dir) throws IOException {
        PermissionNodeCache cache = new PermissionNodeCache(dir.resolve("coffeelib").resolve("permission-nodes.cache"));

        cache.record(node("crates.reset", "cratesmod"));

        String content = Files.readString(cache.file());
        assertTrue(content.startsWith("# CoffeeLib permission node cache"), content);
        assertTrue(content.contains("only let mods register permission nodes"), "the reason is in the header");
        assertTrue(content.contains("Safe to delete"));
        assertTrue(content.contains("crates.reset\tOPERATORS\t4\tcratesmod\tReset the crates"), content);
    }

    @Test
    void entriesRoundTripAndAreNotDuplicated(@TempDir Path dir) {
        PermissionNodeCache cache = new PermissionNodeCache(dir.resolve("permission-nodes.cache"));

        cache.record(node("crates.reset", "cratesmod"));
        cache.record(node("crates.reset", "cratesmod"));
        cache.record(new PermissionNodes.NodeInfo("crates.open", "cratesmod", PermissionDefault.EVERYONE, 2, ""));

        Map<String, PermissionNodes.NodeInfo> read = cache.read();
        assertEquals(2, read.size(), "declaring late twice records it once");
        assertEquals(PermissionDefault.EVERYONE, read.get("crates.open").def());
        assertEquals(4, read.get("crates.reset").operatorLevel());
    }

    @Test
    void reRecordingANodeWithOtherValuesReplacesItsEntry(@TempDir Path dir) throws IOException {
        PermissionNodeCache cache = new PermissionNodeCache(dir.resolve("permission-nodes.cache"));
        cache.record(node("crates.reset", "cratesmod"));
        cache.record(new PermissionNodes.NodeInfo("crates.open", "cratesmod", PermissionDefault.EVERYONE, 2, ""));

        cache.record(new PermissionNodes.NodeInfo("crates.reset", "cratesmod", PermissionDefault.NOBODY, 2, "Locked"));

        Map<String, PermissionNodes.NodeInfo> read = cache.read();
        assertEquals(PermissionDefault.NOBODY, read.get("crates.reset").def());
        assertEquals("Locked", read.get("crates.reset").description());
        assertEquals(2, read.size(), "the other entry is kept");
        assertTrue(Files.readString(cache.file()).startsWith("# CoffeeLib"));
    }

    @Test
    void aDescriptionWithATabIsNotRewrittenOnEveryRecord(@TempDir Path dir) throws IOException {
        PermissionNodeCache cache = new PermissionNodeCache(dir.resolve("permission-nodes.cache"));
        PermissionNodes.NodeInfo info = new PermissionNodes.NodeInfo("crates.give", "cratesmod",
                PermissionDefault.OPERATORS, 2, "Give	a crate");
        cache.record(info);
        Files.setLastModifiedTime(cache.file(), java.nio.file.attribute.FileTime.fromMillis(0));

        cache.record(info);

        assertEquals(0, Files.getLastModifiedTime(cache.file()).toMillis(), "unchanged, so not rewritten");
        assertEquals("Give a crate", cache.read().get("crates.give").description());
    }

    @Test
    void entriesOfModsThatAreGoneArePruned(@TempDir Path dir) throws IOException {
        PermissionNodeCache cache = new PermissionNodeCache(dir.resolve("permission-nodes.cache"));
        cache.record(node("crates.reset", "cratesmod"));
        cache.record(node("old.thing", "removedmod"));

        List<PermissionNodes.NodeInfo> kept = cache.prune(info -> !info.ownerId().equals("removedmod"));

        assertEquals(1, kept.size());
        assertEquals("crates.reset", kept.get(0).node());
        assertFalse(Files.readString(cache.file()).contains("old.thing"));
        assertTrue(Files.readString(cache.file()).startsWith("# CoffeeLib"), "the header survives a rewrite");

        cache.prune(info -> false);
        assertFalse(Files.exists(cache.file()), "nothing left, no file");
    }

    @Test
    void aMissingOrDamagedFileIsNotFatal(@TempDir Path dir) throws IOException {
        PermissionNodeCache cache = new PermissionNodeCache(dir.resolve("permission-nodes.cache"));
        assertTrue(cache.read().isEmpty());

        Files.writeString(cache.file(), "# header\n\nnot a line\ncrates.reset\tBOGUS\t2\tmod\tx\n"
                + "crates.ok\tNOBODY\t2\tmod\tfine\nBad Node\tNOBODY\t2\tmod\tx\n");

        Map<String, PermissionNodes.NodeInfo> read = cache.read();
        assertEquals(List.of("crates.ok"), List.copyOf(read.keySet()), "malformed lines are skipped, not fatal");
    }

    @Test
    void thePlatformApiAnswersBeforeLuckPermsAndTheDefault() {
        PermissionNodes nodes = new PermissionNodes();
        nodes.declare("mod", "crates.give", PermissionDefault.NOBODY, "");
        UUID player = UUID.randomUUID();
        PermissionPlatform platform = new PermissionPlatform() {
            @Override
            public UUID playerId(Object subject) {
                return player;
            }

            @Override
            public boolean isOperator(Object subject, int level) {
                return false;
            }
        };

        PermissionSource platformApi = (subject, id, node) -> true;
        PermissionSource noAnswer = (subject, id, node) -> null;
        PermissionSource luckPermsSaysNo = (subject, id, node) -> false;
        PermissionSource luckPermsSaysYes = (subject, id, node) -> true;

        PermissionResolver apiSaysYes = new PermissionResolver(nodes, platform, platformApi, luckPermsSaysNo);
        PermissionResolver apiHasNoAnswer = new PermissionResolver(nodes, platform, noAnswer, luckPermsSaysYes);
        PermissionResolver nothingAnswers = new PermissionResolver(nodes, platform, noAnswer, noAnswer);

        assertTrue(apiSaysYes.has("player", "crates.give"), "the handler decides first");
        assertTrue(apiHasNoAnswer.has("player", "crates.give"), "a late node: LuckPerms directly");
        assertFalse(nothingAnswers.has("player", "crates.give"), "then the default");
    }

    @Test
    void aDeclaredOperatorLevelIsWhatOperatorsMean() {
        PermissionNodes nodes = new PermissionNodes();
        nodes.declare("mod", "server.reset", PermissionDefault.OPERATORS, 4, "");
        PermissionPlatform level2 = new PermissionPlatform() {
            @Override
            public UUID playerId(Object subject) {
                return UUID.randomUUID();
            }

            @Override
            public boolean isOperator(Object subject, int level) {
                return level <= 2;
            }
        };
        PermissionResolver resolver = new PermissionResolver(nodes, level2);

        assertFalse(resolver.has("op2", "server.reset"), "an op at level 2 is not enough for a level-4 node");
    }

    @Test
    void listenersHearNewDeclarationsButNotIgnoredOnes() {
        PermissionNodes nodes = new PermissionNodes();
        List<String> heard = new java.util.ArrayList<>();
        nodes.addListener(info -> heard.add(info.node()));

        nodes.declare("mod", "crates.give", PermissionDefault.OPERATORS, "");
        nodes.declareIfAbsent("mod", "crates.give", PermissionDefault.EVERYONE, "ignored, already declared");
        nodes.declareIfAbsent("mod", "crates.open", PermissionDefault.EVERYONE, "");

        assertEquals(List.of("crates.give", "crates.open"), heard);
    }
}
