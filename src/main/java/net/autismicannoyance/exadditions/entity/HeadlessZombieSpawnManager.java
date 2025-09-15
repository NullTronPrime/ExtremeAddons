package net.autismicannoyance.exadditions.entity;

import net.autismicannoyance.exadditions.entity.custom.HeadlessZombieEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = "exadditions")
public class HeadlessZombieSpawnManager {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        Entity entity = event.getEntity();
        if (entity instanceof HeadlessZombieEntity headlessZombie) {
            // Only prevent NATURAL spawns - allow all other spawn types (spawn eggs, commands, etc.)
            if (headlessZombie.getEntityData().get(HeadlessZombieEntity.SPAWN_TYPE) == MobSpawnType.NATURAL.ordinal()) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        // No longer auto-spawn headless zombie on world load
        // They must be spawned manually via commands or spawn eggs
    }

    private static BlockPos findSafeSpawnLocation(ServerLevel serverLevel) {
        BlockPos worldSpawn = serverLevel.getSharedSpawnPos();

        // Try to spawn within 500-1000 blocks of world spawn
        for (int attempts = 0; attempts < 50; attempts++) {
            int distance = 500 + serverLevel.random.nextInt(500); // 500-1000 blocks away
            double angle = serverLevel.random.nextDouble() * 2 * Math.PI;

            int x = worldSpawn.getX() + (int) (Math.cos(angle) * distance);
            int z = worldSpawn.getZ() + (int) (Math.sin(angle) * distance);

            // Find ground level
            for (int y = serverLevel.getMaxBuildHeight(); y > serverLevel.getMinBuildHeight(); y--) {
                BlockPos checkPos = new BlockPos(x, y, z);
                if (!serverLevel.getBlockState(checkPos).isAir() &&
                        serverLevel.getBlockState(checkPos.above()).isAir() &&
                        serverLevel.getBlockState(checkPos.above(2)).isAir()) {
                    return checkPos.above();
                }
            }
        }

        return worldSpawn; // Fallback
    }

    // Simplified SavedData class - no longer needed for tracking single zombie
    public static class HeadlessZombieData extends SavedData {
        private static final String DATA_NAME = "headless_zombie_data";

        private boolean everSpawned = false;

        public HeadlessZombieData() {}

        public HeadlessZombieData(CompoundTag tag) {
            this.everSpawned = tag.getBoolean("EverSpawned");
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("EverSpawned", this.everSpawned);
            return tag;
        }

        public static HeadlessZombieData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(HeadlessZombieData::new, HeadlessZombieData::new, DATA_NAME);
        }

        public boolean hasEverSpawned() {
            return everSpawned;
        }

        public void setEverSpawned(boolean spawned) {
            this.everSpawned = spawned;
            setDirty();
        }
    }
}