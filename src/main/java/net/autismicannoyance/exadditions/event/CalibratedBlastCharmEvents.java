package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.item.custom.CalibratedBlastCharmItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "exadditions")
public class CalibratedBlastCharmEvents {

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Check if damage is from explosion
        if (!event.getSource().is(DamageTypeTags.IS_EXPLOSION)) return;

        ItemStack charm = getCalibratedBlastCharm(player);
        if (charm.isEmpty()) return;

        CompoundTag itemTag = charm.getOrCreateTag();
        float currentDamage = event.getAmount();

        // Store the original damage before any reductions for tracking purposes
        float totalBlastDamage = itemTag.getFloat(CalibratedBlastCharmItem.BLAST_DAMAGE_TAG);

        // Calculate protection based on accumulated damage (10x slower progression)
        // Formula: protection = min(90%, log(damage/10 + 1) * 15%)
        double protection = Math.min(0.9, Math.log(totalBlastDamage / 10.0 + 1) * 0.15);

        // Apply damage reduction
        float reducedDamage = currentDamage * (1.0f - (float)protection);
        event.setAmount(reducedDamage);

        // Track the original damage amount (before reduction) for calibration
        totalBlastDamage += currentDamage;
        itemTag.putFloat(CalibratedBlastCharmItem.BLAST_DAMAGE_TAG, totalBlastDamage);
    }

    /** Prevent Calibrated Blast Charm from expiring (despawning) */
    @SubscribeEvent
    public static void onItemExpire(ItemExpireEvent event) {
        if (event.getEntity().getItem().getItem() instanceof CalibratedBlastCharmItem) {
            event.setCanceled(true);
            // Set unlimited lifetime
            event.getEntity().setUnlimitedLifetime();
        }
    }

    /** Prevent Calibrated Blast Charm from being destroyed by explosions */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        // Remove Calibrated Blast Charm items from the explosion's affected entities
        event.getAffectedEntities().removeIf(entity -> {
            if (entity instanceof ItemEntity itemEntity) {
                return itemEntity.getItem().getItem() instanceof CalibratedBlastCharmItem;
            }
            return false;
        });
    }

    /** Make Calibrated Blast Charm invulnerable when tossed */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getEntity().getItem().getItem() instanceof CalibratedBlastCharmItem)) return;

        // Make the item invulnerable to all damage (explosions, fire, etc.)
        ItemEntity itemEntity = event.getEntity();
        itemEntity.setInvulnerable(true);

        // Add a custom tag to track this as a Calibrated Blast Charm
        CompoundTag entityData = itemEntity.getPersistentData();
        entityData.putBoolean("CalibratedBlastCharm", true);
    }

    /** Make any Calibrated Blast Charm item entity invulnerable */
    @SubscribeEvent
    public static void onCalibratedBlastCharmSpawn(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        if (!(itemEntity.getItem().getItem() instanceof CalibratedBlastCharmItem)) return;

        // Make invulnerable to prevent damage from explosions, fire, lava, etc.
        itemEntity.setInvulnerable(true);
    }

    /** Find the calibrated blast charm in inventory */
    private static ItemStack getCalibratedBlastCharm(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CalibratedBlastCharmItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}