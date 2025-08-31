package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.effect.EnderosisEffect;
import net.autismicannoyance.exadditions.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "exadditions")
public class EnderosisEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        // Check if the projectile hit a player
        if (!(event.getRayTraceResult().getType() == net.minecraft.world.phys.HitResult.Type.ENTITY)) {
            return;
        }

        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult entityHit)) {
            return;
        }

        if (!(entityHit.getEntity() instanceof Player player)) {
            return;
        }

        // Check if the player has the Enderosis effect
        MobEffectInstance enderosisEffect = player.getEffect(ModEffects.ENDEROSIS.get());
        if (enderosisEffect == null) {
            return;
        }

        // Get the projectile
        Projectile projectile = (Projectile) event.getProjectile();

        // Cancel the impact (projectile goes through the player)
        event.setCanceled(true);

        // Teleport the player away
        int amplifier = enderosisEffect.getAmplifier();
        boolean teleported = EnderosisEffect.teleportFromProjectile(player, amplifier);

        if (teleported) {
            // The projectile continues with its original velocity
            // No need to modify projectile velocity as we're just canceling the impact
        }
    }
}