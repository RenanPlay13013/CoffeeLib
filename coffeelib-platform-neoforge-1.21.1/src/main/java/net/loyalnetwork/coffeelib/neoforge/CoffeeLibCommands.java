package net.loyalnetwork.coffeelib.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.loyalnetwork.coffeelib.api.config.ConfigReloadService;
import net.loyalnetwork.coffeelib.core.config.reload.ConfigReloadRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /coffeelib config}, the operator half of CoffeeLib's managed config
 * formats.
 * <p>
 * A {@link net.loyalnetwork.coffeelib.api.config.ConfigFormat#NATIVE} config
 * is NeoForge's, and NeoForge already exposes {@code /neoforge config}. A managed
 * one (YAML, JSON, HOCON) is CoffeeLib's: nothing in the loader knows the
 * file exists, so without these commands an operator editing it would have no
 * way to apply the change short of restarting the server. Hence both halves:
 * <ul>
 *   <li>{@code /coffeelib config reload [<mod>] [<file>]} — pull, right now</li>
 *   <li>{@code /coffeelib config hotreload start|stop [<mod>]} — push, from a
 *       file watcher, started and stopped at will</li>
 *   <li>{@code /coffeelib config list} — what is managed and what is being
 *       watched</li>
 * </ul>
 * The services acted on come from {@link ConfigReloadRegistry}: this command
 * lives in the CoffeeLib jar and must reach configs created by other mods
 * entirely. Requires permission level 2 (the usual op/command-block level),
 * since reloading a config is a server-state change.
 */
final class CoffeeLibCommands {

    private static final SuggestionProvider<CommandSourceStack> MOD_IDS =
            (context, builder) -> SharedSuggestionProvider.suggest(ConfigReloadRegistry.owners(), builder);

    private static final SuggestionProvider<CommandSourceStack> FILE_NAMES = (context, builder) -> {
        List<String> names = new ArrayList<>();
        for (ConfigReloadService service : servicesOf(modIdOf(context))) {
            names.addAll(service.managedFiles());
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private CoffeeLibCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("coffeelib")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("config")
                        .then(Commands.literal("list")
                                .executes(CoffeeLibCommands::list))
                        .then(Commands.literal("reload")
                                .executes(context -> reload(context, null, null))
                                .then(Commands.argument("mod", StringArgumentType.word())
                                        .suggests(MOD_IDS)
                                        .executes(context -> reload(context, modIdOf(context), null))
                                        .then(Commands.argument("file", StringArgumentType.string())
                                                .suggests(FILE_NAMES)
                                                .executes(context -> reload(context, modIdOf(context),
                                                        StringArgumentType.getString(context, "file"))))))
                        .then(Commands.literal("hotreload")
                                .executes(CoffeeLibCommands::hotReloadStatus)
                                .then(Commands.literal("status")
                                        .executes(CoffeeLibCommands::hotReloadStatus))
                                .then(Commands.literal("start")
                                        .executes(context -> setHotReload(context, null, true))
                                        .then(Commands.argument("mod", StringArgumentType.word())
                                                .suggests(MOD_IDS)
                                                .executes(context -> setHotReload(context, modIdOf(context), true))))
                                .then(Commands.literal("stop")
                                        .executes(context -> setHotReload(context, null, false))
                                        .then(Commands.argument("mod", StringArgumentType.word())
                                                .suggests(MOD_IDS)
                                                .executes(context -> setHotReload(context, modIdOf(context), false)))))));
    }

    /** Lists every managed config by mod, flagging whose watcher is running. */
    private static int list(CommandContext<CommandSourceStack> context) {
        Map<String, List<ConfigReloadService>> owners = ConfigReloadRegistry.all();
        if (owners.isEmpty()) {
            context.getSource().sendSuccess(() -> info("No mod has loaded a CoffeeLib-managed config "
                    + "(native NeoForge configs are listed by /neoforge config)."), false);
            return 0;
        }

        int files = 0;
        for (Map.Entry<String, List<ConfigReloadService>> entry : owners.entrySet()) {
            for (ConfigReloadService service : entry.getValue()) {
                List<String> managed = service.managedFiles();
                files += managed.size();

                String label = entry.getKey()
                        + (service.isHotReloadRunning() ? " [hot reload: on]" : " [hot reload: off]")
                        + (managed.isEmpty() ? " - nothing managed" : " - " + String.join(", ", managed));
                context.getSource().sendSuccess(() -> info(label), false);
            }
        }
        return files;
    }

    /**
     * Manual reload: every managed file, one mod's files, or a single file.
     * Reports what reloaded and keeps going past a file that failed, so one
     * broken config does not block the rest.
     */
    private static int reload(CommandContext<CommandSourceStack> context, String modId, String fileName) {
        List<ConfigReloadService> services = servicesOf(modId);
        if (services.isEmpty()) {
            context.getSource().sendFailure(error(modId == null
                    ? "No mod has loaded a CoffeeLib-managed config."
                    : "No CoffeeLib-managed config registered for '" + modId + "'."));
            return 0;
        }

        int reloaded = 0;
        boolean matchedFile = false;
        for (ConfigReloadService service : services) {
            try {
                if (fileName == null) {
                    reloaded += service.reloadAll();
                } else if (service.reload(fileName)) {
                    matchedFile = true;
                    reloaded++;
                }
            } catch (RuntimeException e) {
                context.getSource().sendFailure(error("Reload failed: " + describe(e)));
            }
        }

        if (fileName != null && !matchedFile) {
            context.getSource().sendFailure(error("'" + fileName + "' is not a managed config file"
                    + (modId == null ? "." : " of '" + modId + "'.")));
            return 0;
        }

        int count = reloaded;
        context.getSource().sendSuccess(() -> info("Reloaded " + count
                + (count == 1 ? " config file." : " config files.")), true);
        return count;
    }

    /** Starts or stops the file watcher, for one mod or all of them. */
    private static int setHotReload(CommandContext<CommandSourceStack> context, String modId, boolean start) {
        List<ConfigReloadService> services = servicesOf(modId);
        if (services.isEmpty()) {
            context.getSource().sendFailure(error(modId == null
                    ? "No mod has loaded a CoffeeLib-managed config."
                    : "No CoffeeLib-managed config registered for '" + modId + "'."));
            return 0;
        }

        int changed = 0;
        for (ConfigReloadService service : services) {
            try {
                boolean flipped = start ? service.startHotReload() : service.stopHotReload();
                if (flipped) {
                    changed++;
                }
            } catch (RuntimeException e) {
                context.getSource().sendFailure(error("Could not "
                        + (start ? "start" : "stop") + " hot reload: " + describe(e)));
            }
        }

        int count = changed;
        String verb = start ? "Started" : "Stopped";
        context.getSource().sendSuccess(() -> info(count == 0
                ? "Nothing to do - hot reload was already " + (start ? "running" : "stopped")
                        + " everywhere it applies."
                : verb + " hot reload for " + count + (count == 1 ? " config set." : " config sets.")), true);
        return count;
    }

    private static int hotReloadStatus(CommandContext<CommandSourceStack> context) {
        Map<String, List<ConfigReloadService>> owners = ConfigReloadRegistry.all();
        if (owners.isEmpty()) {
            context.getSource().sendSuccess(() -> info("No mod has loaded a CoffeeLib-managed config."), false);
            return 0;
        }

        int running = 0;
        for (Map.Entry<String, List<ConfigReloadService>> entry : owners.entrySet()) {
            for (ConfigReloadService service : entry.getValue()) {
                boolean on = service.isHotReloadRunning();
                if (on) {
                    running++;
                }
                String label = entry.getKey() + ": hot reload " + (on ? "running" : "stopped")
                        + " (" + service.managedFiles().size() + " file(s))";
                context.getSource().sendSuccess(() -> info(label), false);
            }
        }
        return running;
    }

    /** Services for one mod id, or every registered service when {@code modId} is null. */
    private static List<ConfigReloadService> servicesOf(String modId) {
        if (modId != null) {
            return ConfigReloadRegistry.of(modId);
        }
        List<ConfigReloadService> all = new ArrayList<>();
        for (List<ConfigReloadService> services : ConfigReloadRegistry.all().values()) {
            all.addAll(services);
        }
        return all;
    }

    /** Reads the {@code mod} argument, or null on the overloads that take none. */
    private static String modIdOf(CommandContext<CommandSourceStack> context) {
        try {
            return StringArgumentType.getString(context, "mod");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A config failure's message is the useful part for an operator; the stack trace is in the log. */
    private static String describe(RuntimeException e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }

    private static Component info(String message) {
        return Component.literal("[CoffeeLib] " + message);
    }

    private static Component error(String message) {
        return Component.literal("[CoffeeLib] " + message).withStyle(ChatFormatting.RED);
    }
}
