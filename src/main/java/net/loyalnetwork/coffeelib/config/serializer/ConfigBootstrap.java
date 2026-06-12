package net.loyalnetwork.coffeelib.config.serializer;

public final class ConfigBootstrap {

    private ConfigBootstrap() {
    }

    public static void registerDefaults() {
        SerializerRegistry.register(
                String.class,
                new StringSerializer()
        );
        SerializerRegistry.register(
                Integer.class,
                new IntegerSerializer()
        );
        SerializerRegistry.register(
                Double.class,
                new DoubleSerializer()
        );
        SerializerRegistry.register(
                Boolean.class,
                new BooleanSerializer()
        );
    }
}
