package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.enchantment.ModEnchantments;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = "exadditions")
public class AttributeEnchantmentEvents {

    // UUIDs for attribute modifiers
    private static final UUID SPRINT_SPEED_UUID = UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B");
    private static final UUID MASTERY_SPEED_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5D6");

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        updateSprintSpeedModifier(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;

        // Update mastery speed modifier every few ticks to avoid performance issues
        if (player.tickCount % 20 == 0) { // Every second
            updateMasterySpeedModifier(player);
        }

        // Handle marathon hunger reduction
        handleMarathonHunger(player);
    }

    private static void updateSprintSpeedModifier(Player player) {
        ItemStack boots = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        int sprintLevel = boots.getEnchantmentLevel(ModEnchantments.SPRINT.get());

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) return;

        // Remove old modifier
        movementSpeed.removeModifier(SPRINT_SPEED_UUID);

        // Add new modifier if enchantment exists
        if (sprintLevel > 0 && player.isSprinting()) {
            double speedBonus = sprintLevel * 0.05; // 5% per level
            AttributeModifier modifier = new AttributeModifier(
                    SPRINT_SPEED_UUID,
                    "Sprint enchantment speed boost",
                    speedBonus,
                    AttributeModifier.Operation.MULTIPLY_BASE
            );
            movementSpeed.addPermanentModifier(modifier);
        }
    }

    private static void updateMasterySpeedModifier(Player player) {
        ItemStack tool = player.getMainHandItem();
        int masteryLevel = tool.getEnchantmentLevel(ModEnchantments.MASTERY.get());

        AttributeInstance digSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (digSpeed == null) return;

        // Remove old modifier
        digSpeed.removeModifier(MASTERY_SPEED_UUID);

        // Add new modifier if enchantment exists and tool has been used
        if (masteryLevel > 0) {
            float speedBonus = net.autismicannoyance.exadditions.event.EnchantmentEvents.getMasterySpeedBonus(tool) - 1.0f;
            if (speedBonus > 0) {
                AttributeModifier modifier = new AttributeModifier(
                        MASTERY_SPEED_UUID,
                        "Mastery enchantment speed boost",
                        speedBonus,
                        AttributeModifier.Operation.MULTIPLY_BASE
                );
                digSpeed.addPermanentModifier(modifier);
            }
        }
    }

    private static void handleMarathonHunger(Player player) {
        ItemStack legs = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        ItemStack feet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);

        int marathonLegs = legs.getEnchantmentLevel(ModEnchantments.MARATHON.get());
        int marathonFeet = feet.getEnchantmentLevel(ModEnchantments.MARATHON.get());
        int marathonLevel = Math.max(marathonLegs, marathonFeet);

        if (marathonLevel > 0 && (player.isSprinting() || isPlayerMoving(player))) {
            // Reduce hunger drain by giving small amounts of saturation
            // This is a workaround since we can't directly modify hunger drain
            if (player.tickCount % (100 - (marathonLevel * 10)) == 0) { // Less frequent with higher levels
                player.getFoodData().setSaturation(
                        Math.min(player.getFoodData().getSaturationLevel() + 0.1f,
                                player.getFoodData().getFoodLevel())
                );
            }
        }
    }

    private static boolean isPlayerMoving(Player player) {
        Vec3 movement = player.getDeltaMovement();
        return movement.horizontalDistanceSqr() > 0.001; // Small threshold for movement
    }
}