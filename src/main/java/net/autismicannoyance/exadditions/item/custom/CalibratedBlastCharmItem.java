package net.autismicannoyance.exadditions.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class CalibratedBlastCharmItem extends Item {
    public static final String BLAST_DAMAGE_TAG = "CalibratedBlastDamage";

    public CalibratedBlastCharmItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag itemTag = stack.getOrCreateTag();
        float totalBlastDamage = itemTag.getFloat(BLAST_DAMAGE_TAG);

        if (totalBlastDamage <= 0) {
            tooltip.add(Component.literal(ChatFormatting.GRAY + "No blast damage absorbed yet"));
            tooltip.add(Component.literal(ChatFormatting.GRAY + "Current protection: 0%"));
        } else {
            // Calculate protection percentage using logarithmic scaling (10x slower progression)
            // Formula: protection = min(90%, log(damage/10 + 1) * 15%)
            double protection = Math.min(0.9, Math.log(totalBlastDamage / 10.0 + 1) * 0.15);
            int protectionPercent = (int) Math.round(protection * 100);

            tooltip.add(Component.literal(ChatFormatting.GOLD + "Blast damage absorbed: " + String.format("%.1f", totalBlastDamage)));
            tooltip.add(Component.literal(ChatFormatting.GREEN + "Blast protection: " + protectionPercent + "%"));

            if (protection < 0.9) {
                double nextProtectionLevel = protection + 0.05;
                double nextMilestone = (Math.exp(nextProtectionLevel / 0.15) - 1) * 10.0;
                tooltip.add(Component.literal(ChatFormatting.DARK_GRAY + "Next 5% at " + String.format("%.1f", nextMilestone) + " damage"));
            } else {
                tooltip.add(Component.literal(ChatFormatting.DARK_GRAY + "Maximum protection reached!"));
            }
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}