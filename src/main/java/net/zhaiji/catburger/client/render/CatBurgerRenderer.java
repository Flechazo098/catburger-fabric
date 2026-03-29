package net.zhaiji.catburger.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.client.TrinketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.zhaiji.catburger.config.CatBurgerConfig;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.WeakHashMap;

public class CatBurgerRenderer implements TrinketRenderer {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Map<TrinketInventory, MotionState> MOTION_STATES = new WeakHashMap<>();

    private static final double MOVEMENT_LAG_STRENGTH = 0.024;
    private static final double MOVEMENT_LAG_RESPONSE = 15.0;
    private static final double POSITION_SPRING_STIFFNESS = 70.0;
    private static final double POSITION_SPRING_DAMPING = 14.0;
    private static final double PITCH_RESPONSE = 14.0;
    private static final double ROLL_SPRING_STIFFNESS = 52.0;
    private static final double ROLL_SPRING_DAMPING = 11.0;
    private static final double TURN_RATE_TO_ROLL = 0.02;
    private static final float MAX_TURN_ROLL_DEG = 10.0F;
    private static final double MAX_DELTA_PER_FRAME = 0.35;

    private static double getFloatSpeed(LivingEntityRenderState state) {
        return CatBurgerConfig.get().client.float_distance / 2.0 * Math.sin(state.ageInTicks * Math.PI / CatBurgerConfig.get().client.time * 2.0);
    }

    private static MotionState getMotionState(SlotReference slotReference) {
        TrinketInventory inventory = slotReference.inventory();
        MotionState state = MOTION_STATES.get(inventory);
        if (state == null) {
            state = new MotionState();
            MOTION_STATES.put(inventory, state);
        }
        return state;
    }

    private static double smoothingAlpha(double rate, double dtSeconds) {
        return 1.0 - Math.exp(-rate * dtSeconds);
    }

    private static double lerp(double current, double target, double alpha) {
        return current + (target - current) * alpha;
    }

    private static void updateSpringPosition(MotionState motionState, double targetX, double targetY, double targetZ, double dtSeconds) {
        motionState.followVelocityX += (targetX - motionState.followX) * POSITION_SPRING_STIFFNESS * dtSeconds;
        motionState.followVelocityY += (targetY - motionState.followY) * POSITION_SPRING_STIFFNESS * dtSeconds;
        motionState.followVelocityZ += (targetZ - motionState.followZ) * POSITION_SPRING_STIFFNESS * dtSeconds;

        double dampingFactor = Math.exp(-POSITION_SPRING_DAMPING * dtSeconds);
        motionState.followVelocityX *= dampingFactor;
        motionState.followVelocityY *= dampingFactor;
        motionState.followVelocityZ *= dampingFactor;

        motionState.followX += motionState.followVelocityX * dtSeconds;
        motionState.followY += motionState.followVelocityY * dtSeconds;
        motionState.followZ += motionState.followVelocityZ * dtSeconds;
    }

    private static float updateRollSpring(MotionState motionState, float targetRoll, double dtSeconds) {
        motionState.rollVelocity += (targetRoll - motionState.roll) * (float) (ROLL_SPRING_STIFFNESS * dtSeconds);
        motionState.rollVelocity *= (float) Math.exp(-ROLL_SPRING_DAMPING * dtSeconds);
        motionState.roll += motionState.rollVelocity * dtSeconds;
        return motionState.roll;
    }

    @Override
    public void render(
            ItemStack stack,
            SlotReference slotReference,
            EntityModel<? extends LivingEntityRenderState> contextModel,
            PoseStack matrices,
            SubmitNodeCollector nodeCollector,
            int light,
            LivingEntityRenderState state,
            float limbAngle, float limbDistance
    ) {
        MotionState motionState = getMotionState(slotReference);
        float ageInTicks = state.ageInTicks;
        float deltaTicks = ageInTicks - motionState.lastAgeInTicks;
        if (!motionState.initialized || deltaTicks <= 0.0F || deltaTicks > 5.0F) {
            deltaTicks = 1.0F;
        }
        double dtSeconds = Mth.clamp(deltaTicks / 20.0, 0.01, 0.1);

        double dx = 0.0;
        double dy = 0.0;
        double dz = 0.0;

        if (motionState.initialized) {
            dx = Mth.clamp(state.x - motionState.lastX, -MAX_DELTA_PER_FRAME, MAX_DELTA_PER_FRAME);
            dy = Mth.clamp(state.y - motionState.lastY, -MAX_DELTA_PER_FRAME, MAX_DELTA_PER_FRAME);
            dz = Mth.clamp(state.z - motionState.lastZ, -MAX_DELTA_PER_FRAME, MAX_DELTA_PER_FRAME);
        } else {
            motionState.lastViewYaw = state.yRot;
            motionState.smoothPitch = state.xRot;
        }

        motionState.lastX = state.x;
        motionState.lastY = state.y;
        motionState.lastZ = state.z;
        motionState.lastAgeInTicks = ageInTicks;

        float viewYaw = state.yRot;
        float viewPitch = state.xRot;
        double yawRadians = Math.toRadians(viewYaw);

        double invDt = 1.0 / Math.max(dtSeconds, 1.0E-4);
        double velocityX = dx * invDt;
        double velocityY = dy * invDt;
        double velocityZ = dz * invDt;

        double cos = Math.cos(yawRadians);
        double sin = Math.sin(yawRadians);
        double rightVelocity = velocityX * cos - velocityZ * sin;
        double forwardVelocity = -velocityX * sin - velocityZ * cos;

        double lagAlpha = smoothingAlpha(MOVEMENT_LAG_RESPONSE, dtSeconds);
        motionState.movementLagRight = lerp(motionState.movementLagRight, -rightVelocity * MOVEMENT_LAG_STRENGTH, lagAlpha);
        motionState.movementLagForward = lerp(motionState.movementLagForward, -forwardVelocity * MOVEMENT_LAG_STRENGTH, lagAlpha);
        motionState.movementLagY = lerp(motionState.movementLagY, -velocityY * MOVEMENT_LAG_STRENGTH * 0.35, lagAlpha);

        double bobTarget = getFloatSpeed(state);
        double desiredLocalRight = CatBurgerConfig.get().client.left_right_offset + motionState.movementLagRight;
        double desiredLocalForward = CatBurgerConfig.get().client.front_back_offset + motionState.movementLagForward;
        double desiredX = desiredLocalRight * cos - desiredLocalForward * sin;
        double desiredZ = -desiredLocalRight * sin - desiredLocalForward * cos;
        double desiredY = bobTarget + motionState.movementLagY - CatBurgerConfig.get().client.vertical_offset;

        if (state.hasPose(Pose.CROUCHING)) {
            desiredY += 0.1875F;
        }

        if (!motionState.initialized) {
            motionState.followX = desiredX;
            motionState.followY = desiredY;
            motionState.followZ = desiredZ;
            motionState.initialized = true;
        } else {
            updateSpringPosition(motionState, desiredX, desiredY, desiredZ, dtSeconds);
        }

        double pitchAlpha = smoothingAlpha(PITCH_RESPONSE, dtSeconds);
        motionState.smoothPitch = (float) lerp(motionState.smoothPitch, viewPitch, pitchAlpha);
        float yawDelta = Mth.wrapDegrees(viewYaw - motionState.lastViewYaw);
        float yawRate = (float) (yawDelta / dtSeconds);
        float targetRoll = Mth.clamp((float) (-yawRate * TURN_RATE_TO_ROLL), -MAX_TURN_ROLL_DEG, MAX_TURN_ROLL_DEG);
        float roll = updateRollSpring(motionState, targetRoll, dtSeconds);
        motionState.lastViewYaw = viewYaw;

        matrices.pushPose();

        matrices.translate(motionState.followX, motionState.followY, motionState.followZ);

        float scale = (float) CatBurgerConfig.get().client.scale;
        matrices.scale(scale, scale, scale);
        matrices.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(180)));
        matrices.mulPose(Axis.ZP.rotationDegrees(roll));
        matrices.mulPose(Axis.YP.rotationDegrees(-viewYaw));
        matrices.mulPose(Axis.XP.rotationDegrees(-motionState.smoothPitch));

        if (MC.level != null) {
            MC.getItemModelResolver().updateForTopItem(motionState.renderState, stack, ItemDisplayContext.HEAD, MC.level, null, 0);
            motionState.renderState.submit(matrices, nodeCollector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }


        matrices.popPose();
    }

    private static final class MotionState {
        private final ItemStackRenderState renderState = new ItemStackRenderState();

        private boolean initialized;
        private double lastX;
        private double lastY;
        private double lastZ;
        private float lastAgeInTicks;
        private float lastViewYaw;

        private double movementLagRight;
        private double movementLagForward;
        private double movementLagY;

        private double followX;
        private double followY;
        private double followZ;
        private double followVelocityX;
        private double followVelocityY;
        private double followVelocityZ;

        private float roll;
        private float rollVelocity;
        private float smoothPitch;
    }
}
