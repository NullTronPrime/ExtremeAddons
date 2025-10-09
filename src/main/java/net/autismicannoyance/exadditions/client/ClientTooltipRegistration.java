package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.item.custom.ArcanePouchItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles registration of custom tooltip components on the client side.
 * This event-based approach is the correct way to register tooltip components in Forge 1.20.1+
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientTooltipRegistration {

    @SubscribeEvent
    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(
                ArcanePouchItem.ArcanePouchTooltip.class,
                ArcanePouchTooltipRenderer::new
        );
    }
}