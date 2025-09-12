package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.world.dimension.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class VoidPortalItem extends Item {
    public VoidPortalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // Check if player is in void dimension
            if (level.dimension() == ModDimensions.VOID_DIM_LEVEL) {
                // Teleport back to overworld
                ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
                if (overworld != null) {
                    BlockPos spawnPos = overworld.getSharedSpawnPos();
                    serverPlayer.teleportTo(overworld, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), 0, 0);
                    player.sendSystemMessage(Component.literal("Returned to the Overworld!"));
                }
            } else {
                // Teleport to void dimension
                ServerLevel voidDim = level.getServer().getLevel(ModDimensions.VOID_DIM_LEVEL);
                if (voidDim != null) {
                    // Teleport to center of void dimension
                    serverPlayer.teleportTo(voidDim, 0.5, 100, 0.5, 0, 0);
                    player.sendSystemMessage(Component.literal("Entered the Void Dimension!"));
                }
            }

            // Damage the item (if it has durability)
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isDamageableItem()) {
                stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
            }
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}