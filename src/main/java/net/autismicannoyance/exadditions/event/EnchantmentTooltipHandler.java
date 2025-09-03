package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.enchantment.ModEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "exadditions")
public class EnchantmentTooltipHandler {

    // UUID for our custom attribute modifiers to avoid conflicts
    private static final UUID ENCHANT_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        List<Component> tooltip = event.getToolTip();

        // Modify existing damage tooltip lines instead of adding new ones
        modifyDamageTooltip(stack, tooltip, player);

        // Add custom enchantment tooltips (only for enchantments that don't affect damage)
        addNonDamageEnchantmentTooltips(stack, event);
    }

    private static void modifyDamageTooltip(ItemStack stack, List<Component> tooltip, Player player) {
        // Calculate total damage bonus from our enchantments
        float totalDamageBonus = calculateTotalDamageBonus(stack, player);

        if (totalDamageBonus == 0) return;

        // Find and modify the damage line in the tooltip
        for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
            String text = line.getString();

            // Look for the damage line (contains "Attack Damage" and a number)
            if (text.contains("Attack Damage") && text.matches(".*\\d+.*")) {
                // Extract the base damage value
                String[] parts = text.split(" ");
                for (String part : parts) {
                    try {
                        float baseDamage = Float.parseFloat(part);
                        float newDamage = baseDamage + totalDamageBonus;

                        // Replace the line with updated damage
                        String newText = text.replace(part, String.format("%.1f", newDamage));
                        tooltip.set(i, Component.literal(newText).withStyle(line.getStyle()));

                        // Add a line showing the bonus if significant
                        if (totalDamageBonus >= 0.1f) {
                            tooltip.add(i + 1, Component.literal(" " + String.format("+%.1f", totalDamageBonus) + " Attack Damage from Enchantments")
                                    .withStyle(ChatFormatting.BLUE));
                        }
                        return;
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
            }
        }
    }

    private static float calculateTotalDamageBonus(ItemStack stack, Player player) {
        float totalBonus = 0.0f;

        // RENOUNCE ENCHANTMENT - Flat +5 damage
        int renounceLevel = stack.getEnchantmentLevel(ModEnchantments.RENOUNCE.get());
        if (renounceLevel > 0) {
            totalBonus += 5.0f;
        }

        // ALL IN ENCHANTMENT - 200% damage multiplier (shown as bonus)
        int allInLevel = stack.getEnchantmentLevel(ModEnchantments.ALL_IN.get());
        if (allInLevel > 0) {
            // This is a multiplier, so we need to calculate it based on base damage
            // For tooltip purposes, we'll show the potential bonus
            totalBonus += getBaseDamage(stack) * 2.0f; // 200% of base damage
        }

        // HEALTHY ENCHANTMENT - Dynamic based on current health
        int healthyLevel = stack.getEnchantmentLevel(ModEnchantments.HEALTHY.get());
        if (healthyLevel > 0 && player != null) {
            float currentHealth = player.getHealth();
            float maxHealth = player.getMaxHealth();
            float healthRatio = currentHealth / maxHealth;
            float healthBonus = healthRatio * (healthyLevel * 0.5f) * getBaseDamage(stack);
            totalBonus += healthBonus;
        }

        // ADRENALINE ENCHANTMENT - Dynamic based on lost health
        int adrenalineLevel = stack.getEnchantmentLevel(ModEnchantments.ADRENALINE.get());
        if (adrenalineLevel > 0 && player != null) {
            float currentHealth = player.getHealth();
            float maxHealth = player.getMaxHealth();
            float lostHealth = maxHealth - currentHealth;
            float lostHealthRatio = lostHealth / maxHealth;
            float adrenalineBonus = lostHealthRatio * (adrenalineLevel * 0.5f) * getBaseDamage(stack);
            totalBonus += adrenalineBonus;
        }

        // COMBO ENCHANTMENT - Based on current combo
        int comboLevel = stack.getEnchantmentLevel(ModEnchantments.COMBO.get());
        if (comboLevel > 0) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                int comboCount = tag.getInt("ComboCount");
                float comboDamage = comboCount * (1.0f + comboLevel * 0.5f);
                totalBonus += comboDamage;
            }
        }

        return totalBonus;
    }

    private static float getBaseDamage(ItemStack stack) {
        // Get the base attack damage from the item's attributes
        Collection<AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE);

        float baseDamage = 1.0f; // Default punch damage
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                baseDamage += modifier.getAmount();
            }
        }
        return baseDamage;
    }

    private static void addNonDamageEnchantmentTooltips(ItemStack stack, ItemTooltipEvent event) {
        // Only add tooltips for enchantments that don't affect damage directly
        addVeinMineTooltip(stack, event);
        addMasteryTooltip(stack, event);
        addExtractTooltip(stack, event);
        addLavaWalkerTooltip(stack, event);
        addMagnetismTooltip(stack, event);
        addBreakingTooltip(stack, event);
        addHomingTooltip(stack, event);
        addLifestealTooltip(stack, event);
        addFarmerTooltip(stack, event);
        addChanceTooltip(stack, event);
        addSmeltingTooltip(stack, event);
        addSprintTooltip(stack, event);
        addFrostTooltip(stack, event);
        addSiphonTooltip(stack, event);
        addMarathonTooltip(stack, event);
        addDrawTooltip(stack, event);
        addRejuvenateTooltip(stack, event);
        addRiskyTooltip(stack, event);
    }

    // Keep only the non-damage affecting enchantment tooltips
    private static void addVeinMineTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.VEIN_MINE.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("§7Vein Mine I"));
            event.getToolTip().add(Component.literal("§2 Mines entire ore veins"));
        }
    }

    private static void addMasteryTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.MASTERY.get());
        if (level > 0) {
            CompoundTag tag = stack.getTag();
            int blocksMined = tag != null ? tag.getInt("MasteryBlocksMined") : 0;
            double bonus = blocksMined >= 10 ? Math.log10(blocksMined) * 100 : 0;

            event.getToolTip().add(Component.literal("§7Mastery I"));
            event.getToolTip().add(Component.literal("§2 Blocks Mined: " + blocksMined));
            event.getToolTip().add(Component.literal("§2 +" + String.format("%.1f", bonus) + "% Mining Speed"));
        }
    }

    private static void addRiskyTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.RISKY.get());
        if (level > 0) {
            int normalPenalty = level * 5;
            int criticalBonus = level * 10;

            event.getToolTip().add(Component.literal("§7Risky " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§c -" + normalPenalty + "% Normal Attack Damage"));
            event.getToolTip().add(Component.literal("§2 +" + criticalBonus + "% Critical Attack Damage"));
        }
    }

    private static void addExtractTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.EXTRACT.get());
        if (level > 0) {
            int bonusExp = level * 5;
            event.getToolTip().add(Component.literal("§7Extract " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§2 +" + bonusExp + "% Experience from mobs"));
        }
    }

    private static void addLavaWalkerTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.LAVA_WALKER.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("§7Lava Walker " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§2 " + level + " block radius lava walking"));
            event.getToolTip().add(Component.literal("§2 Immunity to magma damage"));
        }
    }

    private static void addMagnetismTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.MAGNETISM.get());
        if (level > 0) {
            int radius = 3 + level * 2;
            event.getToolTip().add(Component.literal("§7Magnetism " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§2 Pulls items from " + radius + " blocks"));
        }
    }

    private static void addBreakingTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.BREAKING.get());
        if (level > 0) {
            int armorDamage = level * 200;
            event.getToolTip().add(Component.literal("§7Breaking " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§2 +" + armorDamage + "% Damage to Armor"));
            event.getToolTip().add(Component.literal("§8 Slightly reduces base damage"));
        }
    }

    private static void addHomingTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.HOMING.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("§7Homing I"));
            event.getToolTip().add(Component.literal("§2 Arrows home towards enemies"));
            event.getToolTip().add(Component.literal("§2 Works with Multishot"));
        }
    }

    private static void addLifestealTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.LIFESTEAL.get());
        if (level > 0) {
            int healPercent = 10 + (level - 1) * 5;
            event.getToolTip().add(Component.literal("§7Lifesteal " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§2 Heal " + healPercent + "% of damage dealt"));
            event.getToolTip().add(Component.literal("§8 Only works against players"));
        }
    }

    private static void addFarmerTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.FARMER.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("§7Farmer I"));
            event.getToolTip().add(Component.literal("§2 Auto-replants crops"));
            event.getToolTip().add(Component.literal("§2 Prevents breaking immature crops"));
            event.getToolTip().add(Component.literal("§2 +30% crop yield"));
            event.getToolTip().add(Component.literal("§8 Sneak to disable"));
        }
    }

    private static void addChanceTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.CHANCE.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("§7Chance " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§2 Looting effect for bows and crossbows"));
            event.getToolTip().add(Component.literal("§2 Increases mob drop rates"));
        }
    }

    private static void addSmeltingTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.SMELTING.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("§7Smelting I"));
            event.getToolTip().add(Component.literal("§2 Auto-smelts mined blocks"));
            event.getToolTip().add(Component.literal("§2 Logs → Charcoal, Ores → Ingots"));
            event.getToolTip().add(Component.literal("§8 Incompatible with Fortune/Silk Touch"));
        }
    }

    private static void addSprintTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.SPRINT.get());
        if (level > 0) {
            int speedBonus = level * 5;
            event.getToolTip().add(Component.literal("§7Sprint " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§2 +" + speedBonus + "% Sprint Speed"));
            event.getToolTip().add(Component.literal("§8 Does not affect walking speed"));
        }
    }

    private static void addFrostTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.FROST.get());
        if (level > 0) {
            int slowPercent = level * 5;
            int freezeTime = level * 2;
            event.getToolTip().add(Component.literal("§7Frost " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§2 -" + slowPercent + "% Enemy Movement & Attack Speed"));
            event.getToolTip().add(Component.literal("§2 Freezes for " + freezeTime + " seconds"));
            event.getToolTip().add(Component.literal("§8 Incompatible with Flame"));
        }
    }

    private static void addSiphonTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.SIPHON.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("§7Siphon I"));
            event.getToolTip().add(Component.literal("§2 Drops go directly to inventory"));
            event.getToolTip().add(Component.literal("§2 Works with all tool enchantments"));
        }
    }

    private static void addMarathonTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.MARATHON.get());
        if (level > 0) {
            int hungerReduction = level * 10;
            event.getToolTip().add(Component.literal("§7Marathon " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§2 -" + hungerReduction + "% Hunger Drain"));
            event.getToolTip().add(Component.literal("§8 While walking or sprinting"));
        }
    }

    private static void addDrawTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.DRAW.get());
        if (level > 0) {
            int speedBonus = level * 7;
            event.getToolTip().add(Component.literal("§7Draw " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§2 -" + speedBonus + "% Bow Draw Time"));
            event.getToolTip().add(Component.literal("§2 Shoot arrows faster"));
        }
    }

    private static void addRejuvenateTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.REJUVENATE.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("§7Rejuvenate " + getRomanNumeral(level)));
            event.getToolTip().add(Component.literal("§2 Chance to repair on use"));
            event.getToolTip().add(Component.literal("§2 Chance to increase max durability"));
            event.getToolTip().add(Component.literal("§8 Incompatible with Mending"));
        }
    }

    private static String getRomanNumeral(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            case 6: return "VI";
            case 7: return "VII";
            case 8: return "VIII";
            case 9: return "IX";
            case 10: return "X";
            default: return String.valueOf(level);
        }
    }
}