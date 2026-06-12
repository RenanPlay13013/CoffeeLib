package net.loyalnetwork.coffeelib.config.serializer;

public final class IntegerSerializer implements ConfigSerializer<Integer> {

    @Override
    public Object serialize(Integer value) {
        return value;
    }

    @Override
    public Integer deserialize(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
