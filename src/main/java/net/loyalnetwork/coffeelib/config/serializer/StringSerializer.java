package net.loyalnetwork.coffeelib.config.serializer;

public final class StringSerializer implements ConfigSerializer<String> {

    @Override
    public Object serialize(String value) {
        return value;
    }

    @Override
    public String deserialize(Object value) {
        return value == null ? null : value.toString();
    }
}
