package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.network.ChaosCrystalPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

public class ChaosCrystalItem extends Item {

    public ChaosCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Only send from server — packet will be delivered to tracking clients where the renderer runs
        if (!attacker.level().isClientSide) {
            // send to all clients tracking the target (same as Cocoon flow)
            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> target),
                    new ChaosCrystalPacket(target.getId(), 100) // lifetime in ticks (changeable)
            );
        }

        return super.hurtEnemy(stack, target, attacker);
    }
}
