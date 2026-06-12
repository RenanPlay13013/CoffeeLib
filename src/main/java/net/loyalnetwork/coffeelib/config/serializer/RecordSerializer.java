package net.loyalnetwork.coffeelib.config.serializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RecordSerializer implements ConfigSerializer<Record> {

    private final Class<? extends Record> recordType;
    private final Constructor<?> constructor;
    private final RecordComponent[] components;

    @SuppressWarnings("unchecked")
    public RecordSerializer(Class<? extends Record> recordType) {
        this.recordType = recordType;
        this.components = recordType.getRecordComponents();
        Class<?>[] paramTypes = Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
        try {
            this.constructor = recordType.getDeclaredConstructor(paramTypes);
            this.constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No canonical constructor for record: " + recordType, e);
        }
    }

    @Override
    public Object serialize(Record value) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (RecordComponent comp : components) {
            try {
                Object val = comp.getAccessor().invoke(value);
                map.put(comp.getName(), serializeComponent(val));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize record component: " + comp.getName(), e);
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Record deserialize(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected Map for record deserialization, got: " + value.getClass());
        }
        Map<String, Object> data = (Map<String, Object>) map;
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            Object raw = data.get(components[i].getName());
            args[i] = deserializeComponent(components[i].getType(), raw);
        }
        try {
            return (Record) constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize record: " + recordType, e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object serializeComponent(Object val) {
        if (val == null) return null;
        if (val instanceof Record record) {
            RecordSerializer nested = new RecordSerializer(record.getClass());
            return nested.serialize(record);
        }
        ConfigSerializer serializer = SerializerRegistry.find(val.getClass());
        if (serializer != null) {
            return serializer.serialize(val);
        }
        return val;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object deserializeComponent(Class<?> type, Object raw) {
        if (raw == null) return null;
        if (type.isRecord()) {
            RecordSerializer nested = new RecordSerializer((Class<? extends Record>) type);
            return nested.deserialize(raw);
        }
        ConfigSerializer serializer = SerializerRegistry.find(type);
        if (serializer != null) {
            return serializer.deserialize(raw);
        }
        return raw;
    }
}
