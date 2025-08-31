package net.autismicannoyance.exadditions.enchantment;

import net.autismicannoyance.exadditions.item.custom.ReflectCharmItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class RingCapacityEnchantment extends Enchantment {

    public RingCapacityEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.BREAKABLE, new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
    }

    @Override
    public int getMinCost(int level) {
        return 10 + (level - 1) * 10; // 10, 20, 30, 40, 50 experience levels
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15; // 15 level range for each tier
    }

    @Override
    public int getMaxLevel() {
        return 10; // 10 levels: +1, +2, +3, +4, +5, +6, +7, +8, +9, +10 rings
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof ReflectCharmItem;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true; // Can appear on enchanted books
    }

    @Override
    public boolean isTreasureOnly() {
        return false; // Can appear in enchanting table
    }

    @Override
    public boolean isCurse() {
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        return true; // Can be found in loot/trading
    }

    @Override
    public boolean isTradeable() {
        return true; // Villagers can trade this enchantment
    }

    /**
     * Get the total number of rings based on enchantment level
     * Default: 5 rings (no enchantment)
     * Level 1: 6 rings (+1)
     * Level 2: 7 rings (+2)
     * Level 3: 8 rings (+3)
     * Level 4: 9 rings (+4)
     * Level 5: 10 rings (+5)
     * Level 6: 11 rings (+6)
     * Level 7: 12 rings (+7)
     * Level 8: 13 rings (+8)
     * Level 9: 14 rings (+9)
     * Level 10: 15 rings (+10)
     */
    public static int getRingCount(ItemStack charm) {
        int enchantLevel = charm.getEnchantmentLevel(ModEnchantments.RING_CAPACITY.get());
        int ringCount = 5 + enchantLevel;

        // Debug logging - remove this later
        System.out.println("DEBUG: Charm enchantment level: " + enchantLevel);
        System.out.println("DEBUG: Calculated ring count: " + ringCount);

        return ringCount; // Base 5 + enchantment level
    }
}