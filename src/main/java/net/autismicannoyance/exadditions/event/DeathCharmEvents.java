package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.item.custom.DeathCharmItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Iterator;

@Mod.EventBusSubscriber(modid = "exadditions")
public class DeathCharmEvents {

    private static final String DEATH_CHARM_SLOT_TAG = "DeathCharmSlot";
    private static final String DEATH_CHARM_DATA_TAG = "DeathCharmData";

    /** When player dies, record the source of death */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack charm = getDeathCharm(player);
        if (charm.isEmpty()) return;

        DamageSource source = event.getSource();
        String key = getDeathKey(source);

        // Save into player persistent data
        CompoundTag persistent = player.getPersistentData();
        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);

        CompoundTag map = persisted.getCompound(DeathCharmItem.DEATH_MAP_TAG);
        int prev = map.getInt(key);
        map.putInt(key, prev + 1);

        persisted.put(DeathCharmItem.DEATH_MAP_TAG, map);
        persistent.put(Player.PERSISTED_NBT_TAG, persisted);

        // Also copy into item NBT so tooltip works
        CompoundTag itemTag = charm.getOrCreateTag();
        itemTag.put(DeathCharmItem.DEATH_MAP_TAG, map.copy());

        // Store the charm's slot and data for restoration
        int slot = getDeathCharmSlot(player);
        if (slot != -1) {
            persisted.putInt(DEATH_CHARM_SLOT_TAG, slot);
            persisted.put(DEATH_CHARM_DATA_TAG, charm.save(new CompoundTag()));
        }
    }

    /** Apply reductions */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack charm = getDeathCharm(player);
        if (charm.isEmpty()) return;

        DamageSource source = event.getSource();
        String key = getDeathKey(source);

        CompoundTag map = charm.getOrCreateTag().getCompound(DeathCharmItem.DEATH_MAP_TAG);

        int deaths = map.getInt(key);
        if (deaths > 0) {
            double multiplier = Math.pow(0.5, deaths);
            float newAmount = (float) (event.getAmount() * multiplier);
            event.setAmount(newAmount);
        }
    }

    /** Prevent Death Charm from being dropped on death */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Remove Death Charm from drops to prevent duplication
        Iterator<ItemEntity> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemEntity itemEntity = iterator.next();
            if (itemEntity.getItem().getItem() instanceof DeathCharmItem) {
                iterator.remove();
            }
        }
    }

    /** Restore Death Charm when player respawns */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        CompoundTag persistent = player.getPersistentData();
        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);

        if (persisted.contains(DEATH_CHARM_DATA_TAG)) {
            // Restore the charm to the same slot
            ItemStack charm = ItemStack.of(persisted.getCompound(DEATH_CHARM_DATA_TAG));
            int slot = persisted.getInt(DEATH_CHARM_SLOT_TAG);

            // Make sure the slot is valid
            if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
                player.getInventory().setItem(slot, charm);
            } else {
                // If slot is invalid, put it in the first available slot
                if (!player.getInventory().add(charm)) {
                    // If inventory is full, force it into slot 0
                    player.getInventory().setItem(0, charm);
                }
            }

            // Clean up the temporary data
            persisted.remove(DEATH_CHARM_DATA_TAG);
            persisted.remove(DEATH_CHARM_SLOT_TAG);
        }
    }

    /** Prevent Death Charm from expiring (despawning) */
    @SubscribeEvent
    public static void onItemExpire(ItemExpireEvent event) {
        if (event.getEntity().getItem().getItem() instanceof DeathCharmItem) {
            event.setCanceled(true);
            // Reset the age to keep it fresh
            event.getEntity().setUnlimitedLifetime();
        }
    }

    /** Handle Death Charm falling into void - teleport to world spawn */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        if (!(itemEntity.getItem().getItem() instanceof DeathCharmItem)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // Check if the item is below Y-level -64 (void level in most dimensions)
        if (itemEntity.getY() < -64) {
            BlockPos spawnPos = serverLevel.getSharedSpawnPos();
            // Teleport to world spawn with a bit of height to prevent getting stuck
            itemEntity.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
            // Reset velocity to prevent it from continuing to fall
            itemEntity.setDeltaMovement(0, 0, 0);
        }
    }

    /** Prevent Death Charm from being destroyed by explosions */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        // Remove Death Charm items from the explosion's affected entities
        event.getAffectedEntities().removeIf(entity -> {
            if (entity instanceof ItemEntity itemEntity) {
                return itemEntity.getItem().getItem() instanceof DeathCharmItem;
            }
            return false;
        });
    }

    /** Track when Death Charm is tossed to handle void teleportation */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getEntity().getItem().getItem() instanceof DeathCharmItem)) return;

        // Add a custom tag to track this as a Death Charm for void detection
        ItemEntity itemEntity = event.getEntity();
        CompoundTag entityData = itemEntity.getPersistentData();
        entityData.putBoolean("DeathCharm", true);
    }

    /**
     * Generate a specific death key based on the damage source
     * For mob damage, use the entity type instead of generic damage type
     */
    private static String getDeathKey(DamageSource source) {
        Entity directEntity = source.getDirectEntity();
        Entity entity = source.getEntity();

        // If damage is from a living entity (mob), use the entity type
        if (directEntity instanceof LivingEntity && !(directEntity instanceof Player)) {
            ResourceLocation entityType = ForgeRegistries.ENTITY_TYPES.getKey(directEntity.getType());
            if (entityType != null) {
                return "mob." + entityType.toString();
            }
        }

        // If indirect damage from a living entity (like projectiles), use the source entity
        if (entity instanceof LivingEntity && !(entity instanceof Player) && directEntity != entity) {
            ResourceLocation entityType = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (entityType != null) {
                return "mob." + entityType.toString();
            }
        }

        // For non-mob damage sources, use the original damage type
        return source.type().msgId();
    }

    /** Find the charm in inventory */
    private static ItemStack getDeathCharm(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof DeathCharmItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Find the slot number of the Death Charm in inventory */
    private static int getDeathCharmSlot(Player player) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.getItem() instanceof DeathCharmItem) {
                return i;
            }
        }
        return -1;
    }
}