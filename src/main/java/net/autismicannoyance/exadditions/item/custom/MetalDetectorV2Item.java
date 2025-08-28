package net.autismicannoyance.exadditions.item.custom;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class MetalDetectorV2Item extends Item {

    public MetalDetectorV2Item(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (!level.isClientSide() && player != null) {
            BlockPos clickedPos = context.getClickedPos();

            // prevent spamming (cooldown of 40 ticks = 2 seconds)
            player.getCooldowns().addCooldown(this, 40);

            List<Vein> veins = new ArrayList<>();
            Set<BlockPos> visited = new HashSet<>();

            for (int i = 0; i <= clickedPos.getY() + 64; i++) {
                BlockPos checkPos = clickedPos.below(i);
                if (!visited.contains(checkPos)) {
                    BlockState state = level.getBlockState(checkPos);
                    if (isValuableBlock(state)) {
                        Vein vein = findVein(level, checkPos, state.getBlock(), visited);
                        if (vein != null) {
                            veins.add(vein);
                        }
                    }
                }
            }

            if (veins.isEmpty()) {
                player.sendSystemMessage(Component.literal("No valuables found!"));
            } else {
                // Sort veins by distance to the clicked position
                veins.sort(Comparator.comparingDouble(v -> v.distanceTo(clickedPos)));

                // Report up to 3 nearest veins
                int veinsToReport = Math.min(3, veins.size());
                for (int i = 0; i < veinsToReport; i++) {
                    Vein vein = veins.get(i);
                    player.sendSystemMessage(Component.literal(
                            "Found " + I18n.get(vein.block.getDescriptionId()) +
                                    " vein (" + vein.size + " blocks) at " +
                                    "(" + vein.origin.getX() + ", " +
                                    vein.origin.getY() + ", " +
                                    vein.origin.getZ() + ")"
                    ));
                }
            }
        }

        // Damage the item on use
        context.getItemInHand().hurtAndBreak(1, player,
                p -> p.broadcastBreakEvent(p.getUsedItemHand()));

        return InteractionResult.SUCCESS;
    }

    private boolean isValuableBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE ||
                block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE ||
                block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE ||
                block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE ||
                block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE ||
                block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE ||
                block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE ||
                block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE ||
                block == Blocks.NETHER_QUARTZ_ORE || block == Blocks.NETHER_GOLD_ORE ||
                block == Blocks.ANCIENT_DEBRIS;
    }

    private Vein findVein(Level level, BlockPos startPos, Block oreBlock, Set<BlockPos> visited) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(startPos);

        Set<BlockPos> veinBlocks = new HashSet<>();
        veinBlocks.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            // Check neighbors within a 2-block radius
            for (BlockPos neighbor : BlockPos.betweenClosed(
                    current.getX() - 2, current.getY() - 2, current.getZ() - 2,
                    current.getX() + 2, current.getY() + 2, current.getZ() + 2)) {

                if (!visited.contains(neighbor)) {
                    BlockState neighborState = level.getBlockState(neighbor);
                    if (neighborState.getBlock() == oreBlock) {
                        visited.add(neighbor);
                        veinBlocks.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        if (veinBlocks.isEmpty()) return null;
        return new Vein(startPos, oreBlock, veinBlocks.size());
    }

    private static class Vein {
        BlockPos origin;
        Block block;
        int size;

        Vein(BlockPos origin, Block block, int size) {
            this.origin = origin;
            this.block = block;
            this.size = size;
        }

        double distanceTo(BlockPos pos) {
            return pos.distSqr(origin);
        }
    }
}
