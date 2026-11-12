package net.loyalnetwork.coffeelib.core.config;

import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The name in {@code @ConfigFile} is the one string a mod author controls that
 * turns into a path, so what it may and may not do is worth pinning down.
 */
class ConfigFileNamesTest {

    private static final Path BASE = Path.of("/server/config").toAbsolutePath();

    @Test
    void aPlainNameBecomesAFileInTheBaseDirectory() {
        assertEquals(BASE.resolve("server.yml"), ConfigFileNames.resolve(BASE, "server", "yml"));
    }

    @Test
    void aSubdirectoryIsSupported() {
        // Deliberately allowed: a mod with several configs can group them.
        assertEquals(BASE.resolve("mymod").resolve("server.yml"),
                ConfigFileNames.resolve(BASE, "mymod/server", "yml"));
    }

    @Test
    void escapingTheBaseDirectoryIsRejected() {
        // This used to resolve, quietly writing outside the folder the platform gave us.
        ConfigException e = assertThrows(ConfigException.class,
                () -> ConfigFileNames.resolve(BASE, "../mymod/server", "yml"));
        assertTrue(e.getMessage().contains("stay inside the config directory"));
        assertTrue(e.getMessage().contains("pass that directory"), "should point at the supported way");

        assertThrows(ConfigException.class, () -> ConfigFileNames.resolve(BASE, "./server", "yml"));
        assertThrows(ConfigException.class, () -> ConfigFileNames.resolve(BASE, "mymod/../../server", "yml"));
    }

    @Test
    void anAbsoluteNameIsRejected() {
        assertThrows(ConfigException.class, () -> ConfigFileNames.resolve(BASE, "/etc/server", "yml"));
        assertThrows(ConfigException.class, () -> ConfigFileNames.resolve(BASE, "C:/temp/server", "yml"));
    }

    @Test
    void aBackslashIsRejectedSoNamesMeanTheSameEverywhere() {
        ConfigException e = assertThrows(ConfigException.class,
                () -> ConfigFileNames.resolve(BASE, "mymod\\server", "yml"));
        assertTrue(e.getMessage().contains("'/'"));
    }

    @Test
    void blankAndMalformedNamesAreRejected() {
        assertThrows(ConfigException.class, () -> ConfigFileNames.resolve(BASE, "", "yml"));
        assertThrows(ConfigException.class, () -> ConfigFileNames.resolve(BASE, "  ", "yml"));
        assertThrows(ConfigException.class, () -> ConfigFileNames.resolve(BASE, "mymod/", "yml"));
        assertThrows(ConfigException.class, () -> ConfigFileNames.resolve(BASE, "mymod//server", "yml"));
    }

    @Test
    void aNameAlreadyCarryingTheExtensionIsRejected() {
        // Otherwise the backend appends its own and you get server.yml.yml.
        ConfigException e = assertThrows(ConfigException.class,
                () -> ConfigFileNames.resolve(BASE, "server.yml", "yml"));
        assertTrue(e.getMessage().contains("drop the extension"));

        // A dot that is not the backend's extension is left alone.
        assertEquals(BASE.resolve("server.v2.yml"), ConfigFileNames.resolve(BASE, "server.v2", "yml"));
    }

    @Test
    void theIdentifierIsTheRelativePathWithForwardSlashes() {
        assertEquals("server.yml", ConfigFileNames.identifier(BASE, BASE.resolve("server.yml")));
        assertEquals("mymod/server.yml",
                ConfigFileNames.identifier(BASE, BASE.resolve("mymod").resolve("server.yml")));
    }

    @Test
    void withExtensionAppendsOnlyWhenItIsMissing() {
        assertEquals("server.toml", ConfigFileNames.withExtension("server", "toml"));
        assertEquals("mymod-common.toml", ConfigFileNames.withExtension("mymod-common.toml", "toml"));
        assertEquals("mymod/server.toml", ConfigFileNames.withExtension("mymod/server", "toml"));

        // Same validation as the managed path: the native one must not escape either.
        assertThrows(ConfigException.class, () -> ConfigFileNames.withExtension("../server", "toml"));
    }
}
