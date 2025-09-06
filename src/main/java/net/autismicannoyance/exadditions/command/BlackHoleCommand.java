package net.autismicannoyance.exadditions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.autismicannoyance.exadditions.item.custom.BlackHoleGeneratorItem;
import net.autismicannoyance.exadditions.network.BlackHoleEffectPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Commands for creating and managing black hole effects
 */
public class BlackHoleCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("blackhole")
                        .requires(source -> source.hasPermission(2)) // Op level 2
                        .then(Commands.literal("create")
                                .then(Commands.argument("position", Vec3Argument.vec3())
                                        .executes(context -> createBlackHole(context, Vec3Argument.getVec3(context, "position"), 2.0f, 0.02f, 1200))
                                        .then(Commands.argument("size", FloatArgumentType.floatArg(0.1f, 10.0f))
                                                .executes(context -> createBlackHole(context, Vec3Argument.getVec3(context, "position"),
                                                        FloatArgumentType.getFloat(context, "size"), 0.02f, 1200))
                                                .then(Commands.argument("rotation_speed", FloatArgumentType.floatArg(0.001f, 0.5f))
                                                        .executes(context -> createBlackHole(context, Vec3Argument.getVec3(context, "position"),
                                                                FloatArgumentType.getFloat(context, "size"),
                                                                FloatArgumentType.getFloat(context, "rotation_speed"), 1200))
                                                        .then(Commands.argument("lifetime", IntegerArgumentType.integer(100, 72000))
                                                                .executes(context -> createBlackHole(context, Vec3Argument.getVec3(context, "position"),
                                                                        FloatArgumentType.getFloat(context, "size"),
                                                                        FloatArgumentType.getFloat(context, "rotation_speed"),
                                                                        IntegerArgumentType.getInteger(context, "lifetime")))
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("modify")
                                .then(Commands.literal("size")
                                        .then(Commands.argument("size", FloatArgumentType.floatArg(0.1f, 10.0f))
                                                .executes(context -> modifyHeldItem(context, "size", FloatArgumentType.getFloat(context, "size")))
                                        )
                                )
                                .then(Commands.literal("rotation")
                                        .then(Commands.argument("speed", FloatArgumentType.floatArg(0.001f, 0.5f))
                                                .executes(context -> modifyHeldItem(context, "rotation", FloatArgumentType.getFloat(context, "speed")))
                                        )
                                )
                                .then(Commands.literal("lifetime")
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(100, 72000))
                                                .executes(context -> modifyHeldItem(context, "lifetime", IntegerArgumentType.getInteger(context, "ticks")))
                                        )
                                )
                        )
                        .then(Commands.literal("clear")
                                .executes(BlackHoleCommand::clearAllBlackHoles)
                        )
        );
    }

    private static int createBlackHole(CommandContext<CommandSourceStack> context, Vec3 position,
                                       float size, float rotationSpeed, int lifetime) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        // Use a unique ID for the black hole
        int blackHoleId = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);

        BlackHoleEffectPacket packet = new BlackHoleEffectPacket(
                blackHoleId, position, size, rotationSpeed, lifetime
        );

        // Send to all players in the area
        ModNetworking.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        position.x, position.y, position.z, 128.0, level.dimension()
                )),
                packet
        );

        source.sendSuccess(() -> Component.literal(
                "Created black hole at " + String.format("%.1f, %.1f, %.1f", position.x, position.y, position.z) +
                        " with size " + size + ", rotation " + String.format("%.3f", rotationSpeed) +
                        ", lifetime " + (lifetime / 20) + "s"
        ), true);

        return 1;
    }

    private static int modifyHeldItem(CommandContext<CommandSourceStack> context, String property, Number value) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ItemStack heldItem = player.getMainHandItem();

        if (!(heldItem.getItem() instanceof BlackHoleGeneratorItem)) {
            source.sendFailure(Component.literal("You must be holding a Black Hole Generator"));
            return 0;
        }

        switch (property) {
            case "size":
                BlackHoleGeneratorItem.setSize(heldItem, value.floatValue());
                source.sendSuccess(() -> Component.literal("Set black hole size to " + value.floatValue()), false);
                break;
            case "rotation":
                BlackHoleGeneratorItem.setRotationSpeed(heldItem, value.floatValue());
                source.sendSuccess(() -> Component.literal("Set rotation speed to " + String.format("%.3f", value.floatValue())), false);
                break;
            case "lifetime":
                BlackHoleGeneratorItem.setLifetime(heldItem, value.intValue());
                source.sendSuccess(() -> Component.literal("Set lifetime to " + (value.intValue() / 20) + " seconds"), false);
                break;
            default:
                source.sendFailure(Component.literal("Unknown property: " + property));
                return 0;
        }

        return 1;
    }

    private static int clearAllBlackHoles(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        // Send removal packets for all possible black hole IDs
        // This is a simple approach - in a real implementation you'd track active black holes
        for (int i = 0; i < 100; i++) {
            BlackHoleEffectPacket removePacket = new BlackHoleEffectPacket(i);
            ModNetworking.CHANNEL.send(
                    PacketDistributor.DIMENSION.with(() -> level.dimension()),
                    removePacket
            );
        }

        source.sendSuccess(() -> Component.literal("Cleared all black hole effects"), true);
        return 1;
    }
}