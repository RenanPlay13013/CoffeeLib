package net.loyalnetwork.coffeelib.config.file;

public record ConfigEntry(
        String path,
        Object value,
        String[] comments
) {
}