package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.item.custom.BloodCharmItem;
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
public class BloodCharmEvents {

    /** When player kills a mob, record the kill */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        // Check if the entity that died is a mob (not a player)
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return;

        // Check if the damage source is from a player
        DamageSource source = event.getSource();
        Entity sourceEntity = source.getEntity();
        if (!(sourceEntity instanceof Player player)) return;

        ItemStack charm = getBloodCharm(player);
        if (charm.isEmpty()) return;

        // Get the mob type key
        String key = getMobKey(victim);
        if (key == null) return;

        // Store kill data and mob health only in the specific charm's NBT
        CompoundTag itemTag = charm.getOrCreateTag();
        CompoundTag killMap = itemTag.getCompound(BloodCharmItem.KILL_MAP_TAG);
        CompoundTag healthMap = itemTag.getCompound(BloodCharmItem.HEALTH_MAP_TAG);

        int prev = killMap.getInt(key);
        killMap.putInt(key, prev + 1);
        healthMap.putFloat(key, victim.getMaxHealth()); // Store mob's max health

        itemTag.put(BloodCharmItem.KILL_MAP_TAG, killMap);
        itemTag.put(BloodCharmItem.HEALTH_MAP_TAG, healthMap);
    }

    /** Apply damage bonuses when attacking mobs */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        // Check if the entity being damaged is a mob (not a player)
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return;

        // Check if the damage source is from a player
        DamageSource source = event.getSource();
        Entity sourceEntity = source.getEntity();
        if (!(sourceEntity instanceof Player player)) return;

        ItemStack charm = getBloodCharm(player);
        if (charm.isEmpty()) return;

        // Get the mob type key
        String key = getMobKey(victim);
        if (key == null) return;

        CompoundTag map = charm.getOrCreateTag().getCompound(BloodCharmItem.KILL_MAP_TAG);

        int kills = map.getInt(key);
        if (kills > 0) {
            // Get mob's max health and calculate scaling based on Warden health ratio
            float mobMaxHealth = victim.getMaxHealth();
            float wardenHealth = 500.0f; // Warden has 500 health points
            float healthRatio = mobMaxHealth / wardenHealth;

            // Base damage scaling: (n^0.5) * log(n) scaled by mob health ratio
            double baseMultiplier = Math.sqrt(kills) * Math.log(kills);
            double scaledMultiplier = 1.0 + (baseMultiplier * healthRatio);

            float newAmount = (float) (event.getAmount() * scaledMultiplier);
            event.setAmount(newAmount);
        }
    }

    /**
     * Generate a mob key based on the entity type
     */
    private static String getMobKey(LivingEntity entity) {
        ResourceLocation entityType = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityType != null) {
            return "mob." + entityType.toString();
        }
        return null;
    }

    /** Find the charm in inventory */
    private static ItemStack getBloodCharm(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof BloodCharmItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}