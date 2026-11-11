package net.loyalnetwork.coffeelib.api.config;

/**
 * Which on-disk format backs a config class, declared per class through
 * {@link net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile#format()}.
 * <p>
 * {@link #NATIVE} means "whatever the platform itself uses", and is the only
 * value whose behavior differs per platform:
 * <ul>
 *   <li><b>Paper</b> — YAML through Configurate, loaded and validated by
 *       CoffeeLib itself. {@code NATIVE} and {@link #YAML} are the same thing
 *       here.</li>
 *   <li><b>Forge / NeoForge</b> — the loader's own config system
 *       ({@code ForgeConfigSpec} / {@code ModConfigSpec}, TOML on disk). The
 *       config becomes a real registered {@code ModConfig}, so the loader —
 *       not CoffeeLib — owns parsing, validation and <i>when</i> fields are
 *       populated (only once it fires its {@code ModConfigEvent}, after mod
 *       construction).</li>
 * </ul>
 * Every other value is a format CoffeeLib owns end to end: the file is read,
 * validated and written by {@code core}, synchronously inside
 * {@link ConfigManager#load}, on every platform alike. On Forge-like
 * platforms that is the whole point of choosing one — the mod-loading
 * lifecycle stops being involved, and reloading becomes something you (or an
 * operator, through {@code /coffeelib config}) can trigger at any time via
 * {@link ConfigReloadService}.
 */
public enum ConfigFormat {

    /** The platform's own format and config system. See the type javadoc. */
    NATIVE("", false),

    /** YAML, via {@code configurate-yaml}. Comments supported. */
    YAML("yml", true),

    /** JSON, via {@code configurate-gson}. Comments are dropped — JSON has none. */
    JSON("json", true),

    /** HOCON, via {@code configurate-hocon}. Comments supported. */
    HOCON("conf", true);

    private final String fileExtension;
    private final boolean managedByCoffeeLib;

    ConfigFormat(String fileExtension, boolean managedByCoffeeLib) {
        this.fileExtension = fileExtension;
        this.managedByCoffeeLib = managedByCoffeeLib;
    }

    /**
     * File extension without the leading dot, or an empty string for
     * {@link #NATIVE} (whose extension is the platform's business).
     */
    public String fileExtension() {
        return fileExtension;
    }

    /**
     * {@code true} when CoffeeLib itself reads, validates and writes the
     * file — i.e. everything except {@link #NATIVE}. These are the formats
     * that support {@link ConfigReloadService} on every platform.
     */
    public boolean isManagedByCoffeeLib() {
        return managedByCoffeeLib;
    }
}
