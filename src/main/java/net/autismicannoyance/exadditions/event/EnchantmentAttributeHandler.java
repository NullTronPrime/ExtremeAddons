package net.autismicannoyance.exadditions.event;

import com.google.common.collect.Multimap;
import net.autismicannoyance.exadditions.enchantment.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "exadditions")
public class EnchantmentAttributeHandler {

    // UUIDs for custom attribute modifiers
    private static final UUID RENOUNCE_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID ALL_IN_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5D0");
    private static final UUID HEALTHY_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5D1");
    private static final UUID ADRENALINE_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5D2");
    private static final UUID COMBO_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5D3");

    // Movement / tool related UUIDs
    private static final UUID SPRINT_SPEED_UUID = UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B");
    private static final UUID MASTERY_SPEED_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5D6");

    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        EquipmentSlot slot = event.getSlotType();

        if (slot != EquipmentSlot.MAINHAND) return;

        // RENOUNCE - flat +5 damage
        int renounceLevel = stack.getEnchantmentLevel(ModEnchantments.RENOUNCE.get());
        if (renounceLevel > 0) {
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(RENOUNCE_DAMAGE_UUID, "Renounce Damage", 5.0, AttributeModifier.Operation.ADDITION));
        }

        // ALL IN - 200% damage multiplier (exclusive)
        int allInLevel = stack.getEnchantmentLevel(ModEnchantments.ALL_IN.get());
        if (allInLevel > 0) {
            // remove all existing attack damage modifiers first (exclusive behavior)
            event.removeAttribute(Attributes.ATTACK_DAMAGE);

            double baseDamage = getBaseItemDamage(stack);
            double totalDamage = baseDamage * 3.0; // base * 3 = 200% bonus
            // Subtract 1.0 because many vanilla items account for a base 1.0 "punch"
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(ALL_IN_DAMAGE_UUID, "All In Damage", totalDamage - 1.0, AttributeModifier.Operation.ADDITION));
        }

        // COMBO - dynamic based on ComboCount NBT (adds attribute for equip-time calculations)
        int comboLevel = stack.getEnchantmentLevel(ModEnchantments.COMBO.get());
        if (comboLevel > 0) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                int comboCount = tag.getInt("ComboCount");
                if (comboCount > 0) {
                    double comboDamage = comboCount * (1.0 + comboLevel * 0.5);
                    event.addModifier(Attributes.ATTACK_DAMAGE,
                            new AttributeModifier(COMBO_DAMAGE_UUID, "Combo Damage", comboDamage, AttributeModifier.Operation.ADDITION));
                }
            }
        }

        // Note: HEALTHY and ADRENALINE are dynamic and require player context; those are updated in the player-tick handler.
    }

    // Unified player tick handler: updates healthy/adrenaline, mastery speed, and marathon hunger
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player == null) return;

        ItemStack weapon = player.getMainHandItem();
        if (!weapon.isEmpty()) {
            updateHealthyDamage(weapon, player);
            updateAdrenalineDamage(weapon, player);
        }

        // Every second update mastery speed (every 20 ticks)
        if (player.tickCount % 20 == 0) {
            updateMasterySpeedModifier(player);
        }

        // Sprint speed potentially depends on sprinting state - but we also update on tick to catch sprint start/stop.
        updateSprintSpeedModifier(player);

        // Marathon hunger handling runs every tick but throttles inside method
        handleMarathonHunger(player);
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        updateSprintSpeedModifier(player);
    }

    // Helper: read base attack damage from the item
    private static double getBaseItemDamage(ItemStack stack) {
        Multimap<Attribute, AttributeModifier> defaultModifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        double baseDamage = 1.0;
        Collection<AttributeModifier> dmgModifiers = defaultModifiers.get(Attributes.ATTACK_DAMAGE);
        if (dmgModifiers != null) {
            for (AttributeModifier modifier : dmgModifiers) {
                if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                    baseDamage += modifier.getAmount();
                }
            }
        }
        return baseDamage;
    }

    // HEALTHY: increases damage proportionally to current health (stores NBT for tooltip/use elsewhere)
    private static void updateHealthyDamage(ItemStack weapon, Player player) {
        int healthyLevel = weapon.getEnchantmentLevel(ModEnchantments.HEALTHY.get());
        if (healthyLevel == 0) return;

        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float healthRatio = maxHealth > 0 ? (currentHealth / maxHealth) : 0f;

        double baseDamage = getBaseItemDamage(weapon);
        double healthBonus = healthRatio * (healthyLevel * 0.5) * baseDamage;

        CompoundTag tag = weapon.getOrCreateTag();
        tag.putDouble("HealthyDamageBonus", healthBonus);
    }

    // ADRENALINE: increases damage based on lost health (stores NBT for tooltip/use elsewhere)
    private static void updateAdrenalineDamage(ItemStack weapon, Player player) {
        int adrenalineLevel = weapon.getEnchantmentLevel(ModEnchantments.ADRENALINE.get());
        if (adrenalineLevel == 0) return;

        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float lostHealth = Math.max(0f, maxHealth - currentHealth);
        float lostHealthRatio = maxHealth > 0 ? (lostHealth / maxHealth) : 0f;

        double baseDamage = getBaseItemDamage(weapon);
        double adrenalineBonus = lostHealthRatio * (adrenalineLevel * 0.5) * baseDamage;

        CompoundTag tag = weapon.getOrCreateTag();
        tag.putDouble("AdrenalineDamageBonus", adrenalineBonus);
    }

    // SPRINT: boots enchantment - temporary speed boost while sprinting
    private static void updateSprintSpeedModifier(Player player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int sprintLevel = boots.getEnchantmentLevel(ModEnchantments.SPRINT.get());

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) return;

        // Remove prior modifier (safe: catch mapping differences / exceptions)
        try {
            movementSpeed.removeModifier(SPRINT_SPEED_UUID);
        } catch (Exception ignored) {
        }

        if (sprintLevel > 0 && player.isSprinting()) {
            double speedBonus = sprintLevel * 0.05; // 5% per level
            AttributeModifier modifier = new AttributeModifier(
                    SPRINT_SPEED_UUID,
                    "Sprint enchantment speed boost",
                    speedBonus,
                    AttributeModifier.Operation.MULTIPLY_BASE
            );
            // addPermanentModifier is commonly used in mappings; if yours differs, change accordingly
            try {
                movementSpeed.addPermanentModifier(modifier);
            } catch (NoSuchMethodError | AbstractMethodError e) {
                // fallback if mapping uses different name:
                movementSpeed.addTransientModifier(modifier);
            }
        }
    }

    // MASTERY: tool/main-hand based attack-speed (or "dig speed" in original) modifier
    private static void updateMasterySpeedModifier(Player player) {
        ItemStack tool = player.getMainHandItem();
        int masteryLevel = tool.getEnchantmentLevel(ModEnchantments.MASTERY.get());

        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) return;

        try {
            attackSpeed.removeModifier(MASTERY_SPEED_UUID);
        } catch (Exception ignored) {
        }

        if (masteryLevel > 0) {
            // The original referenced EnchantmentEvents.getMasterySpeedBonus(tool)
            float speedBonus = net.autismicannoyance.exadditions.event.EnchantmentEvents.getMasterySpeedBonus(tool) - 1.0f;
            if (speedBonus > 0) {
                AttributeModifier modifier = new AttributeModifier(
                        MASTERY_SPEED_UUID,
                        "Mastery enchantment speed boost",
                        speedBonus,
                        AttributeModifier.Operation.MULTIPLY_BASE
                );
                try {
                    attackSpeed.addPermanentModifier(modifier);
                } catch (NoSuchMethodError | AbstractMethodError e) {
                    attackSpeed.addTransientModifier(modifier);
                }
            }
        }
    }

    // Marathon: reduce hunger drain by adding small saturation ticks while moving/sprinting
    private static void handleMarathonHunger(Player player) {
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);

        int marathonLegs = legs.getEnchantmentLevel(ModEnchantments.MARATHON.get());
        int marathonFeet = feet.getEnchantmentLevel(ModEnchantments.MARATHON.get());
        int marathonLevel = Math.max(marathonLegs, marathonFeet);

        if (marathonLevel > 0 && (player.isSprinting() || isPlayerMoving(player))) {
            int throttle = Math.max(10, 100 - (marathonLevel * 10)); // more level => more frequent
            if (player.tickCount % throttle == 0) {
                // Add a tiny bit of saturation but don't exceed current food level
                float newSaturation = Math.min(player.getFoodData().getSaturationLevel() + 0.1f,
                        player.getFoodData().getFoodLevel());
                player.getFoodData().setSaturation(newSaturation);
            }
        }
    }

    private static boolean isPlayerMoving(Player player) {
        Vec3 movement = player.getDeltaMovement();
        return movement.horizontalDistanceSqr() > 0.001;
    }
}
