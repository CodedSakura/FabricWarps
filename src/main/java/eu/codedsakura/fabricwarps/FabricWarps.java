package eu.codedsakura.fabricwarps;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import eu.codedsakura.mods.ConfigUtils;
import eu.codedsakura.mods.TeleportUtils;
import eu.codedsakura.mods.TextUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.RotationArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static eu.codedsakura.fabricwarps.cca.WarpListComponentInitializer.WARP_LIST;
import static net.minecraft.command.argument.RotationArgumentType.getRotation;
import static net.minecraft.command.argument.Vec3ArgumentType.getPosArgument;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class FabricWarps implements ModInitializer {
    public static final Logger logger = LogManager.getLogger("FabricWarps");
    private static final String CONFIG_NAME = "FabricWarps.properties";

    private final HashMap<UUID, Long> recentRequests = new HashMap<>();
    private ConfigUtils config;
    public static final boolean pre21w08a = determineVersion();

    private static boolean determineVersion() {
        String version = SharedConstants.getGameVersion().getName();
        return (version.startsWith("21w0") && !(version.endsWith("8a") || version.endsWith("8b"))) ||
                (version.startsWith("20w")) ||
                (version.startsWith("1.16"));
    }

    private List<Pair<ServerWorld, Warp>> getAllWarps(MinecraftServer server) {
        List<Pair<ServerWorld, Warp>> out = new ArrayList<>();
        server.getWorlds().forEach(serverWorld ->
                WARP_LIST.get(serverWorld).getWarps().forEach(warp ->
                        out.add(new Pair<>(serverWorld, warp))));
        out.sort((o1, o2) -> o1.getLeft().toString().compareToIgnoreCase(o2.getLeft().toString()));
        return out;
    }

    @Override
    public void onInitialize() {
        logger.info("Initializing...");

        config = new ConfigUtils(FabricLoader.getInstance().getConfigDir().resolve(CONFIG_NAME).toFile(), logger, Arrays.asList(new ConfigUtils.IConfigValue[] {
                new ConfigUtils.IntegerConfigValue("stand-still", 5, new ConfigUtils.IntegerConfigValue.IntLimits(0),
                        new ConfigUtils.Command("Stand-Still time is %s seconds", "Stand-Still time set to %s seconds")),
                new ConfigUtils.IntegerConfigValue("cooldown", 15, new ConfigUtils.IntegerConfigValue.IntLimits(0),
                        new ConfigUtils.Command("Cooldown is %s seconds", "Cooldown set to %s seconds")),
                new ConfigUtils.BooleanConfigValue("bossbar", true,
                        new ConfigUtils.Command("Boss-Bar on: %s", "Boss-Bar is now: %s"))
        }));

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            dispatcher.register(literal("warp")
                    .then(argument("name", StringArgumentType.string()).suggests(this::getWarpSuggestions)
                            .executes(ctx -> warpTo(ctx, getString(ctx, "name")))));

            dispatcher.register(literal("warps")
                    .executes(this::warpList)
                    .then(literal("list")
                            .executes(this::warpList)
                            .then(argument("dimension", DimensionArgumentType.dimension())
                                    .executes(ctx -> warpList(ctx, DimensionArgumentType.getDimensionArgument(ctx, "dimension")))))
                    .then(literal("add").requires(source -> source.hasPermissionLevel(2))
                            .executes(ctx -> {throw new SimpleCommandExceptionType(new LiteralText("Provide a warp name!")).create();})
                            .then(argument("name", StringArgumentType.string())
                                    .executes(ctx -> warpAdd(ctx, getString(ctx, "name")))
                                    .then(argument("position", Vec3ArgumentType.vec3(true))
                                            .executes(ctx -> warpAdd(ctx, getString(ctx, "name"), getPosArgument(ctx, "position").toAbsolutePos(ctx.getSource())))
                                            .then(argument("rotation", RotationArgumentType.rotation())
                                                    .executes(ctx -> warpAdd(ctx, getString(ctx, "name"), getPosArgument(ctx, "position").toAbsolutePos(ctx.getSource()), getRotation(ctx, "rotation").toAbsoluteRotation(ctx.getSource())))
                                                    .then(argument("dimension", DimensionArgumentType.dimension())
                                                            .executes(ctx -> warpAdd(ctx, getString(ctx, "name"), getPosArgument(ctx, "position").toAbsolutePos(ctx.getSource()), getRotation(ctx, "rotation").toAbsoluteRotation(ctx.getSource()), DimensionArgumentType.getDimensionArgument(ctx, "dimension"))))))))
                    .then(literal("remove").requires(source -> source.hasPermissionLevel(2))
                            .executes(ctx -> {throw new SimpleCommandExceptionType(new LiteralText("Provide a warp name!")).create();})
                            .then(argument("name", StringArgumentType.string()).suggests(this::getWarpSuggestions)
                                    .executes(ctx -> warpRemove(ctx, getString(ctx, "name")))))
                    .then(literal("warp_player").requires(source -> source.hasPermissionLevel(2))
                            .then(argument("player", EntityArgumentType.player())
                                    .then(argument("warp_name", StringArgumentType.string()).suggests(this::getWarpSuggestions)
                                            .executes(ctx -> warpTo(ctx, EntityArgumentType.getPlayer(ctx, "player"), getString(ctx, "warp_name"))))))
                    .then(config.generateCommand("config", source -> source.hasPermissionLevel(2))));
        });
    }

    private int warpRemove(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
        Pair<ServerWorld, Warp> warp = getAllWarps(ctx.getSource().getMinecraftServer()).stream()
                .filter(v -> v.getRight().name.equals(name)).findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(new LiteralText("Warp with this name not found!")).create());
        if (!WARP_LIST.get(warp.getLeft()).removeWarp(warp.getRight().name))
            throw new SimpleCommandExceptionType(new LiteralText("Failed to remove warp!")).create();
        ctx.getSource().sendFeedback(new TranslatableText("Warp %s successfully removed!", new LiteralText(name).formatted(Formatting.GOLD)), true);
        return 1;
    }

    private int warpAdd(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
        return warpAdd(ctx, name, ctx.getSource().getPosition(), ctx.getSource().getRotation(), ctx.getSource().getWorld());
    }

    private int warpAdd(CommandContext<ServerCommandSource> ctx, String name, Vec3d position) throws CommandSyntaxException {
        return warpAdd(ctx, name, position, ctx.getSource().getRotation(), ctx.getSource().getWorld());
    }

    private int warpAdd(CommandContext<ServerCommandSource> ctx, String name, Vec3d position, Vec2f rotation) throws CommandSyntaxException {
        return warpAdd(ctx, name, position, rotation, ctx.getSource().getWorld());
    }

    private int warpAdd(CommandContext<ServerCommandSource> ctx, String name, Vec3d position, Vec2f rotation, ServerWorld dimension) throws CommandSyntaxException {
        if (!name.matches("^[!-~]+$")) throw new SimpleCommandExceptionType(new LiteralText("Invalid warp name!")).create();
        if (getAllWarps(ctx.getSource().getMinecraftServer()).stream().anyMatch(w -> w.getRight().name.equalsIgnoreCase(name)))
            throw new SimpleCommandExceptionType(new LiteralText("Warp with this name already exists!")).create();
        Warp newWarp = new Warp(position, rotation, name, ctx.getSource().getPlayer().getUuid());
        if (!WARP_LIST.get(dimension).addWarp(newWarp))
            throw new SimpleCommandExceptionType(new LiteralText("Failed to add warp!")).create();
        ctx.getSource().sendFeedback(new TranslatableText("Warp %s successfully added!",
                new LiteralText(name).styled(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, newWarp.toText(ctx.getSource().getWorld()))).withColor(Formatting.GOLD))), true);
        return 1;
    }

    private MutableText warpListForDimension(ServerWorld dimension) {
        List<Warp> warps = WARP_LIST.get(dimension).getWarps();
        MutableText list = new LiteralText("In ").formatted(Formatting.LIGHT_PURPLE)
                .append(new LiteralText(dimension.getRegistryKey().getValue().toString()).formatted(Formatting.AQUA))
                .append(new LiteralText(":").formatted(Formatting.LIGHT_PURPLE));
        warps.stream().sorted((o1, o2) -> o1.name.compareToIgnoreCase(o2.name)).forEach((v) ->
                list.append(new LiteralText("\n  ").append(new LiteralText(v.name).styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/warp \"" + v.name + "\""))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, LiteralText.EMPTY.copy().append(new LiteralText("Click to teleport.\n").formatted(Formatting.ITALIC)).append(v.toText(dimension))))
                                .withColor(Formatting.GOLD)))));
        return list;
    }

    private int warpList(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ctx.getSource().getPlayer().sendMessage(TextUtils.join(StreamSupport.stream(ctx.getSource().getMinecraftServer().getWorlds().spliterator(), false)
                .map(this::warpListForDimension).collect(Collectors.toList()), new LiteralText("\n")), false);//.reduce(LiteralText.EMPTY.copy(), (buff, elem) -> buff.append(elem).append("\n")), false);
        return 1;
    }

    private int warpList(CommandContext<ServerCommandSource> ctx, ServerWorld dimension) throws CommandSyntaxException {
        ctx.getSource().getPlayer().sendMessage(warpListForDimension(dimension), false);
        return 1;
    }

    private boolean checkCooldown(ServerPlayerEntity tFrom) {
        if (recentRequests.containsKey(tFrom.getUuid())) {
            long diff = Instant.now().getEpochSecond() - recentRequests.get(tFrom.getUuid());
            if (diff < (int) config.getValue("cooldown")) {
                tFrom.sendMessage(new TranslatableText("You cannot make a request for %s more seconds!", String.valueOf((int) config.getValue("cooldown") - diff)).formatted(Formatting.RED), false);
                return true;
            }
        }
        return false;
    }

    private int warpTo(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (checkCooldown(player)) return 1;
        return warpTo(ctx, player, name);
    }

    private int warpTo(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player, String name) throws CommandSyntaxException {
        Pair<ServerWorld, Warp> warp = getAllWarps(ctx.getSource().getMinecraftServer()).stream()
                .filter(v -> v.getRight().name.equals(name)).findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(new LiteralText("Invalid warp")).create());

        TeleportUtils.genericTeleport((boolean) config.getValue("bossbar"), (int) config.getValue("stand-still"), player, () -> {
            player.teleport(warp.getLeft(), warp.getRight().x, warp.getRight().y, warp.getRight().z, warp.getRight().yaw, warp.getRight().pitch);
            recentRequests.put(player.getUuid(), Instant.now().getEpochSecond());
        });
        return 1;
    }

    private CompletableFuture<Suggestions> getWarpSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        String start = builder.getRemaining().toLowerCase();
        getAllWarps(context.getSource().getMinecraftServer()).stream()
                .map(v -> v.getRight().name)
                .sorted(String::compareToIgnoreCase)
                .filter(pair -> pair.toLowerCase().startsWith(start))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    public static class Warp {
        public double x, y, z;
        public float yaw, pitch;
        public String name;
        public UUID id, owner;

        public Warp(double x, double y, double z, float yaw, float pitch, String name, UUID owner, UUID id) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.name = name;
            this.owner = owner;
            this.id = id;
        }

        public Warp(double x, double y, double z, float yaw, float pitch, String name, UUID owner) {
            this(x, y, z, pitch, yaw, name, owner, UUID.randomUUID());
        }

        public Warp(Vec3d pos, Vec2f rot, String name, UUID owner) {
            this(pos.x, pos.y, pos.z, rot.x, rot.y, name, owner);
        }

        private static MutableText valueRepr(String name, Text value) {
            if (value.getStyle().getColor() == null)
                return new LiteralText(name + ": ").formatted(Formatting.RESET).append(value.copy().formatted(Formatting.GOLD));
            return new LiteralText(name + ": ").formatted(Formatting.RESET).append(value);
        }
        private static MutableText valueRepr(String name, String value) {
            return valueRepr(name, new LiteralText(value).formatted(Formatting.GOLD));
        }
        private static MutableText valueRepr(String name, double value) {
            return valueRepr(name, String.format("%.2f", value));
        }
        private static MutableText valueRepr(String name, float value) {
            return valueRepr(name, String.format("%.2f", value));
        }

        public MutableText toText(ServerWorld world) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(this.owner);
            Text ownerName = player == null ? new LiteralText("[unknown]") : player.getDisplayName();
            return new TranslatableText("%s\n%s\n%s; %s; %s\n%s; %s\n%s\n%s",
                    valueRepr("Name", name), valueRepr("Made by", ownerName),
                    valueRepr("X", x), valueRepr("Y", y), valueRepr("Z", z),
                    valueRepr("Yaw", yaw), valueRepr("Pitch", pitch),
                    valueRepr("In", world.getRegistryKey().getValue().toString()),
                    valueRepr("ID", id.toString().substring(0, 21) + "..."));
        }

        @Override
        public String toString() {
            return "Warp{" +
                    "name='" + name + '\'' +
                    ", x=" + x +
                    ", y=" + y +
                    ", z=" + z +
                    ", yaw=" + yaw +
                    ", pitch=" + pitch +
                    ", owner=" + owner +
                    ", id=" + id +
                    '}';
        }
    }
}
