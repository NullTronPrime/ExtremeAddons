package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.item.ModItems;
import net.autismicannoyance.exadditions.item.custom.MomentumBatteryItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "exadditions")
public class MomentumBatteryEvents {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        tryStoreMomentum(e.getEntity());
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty e) {
        tryStoreMomentum(e.getEntity());
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent e) {
        tryStoreMomentum(e.getEntity());
    }

    private static void tryStoreMomentum(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.is(ModItems.MOMENTUM_BATTERY.get())) {
            boolean stored = MomentumBatteryItem.storeMomentum(stack, player);
            if (stored && !player.level().isClientSide) {
                ((ServerLevel)player.level()).sendParticles(
                        ParticleTypes.CRIT,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        6, 0.2, 0.2, 0.2, 0.05
                );
            }
        }
    }
}
