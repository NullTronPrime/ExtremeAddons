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

public class DeathCharmItem extends Item {
    public static final String DEATH_MAP_TAG = "DeathCharmDeaths";

    public DeathCharmItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // Read ONLY from this specific item's NBT data, not player data
        CompoundTag itemTag = stack.getOrCreateTag();
        CompoundTag tag = itemTag.getCompound(DEATH_MAP_TAG);

        if (tag.isEmpty()) {
            tooltip.add(Component.literal(ChatFormatting.GRAY + "No deaths recorded yet"));
        } else {
            tooltip.add(Component.literal(ChatFormatting.DARK_PURPLE + "Death Resistances:"));
            for (String key : tag.getAllKeys()) {
                int deaths = tag.getInt(key);
                if (deaths > 0) {
                    // Updated formula to match the new scaling (deaths/10)
                    double reduction = 1 - Math.pow(0.5, deaths / 10.0);
                    int percent = (int) Math.round(reduction * 100);
                    tooltip.add(Component.literal(" - " + prettyName(key) + ": " + deaths + " deaths (" + percent + "% reduced)")
                            .withStyle(ChatFormatting.GREEN));
                }
            }
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    /** Turns a registry key like "minecraft.lava" into "Lava" */
    private static String prettyName(String key) {
        String[] parts = key.split("\\.");
        String last = parts[parts.length - 1];
        return Character.toUpperCase(last.charAt(0)) + last.substring(1).replace('_', ' ');
    }
}