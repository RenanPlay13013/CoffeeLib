package net.loyalnetwork.coffeelib.core.config;

import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigException;

import java.nio.file.Path;

/**
 * Turns the name in {@link ConfigFile#value()} into a file under a base
 * directory, and says no to the names that should not resolve at all.
 * <p>
 * The name is deliberately more than a file name: {@code "mymod/server"} puts
 * the config in a subdirectory, which is useful for a mod with several files
 * and is created on write. What it is not allowed to be is a way out of the
 * base directory. {@code "../mymod/server"} used to resolve, quietly writing
 * outside the folder the platform handed CoffeeLib — a mod could land anywhere
 * on disk by accident, and an operator looking in {@code config/} would not
 * find the file. Pointing somewhere else is a legitimate thing to want, but it
 * is the owner's call to make through
 * {@code CoffeeLib.forMod(configDirectory)}, explicit at the call site, rather
 * than something a string in an annotation does on the side.
 */
public final class ConfigFileNames {

    private ConfigFileNames() {
    }

    /**
     * Resolves {@code name} under {@code baseDirectory}, appending
     * {@code extension}.
     *
     * @throws ConfigException if the name is blank, absolute, uses {@code \},
     *                         contains a {@code .} or {@code ..} segment, has
     *                         an empty segment, or already carries the
     *                         extension the backend is about to add
     */
    public static Path resolve(Path baseDirectory, String name, String extension) {
        validate(name, extension);
        return baseDirectory.resolve(name + "." + extension).normalize();
    }

    /**
     * How a managed config is named to an operator: its path relative to the
     * base directory, with {@code /} separators on every platform, so a config
     * in a subdirectory reads as {@code "mymod/server.yml"} rather than
     * collapsing to {@code "server.yml"} and colliding with another mod's file
     * of the same name.
     */
    public static String identifier(Path baseDirectory, Path file) {
        Path relative;
        try {
            relative = baseDirectory.toAbsolutePath().normalize()
                    .relativize(file.toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            // Different roots (another drive on Windows); the file name is all we can say.
            return file.getFileName().toString();
        }
        return relative.toString().replace('\\', '/');
    }

    /**
     * Appends {@code extension} unless {@code name} already ends with it.
     * <p>
     * For the platforms whose own config system takes a complete file name
     * rather than a stem: Forge and NeoForge resolve exactly the string handed
     * to {@code registerConfig}, so {@code @ConfigFile("server")} used to
     * produce a file literally called {@code config/server}, with no
     * extension, next to the {@code .toml} files every other mod writes. Mods
     * that already spell it out as {@code "mymod-common.toml"} keep working.
     */
    public static String withExtension(String name, String extension) {
        validate(name, null);
        return name.endsWith("." + extension) ? name : name + "." + extension;
    }

    /**
     * @param extension the extension about to be appended, or {@code null} to
     *                  skip the "name already has it" check
     */
    private static void validate(String name, String extension) {
        if (name == null || name.isBlank()) {
            throw new ConfigException("@ConfigFile name must not be blank");
        }
        if (name.indexOf('\\') >= 0) {
            throw new ConfigException("@ConfigFile name '" + name
                    + "' must use '/' for subdirectories, not '\\', so it means the same on every platform");
        }
        if (name.startsWith("/") || Path.of(name).isAbsolute()) {
            throw new ConfigException("@ConfigFile name '" + name + "' must be relative to the config directory. "
                    + "To put configs somewhere else, pass that directory when you create the manager.");
        }
        if (name.endsWith("/")) {
            throw new ConfigException("@ConfigFile name '" + name + "' must name a file, not a directory");
        }

        for (String segment : name.split("/")) {
            if (segment.isEmpty()) {
                throw new ConfigException("@ConfigFile name '" + name + "' has an empty path segment");
            }
            if (segment.equals(".") || segment.equals("..")) {
                throw new ConfigException("@ConfigFile name '" + name + "' must stay inside the config directory. "
                        + "To put configs somewhere else, pass that directory when you create the manager.");
            }
        }

        if (extension != null && name.endsWith("." + extension)) {
            throw new ConfigException("@ConfigFile name '" + name + "' already ends with '." + extension
                    + "', which the backend appends - drop the extension from the annotation");
        }
    }
}
