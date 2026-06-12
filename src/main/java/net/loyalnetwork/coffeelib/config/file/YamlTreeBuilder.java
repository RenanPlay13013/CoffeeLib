package net.loyalnetwork.coffeelib.config.file;

import java.util.List;

public final class YamlTreeBuilder {

    private YamlTreeBuilder() {
    }

    public static YamlNode build(List<ConfigEntry> entries) {

        YamlNode root = new YamlNode();

        for (ConfigEntry entry : entries) {

            String[] parts = entry.path().split("\\.");

            YamlNode current = root;

            for (int i = 0; i < parts.length; i++) {

                current = current.getOrCreateChild(parts[i]);

                if (i == parts.length - 1) {

                    current.value(entry.value());

                    if (entry.comments() != null) {
                        current.addComments(entry.comments());
                    }
                }
            }
        }

        return root;
    }
}