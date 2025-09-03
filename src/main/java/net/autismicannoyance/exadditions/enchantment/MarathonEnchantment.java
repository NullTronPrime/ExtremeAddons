package net.autismicannoyance.exadditions.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class MarathonEnchantment extends Enchantment {
    public MarathonEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.ARMOR_LEGS,
                new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS});
    }

    @Override
    public int getMinCost(int level) {
        return 8 + (level - 1) * 8;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armorItem)) return false;
        EquipmentSlot slot = armorItem.getEquipmentSlot();
        // Only works on boots and leggings as specified
        return slot == EquipmentSlot.FEET || slot == EquipmentSlot.LEGS;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
}
