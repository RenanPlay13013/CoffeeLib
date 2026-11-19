package net.loyalnetwork.coffeelib.neoforge.player;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import net.loyalnetwork.coffeelib.api.player.PlayerData;
import net.loyalnetwork.coffeelib.api.player.PlayerDataFactory;
import net.loyalnetwork.coffeelib.core.player.data.PlayerDataSchema;
import net.loyalnetwork.coffeelib.core.player.data.PlayerDataValues;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One attachment per field in the consumer mod's namespace. Must be created
 * in the mod constructor, before NeoForge freezes the attachment registry.
 */
public final class NeoForgePlayerDataFactory implements PlayerDataFactory {
    private static final Map<String, Owner> OWNERS = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();
    private final Owner owner;
    private final boolean copyOnDeath;

    private NeoForgePlayerDataFactory(Owner owner, boolean copyOnDeath) {
        this.owner = owner;
        this.copyOnDeath = copyOnDeath;
    }

    public static NeoForgePlayerDataFactory forContainer(ModContainer container) {
        Objects.requireNonNull(container, "container");
        Owner state = OWNERS.computeIfAbsent(container.getModId(), ignored -> new Owner(container));
        if (state.container != container) throw new IllegalStateException(
                "Player data registered with different ModContainer for " + container.getModId());
        return new NeoForgePlayerDataFactory(state, false);
    }

    @Override public PlayerDataFactory copyOnDeath(boolean enabled) { return new NeoForgePlayerDataFactory(owner, enabled); }
    @Override public PlayerData<Boolean> flag(String key) {
        return entry(key, PlayerDataSchema.Kind.FLAG, Boolean.class,
                PlayerDataValues::decodeFlag, PlayerDataValues::encodeFlag);
    }
    @Override public PlayerData<Integer> integer(String key) {
        return entry(key, PlayerDataSchema.Kind.INTEGER, Integer.class,
                PlayerDataValues::decodeInteger, PlayerDataValues::encodeInteger);
    }
    @Override public PlayerData<String> text(String key) {
        return entry(key, PlayerDataSchema.Kind.TEXT, String.class,
                PlayerDataValues::decodeText, PlayerDataValues::encodeText);
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
        PlayerDataSchema.Field field = owner.schema.declare(key, kind, type, copyOnDeath);
        Supplier<AttachmentType<String>> attachment = owner.types.register(field.key(), () -> {
            AttachmentType.Builder<String> builder = AttachmentType.builder(() -> "").serialize(Codec.STRING);
            if (field.copyOnDeath()) builder.copyOnDeath();
            return builder.build();
        });
        return new PlayerData<>() {
            @Override public T get(Object raw) {
                ServerPlayer player = player(raw);
                return decode.apply(player.hasData(attachment) ? emptyAsMissing(player.getData(attachment), kind) : null);
            }
            @Override public void set(Object raw, T value) {
                ServerPlayer player = player(raw);
                String serialized = encode.apply(value);
                // Empty raw string is reserved for absent JSON data. Other kinds
                // (including empty text) are explicitly set through their normal codecs.
                player.setData(attachment, serialized == null ? "" : serialized);
            }
        };
    }

    private static String emptyAsMissing(String value, PlayerDataSchema.Kind kind) {
        return kind == PlayerDataSchema.Kind.JSON && value.isEmpty() ? null : value;
    }

    private static ServerPlayer player(Object raw) {
        if (!(raw instanceof ServerPlayer player)) throw new IllegalArgumentException("Expected NeoForge ServerPlayer: " + raw);
        if (player.getServer() == null || !player.getServer().isSameThread())
            throw new IllegalStateException("Player data requires the server thread");
        if (player.hasDisconnected()) throw new IllegalStateException("Player has disconnected");
        return player;
    }

    private static final class Owner {
        final ModContainer container;
        final PlayerDataSchema schema;
        final DeferredRegister<AttachmentType<?>> types;
        Owner(ModContainer container) {
            this.container = container;
            this.schema = new PlayerDataSchema(container.getModId());
            this.types = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, container.getModId());
            types.register(container.getEventBus());
        }
    }
}
