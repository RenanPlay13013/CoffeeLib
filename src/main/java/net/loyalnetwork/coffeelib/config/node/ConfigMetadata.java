package net.loyalnetwork.coffeelib.config.node;

public record ConfigMetadata(
        String path,
        String[] comments,
        Double min,
        Double max
) {
}