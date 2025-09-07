package net.autismicannoyance.exadditions;

import net.autismicannoyance.exadditions.command.BlackHoleCommand;
import net.autismicannoyance.exadditions.command.TestRenderCommand;
import net.autismicannoyance.exadditions.effect.ModEffects;
import net.autismicannoyance.exadditions.enchantment.ModEnchantments;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.autismicannoyance.exadditions.potion.ModPotions;
import net.autismicannoyance.exadditions.util.ModBrewingRecipes;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import com.mojang.logging.LogUtils;
import net.autismicannoyance.exadditions.block.ModBlocks;
import net.autismicannoyance.exadditions.item.ModCreativeModeTabs;
import net.autismicannoyance.exadditions.item.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ExAdditions.MOD_ID)
public class ExAdditions {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "exadditions";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public ExAdditions(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModCreativeModeTabs.register(modEventBus);

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModEnchantments.register(modEventBus);
        ModEffects.register(modEventBus);
        ModPotions.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Register brewing recipes
        ModBrewingRecipes.registerBrewingRecipes(event);

        // 👇 Register networking (very important!)
        event.enqueueWork(ModNetworking::register);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.SAPPHIRE);
            event.accept(ModItems.RAW_SAPPHIRE);
            event.accept(ModItems.CHAOS_CRYSTAL); // Add chaos crystal to creative tab
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    // FIXED: Enable command registration event handler
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BlackHoleCommand.register(event.getDispatcher());
        //TestRenderCommand.register(event.getDispatcher()); // Uncomment if needed
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
    }
}