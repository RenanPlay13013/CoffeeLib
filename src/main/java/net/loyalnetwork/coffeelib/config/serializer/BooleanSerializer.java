package net.loyalnetwork.coffeelib.config.serializer;

public final class BooleanSerializer implements ConfigSerializer<Boolean> {

    @Override
    public Object serialize(Boolean value) {
        return value;
    }

    @Override
    public Boolean deserialize(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
