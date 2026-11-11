package net.loyalnetwork.coffeelib.api.config;

/**
 * What a platform whose configs CoffeeLib can manage itself hands back: a
 * plain {@link ConfigManager} that also exposes {@link ConfigReloadService}
 * for the classes declaring a non-{@link ConfigFormat#NATIVE} format.
 * <p>
 * Callers that only load and save can keep assigning to {@link ConfigManager}
 * and ignore this type entirely.
 */
public interface ReloadableConfigManager extends ConfigManager, ConfigReloadService {
}
