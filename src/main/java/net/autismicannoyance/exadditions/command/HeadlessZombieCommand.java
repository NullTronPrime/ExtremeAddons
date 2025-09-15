package net.autismicannoyance.exadditions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.autismicannoyance.exadditions.entity.ModEntities;
import net.autismicannoyance.exadditions.entity.HeadlessZombieSpawnManager;
import net.autismicannoyance.exadditions.entity.custom.HeadlessZombieEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Commands for controlling the Headless Zombie: spawn, kill, info, setdeaths.
 */
public final class HeadlessZombieCommand {

    private HeadlessZombieCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("headlesszombie")
                .requires(source -> source.hasPermission(2)) // op level 2
                .then(Commands.literal("spawn")
                        .executes(HeadlessZombieCommand::spawnHeadlessZombie))
                .then(Commands.literal("kill")
                        .executes(HeadlessZombieCommand::killHeadlessZombie))
                .then(Commands.literal("info")
                        .executes(HeadlessZombieCommand::getHeadlessZombieInfo))
                .then(Commands.literal("setdeaths")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(HeadlessZombieCommand::setDeathCount)))
        );
    }

    private static int spawnHeadlessZombie(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // Directly assign ServerLevel (avoids the pattern-matching same-type problem)
        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("This command can only be used on a server-side level"));
            return 0;
        }

        HeadlessZombieSpawnManager.HeadlessZombieData data = HeadlessZombieSpawnManager.HeadlessZombieData.get(serverLevel);

        if (data.hasActiveZombie()) {
            source.sendFailure(Component.literal("There is already a Headless Zombie active in the world"));
            return 0;
        }

        BlockPos spawnPos = source.getEntity() != null ? source.getEntity().blockPosition() : serverLevel.getSharedSpawnPos();

        HeadlessZombieEntity zombie = ModEntities.HEADLESS_ZOMBIE.get().create(serverLevel);
        if (zombie == null) {
            source.sendFailure(Component.literal("Failed to create Headless Zombie entity"));
            return 0;
        }

        zombie.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 0.0F, 0.0F);

        if (!serverLevel.addFreshEntity(zombie)) {
            source.sendFailure(Component.literal("Failed to spawn Headless Zombie into the world"));
            return 0;
        }

        data.setZombieUUID(zombie.getUUID());
        data.setEverSpawned(true);
        data.setDirty();

        // no captured non-final variables here
        source.sendSuccess(() -> Component.literal("Spawned Headless Zombie successfully"), true);
        return 1;
    }

    private static int killHeadlessZombie(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("This command can only be used on a server-side level"));
            return 0;
        }

        HeadlessZombieSpawnManager.HeadlessZombieData data = HeadlessZombieSpawnManager.HeadlessZombieData.get(serverLevel);

        if (!data.hasActiveZombie()) {
            source.sendFailure(Component.literal("No active Headless Zombie found"));
            return 0;
        }

        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeadlessZombieEntity zombie && zombie.getUUID().equals(data.getZombieUUID())) {
                zombie.kill();
                data.clearZombieUUID();
                data.setDirty();
                source.sendSuccess(() -> Component.literal("Killed the Headless Zombie"), true);
                return 1;
            }
        }

        data.clearZombieUUID();
        data.setDirty();
        source.sendFailure(Component.literal("Headless Zombie not found (cleared from records)"));
        return 0;
    }

    private static int getHeadlessZombieInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("This command can only be used on a server-side level"));
            return 0;
        }

        HeadlessZombieSpawnManager.HeadlessZombieData data = HeadlessZombieSpawnManager.HeadlessZombieData.get(serverLevel);

        if (!data.hasActiveZombie()) {
            source.sendSuccess(() -> Component.literal("No active Headless Zombie in the world"), false);
            return 1;
        }

        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeadlessZombieEntity zombie && zombie.getUUID().equals(data.getZombieUUID())) {
                final int deathCount = zombie.getDeathCount();
                final String lastDeathSource = zombie.getLastDeathSource();
                final BlockPos pos = zombie.blockPosition();

                double attackDamageValue = 0.0;
                if (zombie.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                    attackDamageValue = zombie.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
                }

                // Build the message in a final local before the lambda
                final String infoMsg = String.format(
                        "Headless Zombie Info:\n" +
                                "Deaths: %d\n" +
                                "Last Death Source: %s\n" +
                                "Position: %d, %d, %d\n" +
                                "Health: %.1f/%.1f\n" +
                                "Attack Damage: %.1f",
                        deathCount,
                        (lastDeathSource == null || lastDeathSource.isEmpty()) ? "None" : lastDeathSource,
                        pos.getX(), pos.getY(), pos.getZ(),
                        zombie.getHealth(), zombie.getMaxHealth(),
                        attackDamageValue
                );

                source.sendSuccess(() -> Component.literal(infoMsg), false);
                return 1;
            }
        }

        data.clearZombieUUID();
        data.setDirty();
        source.sendFailure(Component.literal("Headless Zombie not found (cleared from records)"));
        return 0;
    }

    private static int setDeathCount(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        final int deathCount = IntegerArgumentType.getInteger(context, "count");

        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("This command can only be used on a server-side level"));
            return 0;
        }

        HeadlessZombieSpawnManager.HeadlessZombieData data = HeadlessZombieSpawnManager.HeadlessZombieData.get(serverLevel);

        if (!data.hasActiveZombie()) {
            source.sendFailure(Component.literal("No active Headless Zombie found"));
            return 0;
        }

        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeadlessZombieEntity zombie && zombie.getUUID().equals(data.getZombieUUID())) {
                // Set the synched entity data (same as your entity uses)
                zombie.getEntityData().set(HeadlessZombieEntity.DEATH_COUNT, deathCount);

                // Recalculate stats (use same values as your entity's applyStatUpgrades)
                final double newMaxHealth = 20.0 + (deathCount * 2.0); // BASE_HEALTH + 2 per death
                if (zombie.getAttribute(Attributes.MAX_HEALTH) != null) {
                    zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMaxHealth);
                }
                zombie.setHealth((float) newMaxHealth);

                final double newDamage = 3.0 + deathCount; // BASE_DAMAGE + deathCount
                if (zombie.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                    zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(newDamage);
                }

                final double speedMultiplier = 1.0 + (deathCount * 0.1); // +10% per death
                final double newSpeed = 0.23 * speedMultiplier; // BASE_SPEED * multiplier
                if (zombie.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                    zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(newSpeed);
                }

                // Pre-build the message string (final) so lambda doesn't capture a non-final variable.
                final String msg = String.format(
                        "Set Headless Zombie death count to %d\n" +
                                "New Health: %.1f\n" +
                                "New Attack Damage: %.1f\n" +
                                "New Speed: %.3f",
                        deathCount, newMaxHealth, newDamage, newSpeed
                );

                source.sendSuccess(() -> Component.literal(msg), true);
                return 1;
            }
        }

        source.sendFailure(Component.literal("Headless Zombie not found"));
        return 0;
    }
}
