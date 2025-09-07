package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.network.BlackHoleEffectPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * Handles syncing black holes to players who join the server
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerJoinHandler {

    /**
     * When a player joins, send them all active black hole effects
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Get all active black hole data - FIXED method call
        List<BlackHoleEvents.BlackHoleData> activeBlackHoles = BlackHoleEvents.getAllBlackHoleData();

        // Send each black hole to the joining player
        for (BlackHoleEvents.BlackHoleData bhData : activeBlackHoles) {
            BlackHoleEffectPacket packet = new BlackHoleEffectPacket(
                    bhData.id,
                    bhData.position,
                    bhData.size,
                    bhData.rotationSpeed,
                    999999 // Long lifetime
            );

            ModNetworking.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    packet
            );
        }

        if (activeBlackHoles.size() > 0) {
            System.out.println("Synced " + activeBlackHoles.size() + " black holes to joining player: " + player.getName().getString());
        }
    }
}