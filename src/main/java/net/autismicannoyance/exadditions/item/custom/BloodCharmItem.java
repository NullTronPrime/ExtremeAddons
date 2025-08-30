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

public class BloodCharmItem extends Item {
    public static final String KILL_MAP_TAG = "BloodCharmKills";
    public static final String HEALTH_MAP_TAG = "BloodCharmMobHealths";

    public BloodCharmItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag killTag = stack.getOrCreateTag().getCompound(KILL_MAP_TAG);
        CompoundTag healthTag = stack.getOrCreateTag().getCompound(HEALTH_MAP_TAG);

        if (killTag.isEmpty()) {
            tooltip.add(Component.literal(ChatFormatting.GRAY + "No kills recorded yet"));
        } else {
            tooltip.add(Component.literal(ChatFormatting.DARK_RED + "Damage Bonuses:"));
            for (String key : killTag.getAllKeys()) {
                int kills = killTag.getInt(key);
                if (kills > 0) {
                    float mobMaxHealth = healthTag.getFloat(key);
                    float wardenHealth = 500.0f;
                    float healthRatio = mobMaxHealth / wardenHealth;

                    // Calculate actual scaled bonus
                    double baseMultiplier = Math.sqrt(kills) * Math.log(kills);
                    double scaledMultiplier = 1.0 + (baseMultiplier * healthRatio);
                    double bonus = scaledMultiplier - 1;
                    int percent = (int) Math.round(bonus * 100);

                    String mobName = prettyName(key);
                    String healthText = String.format("%.0f HP", mobMaxHealth);
                    tooltip.add(Component.literal(" - " + mobName + " (" + kills + " kills, " + healthText + "): +" + percent + "% damage")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    /** Turns a registry key like "mob.minecraft:zombie" into "Zombie" */
    private static String prettyName(String key) {
        // Remove "mob." prefix if present
        if (key.startsWith("mob.")) {
            key = key.substring(4);
        }

        // Split by colon to get the entity name part
        String[] parts = key.split(":");
        String entityName = parts[parts.length - 1];

        // Convert underscores to spaces and capitalize
        String[] words = entityName.split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
            }
        }

        return result.toString();
    }
}