package net.loyalnetwork.coffeelib.paper.player;

import com.google.gson.Gson;
import net.loyalnetwork.coffeelib.api.player.PlayerData;
import net.loyalnetwork.coffeelib.api.player.PlayerDataFactory;
import net.loyalnetwork.coffeelib.core.player.data.PlayerDataSchema;
import net.loyalnetwork.coffeelib.core.player.data.PlayerDataValues;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Native PersistentDataContainer storage on Bukkit 1.14+ without a 1.14 compile dependency. */
public final class PaperPlayerDataFactory implements PlayerDataFactory {
    private static final Map<String, State> STATES = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();
    private final State state;
    private final boolean copyOnDeath;

    private PaperPlayerDataFactory(State state, boolean copyOnDeath) {
        this.state = state;
        this.copyOnDeath = copyOnDeath;
    }

    public static PaperPlayerDataFactory forPlugin(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (!plugin.isEnabled()) throw new IllegalStateException("Player data registration requires an enabled plugin");
        // Fail on unsupported Bukkit versions immediately, not on the first player write.
        String owner = plugin.getName().toLowerCase(Locale.ROOT);
        State state = STATES.computeIfAbsent(owner, ignored -> new State(plugin));
        if (state.owner != plugin) throw new IllegalStateException("Player data still bound to a previous plugin instance: " + owner);
        return new PaperPlayerDataFactory(state, false);
    }

    public static void releaseOwner(String ownerId) {
        STATES.remove(ownerId.toLowerCase(Locale.ROOT));
    }

    /** Called when CoffeeLib itself is disabled; never retain old plugin loaders. */
    public static void clearAll() { STATES.clear(); }

    /** Clears only non-copying keys after the death event, never on End portal return. */
    public static void onDeath(Player player) {
        ensureThread();
        for (State state : STATES.values()) {
            for (PlayerDataSchema.Field field : state.schema.transientOnDeath()) {
                state.access.write(player, state.access.key(state.owner, field.key()), null);
            }
        }
    }

    @Override public PlayerDataFactory copyOnDeath(boolean enabled) { return new PaperPlayerDataFactory(state, enabled); }
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
                || (type.isMemberClass() && !java.lang.reflect.Modifier.isStatic(type.getModifiers()))) {
            throw new IllegalArgumentException("JSON player data needs a concrete, non-array class: " + type);
        }
        return entry(key, PlayerDataSchema.Kind.JSON, type, json -> json == null ? null : GSON.fromJson(json, type),
                value -> value == null ? null : GSON.toJson(value));
    }

    private <T> PlayerData<T> entry(String key, PlayerDataSchema.Kind kind, Class<T> type,
                                    Function<String,T> decode, Function<T,String> encode) {
        PlayerDataSchema.Field field = state.schema.declare(key, kind, type, copyOnDeath);
        Object dataKey = state.access.key(state.owner, field.key());
        return new PlayerData<>() {
            @Override public T get(Object player) {
                ensureThread();
                return decode.apply(state.access.read(player(player), dataKey));
            }
            @Override public void set(Object player, T value) {
                ensureThread();
                state.access.write(player(player), dataKey, encode.apply(value));
            }
        };
    }

    private static Player player(Object player) {
        if (!(player instanceof Player bukkitPlayer)) throw new IllegalArgumentException("Expected Bukkit Player, got " + player);
        if (!bukkitPlayer.isOnline()) throw new IllegalStateException("Cannot access data of disconnected player " + bukkitPlayer.getUniqueId());
        return bukkitPlayer;
    }

    private static void ensureThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Player data must be accessed on the Bukkit server thread");
    }

    private static final class State {
        final JavaPlugin owner;
        final PaperDataAccess access = new PaperDataAccess();
        final PlayerDataSchema schema;
        State(JavaPlugin plugin) {
            owner = plugin;
            schema = new PlayerDataSchema(plugin.getName().toLowerCase(Locale.ROOT));
        }
    }
}
