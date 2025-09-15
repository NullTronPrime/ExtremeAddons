package net.autismicannoyance.exadditions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.autismicannoyance.exadditions.entity.ModEntities;
import net.autismicannoyance.exadditions.entity.custom.HeadlessZombieEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Commands for controlling Headless Zombies: spawn, kill, info, setdeaths, count.
 */
public final class HeadlessZombieCommand {

    private HeadlessZombieCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("headlesszombie")
                .requires(source -> source.hasPermission(2)) // op level 2
                .then(Commands.literal("spawn")
                        .executes(HeadlessZombieCommand::spawnHeadlessZombie))
                .then(Commands.literal("kill")
                        .executes(HeadlessZombieCommand::killAllHeadlessZombies))
                .then(Commands.literal("killall")
                        .executes(HeadlessZombieCommand::killAllHeadlessZombies))
                .then(Commands.literal("info")
                        .executes(HeadlessZombieCommand::getHeadlessZombieInfo))
                .then(Commands.literal("count")
                        .executes(HeadlessZombieCommand::getHeadlessZombieCount))
                .then(Commands.literal("setdeaths")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(HeadlessZombieCommand::setDeathCount)))
        );
    }

    private static int spawnHeadlessZombie(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("This command can only be used on a server-side level"));
            return 0;
        }

        BlockPos spawnPos = source.getEntity() != null ? source.getEntity().blockPosition() : serverLevel.getSharedSpawnPos();

        HeadlessZombieEntity zombie = ModEntities.HEADLESS_ZOMBIE.get().create(serverLevel);
        if (zombie == null) {
            source.sendFailure(Component.literal("Failed to create Headless Zombie entity"));
            return 0;
        }

        zombie.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 0.0F, 0.0F);
        zombie.setSpawnType(MobSpawnType.COMMAND); // Set spawn type to command

        if (!serverLevel.addFreshEntity(zombie)) {
            source.sendFailure(Component.literal("Failed to spawn Headless Zombie into the world"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Spawned Headless Zombie successfully"), true);
        return 1;
    }

    private static int killAllHeadlessZombies(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("This command can only be used on a server-side level"));
            return 0;
        }

        List<HeadlessZombieEntity> headlessZombies = new ArrayList<>();

        // Find all headless zombies
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeadlessZombieEntity zombie) {
                headlessZombies.add(zombie);
            }
        }

        if (headlessZombies.isEmpty()) {
            source.sendFailure(Component.literal("No Headless Zombies found"));
            return 0;
        }

        // Kill all found zombies
        int killedCount = 0;
        for (HeadlessZombieEntity zombie : headlessZombies) {
            zombie.kill();
            killedCount++;
        }

        final int finalKilledCount = killedCount;
        source.sendSuccess(() -> Component.literal("Killed " + finalKilledCount + " Headless Zombie(s)"), true);
        return killedCount;
    }

    private static int getHeadlessZombieCount(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("This command can only be used on a server-side level"));
            return 0;
        }

        int count = 0;

        // Count all headless zombies
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeadlessZombieEntity) {
                count++;
            }
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("Found " + finalCount + " Headless Zombie(s) in the world"), false);
        return count;
    }

    private static int getHeadlessZombieInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("This command can only be used on a server-side level"));
            return 0;
        }

        List<HeadlessZombieEntity> headlessZombies = new ArrayList<>();

        // Find all headless zombies
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeadlessZombieEntity zombie) {
                headlessZombies.add(zombie);
            }
        }

        if (headlessZombies.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Headless Zombies found in the world"), false);
            return 0;
        }

        // Show info for all zombies
        StringBuilder infoBuilder = new StringBuilder();
        infoBuilder.append("Found ").append(headlessZombies.size()).append(" Headless Zombie(s):\n\n");

        for (int i = 0; i < headlessZombies.size(); i++) {
            HeadlessZombieEntity zombie = headlessZombies.get(i);
            final int deathCount = zombie.getDeathCount();
            final String lastDeathSource = zombie.getLastDeathSource();
            final BlockPos pos = zombie.blockPosition();

            double attackDamageValue = 0.0;
            if (zombie.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                attackDamageValue = zombie.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
            }

            infoBuilder.append("Zombie #").append(i + 1).append(":\n");
            infoBuilder.append("  Deaths: ").append(deathCount).append("\n");
            infoBuilder.append("  Last Death Source: ")
                    .append((lastDeathSource == null || lastDeathSource.isEmpty()) ? "None" : lastDeathSource)
                    .append("\n");
            infoBuilder.append("  Position: ").append(pos.getX()).append(", ")
                    .append(pos.getY()).append(", ").append(pos.getZ()).append("\n");
            infoBuilder.append("  Health: ").append(String.format("%.1f", zombie.getHealth()))
                    .append("/").append(String.format("%.1f", zombie.getMaxHealth())).append("\n");
            infoBuilder.append("  Attack Damage: ").append(String.format("%.1f", attackDamageValue)).append("\n");

            if (i < headlessZombies.size() - 1) {
                infoBuilder.append("\n");
            }
        }

        final String finalInfo = infoBuilder.toString();
        source.sendSuccess(() -> Component.literal(finalInfo), false);
        return headlessZombies.size();
    }

    private static int setDeathCount(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        final int deathCount = IntegerArgumentType.getInteger(context, "count");

        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("This command can only be used on a server-side level"));
            return 0;
        }

        List<HeadlessZombieEntity> headlessZombies = new ArrayList<>();

        // Find all headless zombies
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeadlessZombieEntity zombie) {
                headlessZombies.add(zombie);
            }
        }

        if (headlessZombies.isEmpty()) {
            source.sendFailure(Component.literal("No Headless Zombies found"));
            return 0;
        }

        // Update all zombies
        int updatedCount = 0;
        for (HeadlessZombieEntity zombie : headlessZombies) {
            // Set the synched entity data
            zombie.getEntityData().set(HeadlessZombieEntity.DEATH_COUNT, deathCount);

            // Recalculate stats (use same values as the entity's applyStatUpgrades)
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

            updatedCount++;
        }

        final int finalUpdatedCount = updatedCount;
        final double newMaxHealth = 20.0 + (deathCount * 2.0);
        final double newDamage = 3.0 + deathCount;
        final double newSpeed = 0.23 * (1.0 + (deathCount * 0.1));

        final String msg = String.format(
                "Updated %d Headless Zombie(s) death count to %d\n" +
                        "New Health: %.1f\n" +
                        "New Attack Damage: %.1f\n" +
                        "New Speed: %.3f",
                finalUpdatedCount, deathCount, newMaxHealth, newDamage, newSpeed
        );

        source.sendSuccess(() -> Component.literal(msg), true);
        return updatedCount;
    }
}