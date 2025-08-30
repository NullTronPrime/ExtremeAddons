package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.item.ModItems;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "exadditions")
public class FireResistCharmEvents {

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            if (player.getInventory().contains(ModItems.FIRE_RESIST_CHARM.get().getDefaultInstance())) {
                event.setAmount(event.getAmount() * 0.8f); // reduce fire damage by 20%
            }
        }
    }
}
