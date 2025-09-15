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

public class HeadlessZombieCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("headlesszombie")
                .requires(source -> source.hasPermission(2)) // Requires op level 2
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

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("This command can only be used in the overworld"));
            return 0;
        }

        HeadlessZombieSpawnManager.HeadlessZombieData data = HeadlessZombieSpawnManager.HeadlessZombieData.get(serverLevel);

        // Check if there's already an active headless zombie
        if (data.hasActiveZombie()) {
            source.sendFailure(Component.literal("There is already a Headless Zombie active in the world"));
            return 0;
        }

        // Spawn near the command executor
        BlockPos spawnPos = source.getEntity() != null ? source.getEntity().blockPosition() : serverLevel.getSharedSpawnPos();

        HeadlessZombieEntity zombie = ModEntities.HEADLESS_ZOMBIE.get().create(serverLevel);
        if (zombie != null) {
            zombie.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5, 0.0F, 0.0F);

            if (serverLevel.addFreshEntity(zombie)) {
                data.setZombieUUID(zombie.getUUID());
                data.setEverSpawned(true);
                data.setDirty();

                source.sendSuccess(() -> Component.literal("Spawned Headless Zombie successfully"), true);
                return 1;
            }
        }

        source.sendFailure(Component.literal("Failed to spawn Headless Zombie"));
        return 0;
    }

    private static int killHeadlessZombie(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("This command can only be used in the overworld"));
            return 0;
        }

        HeadlessZombieSpawnManager.HeadlessZombieData data = HeadlessZombieSpawnManager.HeadlessZombieData.get(serverLevel);

        if (!data.hasActiveZombie()) {
            source.sendFailure(Component.literal("No active Headless Zombie found"));
            return 0;
        }

        // Find and kill the headless zombie
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeadlessZombieEntity zombie && zombie.getUUID().equals(data.getZombieUUID())) {
                zombie.kill();
                data.clearZombieUUID();
                data.setDirty();
                source.sendSuccess(() -> Component.literal("Killed the Headless Zombie"), true);
                return 1;
            }
        }

        // Zombie not found, clear the data
        data.clearZombieUUID();
        data.setDirty();
        source.sendFailure(Component.literal("Headless Zombie not found (cleared from records)"));
        return 0;
    }

    private static int getHeadlessZombieInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("This command can only be used in the overworld"));
            return 0;
        }

        HeadlessZombieSpawnManager.HeadlessZombieData data = HeadlessZombieSpawnManager.HeadlessZombieData.get(serverLevel);

        if (!data.hasActiveZombie()) {
            source.sendSuccess(() -> Component.literal("No active Headless Zombie in the world"), false);
            return 1;
        }

        // Find the headless zombie and get its info
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeadlessZombieEntity zombie && zombie.getUUID().equals(data.getZombieUUID())) {
                int deathCount = zombie.getDeathCount();
                String lastDeathSource = zombie.getLastDeathSource();
                BlockPos pos = zombie.blockPosition();

                source.sendSuccess(() -> Component.literal(String.format(
                        "Headless Zombie Info:\n" +
                                "Deaths: %d\n" +
                                "Last Death Source: %s\n" +
                                "Position: %d, %d, %d\n" +
                                "Health: %.1f/%.1f\n" +
                                "Attack Damage: %.1f",
                        deathCount,
                        lastDeathSource.isEmpty() ? "None" : lastDeathSource,
                        pos.getX(), pos.getY(), pos.getZ(),
                        zombie.getHealth(), zombie.getMaxHealth(),
                        zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                )), false);
                return 1;
            }
        }

        // Zombie not found
        data.clearZombieUUID();
        data.setDirty();
        source.sendFailure(Component.literal("Headless Zombie not found (cleared from records)"));
        return 0;
    }

    private static int setDeathCount(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int deathCount = IntegerArgumentType.getInteger(context, "count");

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("This command can only be used in the overworld"));
            return 0;
        }

        HeadlessZombieSpawnManager.HeadlessZombieData data = HeadlessZombieSpawnManager.HeadlessZombieData.get(serverLevel);

        if (!data.hasActiveZombie()) {
            source.sendFailure(Component.literal("No active Headless Zombie found"));
            return 0;
        }

        // Find and modify the headless zombie
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeadlessZombieEntity zombie && zombie.getUUID().equals(data.getZombieUUID())) {
                // Set the death count and update stats
                zombie.getEntityData().set(zombie.getEntityData().defineId(HeadlessZombieEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT), deathCount);

                // Recalculate stats based on new death count
                double newMaxHealth = 20.0 + (deathCount * 2.0);
                zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(newMaxHealth);
                zombie.setHealth((float) newMaxHealth);

                double newDamage = 3.0 + deathCount;
                zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(newDamage);

                double speedMultiplier = 1.0 + (deathCount * 0.1);
                double newSpeed = 0.23 * speedMultiplier;
                zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(newSpeed);

                source.sendSuccess(() -> Component.literal(String.format(
                        "Set Headless Zombie death count to %d\n" +
                                "New Health: %.1f\n" +
                                "New Attack Damage: %.1f\n" +
                                "New Speed: %.3f",
                        deathCount, newMaxHealth, newDamage, newSpeed
                )), true);
                return 1;
            }
        }

        source.sendFailure(Component.literal("Headless Zombie not found"));
        return 0;
    }
}