package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.network.BlackHoleEffectPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Random;

/**
 * Item for creating black hole visual effects for testing/demonstration
 */
public class BlackHoleGeneratorItem extends Item {
    private static final String SIZE_TAG = "black_hole_size";
    private static final String ROTATION_SPEED_TAG = "rotation_speed";
    private static final String LIFETIME_TAG = "lifetime";

    private static final float DEFAULT_SIZE = 2.0f;
    private static final float DEFAULT_ROTATION_SPEED = 0.02f;
    private static final int DEFAULT_LIFETIME = 1200; // 60 seconds at 20 TPS

    private static final Random RAND = new Random();

    public BlackHoleGeneratorItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            // Get parameters from item NBT or use defaults
            float size = getSize(stack);
            float rotationSpeed = getRotationSpeed(stack);
            int lifetime = getLifetime(stack);

            // Create black hole at player's position with slight offset
            Vec3 playerPos = player.position();
            Vec3 blackHolePos = playerPos.add(
                    player.getLookAngle().scale(5.0) // 5 blocks in front of player
            ).add(0, 2, 0); // Slightly above ground

            // Create unique ID for this black hole
            int blackHoleId = RAND.nextInt(Integer.MAX_VALUE);

            // Create the visual effect packet
            BlackHoleEffectPacket packet = new BlackHoleEffectPacket(
                    blackHoleId,
                    blackHolePos,
                    size,
                    rotationSpeed,
                    lifetime
            );

            // Send to all players in the area
            ModNetworking.CHANNEL.send(
                    PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                            blackHolePos.x, blackHolePos.y, blackHolePos.z, 128.0, serverLevel.dimension()
                    )),
                    packet
            );

            player.displayClientMessage(
                    Component.literal("Created black hole with size " + size +
                            ", rotation speed " + String.format("%.3f", rotationSpeed) +
                            ", lifetime " + (lifetime / 20) + "s"),
                    true
            );

            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    // NBT getter/setter methods
    public static float getSize(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(SIZE_TAG)) {
            return stack.getTag().getFloat(SIZE_TAG);
        }
        return DEFAULT_SIZE;
    }

    public static void setSize(ItemStack stack, float size) {
        stack.getOrCreateTag().putFloat(SIZE_TAG, Math.max(0.1f, Math.min(10.0f, size)));
    }

    public static float getRotationSpeed(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(ROTATION_SPEED_TAG)) {
            return stack.getTag().getFloat(ROTATION_SPEED_TAG);
        }
        return DEFAULT_ROTATION_SPEED;
    }

    public static void setRotationSpeed(ItemStack stack, float speed) {
        stack.getOrCreateTag().putFloat(ROTATION_SPEED_TAG, Math.max(0.001f, Math.min(0.5f, speed)));
    }

    public static int getLifetime(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(LIFETIME_TAG)) {
            return stack.getTag().getInt(LIFETIME_TAG);
        }
        return DEFAULT_LIFETIME;
    }

    public static void setLifetime(ItemStack stack, int lifetime) {
        stack.getOrCreateTag().putInt(LIFETIME_TAG, Math.max(100, Math.min(72000, lifetime))); // 5s to 1 hour
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        float size = getSize(stack);
        float rotationSpeed = getRotationSpeed(stack);
        int lifetime = getLifetime(stack);

        tooltip.add(Component.literal("§7Size: §f" + String.format("%.1f", size)));
        tooltip.add(Component.literal("§7Rotation Speed: §f" + String.format("%.3f", rotationSpeed)));
        tooltip.add(Component.literal("§7Lifetime: §f" + (lifetime / 20) + "s"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8Right-click to create black hole"));
        tooltip.add(Component.literal("§8Use /blackhole modify commands"));
        tooltip.add(Component.literal("§8to adjust properties"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§6⚠ §eExperimental Technology"));
        tooltip.add(Component.literal("§7May cause spacetime distortions"));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Make it enchanted-looking
        return true;
    }
}