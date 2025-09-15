package net.autismicannoyance.exadditions.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.entity.custom.HeadlessZombieEntity;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class HeadlessZombieRenderer extends MobRenderer<HeadlessZombieEntity, ZombieModel<HeadlessZombieEntity>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(ExAdditions.MOD_ID, "textures/entity/headless_zombie.png");

    public HeadlessZombieRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(HeadlessZombieEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(HeadlessZombieEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {

        // Scale based on death count to show power progression
        int deathCount = entity.getDeathCount();
        float scale = 1.0f + (deathCount * 0.05f); // Grow 5% per death
        poseStack.scale(scale, scale, scale);

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}