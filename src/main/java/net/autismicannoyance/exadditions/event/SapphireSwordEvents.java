package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.item.ModItems;
import net.autismicannoyance.exadditions.network.CocoonEffectPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID)
public class SapphireSwordEvents {

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        // Only run server-side (avoid client-only sending)
        if (event.getEntity().level().isClientSide) return;

        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();

        // Check if player is holding the Sapphire Sword
        if (held.is(ModItems.SAPPHIRE_SWORD.get())) {
            if (event.getTarget() instanceof LivingEntity target) {
                // Send packet to all clients tracking this entity (nearby players, attacker, etc.)
                ModNetworking.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> target),
                        new CocoonEffectPacket(target.getId(), 100) // lifetime in ticks (100 = ~5s)
                );
            }
        }
    }
}
