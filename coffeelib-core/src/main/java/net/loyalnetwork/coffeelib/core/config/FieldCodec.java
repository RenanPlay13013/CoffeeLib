package net.loyalnetwork.coffeelib.core.config;

import net.loyalnetwork.coffeelib.api.config.exception.ConfigLoadException;

import java.math.BigDecimal;

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
            if (raw instanceof Boolean bool) return bool;
            String text = raw.toString().trim();
            if ("true".equalsIgnoreCase(text)) return true;
            if ("false".equalsIgnoreCase(text)) return false;
            throw new ConfigLoadException("Value '" + raw + "' is not a boolean for field '" + key + "'");
        }
        if (targetType == char.class || targetType == Character.class) {
            return decodeCharacter(raw, key);
        }
        if (raw instanceof Number number) {
            if (targetType == byte.class || targetType == Byte.class
                    || targetType == short.class || targetType == Short.class
                    || targetType == int.class || targetType == Integer.class
                    || targetType == long.class || targetType == Long.class) {
                return exactInteger(number, targetType, key);
            }
            if (targetType == double.class || targetType == Double.class) {
                double value = number.doubleValue();
                if (!Double.isFinite(value)) throw invalidNumber(number, targetType, key);
                return value;
            }
            if (targetType == float.class || targetType == Float.class) {
                float value = number.floatValue();
                if (!Float.isFinite(value)) throw invalidNumber(number, targetType, key);
                return value;
            }
        }

        throw new ConfigLoadException("Cannot convert value '" + raw + "' (" + raw.getClass().getSimpleName()
                + ") to " + targetType.getSimpleName() + " for field '" + key + "'");
    }


    /** Rejects overflow and fractional numbers instead of silently truncating them. */
    private static Object exactInteger(Number number, Class<?> type, String key) {
        try {
            BigDecimal value = new BigDecimal(number.toString());
            if (type == byte.class || type == Byte.class) return value.byteValueExact();
            if (type == short.class || type == Short.class) return value.shortValueExact();
            if (type == int.class || type == Integer.class) return value.intValueExact();
            return value.longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw invalidNumber(number, type, key);
        }
    }

    private static ConfigLoadException invalidNumber(Number number, Class<?> type, String key) {
        return new ConfigLoadException("Value '" + number + "' cannot be represented exactly as "
                + type.getSimpleName() + " for field '" + key + "'");
    }

    /**
     * A char has no representation of its own in YAML, JSON or TOML, so it
     * travels as a one-character string. Anything longer is a mistake worth
     * reporting rather than truncating.
     */
    private static Object decodeCharacter(Object raw, String key) {
        String text = raw.toString();
        if (text.length() != 1) {
            throw new ConfigLoadException("Value '" + raw + "' must be a single character for field '"
                    + key + "'");
        }
        return text.charAt(0);
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
        if (value instanceof Character character) {
            // Written as a one-character string, which is how it comes back on read.
            return character.toString();
        }
        return value;
    }

    /**
     * A value this codec can convert on its own, as opposed to a config object,
     * a collection or a map.
     * <p>
     * Covers every primitive and its wrapper. {@code byte}, {@code char} and
     * their wrappers were missing here while every other primitive was
     * present, which looked like an oversight and behaved like one: the
     * primitives were written out as empty mappings and the wrappers crashed
     * the reflector against the JDK's own fields.
     */
    static boolean isScalar(Class<?> type) {
        return type == String.class
                || type == boolean.class || type == Boolean.class
                || type == byte.class || type == Byte.class
                || type == char.class || type == Character.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == double.class || type == Double.class
                || type == float.class || type == Float.class
                || type == short.class || type == Short.class
                || type.isEnum();
    }
}
