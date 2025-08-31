package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.enchantment.RingCapacityEnchantment;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class ReflectCharmItem extends Item {
    private static final String NBT_COOLDOWN_END = "ReflectCharmCooldown";
    private static final String NBT_RING_DATA = "ReflectCharmRings";

    public ReflectCharmItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant().durability(200));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getOrCreateTag();

        // Show enchantment info and ring capacity
        int maxRings = RingCapacityEnchantment.getRingCount(stack);
        int enchantLevel = stack.getEnchantmentLevel(net.autismicannoyance.exadditions.enchantment.ModEnchantments.RING_CAPACITY.get());

        if (enchantLevel > 0) {
            tooltip.add(Component.literal(ChatFormatting.AQUA + "Ring Capacity " + toRoman(enchantLevel) + " - Max Rings: " + maxRings));
        } else {
            tooltip.add(Component.literal(ChatFormatting.AQUA + "Maximum Rings: " + maxRings + ChatFormatting.GRAY + " (Base)"));
        }

        // Cooldown information
        if (level != null) {
            long currentTime = level.getGameTime();
            long cooldownEnd = tag.getLong(NBT_COOLDOWN_END);

            if (currentTime < cooldownEnd) {
                long remainingTicks = cooldownEnd - currentTime;
                double remainingSeconds = remainingTicks / 20.0;
                tooltip.add(Component.literal(ChatFormatting.RED + "⏰ Cooldown: " + String.format("%.1fs remaining", remainingSeconds)));
                tooltip.add(Component.literal(ChatFormatting.DARK_RED + "Cannot reflect attacks while on cooldown"));
            } else {
                tooltip.add(Component.literal(ChatFormatting.GREEN + "✓ Ready to use"));
            }
        }

        // Show current ring status if any exist
        if (tag.contains(NBT_RING_DATA)) {
            CompoundTag ringData = tag.getCompound(NBT_RING_DATA);
            int totalRings = 0;
            int brokenRings = 0;

            for (String key : ringData.getAllKeys()) {
                if (key.startsWith("ring_")) {
                    totalRings++;
                    CompoundTag ringTag = ringData.getCompound(key);
                    if (ringTag.getBoolean("broken")) {
                        brokenRings++;
                    }
                }
            }

            if (totalRings > 0) {
                int intactRings = totalRings - brokenRings;
                if (intactRings > 0) {
                    tooltip.add(Component.literal(ChatFormatting.BLUE + "🛡 Active Rings: " + intactRings + "/" + totalRings + " intact"));
                    if (brokenRings > 0) {
                        tooltip.add(Component.literal(ChatFormatting.YELLOW + "⚠ " + brokenRings + " rings damaged"));
                    }
                } else {
                    tooltip.add(Component.literal(ChatFormatting.RED + "💥 All rings broken - explosion imminent!"));
                }
            }
        } else {
            tooltip.add(Component.literal(ChatFormatting.GRAY + "No active rings (sneak to generate)"));
        }

        // Usage instructions
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal(ChatFormatting.YELLOW + "Hold SNEAK to activate barrier"));
        tooltip.add(Component.literal(ChatFormatting.GRAY + "• Reflects projectiles back at attackers"));
        tooltip.add(Component.literal(ChatFormatting.GRAY + "• Absorbs melee damage and reflects it"));
        tooltip.add(Component.literal(ChatFormatting.GRAY + "• Broken rings stay broken until explosion"));

        // Durability costs
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal(ChatFormatting.GOLD + "Durability Costs:"));
        tooltip.add(Component.literal(ChatFormatting.GRAY + "• -1 per projectile reflected"));
        tooltip.add(Component.literal(ChatFormatting.GRAY + "• -2 per ring broken"));
        tooltip.add(Component.literal(ChatFormatting.GRAY + "• -10 when all rings destroyed"));

        super.appendHoverText(stack, level, tooltip, flag);
    }

    private static String toRoman(int number) {
        String[] values = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (number < 1 || number > 10) return String.valueOf(number);
        return values[number];
    }
}