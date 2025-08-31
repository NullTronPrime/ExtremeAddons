package net.autismicannoyance.exadditions.enchantment;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "exadditions");

    // Existing enchantment
    public static final RegistryObject<Enchantment> RING_CAPACITY =
            ENCHANTMENTS.register("ring_capacity", RingCapacityEnchantment::new);

    // Tool enchantments
    public static final RegistryObject<Enchantment> VEIN_MINE =
            ENCHANTMENTS.register("vein_mine", VeinMineEnchantment::new);

    public static final RegistryObject<Enchantment> RENOUNCE =
            ENCHANTMENTS.register("renounce", RenounceEnchantment::new);

    public static final RegistryObject<Enchantment> MASTERY =
            ENCHANTMENTS.register("mastery", MasteryEnchantment::new);

    public static final RegistryObject<Enchantment> SMELTING =
            ENCHANTMENTS.register("smelting", SmeltingEnchantment::new);

    public static final RegistryObject<Enchantment> SIPHON =
            ENCHANTMENTS.register("siphon", SiphonEnchantment::new);

    public static final RegistryObject<Enchantment> FARMER =
            ENCHANTMENTS.register("farmer", FarmerEnchantment::new);

    // Combat enchantments
    public static final RegistryObject<Enchantment> RISKY =
            ENCHANTMENTS.register("risky", RiskyEnchantment::new);

    public static final RegistryObject<Enchantment> EXTRACT =
            ENCHANTMENTS.register("extract", ExtractEnchantment::new);

    public static final RegistryObject<Enchantment> HEALTHY =
            ENCHANTMENTS.register("healthy", HealthyEnchantment::new);

    public static final RegistryObject<Enchantment> COMBO =
            ENCHANTMENTS.register("combo", ComboEnchantment::new);

    public static final RegistryObject<Enchantment> BREAKING =
            ENCHANTMENTS.register("breaking", BreakingEnchantment::new);

    public static final RegistryObject<Enchantment> LIFESTEAL =
            ENCHANTMENTS.register("lifesteal", LifestealEnchantment::new);

    public static final RegistryObject<Enchantment> ALL_IN =
            ENCHANTMENTS.register("all_in", AllInEnchantment::new);

    public static final RegistryObject<Enchantment> ADRENALINE =
            ENCHANTMENTS.register("adrenaline", AdrenalineEnchantment::new);

    // Ranged enchantments
    public static final RegistryObject<Enchantment> HOMING =
            ENCHANTMENTS.register("homing", HomingEnchantment::new);

    public static final RegistryObject<Enchantment> CHANCE =
            ENCHANTMENTS.register("chance", ChanceEnchantment::new);

    public static final RegistryObject<Enchantment> FROST =
            ENCHANTMENTS.register("frost", FrostEnchantment::new);

    public static final RegistryObject<Enchantment> DRAW =
            ENCHANTMENTS.register("draw", DrawEnchantment::new);

    // Armor enchantments
    public static final RegistryObject<Enchantment> LAVA_WALKER =
            ENCHANTMENTS.register("lava_walker", LavaWalkerEnchantment::new);

    public static final RegistryObject<Enchantment> MAGNETISM =
            ENCHANTMENTS.register("magnetism", MagnetismEnchantment::new);

    public static final RegistryObject<Enchantment> SPRINT =
            ENCHANTMENTS.register("sprint", SprintEnchantment::new);

    public static final RegistryObject<Enchantment> MARATHON =
            ENCHANTMENTS.register("marathon", MarathonEnchantment::new);

    // Universal enchantments
    public static final RegistryObject<Enchantment> REJUVENATE =
            ENCHANTMENTS.register("rejuvenate", RejuvenateEnchantment::new);

    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }
}