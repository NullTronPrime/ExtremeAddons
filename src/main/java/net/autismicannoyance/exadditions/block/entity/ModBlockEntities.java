package net.autismicannoyance.exadditions.block.entity;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ExAdditions.MOD_ID);

    public static final RegistryObject<BlockEntityType<AdvancedCraftingTableBlockEntity>> ADVANCED_CRAFTING_TABLE_BE =
            BLOCK_ENTITIES.register("advanced_crafting_table_block_entity", () ->
                    BlockEntityType.Builder.of(AdvancedCraftingTableBlockEntity::new,
                            ModBlocks.ADVANCED_CRAFTING_TABLE.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}