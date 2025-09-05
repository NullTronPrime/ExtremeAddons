package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.network.EyeEffectPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class EyeTestItem extends Item {
    public EyeTestItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            double range = 6.0;
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                    e -> e != player && e.isAlive());

            Optional<LivingEntity> opt = nearby.stream()
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)));

            if (opt.isPresent()) {
                LivingEntity target = opt.get();

                int count = 6;
                int lifetime = 2400; // much longer: 2400 ticks (~2 minutes)

                ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target), new EyeEffectPacket(target.getId(), count, lifetime));

                // spawn a few particles on server to indicate success
                if (level instanceof ServerLevel) {
                    ServerLevel slevel = (ServerLevel) level;
                    slevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                            8,
                            0.4, 0.4, 0.4,
                            0.03);
                }
            }
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
