package net.loyalnetwork.coffeelib.config.file;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public final class ConfigFileHandle {

    private final File file;
    private final List<ConfigEntry> entries;
    private final YamlNode defaultTree;
    private FileConfiguration config;

    public ConfigFileHandle(File dataFolder, String fileName, List<ConfigEntry> entries) {
        this.file = new File(dataFolder, fileName);
        this.entries = entries;
        this.defaultTree = YamlTreeBuilder.build(entries);
    }

    public void load() {
        boolean exists = file.exists();
        if (!exists) {
            createFile();
        }
        config = YamlConfiguration.loadConfiguration(file);
        addMissingKeys();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration config() {
        return config;
    }

    public File file() {
        return file;
    }

    public String fileName() {
        return file.getName();
    }

    private void createFile() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String yaml = CommentYamlWriter.write(defaultTree);
            Files.writeString(file.toPath(), yaml);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config file: " + file, e);
        }
    }

    private void addMissingKeys() {
        boolean modified = false;
        for (ConfigEntry entry : entries) {
            if (!config.contains(entry.path())) {
                config.set(entry.path(), entry.value());
                modified = true;
            }
        }
        if (modified) {
            String yaml = CommentYamlWriter.write(YamlTreeBuilder.build(
                    entries.stream()
                            .map(e -> new ConfigEntry(
                                    e.path(),
                                    config.get(e.path()),
                                    e.comments()
                            ))
                            .toList()
            ));
            try {
                Files.writeString(file.toPath(), yaml);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update config file: " + file, e);
            }
        }
    }
}
