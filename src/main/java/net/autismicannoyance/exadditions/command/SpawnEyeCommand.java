package net.autismicannoyance.exadditions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.autismicannoyance.exadditions.network.OrbitalEyeRenderPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collections;

/**
 * Command to spawn orbital eyes with full parameter control
 * Usage: /spawneye <target> [parameters...]
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpawnEyeCommand {

    /**
     * Register the command when server starts up
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        System.out.println("[ExAdditions] RegisterCommandsEvent fired - attempting to register SpawnEye command");
        register(event.getDispatcher());
        System.out.println("[ExAdditions] SpawnEye command registration completed");
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawneye")
                .requires(source -> source.hasPermission(2)) // Op level 2 required
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(SpawnEyeCommand::executeBasic)
                        .then(Commands.argument("offset", Vec3Argument.vec3())
                                .executes(SpawnEyeCommand::executeWithOffset)
                                .then(Commands.argument("radius", FloatArgumentType.floatArg(0.1f, 5.0f))
                                        .executes(SpawnEyeCommand::executeWithRadius)
                                        .then(Commands.argument("scleraColor", StringArgumentType.string())
                                                .then(Commands.argument("irisColor", StringArgumentType.string())
                                                        .then(Commands.argument("pupilColor", StringArgumentType.string())
                                                                .then(Commands.argument("laserColor", StringArgumentType.string())
                                                                        .executes(SpawnEyeCommand::executeWithColors)
                                                                        .then(Commands.argument("firing", BoolArgumentType.bool())
                                                                                .then(Commands.argument("blinking", BoolArgumentType.bool())
                                                                                        .then(Commands.argument("pulsing", BoolArgumentType.bool())
                                                                                                .executes(SpawnEyeCommand::executeWithStates)
                                                                                                .then(Commands.argument("bloodshot", FloatArgumentType.floatArg(0.0f, 1.0f))
                                                                                                        .then(Commands.argument("fatigue", FloatArgumentType.floatArg(0.0f, 1.0f))
                                                                                                                .then(Commands.argument("pupilSize", FloatArgumentType.floatArg(0.1f, 1.0f))
                                                                                                                        .executes(SpawnEyeCommand::executeWithAnatomy)
                                                                                                                        .then(Commands.argument("laserEndOffset", Vec3Argument.vec3())
                                                                                                                                .then(Commands.argument("lookDirection", Vec3Argument.vec3())
                                                                                                                                        .executes(SpawnEyeCommand::executeFull)
                                                                                                                                )
                                                                                                                        )
                                                                                                                )
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                // Preset commands for common eye types
                .then(Commands.literal("preset")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.literal("basic")
                                        .executes(ctx -> executePreset(ctx, "basic"))
                                )
                                .then(Commands.literal("red")
                                        .executes(ctx -> executePreset(ctx, "red"))
                                )
                                .then(Commands.literal("glowing")
                                        .then(Commands.argument("color", StringArgumentType.string())
                                                .executes(ctx -> executePreset(ctx, "glowing"))
                                        )
                                )
                                .then(Commands.literal("tired")
                                        .executes(ctx -> executePreset(ctx, "tired"))
                                )
                                .then(Commands.literal("multiple")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                                .executes(ctx -> executePreset(ctx, "multiple"))
                                        )
                                )
                        )
                )
                // Item-based eye spawning
                .then(Commands.literal("item")
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .executes(SpawnEyeCommand::executeOnItem)
                                .then(Commands.argument("eyeOffset", Vec3Argument.vec3())
                                        .then(Commands.argument("radius", FloatArgumentType.floatArg(0.1f, 5.0f))
                                                .executes(SpawnEyeCommand::executeOnItemFull)
                                        )
                                )
                        )
                )
        );
    }

    // Basic execution - spawn a simple eye on target entity
    private static int executeBasic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(ctx, "target");

        OrbitalEyeRenderPacket.OrbitalEyeEntry eye =
                OrbitalEyeRenderPacket.OrbitalEyeEntry.createBasicEye(Vec3.ZERO, 0.3f);

        sendEyePacket(ctx.getSource(), target, eye);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned basic eye on " + target.getName().getString()), true);

        return 1;
    }

    // Execute with offset
    private static int executeWithOffset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(ctx, "target");
        Vec3 offset = Vec3Argument.getVec3(ctx, "offset");

        OrbitalEyeRenderPacket.OrbitalEyeEntry eye =
                OrbitalEyeRenderPacket.OrbitalEyeEntry.createBasicEye(offset, 0.3f);

        sendEyePacket(ctx.getSource(), target, eye);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned eye with offset on " + target.getName().getString()), true);

        return 1;
    }

    // Execute with radius
    private static int executeWithRadius(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(ctx, "target");
        Vec3 offset = Vec3Argument.getVec3(ctx, "offset");
        float radius = FloatArgumentType.getFloat(ctx, "radius");

        OrbitalEyeRenderPacket.OrbitalEyeEntry eye =
                OrbitalEyeRenderPacket.OrbitalEyeEntry.createBasicEye(offset, radius);

        sendEyePacket(ctx.getSource(), target, eye);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned eye with radius " + radius + " on " + target.getName().getString()), true);

        return 1;
    }

    // Execute with colors
    private static int executeWithColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(ctx, "target");
        Vec3 offset = Vec3Argument.getVec3(ctx, "offset");
        float radius = FloatArgumentType.getFloat(ctx, "radius");
        int scleraColor = parseColor(StringArgumentType.getString(ctx, "scleraColor"));
        int irisColor = parseColor(StringArgumentType.getString(ctx, "irisColor"));
        int pupilColor = parseColor(StringArgumentType.getString(ctx, "pupilColor"));
        int laserColor = parseColor(StringArgumentType.getString(ctx, "laserColor"));

        OrbitalEyeRenderPacket.OrbitalEyeEntry eye = new OrbitalEyeRenderPacket.OrbitalEyeEntry(
                offset, radius, scleraColor, pupilColor, irisColor,
                false, false, 0.0f, null, null,
                false, 0.0f, laserColor
        );

        sendEyePacket(ctx.getSource(), target, eye);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned colored eye on " + target.getName().getString()), true);

        return 1;
    }

    // Execute with states
    private static int executeWithStates(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(ctx, "target");
        Vec3 offset = Vec3Argument.getVec3(ctx, "offset");
        float radius = FloatArgumentType.getFloat(ctx, "radius");
        int scleraColor = parseColor(StringArgumentType.getString(ctx, "scleraColor"));
        int irisColor = parseColor(StringArgumentType.getString(ctx, "irisColor"));
        int pupilColor = parseColor(StringArgumentType.getString(ctx, "pupilColor"));
        int laserColor = parseColor(StringArgumentType.getString(ctx, "laserColor"));
        boolean firing = BoolArgumentType.getBool(ctx, "firing");
        boolean blinking = BoolArgumentType.getBool(ctx, "blinking");
        boolean pulsing = BoolArgumentType.getBool(ctx, "pulsing");

        Vec3 laserEnd = firing ? target.position().add(0, 2, 0) : null;

        OrbitalEyeRenderPacket.OrbitalEyeEntry eye = new OrbitalEyeRenderPacket.OrbitalEyeEntry(
                offset, radius, scleraColor, pupilColor, irisColor,
                firing, blinking, blinking ? 0.5f : 0.0f, laserEnd, null,
                pulsing, pulsing ? 0.7f : 0.0f, laserColor
        );

        sendEyePacket(ctx.getSource(), target, eye);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned eye with states on " + target.getName().getString()), true);

        return 1;
    }

    // Execute with anatomical parameters
    private static int executeWithAnatomy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(ctx, "target");
        Vec3 offset = Vec3Argument.getVec3(ctx, "offset");
        float radius = FloatArgumentType.getFloat(ctx, "radius");
        int scleraColor = parseColor(StringArgumentType.getString(ctx, "scleraColor"));
        int irisColor = parseColor(StringArgumentType.getString(ctx, "irisColor"));
        int pupilColor = parseColor(StringArgumentType.getString(ctx, "pupilColor"));
        int laserColor = parseColor(StringArgumentType.getString(ctx, "laserColor"));
        boolean firing = BoolArgumentType.getBool(ctx, "firing");
        boolean blinking = BoolArgumentType.getBool(ctx, "blinking");
        boolean pulsing = BoolArgumentType.getBool(ctx, "pulsing");
        float bloodshot = FloatArgumentType.getFloat(ctx, "bloodshot");
        float fatigue = FloatArgumentType.getFloat(ctx, "fatigue");
        float pupilSize = FloatArgumentType.getFloat(ctx, "pupilSize");

        Vec3 laserEnd = firing ? target.position().add(0, 2, 0) : null;

        OrbitalEyeRenderPacket.OrbitalEyeEntry eye = new OrbitalEyeRenderPacket.OrbitalEyeEntry(
                offset, radius, scleraColor, pupilColor, irisColor,
                firing, blinking, blinking ? 0.5f : 0.0f, laserEnd, null,
                pulsing, pulsing ? 0.7f : 0.0f, laserColor,
                bloodshot, fatigue, pupilSize
        );

        sendEyePacket(ctx.getSource(), target, eye);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned anatomical eye on " + target.getName().getString()), true);

        return 1;
    }

    // Execute with full parameters
    private static int executeFull(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(ctx, "target");
        Vec3 offset = Vec3Argument.getVec3(ctx, "offset");
        float radius = FloatArgumentType.getFloat(ctx, "radius");
        int scleraColor = parseColor(StringArgumentType.getString(ctx, "scleraColor"));
        int irisColor = parseColor(StringArgumentType.getString(ctx, "irisColor"));
        int pupilColor = parseColor(StringArgumentType.getString(ctx, "pupilColor"));
        int laserColor = parseColor(StringArgumentType.getString(ctx, "laserColor"));
        boolean firing = BoolArgumentType.getBool(ctx, "firing");
        boolean blinking = BoolArgumentType.getBool(ctx, "blinking");
        boolean pulsing = BoolArgumentType.getBool(ctx, "pulsing");
        float bloodshot = FloatArgumentType.getFloat(ctx, "bloodshot");
        float fatigue = FloatArgumentType.getFloat(ctx, "fatigue");
        float pupilSize = FloatArgumentType.getFloat(ctx, "pupilSize");
        Vec3 laserEndOffset = Vec3Argument.getVec3(ctx, "laserEndOffset");
        Vec3 lookDirection = Vec3Argument.getVec3(ctx, "lookDirection");

        Vec3 laserEnd = firing ? target.position().add(laserEndOffset) : null;

        OrbitalEyeRenderPacket.OrbitalEyeEntry eye = new OrbitalEyeRenderPacket.OrbitalEyeEntry(
                offset, radius, scleraColor, pupilColor, irisColor,
                firing, blinking, blinking ? 0.5f : 0.0f, laserEnd, lookDirection,
                pulsing, pulsing ? 0.7f : 0.0f, laserColor,
                bloodshot, fatigue, pupilSize
        );

        sendEyePacket(ctx.getSource(), target, eye);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned full-featured eye on " + target.getName().getString()), true);

        return 1;
    }

    // Execute preset commands
    private static int executePreset(CommandContext<CommandSourceStack> ctx, String preset) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(ctx, "target");

        switch (preset) {
            case "basic":
                return executeBasicPreset(ctx, target);
            case "red":
                return executeRedPreset(ctx, target);
            case "glowing":
                return executeGlowingPreset(ctx, target);
            case "tired":
                return executeTiredPreset(ctx, target);
            case "multiple":
                return executeMultiplePreset(ctx, target);
            default:
                ctx.getSource().sendFailure(Component.literal("Unknown preset: " + preset));
                return 0;
        }
    }

    private static int executeBasicPreset(CommandContext<CommandSourceStack> ctx, Entity target) {
        OrbitalEyeRenderPacket.OrbitalEyeEntry eye =
                OrbitalEyeRenderPacket.OrbitalEyeEntry.createBasicEye(Vec3.ZERO, 0.3f);

        sendEyePacket(ctx.getSource(), target, eye);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned basic preset eye"), true);

        return 1;
    }

    private static int executeRedPreset(CommandContext<CommandSourceStack> ctx, Entity target) {
        OrbitalEyeRenderPacket.OrbitalEyeEntry eye =
                OrbitalEyeRenderPacket.OrbitalEyeEntry.createRedEye(
                        Vec3.ZERO, 0.3f, true, target.position().add(0, 2, 0));

        sendEyePacket(ctx.getSource(), target, eye);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned red laser eye"), true);

        return 1;
    }

    private static int executeGlowingPreset(CommandContext<CommandSourceStack> ctx, Entity target) throws CommandSyntaxException {
        String colorStr = StringArgumentType.getString(ctx, "color");
        int color = parseColor(colorStr);

        OrbitalEyeRenderPacket.OrbitalEyeEntry eye =
                OrbitalEyeRenderPacket.OrbitalEyeEntry.createGlowingEye(
                        Vec3.ZERO, 0.3f, color, true);

        sendEyePacket(ctx.getSource(), target, eye);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned glowing eye"), true);

        return 1;
    }

    private static int executeTiredPreset(CommandContext<CommandSourceStack> ctx, Entity target) {
        OrbitalEyeRenderPacket.OrbitalEyeEntry eye =
                OrbitalEyeRenderPacket.OrbitalEyeEntry.createTiredEye(Vec3.ZERO, 0.3f, 0.7f);

        sendEyePacket(ctx.getSource(), target, eye);

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned tired/bloodshot eye"), true);

        return 1;
    }

    private static int executeMultiplePreset(CommandContext<CommandSourceStack> ctx, Entity target) throws CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(ctx, "count");

        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / count;
            double radius = 0.8;
            Vec3 offset = new Vec3(
                    Math.cos(angle) * radius,
                    Math.sin(i * 0.5) * 0.3,
                    Math.sin(angle) * radius
            );

            // Vary eye colors
            int[] colors = {0xFF4A90E2, 0xFF50C878, 0xFFFF6B35, 0xFF9370DB, 0xFFFFD700};
            int color = colors[i % colors.length];

            OrbitalEyeRenderPacket.OrbitalEyeEntry eye =
                    OrbitalEyeRenderPacket.OrbitalEyeEntry.createGlowingEye(
                            offset, 0.25f, color, true);

            sendEyePacket(ctx.getSource(), target, eye);
        }

        ctx.getSource().sendSuccess(() ->
                Component.literal("Spawned " + count + " orbital eyes"), true);

        return 1;
    }

    // Execute on item
    private static int executeOnItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Vec3 position = Vec3Argument.getVec3(ctx, "position");
        CommandSourceStack source = ctx.getSource();

        // Create a temporary item entity for the eye to attach to
        ItemStack stack = new ItemStack(Items.ENDER_EYE);
        ItemEntity itemEntity = new ItemEntity(source.getLevel(), position.x, position.y, position.z, stack);
        source.getLevel().addFreshEntity(itemEntity);

        OrbitalEyeRenderPacket.OrbitalEyeEntry eye =
                OrbitalEyeRenderPacket.OrbitalEyeEntry.createBasicEye(Vec3.ZERO, 0.3f);

        sendEyePacket(source, itemEntity, eye);

        source.sendSuccess(() ->
                Component.literal("Spawned eye on item at " + position), true);

        return 1;
    }

    private static int executeOnItemFull(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Vec3 position = Vec3Argument.getVec3(ctx, "position");
        Vec3 eyeOffset = Vec3Argument.getVec3(ctx, "eyeOffset");
        float radius = FloatArgumentType.getFloat(ctx, "radius");
        CommandSourceStack source = ctx.getSource();

        // Create a temporary item entity for the eye to attach to
        ItemStack stack = new ItemStack(Items.ENDER_EYE);
        ItemEntity itemEntity = new ItemEntity(source.getLevel(), position.x, position.y, position.z, stack);
        source.getLevel().addFreshEntity(itemEntity);

        OrbitalEyeRenderPacket.OrbitalEyeEntry eye =
                OrbitalEyeRenderPacket.OrbitalEyeEntry.createBasicEye(eyeOffset, radius);

        sendEyePacket(source, itemEntity, eye);

        source.sendSuccess(() ->
                Component.literal("Spawned eye on item with custom parameters"), true);

        return 1;
    }

    // Helper methods
    private static void sendEyePacket(CommandSourceStack source, Entity target, OrbitalEyeRenderPacket.OrbitalEyeEntry eye) {
        OrbitalEyeRenderPacket packet = new OrbitalEyeRenderPacket(
                target.getId(),
                !(target instanceof ItemEntity),
                Collections.singletonList(eye)
        );

        // Send to all players in range
        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> target),
                packet
        );
    }

    private static int parseColor(String colorStr) {
        try {
            // Support both hex format (0xFF123456 or #123456) and color names
            if (colorStr.startsWith("0x") || colorStr.startsWith("0X")) {
                return (int) Long.parseLong(colorStr.substring(2), 16);
            } else if (colorStr.startsWith("#")) {
                String hex = colorStr.substring(1);
                if (hex.length() == 6) {
                    hex = "FF" + hex; // Add full alpha
                }
                return (int) Long.parseLong(hex, 16);
            } else {
                // Color name shortcuts
                switch (colorStr.toLowerCase()) {
                    case "white": return 0xFFFFFFFF;
                    case "black": return 0xFF000000;
                    case "red": return 0xFFFF0000;
                    case "green": return 0xFF00FF00;
                    case "blue": return 0xFF0000FF;
                    case "yellow": return 0xFFFFFF00;
                    case "cyan": return 0xFF00FFFF;
                    case "magenta": return 0xFFFF00FF;
                    case "orange": return 0xFFFF8000;
                    case "pink": return 0xFFFF69B4;
                    case "purple": return 0xFF800080;
                    case "brown": return 0xFF8B4513;
                    case "gray": case "grey": return 0xFF808080;
                    case "lightgray": case "lightgrey": return 0xFFD3D3D3;
                    case "darkgray": case "darkgrey": return 0xFF404040;
                    default:
                        // Try parsing as decimal
                        return Integer.parseInt(colorStr);
                }
            }
        } catch (NumberFormatException e) {
            // Default to white if parsing fails
            return 0xFFFFFFFF;
        }
    }
}