package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.network.LaserAttackPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

public class LaserRifleItem extends Item {
    private static final int COOLDOWN_TICKS = 40; // 2 seconds
    private static final int ENERGY_COST = 1; // Durability cost per shot

    public LaserRifleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        // Check cooldown
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(itemstack);
        }

        // Check durability
        if (itemstack.getDamageValue() >= itemstack.getMaxDamage() - ENERGY_COST) {
            return InteractionResultHolder.fail(itemstack);
        }

        if (!level.isClientSide) {
            // Calculate firing direction from player's look vector
            Vec3 startPos = player.getEyePosition();
            Vec3 lookVec = player.getLookAngle();

            // Send laser attack packet to all clients
            LaserAttackPacket packet = new LaserAttackPacket(
                    startPos,
                    lookVec,
                    player.getId(),
                    5.0f, // base damage
                    10,   // max bounces
                    50.0  // max range
            );

            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    packet
            );

            // Play sound
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 2.0f);
        }

        // Apply cooldown and durability damage
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        itemstack.hurtAndBreak(ENERGY_COST, player, (p) -> p.broadcastBreakEvent(hand));

        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamaged();
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // Cyan color for energy bar
        return 0x00FFFF;
    }
}