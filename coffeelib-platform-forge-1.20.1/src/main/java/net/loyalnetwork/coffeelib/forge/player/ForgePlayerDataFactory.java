package net.loyalnetwork.coffeelib.forge.player;

import com.google.gson.Gson;
import net.loyalnetwork.coffeelib.api.player.PlayerData;
import net.loyalnetwork.coffeelib.api.player.PlayerDataFactory;
import net.loyalnetwork.coffeelib.core.player.data.PlayerDataSchema;
import net.loyalnetwork.coffeelib.core.player.data.PlayerDataValues;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Forge 1.20.1 player data, backed by one serialized capability per player. */
public final class ForgePlayerDataFactory implements PlayerDataFactory {
    public static final Capability<ForgeDataContainer> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });
    private static final Map<String, PlayerDataSchema> SCHEMAS = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();
    private final PlayerDataSchema schema;
    private final boolean copyOnDeath;

    private ForgePlayerDataFactory(PlayerDataSchema schema, boolean copyOnDeath) {
        this.schema = schema;
        this.copyOnDeath = copyOnDeath;
    }

    public static ForgePlayerDataFactory forOwner(String owner) {
        return new ForgePlayerDataFactory(SCHEMAS.computeIfAbsent(owner, PlayerDataSchema::new), false);
    }

    @Override public PlayerDataFactory copyOnDeath(boolean enabled) {
        return new ForgePlayerDataFactory(schema, enabled);
    }
    @Override public PlayerData<Boolean> flag(String key) {
        return entry(key, PlayerDataSchema.Kind.FLAG, Boolean.class, PlayerDataValues::decodeFlag, PlayerDataValues::encodeFlag);
    }
    @Override public PlayerData<Integer> integer(String key) {
        return entry(key, PlayerDataSchema.Kind.INTEGER, Integer.class, PlayerDataValues::decodeInteger, PlayerDataValues::encodeInteger);
    }
    @Override public PlayerData<String> text(String key) {
        return entry(key, PlayerDataSchema.Kind.TEXT, String.class, PlayerDataValues::decodeText, PlayerDataValues::encodeText);
    }
    @Override public <T> PlayerData<T> json(String key, Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (type.isPrimitive() || type.isArray() || type.isInterface() || java.lang.reflect.Modifier.isAbstract(type.getModifiers()) || type.isAnonymousClass()
                || (type.isMemberClass() && !java.lang.reflect.Modifier.isStatic(type.getModifiers())))
            throw new IllegalArgumentException("JSON player data needs a concrete, non-array class: " + type);
        return entry(key, PlayerDataSchema.Kind.JSON, type,
                raw -> raw == null ? null : GSON.fromJson(raw, type),
                value -> value == null ? null : GSON.toJson(value));
    }

    private <T> PlayerData<T> entry(String key, PlayerDataSchema.Kind kind, Class<T> type,
                                    Function<String, T> decode, Function<T, String> encode) {
        PlayerDataSchema.Field field = schema.declare(key, kind, type, copyOnDeath);
        return new PlayerData<>() {
            @Override public T get(Object player) { return decode.apply(container(player).get(field.identifier())); }
            @Override public void set(Object player, T value) { container(player).put(field.identifier(), encode.apply(value)); }
        };
    }

    private static ForgeDataContainer container(Object raw) {
        if (!(raw instanceof ServerPlayer player)) throw new IllegalArgumentException("Expected Forge ServerPlayer: " + raw);
        if (player.getServer() == null || !player.getServer().isSameThread())
            throw new IllegalStateException("Player data requires the server thread");
        if (player.hasDisconnected()) throw new IllegalStateException("Player is disconnected");
        return player.getCapability(CAPABILITY).orElseThrow(() ->
                new IllegalStateException("CoffeeLib player-data capability is missing on " + player.getUUID()));
    }

    /** Called by Forge's Clone event only on death, not on End return. */
    public static void copyOnDeath(ServerPlayer original, ServerPlayer replacement) {
        if (original == replacement) return;
        original.reviveCaps();
        try {
            var oldCap = original.getCapability(CAPABILITY).resolve();
            var newCap = replacement.getCapability(CAPABILITY).resolve();
            if (oldCap.isEmpty() || newCap.isEmpty())
                throw new IllegalStateException("Missing CoffeeLib capability while cloning player");
            for (PlayerDataSchema schema : SCHEMAS.values()) {
                for (PlayerDataSchema.Field field : schemaFieldsToCopy(schema)) {
                    String current = oldCap.get().get(field.identifier());
                    if (current != null) newCap.get().put(field.identifier(), current);
                }
            }
        } finally { original.invalidateCaps(); }
    }

    private static java.util.List<PlayerDataSchema.Field> schemaFieldsToCopy(PlayerDataSchema schema) {
        return schema.allFields().stream().filter(PlayerDataSchema.Field::copyOnDeath).toList();
    }
}
