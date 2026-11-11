package net.loyalnetwork.coffeelib.core.config.format;

import net.loyalnetwork.coffeelib.api.config.ConfigBackend;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigLoadException;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigSaveException;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.gson.GsonConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * JSON backend on {@code configurate-gson}.
 * <p>
 * JSON has no comment syntax, so {@code @Comment} values are silently
 * dropped, which the {@link ConfigBackend} contract explicitly allows
 * (comment support is best-effort, not something every backend must honor).
 * Everything else round-trips: nested sections become objects, lists become
 * arrays.
 */
public final class JsonConfigBackend implements ConfigBackend {

    @Override
    public String fileExtension() {
        return "json";
    }

    @Override
    public Map<String, Object> read(Path file) {
        try {
            return ConfigurateSupport.rootToJava(loader(file).load());
        } catch (IOException e) {
            throw new ConfigLoadException("Could not read " + file, e);
        }
    }

    @Override
    public void write(Path file, Map<String, Object> values, Map<String, String> comments) {
        try {
            BasicConfigurationNode root = BasicConfigurationNode.root();
            root.raw(values);

            Files.createDirectories(file.getParent());
            loader(file).save(root);
        } catch (IOException e) {
            throw new ConfigSaveException("Could not write " + file, e);
        }
    }

    private static GsonConfigurationLoader loader(Path file) {
        return GsonConfigurationLoader.builder()
                .path(file)
                .indent(2)
                .build();
    }
}
