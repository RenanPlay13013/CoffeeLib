package net.loyalnetwork.coffeelib.core.config;

import net.loyalnetwork.coffeelib.api.config.exception.ConfigLoadException;

/** Converts between a field's declared Java type and the raw value a {@code ConfigBackend} works with. */
final class FieldCodec {

    private FieldCodec() {
    }

    static Object decode(Object raw, Class<?> targetType, String key) {
        if (raw == null) {
            return null;
        }
        if (targetType == String.class) {
            return raw.toString();
        }
        if (targetType.isEnum()) {
            return decodeEnum(raw, targetType, key);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return raw instanceof Boolean bool ? bool : Boolean.parseBoolean(raw.toString());
        }
        if (raw instanceof Number number) {
            if (targetType == int.class || targetType == Integer.class) {
                return number.intValue();
            }
            if (targetType == long.class || targetType == Long.class) {
                return number.longValue();
            }
            if (targetType == double.class || targetType == Double.class) {
                return number.doubleValue();
            }
            if (targetType == float.class || targetType == Float.class) {
                return number.floatValue();
            }
            if (targetType == short.class || targetType == Short.class) {
                return number.shortValue();
            }
        }

        throw new ConfigLoadException("Cannot convert value '" + raw + "' (" + raw.getClass().getSimpleName()
                + ") to " + targetType.getSimpleName() + " for field '" + key + "'");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object decodeEnum(Object raw, Class<?> targetType, String key) {
        try {
            return Enum.valueOf((Class<? extends Enum>) targetType, raw.toString());
        } catch (IllegalArgumentException e) {
            throw new ConfigLoadException("Value '" + raw + "' is not a valid " + targetType.getSimpleName()
                    + " for field '" + key + "'");
        }
    }

    static Object encode(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value;
    }

    /** Anything that isn't scalar is treated as a nested config object during reflection. */
    static boolean isScalar(Class<?> type) {
        return type == String.class
                || type == boolean.class || type == Boolean.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == double.class || type == Double.class
                || type == float.class || type == Float.class
                || type == short.class || type == Short.class
                || type.isEnum();
    }
}
