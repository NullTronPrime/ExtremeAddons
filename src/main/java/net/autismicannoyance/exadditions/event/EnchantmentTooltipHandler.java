package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.enchantment.ModEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "exadditions")
public class EnchantmentTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();

        // Add tooltips for each custom enchantment
        addVeinMineTooltip(stack, event);
        addRenounceTooltip(stack, event);
        addMasteryTooltip(stack, event);
        addRiskyTooltip(stack, event);
        addExtractTooltip(stack, event);
        addHealthyTooltip(stack, event, player);
        addLavaWalkerTooltip(stack, event);
        addMagnetismTooltip(stack, event);
        addComboTooltip(stack, event);
        addBreakingTooltip(stack, event);
        addHomingTooltip(stack, event);
        addLifestealTooltip(stack, event);
        addFarmerTooltip(stack, event);
        addAllInTooltip(stack, event);
        addAdrenalineTooltip(stack, event, player);
        addChanceTooltip(stack, event);
        addSmeltingTooltip(stack, event);
        addSprintTooltip(stack, event);
        addFrostTooltip(stack, event);
        addSiphonTooltip(stack, event);
        addMarathonTooltip(stack, event);
        addDrawTooltip(stack, event);
        addRejuvenateTooltip(stack, event);
    }

    private static void addVeinMineTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.VEIN_MINE.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("Vein Mine I").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Mines entire ore veins").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addRenounceTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.RENOUNCE.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("Renounce I").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" +5 Attack Damage").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Removes log mining speed bonus").withStyle(ChatFormatting.RED));
        }
    }

    private static void addMasteryTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.MASTERY.get());
        if (level > 0) {
            CompoundTag tag = stack.getTag();
            int blocksMined = tag != null ? tag.getInt("MasteryBlocksMined") : 0;
            double bonus = blocksMined >= 10 ? Math.log10(blocksMined) * 100 : 0;

            event.getToolTip().add(Component.literal("Mastery I").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Blocks Mined: " + blocksMined).withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" +" + String.format("%.1f", bonus) + "% Mining Speed").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addRiskyTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.RISKY.get());
        if (level > 0) {
            int normalPenalty = level * 5;
            int criticalBonus = level * 10;

            event.getToolTip().add(Component.literal("Risky " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" -" + normalPenalty + "% Normal Attack Damage").withStyle(ChatFormatting.RED));
            event.getToolTip().add(Component.literal(" +" + criticalBonus + "% Critical Attack Damage").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addExtractTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.EXTRACT.get());
        if (level > 0) {
            int bonusExp = level * 5;

            event.getToolTip().add(Component.literal("Extract " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" +" + bonusExp + "% Experience from mobs").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addHealthyTooltip(ItemStack stack, ItemTooltipEvent event, Player player) {
        int level = stack.getEnchantmentLevel(ModEnchantments.HEALTHY.get());
        if (level > 0) {
            // Show maximum potential damage bonus
            float maxHealthBonus = 20.0f * (1.0f + level * 0.5f); // Assuming 20 max health

            event.getToolTip().add(Component.literal("Healthy " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Up to +" + String.format("%.0f", maxHealthBonus) + "% Attack Damage").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Based on current health").withStyle(ChatFormatting.DARK_GRAY));

            // Show current bonus if player is available
            if (player != null) {
                float currentHealth = player.getHealth();
                float maxHealth = player.getMaxHealth();
                float currentBonus = (currentHealth / maxHealth) * (1.0f + level * 0.5f) * 100;
                event.getToolTip().add(Component.literal(" Current: +" + String.format("%.1f", currentBonus) + "% damage").withStyle(ChatFormatting.BLUE));
            }
        }
    }

    private static void addLavaWalkerTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.LAVA_WALKER.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("Lava Walker " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" " + level + " block radius lava walking").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Immunity to magma damage").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addMagnetismTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.MAGNETISM.get());
        if (level > 0) {
            int radius = 3 + level * 2;

            event.getToolTip().add(Component.literal("Magnetism " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Pulls items from " + radius + " blocks").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addComboTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.COMBO.get());
        if (level > 0) {
            int timer = 5 + level;
            float damagePerMob = 1.0f + level * 0.5f;

            event.getToolTip().add(Component.literal("Combo " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" " + timer + "s timer between kills").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" +" + String.format("%.1f", damagePerMob) + " damage per mob in combo").withStyle(ChatFormatting.DARK_GREEN));

            // Show current combo if available
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                int comboCount = tag.getInt("ComboCount");
                if (comboCount > 0) {
                    float totalBonus = comboCount * damagePerMob;
                    event.getToolTip().add(Component.literal(" Current Combo: " + comboCount + " (+" + String.format("%.1f", totalBonus) + " damage)").withStyle(ChatFormatting.GOLD));
                }
            }
        }
    }

    private static void addBreakingTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.BREAKING.get());
        if (level > 0) {
            int damagePenalty = 10 + (level - 1) * 2;
            int armorDamage = level * 200;

            event.getToolTip().add(Component.literal("Breaking " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" -" + damagePenalty + "% Attack Damage").withStyle(ChatFormatting.RED));
            event.getToolTip().add(Component.literal(" +" + armorDamage + "% Damage to Armor").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addHomingTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.HOMING.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("Homing I").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Arrows home towards enemies").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Works with Multishot").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addLifestealTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.LIFESTEAL.get());
        if (level > 0) {
            int healPercent = 10 + (level - 1) * 5;

            event.getToolTip().add(Component.literal("Lifesteal " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Heal " + healPercent + "% of damage dealt").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Only works against players").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void addFarmerTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.FARMER.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("Farmer I").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Auto-replants crops").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Prevents breaking immature crops").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" +30% crop yield").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Sneak to disable").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void addAllInTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.ALL_IN.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("All In I").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" +200% Attack Damage").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Cannot be combined with other enchantments").withStyle(ChatFormatting.RED));
        }
    }

    private static void addAdrenalineTooltip(ItemStack stack, ItemTooltipEvent event, Player player) {
        int level = stack.getEnchantmentLevel(ModEnchantments.ADRENALINE.get());
        if (level > 0) {
            // Show maximum potential damage bonus
            float maxHealthBonus = 20.0f * (1.0f + level * 0.5f); // Assuming 20 max health

            event.getToolTip().add(Component.literal("Adrenaline " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Up to +" + String.format("%.0f", maxHealthBonus) + "% Attack Damage").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Based on lost health").withStyle(ChatFormatting.DARK_GRAY));

            // Show current bonus if player is available
            if (player != null) {
                float currentHealth = player.getHealth();
                float maxHealth = player.getMaxHealth();
                float lostHealth = maxHealth - currentHealth;
                float currentBonus = (lostHealth / maxHealth) * (1.0f + level * 0.5f) * 100;
                event.getToolTip().add(Component.literal(" Current: +" + String.format("%.1f", currentBonus) + "% damage").withStyle(ChatFormatting.BLUE));
            }
        }
    }

    private static void addChanceTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.CHANCE.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("Chance " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Looting effect for bows and crossbows").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Increases mob drop rates").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addSmeltingTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.SMELTING.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("Smelting I").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Auto-smelts mined blocks").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Logs → Charcoal, Ores → Ingots").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Incompatible with Fortune/Silk Touch").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void addSprintTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.SPRINT.get());
        if (level > 0) {
            int speedBonus = level * 5;

            event.getToolTip().add(Component.literal("Sprint " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" +" + speedBonus + "% Sprint Speed").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Does not affect walking speed").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void addFrostTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.FROST.get());
        if (level > 0) {
            int slowPercent = level * 5;
            int freezeTime = level * 2;

            event.getToolTip().add(Component.literal("Frost " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" -" + slowPercent + "% Enemy Movement & Attack Speed").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Freezes for " + freezeTime + " seconds").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Incompatible with Flame").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void addSiphonTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.SIPHON.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("Siphon I").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Drops go directly to inventory").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Works with all tool enchantments").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addMarathonTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.MARATHON.get());
        if (level > 0) {
            int hungerReduction = level * 10;

            event.getToolTip().add(Component.literal("Marathon " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" -" + hungerReduction + "% Hunger Drain").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" While walking or sprinting").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void addDrawTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.DRAW.get());
        if (level > 0) {
            int speedBonus = level * 7;

            event.getToolTip().add(Component.literal("Draw " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" -" + speedBonus + "% Bow Draw Time").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Shoot arrows faster").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private static void addRejuvenateTooltip(ItemStack stack, ItemTooltipEvent event) {
        int level = stack.getEnchantmentLevel(ModEnchantments.REJUVENATE.get());
        if (level > 0) {
            event.getToolTip().add(Component.literal("Rejuvenate " + getRomanNumeral(level)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(" Chance to repair on use").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Chance to increase max durability").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal(" Incompatible with Mending").withStyle(ChatFormatting.DARK_GRAY));
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