package net.loyalnetwork.coffeelib.core.config.format;

import net.loyalnetwork.coffeelib.api.config.ConfigBackend;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigLoadException;
import net.loyalnetwork.coffeelib.api.config.exception.ConfigSaveException;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * HOCON backend on {@code configurate-hocon}.
 * <p>
 * Unlike the YAML backend, HOCON's writer emits node comments itself, so
 * {@code @Comment}s are attached to their nodes (see
 * {@code ConfigurateSupport.applyComments}) and no post-pass over the emitted
 * text is needed.
 */
public final class HoconConfigBackend implements ConfigBackend {

    @Override
    public String fileExtension() {
        return "conf";
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
            CommentedConfigurationNode root = CommentedConfigurationNode.root();
            root.raw(values);
            ConfigurateSupport.applyComments(root, comments);

            Files.createDirectories(file.getParent());
            loader(file).save(root);
        } catch (IOException e) {
            throw new ConfigSaveException("Could not write " + file, e);
        }
    }

    private static HoconConfigurationLoader loader(Path file) {
        return HoconConfigurationLoader.builder()
                .path(file)
                .build();
    }
}
