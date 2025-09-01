package net.autismicannoyance.exadditions.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.DiggerItem;

public class VeinMineEnchantment extends Enchantment {

    public VeinMineEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.DIGGER, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int level) {
        return 15;
    }

    @Override
    public int getMaxCost(int level) {
        return 30;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        // Check if it's a digger item and can be enchanted by the DIGGER category
        if (!EnchantmentCategory.DIGGER.canEnchant(stack.getItem())) {
            return false;
        }

        // Check if it's actually a DiggerItem (tools like pickaxe, axe, shovel, hoe)
        if (!(stack.getItem() instanceof DiggerItem)) {
            return false;
        }

        // For DiggerItems, we can safely assume they have a destroy speed > 1.0f
        // since they are designed for breaking blocks
        return true;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
}