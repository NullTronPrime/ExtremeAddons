package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.item.custom.DeathCharmItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
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

    /** When player dies, record the source of death ONLY in the current charm */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack charm = getDeathCharm(player);
        if (charm.isEmpty()) return;

        DamageSource source = event.getSource();
        String key = getDeathKey(source);

        // Initialize the charm with a unique ID if it doesn't have one
        CompoundTag itemTag = charm.getOrCreateTag();
        if (!itemTag.contains("CharmUUID")) {
            itemTag.putString("CharmUUID", java.util.UUID.randomUUID().toString());
        }

        // Store data ONLY in THIS charm's NBT, never in player data
        CompoundTag map = itemTag.getCompound(DeathCharmItem.DEATH_MAP_TAG);
        int prev = map.getInt(key);
        map.putInt(key, prev + 1);
        itemTag.put(DeathCharmItem.DEATH_MAP_TAG, map);

        // Store the charm's slot and data for restoration after respawn
        CompoundTag persistent = player.getPersistentData();
        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        int slot = getDeathCharmSlot(player);
        if (slot != -1) {
            persisted.putInt(DEATH_CHARM_SLOT_TAG, slot);
            persisted.put(DEATH_CHARM_DATA_TAG, charm.save(new CompoundTag()));
        }
    }

    /** Apply damage reductions with scaled exponential formula - reads ONLY from current charm */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack charm = getDeathCharm(player);
        if (charm.isEmpty()) return;

        DamageSource source = event.getSource();
        String key = getDeathKey(source);

        // Read death count ONLY from this specific charm's NBT data
        CompoundTag itemTag = charm.getOrCreateTag();
        CompoundTag map = itemTag.getCompound(DeathCharmItem.DEATH_MAP_TAG);

        int deaths = map.getInt(key); // This will be 0 for new charms
        if (deaths > 0) {
            // Exponential scaling: 10 deaths = 50%, 20 deaths = 75%, 30 deaths = 87.5%, etc.
            // Formula: multiplier = 0.5^(deaths/10)
            double multiplier = Math.pow(0.5, deaths / 10.0);
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
            // Restore the exact same charm instance with all its data
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
            // Set unlimited lifetime
            event.getEntity().setUnlimitedLifetime();
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

    /** Make Death Charm invulnerable and handle void teleportation when tossed */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getEntity().getItem().getItem() instanceof DeathCharmItem)) return;

        // Make the item invulnerable to all damage (cactus, fire, etc.)
        ItemEntity itemEntity = event.getEntity();
        itemEntity.setInvulnerable(true);

        // Add a custom tag to track this as a Death Charm
        CompoundTag entityData = itemEntity.getPersistentData();
        entityData.putBoolean("DeathCharm", true);
    }

    /** Make any Death Charm item entity invulnerable and handle void teleportation */
    @SubscribeEvent
    public static void onDeathCharmSpawn(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        if (!(itemEntity.getItem().getItem() instanceof DeathCharmItem)) return;

        // Make invulnerable to prevent damage from cactus, fire, lava, etc.
        itemEntity.setInvulnerable(true);

        // Handle void teleportation
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (itemEntity.getY() < -64) {
            BlockPos spawnPos = serverLevel.getSharedSpawnPos();
            // Teleport to world spawn with a bit of height to prevent getting stuck
            itemEntity.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5);
            // Reset velocity to prevent it from continuing to fall
            itemEntity.setDeltaMovement(0, 0, 0);
        }
    }

    /** Ensure Death Charm is properly initialized when picked up (starts fresh) */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        ItemStack stack = event.getItem().getItem();
        if (stack.getItem() instanceof DeathCharmItem) {
            CompoundTag itemTag = stack.getOrCreateTag();
            // Only initialize if it doesn't already have a UUID (brand new charm)
            if (!itemTag.contains("CharmUUID")) {
                itemTag.putString("CharmUUID", java.util.UUID.randomUUID().toString());
                // Ensure it starts with completely empty death data
                itemTag.put(DeathCharmItem.DEATH_MAP_TAG, new CompoundTag());
            }
        }
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