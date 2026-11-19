package net.loyalnetwork.coffeelib.paper.player;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Late-bound PDC: the Paper module still links against Spigot 1.8.8. */
final class PaperDataAccess {
    private final Method container;
    private final Method read;
    private final Method write;
    private final Method remove;
    private final Constructor<?> namespacedKey;
    private final Object stringType;

    PaperDataAccess() {
        try {
            Class<?> key = Class.forName("org.bukkit.NamespacedKey");
            Class<?> type = Class.forName("org.bukkit.persistence.PersistentDataType");
            Class<?> pdc = Class.forName("org.bukkit.persistence.PersistentDataContainer");
            namespacedKey = key.getConstructor(Plugin.class, String.class);
            container = Player.class.getMethod("getPersistentDataContainer");
            read = pdc.getMethod("get", key, type);
            write = pdc.getMethod("set", key, type, Object.class);
            remove = pdc.getMethod("remove", key);
            Field textType = type.getField("STRING");
            stringType = textType.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new UnsupportedOperationException("CoffeeLib playerData requires Bukkit 1.14+ PersistentDataContainer; "
                    + "the rest of CoffeeLib remains compatible with Spigot 1.8.8", ex);
        }
    }

    Object key(Plugin owner, String name) {
        try { return namespacedKey.newInstance(owner, name); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException("Invalid player data key " + name, ex); }
    }

    String read(Player player, Object key) {
        try { return (String) read.invoke(container.invoke(player), key, stringType); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException("Failed reading player data", ex); }
    }

    void write(Player player, Object key, String value) {
        try {
            Object holder = container.invoke(player);
            if (value == null) remove.invoke(holder, key);
            else write.invoke(holder, key, stringType, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed writing player data", ex);
        }
    }
}
