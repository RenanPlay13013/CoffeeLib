package net.loyalnetwork.coffeelib.neoforge.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.loyalnetwork.coffeelib.api.command.CommandArgument;
import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.SuggestContext;
import net.loyalnetwork.coffeelib.api.command.annotation.Registry;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import net.loyalnetwork.coffeelib.core.command.ArgumentTypes;
import net.loyalnetwork.coffeelib.core.player.SharedPlayers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The argument types NeoForge reads besides the core's: {@code ServerPlayer},
 * {@code ServerLevel}, {@code ResourceLocation}, {@code Holder<T>} with
 * {@code @Registry}, {@code Vec3} and {@code BlockPos}.
 */
final class NeoForgeArguments {

    /** Short names for the vanilla dimensions, typed or suggested alongside their ids. */
    private static final Map<String, ResourceKey<Level>> DIMENSION_ALIASES = Map.of(
            "overworld", Level.OVERWORLD,
            "nether", Level.NETHER,
            "the_nether", Level.NETHER,
            "end", Level.END,
            "the_end", Level.END);

    private NeoForgeArguments() {
    }

    static void registerInto(ArgumentTypes types) {
        types.register(ServerPlayer.class, new PlayerArgument());
        types.markPlayer(ServerPlayer.class);
        types.register(ServerLevel.class, new LevelArgument());
        types.register(ResourceLocation.class, new IdArgument());
        types.factory(Holder.class, NeoForgeArguments::holder);
        types.register(Vec3.class, new Vec3Type());
        types.register(BlockPos.class, new BlockPosType());
    }

    // ---------------------------------------------------------------- shared

    static MinecraftServer server(CommandSource source) {
        if (source != null && source.unwrap() instanceof CommandSourceStack stack) {
            return stack.getServer();
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new CommandException("The server is not running.");
        }
        return server;
    }

    private static ResourceLocation id(String input, String what) {
        ResourceLocation id = ResourceLocation.tryParse(input.toLowerCase(Locale.ROOT));
        if (id == null) {
            throw new CommandException("'" + input + "' is not a valid " + what + " id.");
        }
        return id;
    }

    // ---------------------------------------------------------------- players

    /** An online player by exact name, among those the sender may see. */
    static final class PlayerArgument implements CommandArgument<ServerPlayer> {
        @Override
        public ServerPlayer parse(CommandSource source, String input) {
            ServerPlayer player = server(source).getPlayerList().getPlayerByName(input);
            // A hidden player reads exactly like a missing one, so the reply gives nothing away.
            if (player == null || !SharedPlayers.directory().canSee(source == null ? null : source.player(), player)) {
                throw new CommandException("Player not found: " + input + ".");
            }
            return player;
        }

        @Override
        public Collection<String> suggest(SuggestContext context) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return List.of();
            }
            Object viewer = context.source() == null ? null : context.source().player();
            List<String> names = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (SharedPlayers.directory().canSee(viewer, player)) {
                    names.add(player.getGameProfile().getName());
                }
            }
            return names;
        }
    }

    // ---------------------------------------------------------------- dimensions and ids

    /** A loaded dimension by id ({@code minecraft:the_nether}) or short name ({@code nether}). */
    static final class LevelArgument implements BrigadierArgument<ServerLevel> {
        @Override
        public ArgumentType<?> brigadier() {
            return ResourceLocationArgument.id();
        }

        @Override
        public ServerLevel read(CommandContext<CommandSourceStack> context, String name) {
            return level(context.getSource().getServer(), ResourceLocationArgument.getId(context, name).toString());
        }

        @Override
        public ServerLevel parse(CommandSource source, String input) {
            return level(server(source), input);
        }

        private static ServerLevel level(MinecraftServer server, String input) {
            String typed = input.toLowerCase(Locale.ROOT);
            ResourceKey<Level> alias = DIMENSION_ALIASES.get(typed.startsWith("minecraft:") ? typed.substring(10) : typed);
            ResourceKey<Level> key = alias != null ? alias
                    : ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id(input, "dimension"));
            ServerLevel level = server.getLevel(key);
            if (level == null) {
                throw new CommandException("No dimension called " + input + ".");
            }
            return level;
        }

        @Override
        public Collection<String> suggest(SuggestContext context) {
            List<String> names = new ArrayList<>(List.of("overworld", "nether", "end"));
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (ServerLevel level : server.getAllLevels()) {
                    names.add(level.dimension().location().toString());
                }
            }
            return names;
        }
    }

    /** Any resource id, with {@code minecraft:} when the namespace is left out. */
    static final class IdArgument implements BrigadierArgument<ResourceLocation> {
        @Override
        public ArgumentType<?> brigadier() {
            return ResourceLocationArgument.id();
        }

        @Override
        public ResourceLocation read(CommandContext<CommandSourceStack> context, String name) {
            return ResourceLocationArgument.getId(context, name);
        }

        @Override
        public ResourceLocation parse(CommandSource source, String input) {
            return id(input, "resource");
        }
    }

    /** {@code Holder<T>} of the registry its {@code @Registry} names. */
    private static CommandArgument<?> holder(Parameter parameter) {
        Registry registry = parameter.getAnnotation(Registry.class);
        if (registry == null) {
            throw new CoffeeLibException("A Holder argument needs @Registry(\"...\") to say which registry, on "
                    + parameter.getDeclaringExecutable().getDeclaringClass().getName() + "#"
                    + parameter.getDeclaringExecutable().getName());
        }
        ResourceLocation registryId = ResourceLocation.tryParse(registry.value());
        if (registryId == null) {
            throw new CoffeeLibException("@Registry(\"" + registry.value() + "\") is not a valid registry id");
        }
        return new HolderArgument<>(ResourceKey.createRegistryKey(registryId));
    }

    static final class HolderArgument<T> implements BrigadierArgument<Holder<T>> {
        private final ResourceKey<? extends net.minecraft.core.Registry<T>> registryKey;

        HolderArgument(ResourceKey<? extends net.minecraft.core.Registry<T>> registryKey) {
            this.registryKey = registryKey;
        }

        @Override
        public ArgumentType<?> brigadier() {
            return ResourceLocationArgument.id();
        }

        @Override
        public Holder<T> read(CommandContext<CommandSourceStack> context, String name) {
            return lookup(context.getSource().getServer(), ResourceLocationArgument.getId(context, name));
        }

        @Override
        public Holder<T> parse(CommandSource source, String input) {
            return lookup(server(source), id(input, registryKey.location().getPath()));
        }

        private net.minecraft.core.Registry<T> registry(MinecraftServer server) {
            return server.registryAccess().registry(registryKey).orElseThrow(() ->
                    new CommandException("Unknown registry " + registryKey.location() + "."));
        }

        private Holder<T> lookup(MinecraftServer server, ResourceLocation id) {
            return registry(server).getHolder(ResourceKey.create(registryKey, id)).orElseThrow(() ->
                    new CommandException("No " + registryKey.location().getPath() + " called " + id + "."));
        }

        @Override
        public Collection<String> suggest(SuggestContext context) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return List.of();
            }
            List<String> ids = new ArrayList<>();
            for (ResourceLocation id : registry(server).keySet()) {
                ids.add(id.toString());
            }
            return ids;
        }
    }

    // ---------------------------------------------------------------- coordinates

    /** {@code x y z}, with {@code ~} for the sender's position; whole x and z go to the block's centre. */
    static final class Vec3Type implements BrigadierArgument<Vec3> {
        @Override
        public ArgumentType<?> brigadier() {
            return Vec3Argument.vec3();
        }

        @Override
        public Vec3 read(CommandContext<CommandSourceStack> context, String name) {
            return Vec3Argument.getVec3(context, name);
        }

        @Override
        public boolean suggestsNatively() {
            return true;
        }

        @Override
        public int words() {
            return 3;
        }

        @Override
        public Vec3 parse(CommandSource source, String input) {
            double[] at = coordinates(source, input, true);
            return new Vec3(at[0], at[1], at[2]);
        }
    }

    /** {@code x y z} as a block position, with {@code ~} for the sender's. */
    static final class BlockPosType implements BrigadierArgument<BlockPos> {
        @Override
        public ArgumentType<?> brigadier() {
            return BlockPosArgument.blockPos();
        }

        @Override
        public BlockPos read(CommandContext<CommandSourceStack> context, String name) {
            return BlockPosArgument.getBlockPos(context, name);
        }

        @Override
        public boolean suggestsNatively() {
            return true;
        }

        @Override
        public int words() {
            return 3;
        }

        @Override
        public BlockPos parse(CommandSource source, String input) {
            double[] at = coordinates(source, input, false);
            return BlockPos.containing(at[0], at[1], at[2]);
        }
    }

    /** Three coordinates from text - a {@code @Default} such as {@code "~ ~ ~"} - relative to the sender. */
    private static double[] coordinates(CommandSource source, String input, boolean centre) {
        String[] words = input.trim().split("\\s+");
        if (words.length != 3) {
            throw new CommandException("'" + input + "' is not three coordinates.");
        }
        Vec3 origin = source != null && source.unwrap() instanceof CommandSourceStack stack ? stack.getPosition()
                : Vec3.ZERO;
        double[] base = {origin.x, origin.y, origin.z};
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            String word = words[i];
            try {
                if (word.startsWith("~")) {
                    result[i] = base[i] + (word.length() == 1 ? 0 : Double.parseDouble(word.substring(1)));
                } else {
                    result[i] = Double.parseDouble(word);
                    if (centre && i != 1 && !word.contains(".")) {
                        result[i] += 0.5;
                    }
                }
            } catch (NumberFormatException e) {
                throw new CommandException("'" + word + "' is not a coordinate.");
            }
        }
        return result;
    }
}
