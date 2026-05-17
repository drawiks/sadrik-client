package sadrik.modules.impl.combat.aura.rotations;

import sadrik.Initialization;
import sadrik.modules.impl.combat.aura.Angle;
import sadrik.modules.impl.combat.aura.MathAngle;
import sadrik.modules.impl.combat.aura.attack.StrikeManager;
import sadrik.modules.impl.combat.aura.impl.RotateConstructor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

import static sadrik.IMinecraft.mc;

public class FTAngleBypass extends RotateConstructor {
    public FTAngleBypass() {
        super("FTAngleBypass");
    }

    public static final FTAngleBypass INSTANCE = new FTAngleBypass();

    long smoothbackShakeStartMs;

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        StrikeManager attackHandler = Initialization.getInstance().getManager().getAttackPerpetrator().getAttackHandler();

        if (entity instanceof LivingEntity && attackHandler.canAttack(null, 5)) {
            Angle delta = MathAngle.calculateDelta(currentAngle, targetAngle);
            float yawDelta = delta.getYaw();
            float pitchDelta = delta.getPitch();

            float rotationDifference = (float) Math.hypot(yawDelta, pitchDelta);

            float smoothYaw = yawDelta * 0.85f;
            float smoothPitch = pitchDelta * 0.85f;

            float newYaw, newPitch;
            if (rotationDifference > 130.0f) {
                float[] clamped = clampToMax(yawDelta, pitchDelta, rotationDifference, 130.0f);
                newYaw = currentAngle.getYaw() + clamped[0];
                newPitch = currentAngle.getPitch() + clamped[1];
            } else {
                newYaw = currentAngle.getYaw() + smoothYaw;
                newPitch = currentAngle.getPitch() + smoothPitch;
            }

            return new Angle(newYaw, newPitch);
        } else if (entity == null || !attackHandler.canAttack(null, 5)) {
            double rotationDifference = Math.hypot(currentAngle.getYaw() - targetAngle.getYaw(), currentAngle.getPitch() - targetAngle.getPitch());

            if (attackHandler.getCount() % 86 == 0 && !attackHandler.getAttackTimer().finished(250.0)) {
                mc.player.swingHand(Hand.MAIN_HAND);
                return new Angle(mc.player.getYaw(), -90.0f);
            }

            if (rotationDifference > 1.0) {
                float yawDelta = targetAngle.getYaw() - currentAngle.getYaw();
                float pitchDelta = targetAngle.getPitch() - currentAngle.getPitch();

                float smoothYaw = yawDelta * 0.85f;
                float smoothPitch = pitchDelta * 0.85f;

                if (smoothbackShakeStartMs == 0) {
                    smoothbackShakeStartMs = System.currentTimeMillis();
                }

                long elapsedMs = System.currentTimeMillis() - smoothbackShakeStartMs;
                double progressBack = Math.min(elapsedMs / 1000.0, 1.0);
                double shakeMultiplier = 1.0 - progressBack;

                float shakeYaw = (float) shakeMultiplier * randomBetween(18.0f, 28.0f) * (float) Math.sin(elapsedMs * 0.0005);
                float shakePitch = (float) shakeMultiplier * randomBetween(6.0f, 16.0f) * (float) Math.sin(elapsedMs * 0.0007);

                return new Angle(
                        currentAngle.getYaw() + smoothYaw + shakeYaw,
                        currentAngle.getPitch() + smoothPitch + shakePitch
                );
            } else {
                smoothbackShakeStartMs = 0;
            }
        }

        return currentAngle;
    }

    public static float[] clampToMax(float yawDelta, float pitchDelta, float rotationDifference, float max) {
        float clampedYaw = (yawDelta / rotationDifference) * max;
        float clampedPitch = (pitchDelta / rotationDifference) * max;
        return new float[]{clampedYaw, clampedPitch};
    }

    public static float randomBetween(float min, float max) {
        if (min == max) return min;
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }
}
