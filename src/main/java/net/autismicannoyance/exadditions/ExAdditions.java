package net.autismicannoyance.exadditions;

import net.autismicannoyance.exadditions.command.BlackHoleCommand;
import net.autismicannoyance.exadditions.command.ResetVoidCommand;
import net.autismicannoyance.exadditions.command.TestRenderCommand;
import net.autismicannoyance.exadditions.effect.ModEffects;
import net.autismicannoyance.exadditions.enchantment.ModEnchantments;
import net.autismicannoyance.exadditions.entity.ModEntities;
import net.autismicannoyance.exadditions.entity.ModEntityDataSerializers;
import net.autismicannoyance.exadditions.entity.client.PlayerlikeRenderer;
import net.autismicannoyance.exadditions.entity.custom.PlayerlikeEntity;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.autismicannoyance.exadditions.potion.ModPotions;
import net.autismicannoyance.exadditions.util.ModBrewingRecipes;
import net.autismicannoyance.exadditions.world.dimension.ModChunkGenerators;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import com.mojang.logging.LogUtils;
import net.autismicannoyance.exadditions.block.ModBlocks;
import net.autismicannoyance.exadditions.item.ModCreativeModeTabs;
import net.autismicannoyance.exadditions.item.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.autismicannoyance.exadditions.world.dimension.ModDimensions;
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
        ModDimensions.register();
        ModChunkGenerators.register(modEventBus);

        // Register entities
        ModEntities.register(modEventBus);
        ModEntityDataSerializers.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::onAttributeCreate);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Register brewing recipes
        ModBrewingRecipes.registerBrewingRecipes(event);

        // Register networking (very important!)
        event.enqueueWork(ModNetworking::register);

        // Register spawn placements
        event.enqueueWork(() -> {
            SpawnPlacements.register(ModEntities.PLAYERLIKE.get(),
                    SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (EntityType<PlayerlikeEntity> entityType, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) -> true);
        });
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
        ResetVoidCommand.register(event.getDispatcher());
        //TestRenderCommand.register(event.getDispatcher()); // Uncomment if needed
    }

    public void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(ModEntities.PLAYERLIKE.get(), PlayerlikeEntity.createAttributes().build());
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            EntityRenderers.register(ModEntities.PLAYERLIKE.get(), PlayerlikeRenderer::new);
        }
    }
}