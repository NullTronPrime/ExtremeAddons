package net.autismicannoyance.exadditions.item;
import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.item.custom.*;
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

    public static final RegistryObject<Item> STRAWBERRY = ITEMS.register("strawberry",
            () -> new Item(new Item.Properties().food(ModFoods.STRAWBERRY)));
    public static final RegistryObject<Item> CHAOS_FRUIT = ITEMS.register("chaos_fruit",
            () -> new ChaosFoodItem(new Item.Properties().food(ModFoods.CHAOS_FRUIT).stacksTo(16)));


    public static final RegistryObject<Item> RAW_SAPPHIRE= ITEMS.register("raw_sapphire",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> METAL_DETECTOR = ITEMS.register("metal_detector",
            () -> new MetalDetectorItem(new Item.Properties().durability(100)));

    public static final RegistryObject<Item> METAL_DETECTOR_V2 = ITEMS.register("metal_detector_v2",
            () -> new MetalDetectorV2Item(new Item.Properties().durability(500)));

    public static final RegistryObject<Item> FIRE_RESIST_CHARM = ITEMS.register("fire_resist_charm",
            () -> new FireResistCharmItem(new Item.Properties()
                    .stacksTo(1)       // only one per stack
                    .fireResistant()   // can’t burn in lava/fire
            ));
    public static final RegistryObject<Item> BLAST_RESIST_CHARM = ITEMS.register("blast_resist_charm",
            () -> new FireResistCharmItem(new Item.Properties()
                    .stacksTo(1)
            ));
    public static final RegistryObject<Item> DEATH_CHARM = ITEMS.register("death_charm",
            () -> new DeathCharmItem(new Item.Properties()
                    .stacksTo(1)
                    .fireResistant() // mark item as fireResistant so it won't burn as an ItemStack
            ));

    public static final RegistryObject<Item> BLOOD_CHARM = ITEMS.register("blood_charm",
            () -> new BloodCharmItem(new Item.Properties()
                    .stacksTo(1)
                    .fireResistant() // mark item as fireResistant so it won't burn as an ItemStack
            ));

    public static final RegistryObject<Item> REFLECT_CHARM = ITEMS.register("reflect_charm",
            () -> new Item(new Item.Properties()
                    .stacksTo(1)
                    .fireResistant()
            ));
    public static final RegistryObject<Item> MOMENTUM_BATTERY = ITEMS.register("momentum_battery",
            () -> new MomentumBatteryItem(new Item.Properties().durability(128).fireResistant()));

    public static final RegistryObject<Item> EYE_OF_SAURON = ITEMS.register("eye_of_sauron",
            () -> new EyeOfSauronItem(new Item.Properties().fireResistant()));

    public static final RegistryObject<Item> CALIBRATED_BLAST_CHARM = ITEMS.register("calibrated_blast_charm",
            () -> new CalibratedBlastCharmItem(new Item.Properties()
                    .stacksTo(1)
                    .fireResistant()
            ));
    public static final RegistryObject<Item> PINE_CONE = ITEMS.register("pine_cone",
            () -> new FuelItem(new Item.Properties(), 400));


    public static final RegistryObject<Item> CHRONOS_IMPLEMENT = ITEMS.register("chronos_implement",
            () -> new ChronosImplementItem(new Item.Properties()
                    .durability(200)
                    .fireResistant()
            ));

    public static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}
