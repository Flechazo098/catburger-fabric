package net.zhaiji.catburger.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.client.TrinketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.zhaiji.catburger.CatBurger;
import net.zhaiji.catburger.config.CatBurgerConfig;
import org.joml.Quaternionf;

public class CatBurgerRenderer implements TrinketRenderer {

    private static final Minecraft MC = Minecraft.getInstance();

    public static BakedModel getModel() {
        return Minecraft.getInstance().getModelManager().getModel(new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(CatBurger.MOD_ID, "cat_burger"), "inventory"));
    }

    public static double getFloatSpeed(LivingEntityRenderState state) {
        return CatBurgerConfig.get().client.float_distance / 2.0 * Math.sin(state.ageInTicks * Math.PI / CatBurgerConfig.get().client.time * 2.0);
    }

    @Override
    public void render(
            ItemStack stack,
            SlotReference slotReference,
            EntityModel<? extends LivingEntityRenderState> contextModel,
            PoseStack matrices,
            MultiBufferSource vertexConsumers,
            int light,
            LivingEntityRenderState state,
            float headYaw, float headPitch
    ) {
//        MC.getItemModelResolver().updateForTopItem(
//                state.headItem,
//                stack,
//                ItemDisplayContext.HEAD,
//                false,
//                MC.level,
//                MC.player,
//                1
//        );
        matrices.pushPose();

        double yawRadians = Math.toRadians(headYaw);
        double xOffset = 0;
        double yOffset = 0;
        double zOffset = 0;

        xOffset += Math.cos(yawRadians + Math.PI / 2) * CatBurgerConfig.get().client.front_back_offset;
        zOffset -= Math.sin(yawRadians + Math.PI / 2) * CatBurgerConfig.get().client.front_back_offset;

        yOffset += CatBurgerRenderer.getFloatSpeed(state);
        yOffset -= CatBurgerConfig.get().client.vertical_offset;


        if (state.hasPose(Pose.CROUCHING)) {
            matrices.translate(0.0F, 0.1875F, 0.0F);
        }

        xOffset += Math.cos(yawRadians) * CatBurgerConfig.get().client.left_right_offset;
        zOffset -= Math.sin(yawRadians) * CatBurgerConfig.get().client.left_right_offset;

        matrices.translate(xOffset, yOffset, zOffset);

        float scale = (float) CatBurgerConfig.get().client.scale;
        matrices.scale(scale, scale, scale);
        matrices.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(180)));
        matrices.mulPose(Axis.YP.rotationDegrees(-headYaw));
        matrices.mulPose(Axis.XP.rotationDegrees(-headPitch));
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.HEAD,
                light,
                OverlayTexture.NO_OVERLAY,
                matrices,
                vertexConsumers,
                MC.level,
                0
        );


        matrices.popPose();
    }
}