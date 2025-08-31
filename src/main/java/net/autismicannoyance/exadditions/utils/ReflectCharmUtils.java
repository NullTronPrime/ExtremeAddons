package net.autismicannoyance.exadditions.utils;

import net.autismicannoyance.exadditions.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ReflectCharmUtils {
    private static final String NBT_RING_DATA = "ReflectCharmRings";

    /**
     * Manually clear ring data from a Reflect Charm.
     * Useful when enchantments change or for debugging.
     */
    public static void clearRingData(ItemStack charm) {
        if (charm.is(ModItems.REFLECT_CHARM.get())) {
            CompoundTag charmTag = charm.getOrCreateTag();
            charmTag.remove(NBT_RING_DATA);
        }
    }

    /**
     * Clear ring data from all Reflect Charms in player's inventory.
     * Useful for forcing regeneration after enchantment changes.
     */
    public static void clearAllPlayerRingData(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.REFLECT_CHARM.get())) {
                clearRingData(stack);
            }
        }

        // Also check offhand
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(ModItems.REFLECT_CHARM.get())) {
            clearRingData(offhand);
        }
    }

    /**
     * Check if a charm has ring data stored
     */
    public static boolean hasRingData(ItemStack charm) {
        if (!charm.is(ModItems.REFLECT_CHARM.get())) return false;
        CompoundTag charmTag = charm.getOrCreateTag();
        return charmTag.contains(NBT_RING_DATA);
    }
}