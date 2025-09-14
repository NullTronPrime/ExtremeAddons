package net.autismicannoyance.exadditions.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.entity.custom.PlayerlikeEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PlayerlikeRenderer extends MobRenderer<PlayerlikeEntity, PlayerModel<PlayerlikeEntity>> {

    // Path to your custom skin texture - place this in assets/exadditions/textures/entity/
    private static final ResourceLocation TEXTURE = new ResourceLocation(ExAdditions.MOD_ID, "textures/entity/playerlike.png");

    public PlayerlikeRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerlikeEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(PlayerlikeEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (entity.isBaby()) {
            poseStack.scale(0.5f, 0.5f, 0.5f);
        }

        // Render the entity model
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);

        // Note: The adaptation wheel is rendered separately by AdaptationWheelRenderer
        // This keeps the model rendering separate from the dynamic wheel effects
    }
}