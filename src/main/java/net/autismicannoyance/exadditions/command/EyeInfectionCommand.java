package net.autismicannoyance.exadditions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.event.EyeInfectionEventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Debug command for testing the eye infection system.
 *
 * Registers itself on the FORGE event bus so the IDE warning "class is never used"
 * will disappear and the command will be registered automatically.
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EyeInfectionCommand {

    /**
     * Event handler that registers the command dispatcher when the RegisterCommandsEvent is fired.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * Registers the /eyeinfect command and subcommands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("eyeinfect")
                        .requires(source -> source.hasPermission(2)) // OP required
                        .then(Commands.literal("nearby")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                                        .executes(EyeInfectionCommand::infectNearby)
                                )
                                .executes(ctx -> infectNearby(ctx, 10)) // default radius
                        )
                        .then(Commands.literal("cure")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                                        .executes(EyeInfectionCommand::cureNearby)
                                )
                                .executes(ctx -> cureNearby(ctx, 10)) // default radius
                        )
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            source.sendSuccess(() -> Component.literal("Eye Infection Commands:")
                                            .withStyle(ChatFormatting.YELLOW),
                                    false);
                            source.sendSuccess(() -> Component.literal("/eyeinfect nearby [radius] - Infect nearby mobs")
                                            .withStyle(ChatFormatting.GRAY),
                                    false);
                            source.sendSuccess(() -> Component.literal("/eyeinfect cure [radius] - Cure nearby infected mobs")
                                            .withStyle(ChatFormatting.GRAY),
                                    false);
                            return 1;
                        })
        );
    }

    private static int infectNearby(CommandContext<CommandSourceStack> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        return infectNearby(context, radius);
    }

    private static int infectNearby(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();
        Entity sourceEntity = source.getEntity();

        if (sourceEntity == null) {
            source.sendFailure(Component.literal("Must be executed by an entity"));
            return 0;
        }

        Vec3 center = sourceEntity.position();
        AABB searchArea = new AABB(center, center).inflate(radius);

        List<LivingEntity> targets = sourceEntity.level().getEntitiesOfClass(
                LivingEntity.class,
                searchArea,
                entity -> !(entity instanceof Player) && !entity.isRemoved() && entity.getHealth() > 0
        );

        int infected = 0;
        for (LivingEntity target : targets) {
            if (EyeInfectionEventHandler.infectMob(target)) {
                infected++;
                final String entityName = target.getDisplayName().getString(); // final for lambda capture
                source.sendSuccess(() -> Component.literal("Infected: " + entityName)
                                .withStyle(ChatFormatting.DARK_RED),
                        false);
            }
        }

        if (infected > 0) {
            final int fInfected = infected;
            final int fRadius = radius;
            source.sendSuccess(() -> Component.literal("Successfully infected " + fInfected + " mobs within " + fRadius + " blocks")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        } else {
            final int fRadius = radius;
            source.sendSuccess(() -> Component.literal("No suitable targets found within " + fRadius + " blocks")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }

        return infected;
    }

    private static int cureNearby(CommandContext<CommandSourceStack> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        return cureNearby(context, radius);
    }

    private static int cureNearby(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();
        Entity sourceEntity = source.getEntity();

        if (sourceEntity == null) {
            source.sendFailure(Component.literal("Must be executed by an entity"));
            return 0;
        }

        Vec3 center = sourceEntity.position();
        AABB searchArea = new AABB(center, center).inflate(radius);

        List<LivingEntity> targets = sourceEntity.level().getEntitiesOfClass(
                LivingEntity.class,
                searchArea,
                entity -> !(entity instanceof Player) && !entity.isRemoved() &&
                        EyeInfectionEventHandler.isEntityInfected(entity)
        );

        int cured = 0;
        for (LivingEntity target : targets) {
            EyeInfectionEventHandler.cureInfection(target);
            cured++;
            final String entityName = target.getDisplayName().getString(); // final for lambda capture
            source.sendSuccess(() -> Component.literal("Cured: " + entityName)
                            .withStyle(ChatFormatting.BLUE),
                    false);
        }

        if (cured > 0) {
            final int fCured = cured;
            final int fRadius = radius;
            source.sendSuccess(() -> Component.literal("Successfully cured " + fCured + " infected mobs within " + fRadius + " blocks")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        } else {
            final int fRadius = radius;
            source.sendSuccess(() -> Component.literal("No infected mobs found within " + fRadius + " blocks")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }

        return cured;
    }
}
