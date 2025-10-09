package net.autismicannoyance.exadditions.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.function.Function;

@Mod.EventBusSubscriber(modid = "exadditions")
public class PouchDimensionExitHandler {
    @SubscribeEvent
    public static void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ResourceKey<Level> dimKey = player.level().dimension();
        String dimPath = dimKey.location().getPath();
        if (dimPath.startsWith("pouch_")) {
            BlockPos pos = event.getPos();
            if (pos.getX() == 0 && pos.getZ() == 0 && pos.getY() == 64) {
                ServerLevel overworld = serverPlayer.getServer().overworld();
                serverPlayer.changeDimension(overworld, new net.minecraftforge.common.util.ITeleporter() {
                    @Override
                    public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                        entity = repositionEntity.apply(false);
                        entity.moveTo(entity.getX(), entity.getY() + 5, entity.getZ(), yaw, entity.getXRot());
                        return entity;
                    }
                });
                event.setCanceled(true);
            }
        }
    }
}