package net.autismicannoyance.exadditions.item;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExAdditions.MOD_ID);

    public static final RegistryObject<CreativeModeTab> ADDITIONS_TAB = CREATIVE_MODE_TABS.register("additions_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.SAPPHIRE.get()))
                    .title(Component.translatable("creativetab.additions_tab"))
                    .displayItems((pParameters,pOutput)->{
                        pOutput.accept(ModItems.SAPPHIRE.get());
                        pOutput.accept(ModItems.STRAWBERRY.get());
                        pOutput.accept(ModItems.RAW_SAPPHIRE.get());
                        pOutput.accept(ModItems.METAL_DETECTOR.get());
                        pOutput.accept(ModItems.METAL_DETECTOR_V2.get());
                        pOutput.accept(ModItems.FIRE_RESIST_CHARM.get());
                        pOutput.accept(ModItems.REFLECT_CHARM.get());
                        pOutput.accept(ModItems.MOMENTUM_BATTERY.get());
                        pOutput.accept(ModItems.EYE_OF_SAURON.get());
                        pOutput.accept(ModItems.BLAST_RESIST_CHARM.get());
                        pOutput.accept(ModItems.CALIBRATED_BLAST_CHARM.get());
                        pOutput.accept(ModItems.DEATH_CHARM.get());
                        pOutput.accept(ModItems.BLOOD_CHARM.get());
                        pOutput.accept(ModBlocks.SAPPHIRE_BLOCK.get());
                        pOutput.accept(ModBlocks.RAW_SAPPHIRE_BLOCK.get());
                        pOutput.accept(ModBlocks.SAPPHIRE_ORE.get());
                        pOutput.accept(ModBlocks.DEEPSLATE_SAPPHIRE_ORE.get());
                        pOutput.accept(ModBlocks.NETHER_SAPPHIRE_ORE.get());
                        pOutput.accept(ModBlocks.END_STONE_SAPPHIRE_ORE.get());
                        pOutput.accept(ModBlocks.SOUND_BLOCK.get());
                        pOutput.accept(ModItems.CHRONOS_IMPLEMENT.get());

                    })
                    .build());

    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TABS.register(eventBus);
    }

}
