package net.autismicannoyance.exadditions.screen;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ExAdditions.MOD_ID);

    public static final RegistryObject<MenuType<AdvancedCraftingMenu>> ADVANCED_CRAFTING_MENU =
            MENUS.register("advanced_crafting_menu", () -> IForgeMenuType.create(AdvancedCraftingMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}