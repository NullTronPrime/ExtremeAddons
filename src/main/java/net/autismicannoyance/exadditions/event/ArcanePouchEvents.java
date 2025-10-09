package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.item.custom.ArcanePouchItem;
import net.autismicannoyance.exadditions.world.dimension.ArcanePouchDimensionManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "exadditions")
public class ArcanePouchEvents {
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity entity = event.getEntity();
        ServerLevel level = (ServerLevel) entity.level();
        ResourceKey<Level> dimKey = level.dimension();
        String dimPath = dimKey.location().getPath();
        if (dimPath.startsWith("pouch_")) {
            try {
                String uuidStr = dimPath.substring(6).replace("_", "-");
                UUID pouchUUID = UUID.fromString(uuidStr);
                ArcanePouchItem.handleMobDeath(level, entity, pouchUUID);
                ServerLevel overworld = level.getServer().overworld();
                entity.changeDimension(overworld, new net.minecraftforge.common.util.ITeleporter() {
                    @Override
                    public net.minecraft.world.entity.Entity placeEntity(net.minecraft.world.entity.Entity e,
                                                                         ServerLevel currentWorld, ServerLevel destWorld, float yaw,
                                                                         java.util.function.Function<Boolean, net.minecraft.world.entity.Entity> repositionEntity) {
                        e = repositionEntity.apply(false);
                        e.moveTo(0, 100, 0, yaw, e.getXRot());
                        return e;
                    }
                });
            } catch (Exception ignored) {}
        }
    }
}