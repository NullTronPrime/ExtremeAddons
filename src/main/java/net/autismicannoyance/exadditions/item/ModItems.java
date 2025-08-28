package net.autismicannoyance.exadditions.item;
import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.item.custom.MetalDetectorItem;
import net.autismicannoyance.exadditions.item.custom.MetalDetectorV2Item;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExAdditions.MOD_ID);
    //Add items starting here

    public static final RegistryObject<Item> SAPPHIRE= ITEMS.register("sapphire",
            () -> new Item(new Item.Properties()));


    public static final RegistryObject<Item> RAW_SAPPHIRE= ITEMS.register("raw_sapphire",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> METAL_DETECTOR = ITEMS.register("metal_detector",
            () -> new MetalDetectorItem(new Item.Properties().durability(100)));

    public static final RegistryObject<Item> METAL_DETECTOR_V2 = ITEMS.register("metal_detector_v2",
            () -> new MetalDetectorV2Item(new Item.Properties().durability(500)));

    public static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}
