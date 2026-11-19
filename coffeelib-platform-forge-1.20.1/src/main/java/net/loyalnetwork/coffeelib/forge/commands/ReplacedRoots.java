package net.loyalnetwork.coffeelib.forge.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * {@code @Command(replaces = true)} on Brigadier: the command already under
 * that root is taken out before CoffeeLib's is added - unless it is one
 * CoffeeLib added for a replacing root, which a second handler of the same
 * owner merges into instead.
 * <p>
 * Brigadier has no API to remove a node, so its three child maps are edited by
 * reflection, the one place in CoffeeLib that does it.
 */
final class ReplacedRoots {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] CHILD_MAPS = {"children", "literals", "arguments"};

    /** Root nodes CoffeeLib added for a replacing root, per dispatcher (weak: a new server builds a new one). */
    private static final Set<CommandNode<?>> OWN = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private ReplacedRoots() {
    }

    static void removeForeign(CommandDispatcher<CommandSourceStack> dispatcher, String root, String ownerId) {
        CommandNode<CommandSourceStack> existing = dispatcher.getRoot().getChild(root);
        if (existing == null || OWN.contains(existing)) {
            return;
        }
        for (String field : CHILD_MAPS) {
            try {
                Field map = CommandNode.class.getDeclaredField(field);
                map.setAccessible(true);
                ((Map<?, ?>) map.get(dispatcher.getRoot())).remove(root);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not remove '/" + root + "' to replace it", e);
            }
        }
        LOGGER.info("[CoffeeLib/{}] Replaced the existing '/{}' command", ownerId, root);
    }

    static void markOwn(CommandNode<CommandSourceStack> node) {
        OWN.add(node);
    }
}
