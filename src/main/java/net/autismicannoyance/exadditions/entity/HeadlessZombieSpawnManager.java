package net.autismicannoyance.exadditions.entity;

import net.autismicannoyance.exadditions.entity.custom.HeadlessZombieEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
            ServerLevel serverLevel = (ServerLevel) event.getLevel();
            HeadlessZombieData data = HeadlessZombieData.get(serverLevel);

            // Check if there's already a headless zombie in the world
            if (data.hasActiveZombie()) {
                UUID existingId = data.getZombieUUID();

                // If this isn't the existing zombie, cancel the spawn
                if (!headlessZombie.getUUID().equals(existingId)) {
                    event.setCanceled(true);
                    return;
                }
            }

            // Register this zombie as the active one
            data.setZombieUUID(headlessZombie.getUUID());
            data.setDirty();
        }
    }

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        HeadlessZombieData data = HeadlessZombieData.get(serverLevel);

        // Check if we need to spawn the initial headless zombie
        if (!data.hasActiveZombie() && !data.hasEverSpawned()) {
            spawnInitialHeadlessZombie(serverLevel);
            data.setEverSpawned(true);
            data.setDirty();
        }
    }

    private static void spawnInitialHeadlessZombie(ServerLevel serverLevel) {
        HeadlessZombieEntity zombie = ModEntities.HEADLESS_ZOMBIE.get().create(serverLevel);
        if (zombie != null) {
            // Find a safe spawn location
            BlockPos spawnPos = findSafeSpawnLocation(serverLevel);
            zombie.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);

            serverLevel.addFreshEntity(zombie);

            HeadlessZombieData data = HeadlessZombieData.get(serverLevel);
            data.setZombieUUID(zombie.getUUID());
            data.setDirty();
        }
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

    // SavedData class to track the headless zombie across world saves/loads
    public static class HeadlessZombieData extends SavedData {
        private static final String DATA_NAME = "headless_zombie_data";

        private UUID zombieUUID;
        private boolean everSpawned = false;

        public HeadlessZombieData() {}

        public HeadlessZombieData(CompoundTag tag) {
            if (tag.contains("ZombieUUID")) {
                this.zombieUUID = tag.getUUID("ZombieUUID");
            }
            this.everSpawned = tag.getBoolean("EverSpawned");
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            if (this.zombieUUID != null) {
                tag.putUUID("ZombieUUID", this.zombieUUID);
            }
            tag.putBoolean("EverSpawned", this.everSpawned);
            return tag;
        }

        public static HeadlessZombieData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(HeadlessZombieData::new, HeadlessZombieData::new, DATA_NAME);
        }

        public boolean hasActiveZombie() {
            return zombieUUID != null;
        }

        public UUID getZombieUUID() {
            return zombieUUID;
        }

        public void setZombieUUID(UUID uuid) {
            this.zombieUUID = uuid;
            setDirty();
        }

        public void clearZombieUUID() {
            this.zombieUUID = null;
            setDirty();
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