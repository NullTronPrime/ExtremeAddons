package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.item.ModItems;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "exadditions")
public class BlastResistCharmEvents {

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Check if damage is from explosion
        if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            if (player.getInventory().contains(ModItems.BLAST_RESIST_CHARM.get().getDefaultInstance())) {
                event.setAmount(event.getAmount() * 0.8f);
            }
        }
    }
}
