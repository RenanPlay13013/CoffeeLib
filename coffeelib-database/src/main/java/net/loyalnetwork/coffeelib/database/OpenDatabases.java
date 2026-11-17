package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.database.Database;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every database opened through CoffeeLib, by owner, so they are closed when
 * their owner shuts down - a plugin disabled on Paper, the game exiting on
 * Forge and NeoForge (a JVM shutdown hook, as the last resort everywhere).
 * An unclosed pool keeps connections open on the database server and threads
 * alive in the JVM.
 */
public final class OpenDatabases {

    private static final Logger LOGGER = Logger.getLogger(OpenDatabases.class.getName());

    private static final Map<String, List<Database>> OPEN = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(OpenDatabases::closeAll, "CoffeeLib-DB-shutdown"));
    }

    private OpenDatabases() {
    }

    static void add(String ownerId, Database database) {
        OPEN.computeIfAbsent(ownerId, id -> new CopyOnWriteArrayList<>()).add(database);
    }

    /** Closes {@code ownerId}'s databases. Called by the platform when that owner shuts down. */
    public static void closeOwner(String ownerId) {
        close(ownerId, detach(ownerId));
    }

    /**
     * Takes {@code ownerId}'s databases out of the registry without closing
     * them, for a platform that has to close them a little later: databases
     * the owner opens from now on - a new instance after a reload - are not
     * among them.
     */
    public static List<Database> detach(String ownerId) {
        List<Database> databases = OPEN.remove(ownerId);
        return databases == null ? List.of() : List.copyOf(databases);
    }

    /** Closes {@code databases}, logging rather than throwing when one fails. */
    public static void close(String ownerId, List<Database> databases) {
        for (Database database : databases) {
            try {
                database.close();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "[CoffeeLib] Could not close a database of " + ownerId, e);
            }
        }
    }

    /** Closes every owner's databases. */
    public static void closeAll() {
        for (String ownerId : List.copyOf(OPEN.keySet())) {
            closeOwner(ownerId);
        }
    }

    /** How many open databases each owner has, closed ones not counted. */
    static Map<String, Integer> counts() {
        Map<String, Integer> counts = new TreeMap<>();
        OPEN.forEach((owner, databases) -> {
            int open = (int) databases.stream().filter(database -> !database.isClosed()).count();
            if (open > 0) {
                counts.put(owner, open);
            }
        });
        return counts;
    }
}
