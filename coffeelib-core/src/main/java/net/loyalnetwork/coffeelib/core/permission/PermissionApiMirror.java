package net.loyalnetwork.coffeelib.core.permission;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Mirrors the shared permission nodes into a platform permission API that only
 * takes nodes while the server starts - Forge's and NeoForge's PermissionAPI,
 * through their {@code PermissionGatherEvent.Nodes}. The platform supplies the
 * node type {@code N} and the event; the bookkeeping lives here, once.
 * <ul>
 *   <li>At the gather event, every node declared so far is registered, plus
 *       every node in the {@link PermissionNodeCache} - those declared too
 *       late last time. A node another mod already registered under the same
 *       name is not added again (the API refuses duplicates); when it is a
 *       boolean node it is queried instead of ours.</li>
 *   <li>A node declared after the event cannot join until the next start. It
 *       is recorded in the cache - and re-recorded if it is re-declared with
 *       another default - and, until the restart, {@link #registered} has
 *       nothing for it, so the rest of CoffeeLib's chain answers.</li>
 * </ul>
 *
 * @param <N> the platform's permission node type
 */
public final class PermissionApiMirror<N> {

    private static final Logger LOGGER = Logger.getLogger(PermissionApiMirror.class.getName());

    /** Builds the platform node for {@code info}, split the way the platform names nodes: {@code namespace.path}. */
    @FunctionalInterface
    public interface NodeFactory<N> {
        N create(PermissionNodes.NodeInfo info, String namespace, String path);
    }

    /** The platform's gather event, as far as the mirror needs it. */
    public interface Gather<N> {

        /** Whether some mod already registered a node named {@code name}. */
        boolean contains(String name);

        /**
         * The node already registered as {@code name}, when it is a boolean
         * one the mirror can query in place of its own; {@code null} otherwise.
         */
        N existingBoolean(String name);

        void add(N node);
    }

    private final PermissionNodes nodes;
    private final NodeFactory<N> factory;
    private final Predicate<PermissionNodes.NodeInfo> ownerLoaded;
    private final Supplier<Path> cacheFile;
    private final Map<String, N> registered = new ConcurrentHashMap<>();
    private volatile PermissionNodeCache cache;
    private volatile boolean gathered;

    /**
     * @param ownerLoaded whether a cached node's owner is still installed; entries
     *                    of owners that are gone are dropped from the cache
     * @param cacheFile   where the cache lives - asked lazily, since the
     *                    platform's config directory may not be known yet
     */
    public PermissionApiMirror(PermissionNodes nodes, NodeFactory<N> factory,
                               Predicate<PermissionNodes.NodeInfo> ownerLoaded, Supplier<Path> cacheFile) {
        this.nodes = nodes;
        this.factory = factory;
        this.ownerLoaded = ownerLoaded;
        this.cacheFile = cacheFile;
        nodes.addListener(this::onDeclared);
    }

    /** The node to query for {@code node} this run, or {@code null} when the API has none. */
    public N registered(String node) {
        return registered.get(node);
    }

    /**
     * Registers every known node with {@code event}. Call it from the
     * platform's gather event, at the lowest priority, so the nodes other mods
     * register themselves are already there to be recognised.
     *
     * @return how many nodes can be queried through the API this run
     */
    public int gather(Gather<N> event) {
        // A new server (or, in single player, a new world): start from what is declared now.
        registered.clear();

        Map<String, PermissionNodes.NodeInfo> toRegister = new LinkedHashMap<>();
        for (PermissionNodes.NodeInfo cached : cache().prune(ownerLoaded)) {
            toRegister.put(cached.node(), cached);
            // So its default is right even before its owner declares it again this run.
            nodes.declareIfAbsent(cached.ownerId(), cached.node(), cached.def(), cached.operatorLevel(),
                    cached.description());
        }
        for (PermissionNodes.NodeInfo declared : nodes.all()) {
            toRegister.put(declared.node(), declared);
        }

        for (PermissionNodes.NodeInfo info : toRegister.values()) {
            String[] name = split(info.node());
            if (name == null) {
                continue;
            }
            if (event.contains(info.node())) {
                N theirs = event.existingBoolean(info.node());
                if (theirs != null) {
                    registered.put(info.node(), theirs);
                } else {
                    LOGGER.warning("[CoffeeLib] Permission node '" + info.node() + "' is already registered with "
                            + "the PermissionAPI as a non-boolean node; CoffeeLib checks it without the PermissionAPI");
                }
                continue;
            }
            N node = factory.create(info, name[0], name[1]);
            if (node != null) {
                event.add(node);
                registered.put(info.node(), node);
            }
        }
        gathered = true;
        return registered.size();
    }

    private void onDeclared(PermissionNodes.NodeInfo info) {
        if (gathered && !registered.containsKey(info.node()) && split(info.node()) != null) {
            cache().record(info);
        }
    }

    /**
     * {@code crates.give} as {@code {"crates", "give"}}: Forge-like platforms
     * name a node {@code namespace.path}, so the first segment is the
     * namespace and the rest the path. {@code null} for a node with one
     * segment or a wildcard, which has no such form. Valid nodes only hold
     * {@code [a-z0-9_-]} and dots, which both parts accept.
     */
    static String[] split(String node) {
        int dot = node.indexOf('.');
        if (dot <= 0 || dot == node.length() - 1 || node.indexOf('*') >= 0) {
            return null;
        }
        return new String[]{node.substring(0, dot), node.substring(dot + 1)};
    }

    private PermissionNodeCache cache() {
        PermissionNodeCache current = cache;
        if (current == null) {
            synchronized (this) {
                current = cache;
                if (current == null) {
                    current = new PermissionNodeCache(cacheFile.get());
                    cache = current;
                }
            }
        }
        return current;
    }
}
