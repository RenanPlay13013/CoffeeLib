package net.loyalnetwork.coffeelib.config.serializer;

public final class EnumSerializer<E extends Enum<E>> implements ConfigSerializer<E> {

    private final Class<E> enumType;

    public EnumSerializer(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public Object serialize(E value) {
        return value.name();
    }

    @Override
    public E deserialize(Object value) {
        return Enum.valueOf(enumType, value.toString());
    }
}
