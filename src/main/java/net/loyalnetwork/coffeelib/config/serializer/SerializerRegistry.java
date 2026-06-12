package net.loyalnetwork.coffeelib.config.serializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SerializerRegistry {

    private static final Map<Class<?>, ConfigSerializer<?>> SERIALIZERS =
            new ConcurrentHashMap<>();

    private SerializerRegistry() {
    }

    public static <T> void register(
            Class<T> type,
            ConfigSerializer<T> serializer
    ) {
        SERIALIZERS.put(type, serializer);
    }

    @SuppressWarnings("unchecked")
    public static <T> ConfigSerializer<T> get(Class<T> type) {
        return (ConfigSerializer<T>) SERIALIZERS.get(type);
    }

    @SuppressWarnings("unchecked")
    public static <T> ConfigSerializer<T> find(Class<T> type) {
        ConfigSerializer<?> serializer = SERIALIZERS.get(type);
        if (serializer != null) {
            return (ConfigSerializer<T>) serializer;
        }
        Class<?> superClass = type.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            serializer = SERIALIZERS.get(superClass);
            if (serializer != null) {
                return (ConfigSerializer<T>) serializer;
            }
        }
        for (Class<?> iface : type.getInterfaces()) {
            serializer = SERIALIZERS.get(iface);
            if (serializer != null) {
                return (ConfigSerializer<T>) serializer;
            }
        }
        return null;
    }

    public static boolean has(Class<?> type) {
        return SERIALIZERS.containsKey(type);
    }
}