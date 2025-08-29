package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.item.custom.DeathCharmItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "exadditions")
public class DeathCharmEvents {

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
}