package net.loyalnetwork.coffeelib.core.player.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Contract and registration checks, independent of any Minecraft classes. */
public final class PlayerDataSchema {
    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern VALID_OWNER = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private final String owner;
    private final Map<String, Field> registered = new HashMap<>();

    public PlayerDataSchema(String owner) {
        if (owner == null || !VALID_OWNER.matcher(owner).matches()) {
            throw new IllegalArgumentException("Invalid player-data owner: " + owner);
        }
        this.owner = owner;
    }

    public synchronized Field declare(String key, Kind kind, Class<?> type, boolean copyOnDeath) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid player-data key for " + owner + ": " + key);
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(type, "type");
        if (registered.containsKey(key)) {
            throw new IllegalArgumentException("Player-data key already registered: " + owner + ":" + key);
        }
        Field field = new Field(owner, key, kind, type, copyOnDeath);
        registered.put(key, field);
        return field;
    }

    public synchronized java.util.List<Field> allFields() {
        return java.util.List.copyOf(registered.values());
    }

    /** Used on respawn to clear only values not opted into death persistence. */
    public synchronized java.util.List<Field> transientOnDeath() {
        return registered.values().stream().filter(field -> !field.copyOnDeath()).toList();
    }

    public enum Kind { FLAG, INTEGER, TEXT, JSON }

    public record Field(String owner, String key, Kind kind, Class<?> type, boolean copyOnDeath) {
        public String identifier() { return owner + ":" + key; }
    }
}
