package net.loyalnetwork.coffeelib.core.text;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;
import net.loyalnetwork.coffeelib.api.config.annotation.ConfigFile;
import net.loyalnetwork.coffeelib.api.text.Messages;
import net.loyalnetwork.coffeelib.core.config.reload.ConfigReloadRegistry;
import net.loyalnetwork.coffeelib.core.config.reload.ManagedConfigService;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Application service that opens and registers a reloadable message catalog. */
public final class MessageCatalogs {
    private MessageCatalogs() { }

    /**
     * Uses YAML when the catalog declares the platform-native format; this
     * makes message files synchronous, reloadable, and consistent on Forge,
     * NeoForge and Paper. An explicit JSON/HOCON format is respected.
     */
    public static <T> Messages<T> open(String owner, Path directory, Class<T> type,
                                       BiConsumer<Object, String> delivery) {
        Objects.requireNonNull(type, "type");
        if (!type.isAnnotationPresent(ConfigFile.class)) {
            throw new IllegalArgumentException("Message catalog " + type.getName() + " must use @ConfigFile");
        }
        ManagedConfigService service = new ManagedConfigService(owner, directory);
        T config = service.load(type, ConfigFormat.YAML);
        ConfigReloadRegistry.register(owner, service);
        return new DefaultMessages<>(config, service, delivery);
    }
}
