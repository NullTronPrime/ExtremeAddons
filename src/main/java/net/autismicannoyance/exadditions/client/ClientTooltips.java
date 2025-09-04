package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.item.custom.SuperBundleItem;
import net.autismicannoyance.exadditions.item.custom.BoundlessBundleItem;
import net.autismicannoyance.exadditions.item.custom.MobBundleItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "exadditions", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientTooltips {
    @SubscribeEvent
    public static void registerTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(SuperBundleItem.SuperBundleTooltip.class, SuperBundleItem.SuperBundleTooltipRenderer::new);
        event.register(BoundlessBundleItem.BoundlessTooltip.class, BoundlessBundleItem.BoundlessTooltipRenderer::new);

        // Mob bundle tooltip registration (client-only renderer)
        event.register(MobBundleItem.MobTooltip.class, MobBundleTooltipRenderer::new);
    }
}
