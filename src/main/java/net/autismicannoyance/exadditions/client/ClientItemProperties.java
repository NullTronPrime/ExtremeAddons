package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.item.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientItemProperties {
    @SubscribeEvent
    public static void registerItemProperties(RegisterClientReloadListenersEvent event) {
        ItemProperties.register(ModItems.EYE_OF_SAURON.get(),
                new ResourceLocation("firing"),
                (stack, level, entity, seed) -> {
                    if (entity != null && entity.isUsingItem() && entity.getUseItem() == stack) {
                        return entity.getTicksUsingItem() > 60 ? 1.0F : 0.5F;
                    }
                    return 0.0F;
                });
    }
}
