package net.loyalnetwork.coffeelib.config.serializer;

public interface ConfigSerializer<T> {

    Object serialize(T value);

    T deserialize(Object value);
}
