package net.loyalnetwork.coffeelib.core.permission;

import net.loyalnetwork.coffeelib.api.permission.PermissionDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remembers permission nodes declared after the server started, so they can
 * be registered with the platform's permission API on the next start.
 * <p>
 * Forge's and NeoForge's PermissionAPI accept nodes only while the server is
 * starting ({@code PermissionGatherEvent.Nodes}); a node declared after that -
 * by a command registered on a running server, say - cannot join it, and
 * querying it throws. Such a node is checked through CoffeeLib's own chain
 * until a restart, and recorded here so that restart registers it like any
 * other. The file is plain text with a header saying all of this, because an
 * operator who finds it should not have to guess.
 * <p>
 * Format, one node per line after the {@code #} header, tab-separated:
 * {@code node, default, operatorLevel, owner, description}.
 */
public final class PermissionNodeCache {

    private static final Logger LOGGER = Logger.getLogger(PermissionNodeCache.class.getName());

    static final String HEADER = """
            # CoffeeLib permission node cache
            #
            # Why this file exists:
            # Forge and NeoForge only let mods register permission nodes with their
            # PermissionAPI while the server is starting up. A node a mod declares after
            # that - for example by registering a command on a server that is already
            # running - cannot be added until the next start.
            #
            # Until then, CoffeeLib checks such a node itself: LuckPerms when it is
            # installed, the node's default otherwise. It also writes the node here, and
            # on the next start registers every node in this file with the PermissionAPI,
            # so permission handlers (LuckPerms or others) see it like any other node.
            #
            # Entries whose mod is no longer installed are dropped on the next start.
            # Safe to delete: nodes are re-added the next time they are declared late.
            # Format (tab-separated): node  default  operatorLevel  owner  description
            """;

    private final Path file;

    public PermissionNodeCache(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** The cached nodes, by node; empty if the file does not exist or cannot be read. */
    public synchronized Map<String, PermissionNodes.NodeInfo> read() {
        Map<String, PermissionNodes.NodeInfo> entries = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                PermissionNodes.NodeInfo info = parse(line);
                if (info != null) {
                    entries.put(info.node(), info);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[CoffeeLib] Could not read " + file + "; late permission nodes from "
                    + "the last run will not be pre-registered", e);
        }
        return entries;
    }

    /**
     * Records {@code info}: added when the node is new, the entry replaced when
     * the node was cached with another default, op level, owner or
     * description, nothing when it is already cached as it is. Creates the
     * file, header first, if needed.
     */
    public synchronized void record(PermissionNodes.NodeInfo info) {
        Map<String, PermissionNodes.NodeInfo> entries = read();
        PermissionNodes.NodeInfo cached = entries.get(info.node());
        if (cached != null) {
            if (!cached.equals(sanitized(info))) {
                entries.put(info.node(), info);
                write(entries.values());
            }
            return;
        }
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, HEADER, StandardCharsets.UTF_8);
            }
            Files.writeString(file, format(info) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[CoffeeLib] Could not record late permission node '" + info.node()
                    + "' in " + file + "; it keeps working, but will not be pre-registered after a restart", e);
        }
    }

    /**
     * Rewrites the file keeping only the entries {@code keep} accepts - for
     * dropping nodes whose mod is gone. Deletes the file when none are left.
     *
     * @return the entries kept
     */
    public synchronized List<PermissionNodes.NodeInfo> prune(Predicate<PermissionNodes.NodeInfo> keep) {
        Map<String, PermissionNodes.NodeInfo> entries = read();
        List<PermissionNodes.NodeInfo> kept = new ArrayList<>();
        for (PermissionNodes.NodeInfo info : entries.values()) {
            if (keep.test(info)) {
                kept.add(info);
            }
        }
        if (kept.size() != entries.size()) {
            write(kept);
        }
        return kept;
    }

    /** Rewrites the whole file with {@code entries}, or deletes it when there are none. */
    private void write(java.util.Collection<PermissionNodes.NodeInfo> entries) {
        try {
            if (entries.isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                StringBuilder content = new StringBuilder(HEADER);
                for (PermissionNodes.NodeInfo info : entries) {
                    content.append(format(info)).append(System.lineSeparator());
                }
                Files.createDirectories(file.getParent());
                Files.writeString(file, content.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[CoffeeLib] Could not rewrite " + file, e);
        }
    }

    private static String format(PermissionNodes.NodeInfo info) {
        PermissionNodes.NodeInfo clean = sanitized(info);
        return String.join("\t", clean.node(), clean.def().name(), Integer.toString(clean.operatorLevel()),
                clean.ownerId(), clean.description());
    }

    /** {@code info} as it reads back from the file: no tabs or line breaks in the free-text fields. */
    private static PermissionNodes.NodeInfo sanitized(PermissionNodes.NodeInfo info) {
        return new PermissionNodes.NodeInfo(info.node(), flat(info.ownerId()), info.def(), info.operatorLevel(),
                flat(info.description()));
    }

    private static String flat(String text) {
        return text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** A cache line, or {@code null} for the header, a blank line, or anything malformed (skipped, not fatal). */
    private static PermissionNodes.NodeInfo parse(String line) {
        if (line.isBlank() || line.startsWith("#")) {
            return null;
        }
        String[] parts = line.split("\t", 5);
        if (parts.length < 4) {
            return null;
        }
        try {
            PermissionNodes.requireValid(parts[0]);
            return new PermissionNodes.NodeInfo(parts[0], parts[3], PermissionDefault.valueOf(parts[1]),
                    Integer.parseInt(parts[2]), parts.length == 5 ? parts[4] : "");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
