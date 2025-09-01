package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.enchantment.ModEnchantments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "exadditions")
public class LifestealEnchantmentEvents {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Check if the damage source is a player
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        // Get the player's main hand item
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty()) return;

        // Check if the weapon has lifesteal enchantment
        int lifestealLevel = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.LIFESTEAL.get(), weapon);
        if (lifestealLevel <= 0) return;

        // Only run on server side
        if (player.level().isClientSide) return;

        // Calculate healing amount based on damage dealt and enchantment level
        float damageDealt = event.getAmount();
        float healingAmount = damageDealt * (lifestealLevel * 0.1f); // 10% healing per level

        // Heal the player
        if (player.getHealth() < player.getMaxHealth()) {
            player.heal(healingAmount);

            // Optional: Add visual/sound effects
            // You could add particles or sounds here if desired
        }
    }
}