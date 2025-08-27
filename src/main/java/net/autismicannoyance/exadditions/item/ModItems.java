package net.autismicannoyance.exadditions.item;
import net.autismicannoyance.exadditions.ExAdditions;
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

    public static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}
