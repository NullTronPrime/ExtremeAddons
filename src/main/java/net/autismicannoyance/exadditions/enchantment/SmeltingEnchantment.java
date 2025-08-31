package net.autismicannoyance.exadditions.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

public class SmeltingEnchantment extends Enchantment {
    public SmeltingEnchantment() {
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
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof ShovelItem ||
                stack.getItem() instanceof PickaxeItem ||
                stack.getItem() instanceof AxeItem;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    @Override
    public boolean checkCompatibility(Enchantment other) {
        return other != Enchantments.BLOCK_FORTUNE &&
                other != Enchantments.SILK_TOUCH &&
                super.checkCompatibility(other);
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
}