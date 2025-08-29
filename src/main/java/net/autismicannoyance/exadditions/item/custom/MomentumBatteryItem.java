package net.autismicannoyance.exadditions.item.custom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public class MomentumBatteryItem extends Item {
    private static final String TAG_X = "MomentumX";
    private static final String TAG_Y = "MomentumY";
    private static final String TAG_Z = "MomentumZ";

    public MomentumBatteryItem(Properties props) {
        super(props.durability(64)); // single stack, durability like a charge meter
    }

    /**
     * Store player's momentum into the battery (accumulated).
     * Stops the player afterwards.
     */
    public static boolean storeMomentum(ItemStack stack, Player player) {
        Vec3 vel = player.getDeltaMovement();
        if (vel.equals(Vec3.ZERO)) return false; // nothing to store

        CompoundTag tag = stack.getOrCreateTag();

        // Read old stored vector
        double x = tag.getDouble(TAG_X);
        double y = tag.getDouble(TAG_Y);
        double z = tag.getDouble(TAG_Z);

        // Add new velocity to it
        x += vel.x;
        y += vel.y;
        z += vel.z;

        // Save accumulated vector
        tag.putDouble(TAG_X, x);
        tag.putDouble(TAG_Y, y);
        tag.putDouble(TAG_Z, z);

        // Stop the player
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        // Durability loss
        stack.hurtAndBreak(1, player, p -> onBatteryBreak(stack, p));

        return true;
    }

    /**
     * Release stored momentum and clear battery.
     */
    public static boolean releaseMomentum(ItemStack stack, Player player) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_X)) return false;

        Vec3 vel = new Vec3(tag.getDouble(TAG_X), tag.getDouble(TAG_Y), tag.getDouble(TAG_Z));
        if (vel.equals(Vec3.ZERO)) return false;

        // Apply accumulated momentum (vector addition)
        player.push(vel.x, vel.y, vel.z);
        player.hurtMarked = true;

        // Clear storage
        tag.remove(TAG_X);
        tag.remove(TAG_Y);
        tag.remove(TAG_Z);

        // Durability loss
        stack.hurtAndBreak(1, player, p -> onBatteryBreak(stack, p));

        return true;
    }

    /**
     * Right click releases momentum.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        boolean released = releaseMomentum(stack, player);
        if (released && !level.isClientSide && level instanceof ServerLevel server) {
            server.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    10, 0.3, 0.3, 0.3, 0.05
            );
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Called when the item breaks completely.
     * Any remaining stored momentum gets dumped violently.
     */
    private static void onBatteryBreak(ItemStack stack, Player player) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;

        Vec3 vel = new Vec3(
                tag.getDouble(TAG_X),
                tag.getDouble(TAG_Y),
                tag.getDouble(TAG_Z)
        );

        if (!vel.equals(Vec3.ZERO)) {
            // Violent release on break
            player.push(vel.x * 2, vel.y * 2, vel.z * 2);
            player.hurtMarked = true;

            if (player.level() instanceof ServerLevel server) {
                server.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        20, 0.6, 0.6, 0.6, 0.1
                );
            }
        }
    }

    /**
     * Tooltip shows stored vector magnitude.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_X)) {
            Vec3 vel = new Vec3(tag.getDouble(TAG_X), tag.getDouble(TAG_Y), tag.getDouble(TAG_Z));
            double mag = vel.length();
            tooltip.add(Component.literal("Stored Momentum: " + String.format("%.2f", mag)));
        } else {
            tooltip.add(Component.literal("Stored Momentum: 0.00"));
        }
    }
}
