package net.loyalnetwork.coffeelib.paper.commands;

import net.loyalnetwork.coffeelib.api.command.CommandArgument;
import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.SuggestContext;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;
import net.loyalnetwork.coffeelib.core.command.ArgumentTypes;
import net.loyalnetwork.coffeelib.core.player.SharedPlayers;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The argument types Paper reads besides the core's: {@code Player} and
 * {@code World}. Only API every Bukkit since 1.8 has is used.
 */
final class PaperArguments {

    private PaperArguments() {
    }

    static void registerInto(ArgumentTypes types) {
        types.register(Player.class, new PlayerArgument());
        types.markPlayer(Player.class);
        types.register(World.class, new WorldArgument());
    }

    /** CoffeeLib's visibility rules and Bukkit's own {@code canSee}, both. */
    static boolean visible(Object viewer, Player target) {
        if (!SharedPlayers.directory().canSee(viewer, target)) {
            return false;
        }
        return !(viewer instanceof Player player) || player == target || player.canSee(target);
    }

    /** An online player by exact name, among those the sender may see. */
    static final class PlayerArgument implements CommandArgument<Player> {
        @Override
        public Player parse(CommandSource source, String input) {
            Player player = Bukkit.getPlayerExact(input);
            // A hidden player reads exactly like a missing one, so the reply gives nothing away.
            if (player == null || !visible(source == null ? null : source.player(), player)) {
                throw new CommandException("Player not found: " + input + ".");
            }
            return player;
        }

        @Override
        public Collection<String> suggest(SuggestContext context) {
            Object viewer = context.source() == null ? null : context.source().player();
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (visible(viewer, player)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
    }

    /** A loaded world by name, ignoring case. */
    static final class WorldArgument implements CommandArgument<World> {
        @Override
        public World parse(CommandSource source, String input) {
            World world = Bukkit.getWorld(input);
            if (world == null) {
                for (World candidate : Bukkit.getWorlds()) {
                    if (candidate.getName().equalsIgnoreCase(input)) {
                        return candidate;
                    }
                }
                throw new CommandException("No world called " + input + ".");
            }
            return world;
        }

        @Override
        public Collection<String> suggest(SuggestContext context) {
            List<String> names = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                names.add(world.getName());
            }
            return names;
        }
    }
}
