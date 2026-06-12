package net.loyalnetwork.coffeelib.config.serializer;

public final class DoubleSerializer implements ConfigSerializer<Double> {

    @Override
    public Object serialize(Double value) {
        return value;
    }

    @Override
    public Double deserialize(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
