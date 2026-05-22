package sadrik.modules.impl.combat.aura.neuro;

import sadrik.modules.module.setting.implement.SelectSetting;
import sadrik.modules.impl.combat.aura.attack.StrikerConstructor;
import sadrik.modules.impl.combat.aura.MathAngle;
import sadrik.modules.impl.combat.aura.Angle;
import sadrik.modules.impl.combat.Aura;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

public final class NeuroAuraLearn {

    private static final float NEURO_LEARN_RATE_TRACK = 0.10f;
    private static final float NEURO_LEARN_RATE_ATTACK = 0.16f;

    private static final float TRACK_ERR_SOFT = 28.0f;
    private static final float TRACK_ERR_HARD = 85.0f;
    private static final float ATTACK_ERR_SOFT = 38.0f;
    private static final float ATTACK_ERR_HARD = 105.0f;

    private static final float MIN_LR_MUL = 0.02f;
    private static final float MAX_LR_MUL = 1.35f;

    private static final int PATTERN_SAMPLES_TRACK = 2;
    private static final int PATTERN_SAMPLES_ATTACK = 4;

    private static final int POINT_SAMPLES_TRACK = 3;
    private static final int POINT_SAMPLES_ATTACK = 6;

    private static final float CAM_VEL_EMA = 0.18f;

    private static Field swingTicksFieldA;
    private static Field swingTicksFieldB;
    private static Field swingTicksFieldC;
    private static Method swingTicksMethod;

    private final NeuroRotationModel model = new NeuroRotationModel();

    private boolean loaded = false;

    private int prevSwingTicks = 0;
    private int attackBurstTicks = 0;

    private float prevCamYaw = 0.0f;
    private float prevCamPitch = 0.0f;
    private float emaCamVelYaw = 0.0f;
    private float emaCamVelPitch = 0.0f;

    private float lastClampedYaw = 0.0f;
    private float lastClampedPitch = 0.0f;
    private boolean hasLastClamped = false;

    private int lastTargetId = Integer.MIN_VALUE;
    private int targetStableTicks = 0;

    private boolean dirty = false;

    public NeuroRotationModel getModel() {
        return model;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isDirty() {
        return dirty;
    }

    public int getSampleCount() {
        return model.totalSamples();
    }

    public void onActivate(Aura a) {
        prevSwingTicks = 0;
        attackBurstTicks = 0;
        hasLastClamped = false;
        lastTargetId = Integer.MIN_VALUE;
        targetStableTicks = 0;

        dirty = false;
        loaded = false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            prevSwingTicks = neuroSwingTicks(mc.player);
            prevCamYaw = mc.player.getYaw();
            prevCamPitch = mc.player.getPitch();
            emaCamVelYaw = 0.0f;
            emaCamVelPitch = 0.0f;
        }
    }

    public void onDeactivate(Aura a) {
        flushSave(a);
    }

    public void learnStep(Aura a, boolean onAttack) {
        if (a == null) return;
        if (!auraIsNeuroLearn(a)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (!loaded) {
            loaded = true;
            model.load();
            prevSwingTicks = neuroSwingTicks(mc.player);
            prevCamYaw = mc.player.getYaw();
            prevCamPitch = mc.player.getPitch();
            emaCamVelYaw = 0.0f;
            emaCamVelPitch = 0.0f;
        }

        LivingEntity t = a.getTarget();
        if (t == null) return;

        int tid;
        try {
            tid = t.getId();
        } catch (Exception e) {
            tid = System.identityHashCode(t);
        }

        if (tid != lastTargetId) {
            lastTargetId = tid;
            targetStableTicks = 0;
            hasLastClamped = false;
        } else {
            targetStableTicks++;
        }

        int curSwing = neuroSwingTicks(mc.player);
        boolean justAttacked = neuroSwingStarted(prevSwingTicks, curSwing);
        prevSwingTicks = curSwing;

        if (justAttacked) attackBurstTicks = 3;
        boolean burstAttack = onAttack || attackBurstTicks > 0;

        float camYaw = mc.player.getYaw();
        float camPitch = mc.player.getPitch();

        float dYawTick = wrapDeg(camYaw - prevCamYaw);
        float dPitchTick = (camPitch - prevCamPitch);

        prevCamYaw = camYaw;
        prevCamPitch = camPitch;

        emaCamVelYaw = lerp(emaCamVelYaw, dYawTick, CAM_VEL_EMA);
        emaCamVelPitch = lerp(emaCamVelPitch, dPitchTick, CAM_VEL_EMA);

        float camVel = (float) Math.sqrt(emaCamVelYaw * emaCamVelYaw + emaCamVelPitch * emaCamVelPitch);

        StrikerConstructor.AttackPerpetratorConfigurable cfg = a.getCachedConfig() != null ? a.getCachedConfig() : a.getConfig();
        Angle baseAngle = cfg != null ? cfg.getAngle() : a.getConfig().getAngle();

        float baseYaw = baseAngle.getYaw();
        float basePitch = baseAngle.getPitch();

        float rawDy = wrapDeg(camYaw - baseYaw);
        float rawDp = (camPitch - basePitch);

        float err = (float) Math.sqrt(rawDy * rawDy + rawDp * rawDp);

        float soft = burstAttack ? ATTACK_ERR_SOFT : TRACK_ERR_SOFT;
        float hard = burstAttack ? ATTACK_ERR_HARD : TRACK_ERR_HARD;

        float clipMul = 1.0f;
        if (err > soft) {
            float tMul = soft / Math.max(err, 0.001f);
            clipMul *= clamp(tMul, MIN_LR_MUL, 1.0f);
        }
        if (err > hard) {
            float tMul = hard / Math.max(err, 0.001f);
            clipMul *= clamp(tMul, 0.02f, 1.0f);
        }

        float velMul = clamp(0.55f + camVel * 0.11f, 0.55f, 1.25f);
        float stableMul = clamp(0.35f + (targetStableTicks / 8.0f), 0.35f, 1.0f);

        float lrBase = burstAttack ? NEURO_LEARN_RATE_ATTACK : NEURO_LEARN_RATE_TRACK;
        float lrMul = clipMul * velMul * stableMul;
        if (burstAttack && justAttacked) lrMul *= 1.10f;
        lrMul = clamp(lrMul, MIN_LR_MUL, MAX_LR_MUL);

        float maxErr = hard * 1.65f;
        float dy = clamp(rawDy, -maxErr, maxErr);
        float dp = clamp(rawDp, -maxErr, maxErr);

        float clampedYaw = baseYaw + dy;
        float clampedPitch = basePitch + dp;

        Box hb = a.getCachedHitbox();
        Vec3d computed = a.getCachedPoint();
        Vec3d aimedPoint = neuroAimPointFromView(hb, computed, t);

        int patternSamples = burstAttack ? PATTERN_SAMPLES_ATTACK : PATTERN_SAMPLES_TRACK;
        int pointSamples = burstAttack ? POINT_SAMPLES_ATTACK : POINT_SAMPLES_TRACK;

        if (camVel > 3.0f) {
            pointSamples = Math.min(pointSamples + 1, burstAttack ? 8 : 5);
            patternSamples = Math.min(patternSamples + 1, burstAttack ? 6 : 3);
        }

        if (patternSamples < 1) patternSamples = 1;
        if (pointSamples < 1) pointSamples = 1;

        if (!hasLastClamped) {
            lastClampedYaw = clampedYaw;
            lastClampedPitch = clampedPitch;
            hasLastClamped = true;
        }

        float dYawPath = wrapDeg(clampedYaw - lastClampedYaw);
        float dPitchPath = (clampedPitch - lastClampedPitch);

        int seedBase = mixSeed(mc.player.age, tid);

        int corrLabel = -1;
        try {
            corrLabel = corrIndex(a.getCorrectionType());
        } catch (Exception ignored) {
        }

        if (corrLabel >= 0) {
            try {
                model.learnCorrection(t, burstAttack, lrBase * lrMul, corrLabel);
            } catch (Exception ignored) {
            }
        }

        float stepYawAbs = Math.abs(dYawTick);
        float stepPitchAbs = Math.abs(dPitchTick);
        float jitterYaw = Math.abs(dYawTick - emaCamVelYaw);
        float jitterPitch = Math.abs(dPitchTick - emaCamVelPitch);

        touchDirty();

        for (int i = 0; i < patternSamples; i++) {
            float tt = (patternSamples == 1) ? 1.0f : (i + 1) / (float) (patternSamples + 1);

            float py = lastClampedYaw + dYawPath * tt;
            float pp = lastClampedPitch + dPitchPath * tt;

            if (burstAttack) {
                float os = (i == patternSamples - 1) ? 1.18f : 1.0f;
                py = lastClampedYaw + dYawPath * tt * os;
                pp = lastClampedPitch + dPitchPath * tt * os;
            }

            float ddy = clamp(wrapDeg(py - baseYaw), -maxErr, maxErr);
            float ddp = clamp(pp - basePitch, -maxErr, maxErr);

            float fy = baseYaw + ddy;
            float fp = basePitch + ddp;

            Angle playerAngle = new Angle(fy, fp);

            float lr = lrBase * lrMul * (1.0f / Math.max(1, patternSamples));

            for (int p = 0; p < pointSamples; p++) {
                Vec3d pt = pickLearnPoint(hb, aimedPoint, computed, t, seedBase, i, p, pointSamples, burstAttack, camVel);
                if (pt == null) continue;

                float lrPt = lr;
                if (!burstAttack && p > 0) lrPt *= 0.62f;
                if (burstAttack && p > 1) lrPt *= 0.75f;

                try {
                    model.learn(t, baseAngle, playerAngle, hb, pt, lrPt, burstAttack, camVel, stepYawAbs, stepPitchAbs, jitterYaw, jitterPitch);
                } catch (Exception ignored) {
                }
            }
        }

        lastClampedYaw = clampedYaw;
        lastClampedPitch = clampedPitch;

        if (attackBurstTicks > 0) attackBurstTicks--;

        maybeSave(false);
    }

    public void flushSave(Aura a) {
        if (!dirty) return;
        maybeSave(true);
    }

    private void touchDirty() {
        dirty = true;
    }

    private void maybeSave(boolean force) {
        if (!dirty && !force) return;
        try {
            model.save();
            dirty = false;
        } catch (Exception ignored) {
        }
    }

    private static boolean auraIsNeuroLearn(Aura a) {
        if (a == null) return false;
        return a.neuroEnabled() && a.neuroModeIsLearning();
    }

    private Vec3d pickLearnPoint(Box hb, Vec3d aimed, Vec3d computed, LivingEntity t, int seedBase, int patIdx, int pIdx, int pCount, boolean onAttack, float camVel) {
        if (pIdx == 0) return aimed != null ? aimed : computed;
        if (pIdx == 1 && computed != null) return computed;

        if (hb == null) return aimed != null ? aimed : computed;

        double cx = (hb.minX + hb.maxX) * 0.5;
        double cy = (hb.minY + hb.maxY) * 0.5;
        double cz = (hb.minZ + hb.maxZ) * 0.5;

        double sx = (hb.maxX - hb.minX) * 0.5;
        double sy = (hb.maxY - hb.minY) * 0.5;
        double sz = (hb.maxZ - hb.minZ) * 0.5;

        int s = seedBase ^ (patIdx * 0x9E3779B9) ^ (pIdx * 0x85EBCA6B);
        float r1 = rand01(s);
        float r2 = rand01(s ^ 0x68BC21EB);
        float r3 = rand01(s ^ 0x02E5BE93);

        float rel = relativeHeightBlocks(t);

        float biasUp = onAttack ? 0.10f : 0.0f;
        float biasMid = (camVel > 2.0f) ? 0.06f : 0.0f;

        float surfaceBias = 0.0f;
        if (rel <= -0.55f) {
            float k = clamp((-rel - 0.55f) / 2.25f, 0.0f, 1.0f);
            surfaceBias = 0.18f + 0.30f * k;
        } else if (rel >= 0.55f) {
            float k = clamp((rel - 0.55f) / 2.25f, 0.0f, 1.0f);
            surfaceBias = -(0.18f + 0.30f * k);
        }

        float oy = (r2 - 0.5f) * 0.90f + biasUp + biasMid + surfaceBias;
        float ox = (r1 - 0.5f) * 0.95f;
        float oz = (r3 - 0.5f) * 0.95f;

        if (!onAttack && pIdx == pCount - 1) {
            if (rel >= 0.55f) {
                oy = -0.34f;
            } else if (rel <= -0.55f) {
                oy = 0.52f;
            } else {
                oy = 0.18f;
            }
            ox *= 0.65f;
            oz *= 0.65f;
        }

        double px = cx + ox * sx;
        double py = cy + oy * sy;
        double pz = cz + oz * sz;

        px = clampD(px, hb.minX + 1.0E-4, hb.maxX - 1.0E-4);
        py = clampD(py, hb.minY + 1.0E-4, hb.maxY - 1.0E-4);
        pz = clampD(pz, hb.minZ + 1.0E-4, hb.maxZ - 1.0E-4);

        return new Vec3d(px, py, pz);
    }

    private float relativeHeightBlocks(LivingEntity t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || t == null) return 0.0f;
        try {
            double dy = t.getEyeY() - mc.player.getEyeY();
            return (float) dy;
        } catch (Exception ignored) {
        }
        return 0.0f;
    }

    private Vec3d neuroAimPointFromView(Box hb, Vec3d fallback, LivingEntity t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return fallback;
        if (hb == null) return fallback;

        Vec3d eye = mc.player.getEyePos();

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        Vec3d dir;
        try {
            dir = model.vecFromYawPitch(yaw, pitch);
        } catch (Exception e) {
            dir = null;
        }
        if (dir == null) return fallback;

        double dist = 7.0;
        if (t != null) {
            try {
                dist = Math.max(3.0, mc.player.distanceTo(t) + 3.5);
            } catch (Exception ignored) {
            }
        }

        Vec3d end = eye.add(dir.multiply(dist));
        Vec3d hit = boxRaycast(hb, eye, end);
        if (hit != null) return hit;

        if (fallback != null) return fallback;

        return new Vec3d((hb.minX + hb.maxX) * 0.5, (hb.minY + hb.maxY) * 0.6, (hb.minZ + hb.maxZ) * 0.5);
    }

    private static Vec3d boxRaycast(Box box, Vec3d start, Vec3d end) {
        if (box == null || start == null || end == null) return null;
        try {
            Method m = Box.class.getMethod("raycast", Vec3d.class, Vec3d.class);
            Object r = m.invoke(box, start, end);
            if (r instanceof java.util.Optional<?> opt) {
                Object v = opt.orElse(null);
                if (v instanceof Vec3d p) return p;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static int neuroSwingTicks(Object player) {
        if (player == null) return 0;

        try {
            Field f = swingTicksFieldA;
            if (f == null) {
                try {
                    f = player.getClass().getDeclaredField("handSwingTicks");
                    f.setAccessible(true);
                } catch (Exception ignored) {
                }
                swingTicksFieldA = f;
            }
            if (f != null && f.getType() == int.class) return f.getInt(player);
            if (f != null) {
                Object v = f.get(player);
                if (v instanceof Integer i) return i;
            }
        } catch (Exception ignored) {
        }

        try {
            Field f = swingTicksFieldB;
            if (f == null) {
                try {
                    f = player.getClass().getDeclaredField("handSwingingTicks");
                    f.setAccessible(true);
                } catch (Exception ignored) {
                }
                swingTicksFieldB = f;
            }
            if (f != null && f.getType() == int.class) return f.getInt(player);
            if (f != null) {
                Object v = f.get(player);
                if (v instanceof Integer i) return i;
            }
        } catch (Exception ignored) {
        }

        try {
            Method m = swingTicksMethod;
            if (m == null) {
                try {
                    m = player.getClass().getMethod("getHandSwingTicks");
                } catch (Exception ignored) {
                }
                if (m != null) m.setAccessible(true);
                swingTicksMethod = m;
            }
            if (m != null) {
                Object r = m.invoke(player);
                if (r instanceof Integer i) return i;
            }
        } catch (Exception ignored) {
        }

        try {
            Field f = swingTicksFieldC;
            if (f == null) {
                try {
                    f = player.getClass().getDeclaredField("ticksSinceLastSwing");
                    f.setAccessible(true);
                } catch (Exception ignored) {
                }
                swingTicksFieldC = f;
            }
            if (f != null && f.getType() == int.class) return f.getInt(player);
            if (f != null) {
                Object v = f.get(player);
                if (v instanceof Integer i) return i;
            }
        } catch (Exception ignored) {
        }

        return 0;
    }

    static boolean neuroSwingStarted(int prev, int cur) {
        if (cur < prev) return true;
        if (prev == 0 && cur > 0) return true;
        if (prev > 0 && cur == 0) return true;
        return false;
    }

    private static float wrapDeg(float v) {
        v %= 360.0f;
        if (v >= 180.0f) v -= 360.0f;
        if (v < -180.0f) v += 360.0f;
        return v;
    }

    private static float clamp(float v, float mn, float mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }

    private static double clampD(double v, double mn, double mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int mixSeed(int a, int b) {
        int x = a * 0x9E3779B9;
        x ^= b * 0x85EBCA6B;
        x ^= (x >>> 16);
        x *= 0xC2B2AE35;
        x ^= (x >>> 13);
        x *= 0x27D4EB2F;
        x ^= (x >>> 16);
        return x;
    }

    private static float rand01(int x) {
        x ^= (x >>> 16);
        x *= 0x7FEB352D;
        x ^= (x >>> 15);
        x *= 0x846CA68B;
        x ^= (x >>> 16);
        int m = x & 0x00FFFFFF;
        return m / 16777215.0f;
    }

    private static int corrIndex(SelectSetting s) {
        if (s == null) return 0;
        if (s.isSelected("Focused")) return 1;
        if (s.isSelected("Focus V2")) return 2;
        if (s.isSelected("Not visible")) return 3;
        if (s.isSelected("Neuro")) return 0;
        return 0;
    }

    public static final class NeuroRotationModel {
        private static final int DIST_BINS = 14;
        private static final int SPEED_BINS = 10;
        private static final int AIR_BINS = 2;
        private static final int MODE_BINS = 2;
        private static final int SIDE_BINS = 3;

        private static final int DY_BINS = 7;

        private static final int ANCHORS = 7;

        private static final int SIZE_NO_DY = DIST_BINS * SPEED_BINS * AIR_BINS * MODE_BINS * SIDE_BINS;
        private static final int SIZE = SIZE_NO_DY * DY_BINS;

        private static final float DIST_MAX = 6.0f;
        private static final float SPEED_MAX = 0.75f;

        private static final float[] AX = new float[]{0.50f, 0.50f, 0.50f, 0.32f, 0.68f, 0.40f, 0.60f};
        private static final float[] AY = new float[]{0.90f, 0.72f, 0.56f, 0.74f, 0.74f, 0.26f, 0.26f};
        private static final float[] AZ = new float[]{0.50f, 0.50f, 0.50f, 0.50f, 0.50f, 0.50f, 0.50f};

        private static final int CORR_TYPES = 4;

        private final float[] yawDeltaAvg = new float[SIZE];
        private final float[] pitchDeltaAvg = new float[SIZE];
        private final float[] errEma = new float[SIZE];
        private final int[] count = new int[SIZE];

        private final float[] pXAvg = new float[SIZE];
        private final float[] pYAvg = new float[SIZE];
        private final float[] pZAvg = new float[SIZE];
        private final float[] pErrEma = new float[SIZE];

        private final float[] yawSpeedAvg = new float[SIZE];
        private final float[] pitchSpeedAvg = new float[SIZE];
        private final float[] yawJitterAvg = new float[SIZE];
        private final float[] pitchJitterAvg = new float[SIZE];
        private final float[] gcdYawAvg = new float[SIZE];
        private final float[] gcdPitchAvg = new float[SIZE];
        private final float[] ampAvg = new float[SIZE];

        private final float[] anchorW = new float[SIZE * ANCHORS];
        private final float[] corrW = new float[SIZE * CORR_TYPES];

        private final float[] fbYawAvg = new float[SIZE];
        private final float[] fbPitchAvg = new float[SIZE];
        private final float[] fbErrEma = new float[SIZE];
        private final int[] fbCount = new int[SIZE];

        private boolean dirty = false;
        private boolean loadedLegacy = false;

        int totalSamples() {
            int total = 0;
            for (int c : count) total += c;
            return total;
        }

        static final class HumanizerProfile {
            final float noiseYaw;
            final float noisePitch;
            final float freqYaw;
            final float freqPitch;
            final float gate;
            final float gcdHintYaw;
            final float gcdHintPitch;
            final float confidence;

            HumanizerProfile(float noiseYaw, float noisePitch, float freqYaw, float freqPitch, float gate, float gcdHintYaw, float gcdHintPitch, float confidence) {
                this.noiseYaw = noiseYaw;
                this.noisePitch = noisePitch;
                this.freqYaw = freqYaw;
                this.freqPitch = freqPitch;
                this.gate = gate;
                this.gcdHintYaw = gcdHintYaw;
                this.gcdHintPitch = gcdHintPitch;
                this.confidence = confidence;
            }
        }

        HumanizerProfile suggestHumanizer(LivingEntity t, boolean attackSample, float camVel) {
            int i = idxFor(t, attackSample);
            if (i < 0) return new HumanizerProfile(0.25f, 0.18f, 0.75f, 0.68f, 0.55f, 0.0f, 0.0f, 0.0f);

            int c = count[i];
            float e = errEma[i];
            float pe = pErrEma[i];

            float confC = MathHelper.clamp((float) Math.sqrt(MathHelper.clamp(c / 220.0f, 0.0f, 1.0f)), 0.0f, 1.0f);
            float confE = (float) Math.exp(-MathHelper.clamp(e / 22.0f, 0.0f, 3.0f));
            float confP = (float) Math.exp(-MathHelper.clamp(pe / 0.26f, 0.0f, 3.0f));
            float conf = MathHelper.clamp(confC * confE * (0.60f + 0.40f * confP), 0.0f, 1.0f);

            float sYaw = yawSpeedAvg[i];
            float sPitch = pitchSpeedAvg[i];
            float jYaw = yawJitterAvg[i];
            float jPitch = pitchJitterAvg[i];
            float amp = ampAvg[i];

            float speedN = MathHelper.clamp(((sYaw + sPitch) * 0.5f) / 18.0f, 0.0f, 1.0f);
            float jitterN = MathHelper.clamp(((jYaw + jPitch) * 0.5f) / 10.0f, 0.0f, 1.0f);
            float ampN = MathHelper.clamp(amp / 35.0f, 0.0f, 1.0f);

            float camN = MathHelper.clamp(camVel / 6.0f, 0.0f, 1.0f);

            float stable = conf;
            float low = 1.0f - stable;

            float noiseYaw = 0.12f + stable * 0.28f + jitterN * 0.18f + ampN * 0.10f;
            float noisePitch = 0.09f + stable * 0.22f + jitterN * 0.14f + ampN * 0.08f;

            float speedMul = 1.0f - speedN * 0.35f;
            float camMul = 1.0f - camN * 0.60f;

            noiseYaw *= speedMul * camMul;
            noisePitch *= speedMul * camMul;

            if (attackSample) {
                noiseYaw *= 0.82f;
                noisePitch *= 0.78f;
            } else {
                noiseYaw *= (0.92f + low * 0.06f);
                noisePitch *= (0.92f + low * 0.06f);
            }

            noiseYaw = MathHelper.clamp(noiseYaw, 0.04f, 1.25f);
            noisePitch = MathHelper.clamp(noisePitch, 0.03f, 1.05f);

            float freqBase = 0.55f + speedN * 0.75f + jitterN * 0.45f + stable * 0.20f;
            if (attackSample) freqBase *= 1.05f;

            float freqYaw = MathHelper.clamp(freqBase, 0.45f, 2.25f);
            float freqPitch = MathHelper.clamp(freqBase * 0.92f, 0.40f, 2.05f);

            float gate = (0.30f + 0.70f * stable) * (1.0f - camN * 0.55f);
            if (attackSample) gate *= 0.86f;
            gate = MathHelper.clamp(gate, 0.10f, 1.0f);

            float gcdY = gcdYawAvg[i];
            float gcdP = gcdPitchAvg[i];
            float gcdHintYaw = gcdY > 0.0005f ? MathHelper.clamp(gcdY, 0.0005f, 3.50f) : 0.0f;
            float gcdHintPitch = gcdP > 0.0005f ? MathHelper.clamp(gcdP, 0.0005f, 3.50f) : 0.0f;

            return new HumanizerProfile(noiseYaw, noisePitch, freqYaw, freqPitch, gate, gcdHintYaw, gcdHintPitch, conf);
        }

        public int predictCorrectionType(LivingEntity t, boolean attackSample) {
            int i = idxFor(t, attackSample);
            if (i < 0) return 0;

            int off = i * CORR_TYPES;
            float best = 0.0f;
            int bestIdx = 0;
            for (int k = 0; k < CORR_TYPES; k++) {
                float w = corrW[off + k];
                if (w > best) {
                    best = w;
                    bestIdx = k;
                }
            }
            return bestIdx;
        }

        private java.nio.file.Path filePath(MinecraftClient mc) {
            java.io.File dir = new java.io.File(mc.runDirectory, "sadrik");
            if (!dir.exists()) dir.mkdirs();
            return new java.io.File(dir, "neuro_aura_learn.json").toPath();
        }

        private int distBin(float dist) {
            float distN = MathHelper.clamp(dist / DIST_MAX, 0.0f, 1.0f);
            return MathHelper.clamp((int) (distN * (DIST_BINS - 1)), 0, DIST_BINS - 1);
        }

        private int speedBin(float targetSpeed) {
            float spN = MathHelper.clamp(targetSpeed / SPEED_MAX, 0.0f, 1.0f);
            return MathHelper.clamp((int) (spN * (SPEED_BINS - 1)), 0, SPEED_BINS - 1);
        }

        private int dyBin(float dyBlocks) {
            if (dyBlocks <= -2.5f) return 0;
            if (dyBlocks <= -1.5f) return 1;
            if (dyBlocks <= -0.5f) return 2;
            if (dyBlocks < 0.5f) return 3;
            if (dyBlocks < 1.5f) return 4;
            if (dyBlocks < 2.5f) return 5;
            return 6;
        }

        private int sideBin(Vec3d tv, float playerYawDeg) {
            if (tv == null) return 1;

            double vx = tv.x;
            double vz = tv.z;
            double sp = Math.sqrt(vx * vx + vz * vz);
            if (sp < 0.02) return 1;

            double yaw = Math.toRadians(playerYawDeg);
            double rx = Math.cos(yaw);
            double rz = Math.sin(yaw);

            double s = vx * rx + vz * rz;

            if (s > 0.03) return 2;
            if (s < -0.03) return 0;
            return 1;
        }

        private int idxBins(int db, int sb, int ab, int mb, int xb, int yb) {
            int i = MathHelper.clamp(db, 0, DIST_BINS - 1);
            i = i * SPEED_BINS + MathHelper.clamp(sb, 0, SPEED_BINS - 1);
            i = i * AIR_BINS + MathHelper.clamp(ab, 0, AIR_BINS - 1);
            i = i * MODE_BINS + MathHelper.clamp(mb, 0, MODE_BINS - 1);
            i = i * SIDE_BINS + MathHelper.clamp(xb, 0, SIDE_BINS - 1);
            i = i * DY_BINS + MathHelper.clamp(yb, 0, DY_BINS - 1);
            return i;
        }

        private int idxFor(LivingEntity t, boolean attackSample) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || t == null) return -1;

            Vec3d tv = t.getVelocity();
            float sp = (float) tv.horizontalLength();
            float dist = mc.player.distanceTo(t);

            boolean airborne = mc.player.isGliding() || t.isGliding() || !mc.player.isOnGround();

            float dyBlocks;
            try {
                dyBlocks = (float) (t.getEyeY() - mc.player.getEyeY());
            } catch (Exception e) {
                dyBlocks = 0.0f;
            }

            int db = distBin(dist);
            int sb = speedBin(sp);
            int ab = airborne ? 1 : 0;
            int mb = attackSample ? 1 : 0;
            int xb = sideBin(tv, mc.player.getYaw());
            int yb = dyBin(dyBlocks);

            return idxBins(db, sb, ab, mb, xb, yb);
        }

        private float adaptiveLr(float baseLr, int c, float err) {
            float k = 1.0f / (float) Math.sqrt(1.0 + (c / 55.0));
            float e = MathHelper.clamp(err / 18.0f, 0.35f, 1.15f);
            float lr = baseLr * k * e;
            return MathHelper.clamp(lr, 0.012f, 0.22f);
        }

        private void ensureAnchorInitIfEmpty(int i, float px, float py, float pz) {
            int off = i * ANCHORS;
            float s = 0.0f;
            for (int k = 0; k < ANCHORS; k++) s += anchorW[off + k];
            if (s > 0.0001f) return;

            float[] w = softAnchorWeights(px, py, pz);
            for (int k = 0; k < ANCHORS; k++) anchorW[off + k] = w[k];
        }

        private float[] softAnchorWeights(float px, float py, float pz) {
            float[] w = new float[ANCHORS];
            float sum = 0.0f;
            float sigma = 0.055f;
            float inv2 = 1.0f / (2.0f * sigma * sigma);

            for (int k = 0; k < ANCHORS; k++) {
                float dx = px - AX[k];
                float dy = py - AY[k];
                float dz = pz - AZ[k];
                float d2 = dx * dx + dy * dy + dz * dz;
                float ww = (float) Math.exp(-d2 * inv2);
                w[k] = ww;
                sum += ww;
            }

            if (sum < 1.0E-8f) {
                float u = 1.0f / (float) ANCHORS;
                for (int k = 0; k < ANCHORS; k++) w[k] = u;
                return w;
            }

            float inv = 1.0f / sum;
            for (int k = 0; k < ANCHORS; k++) w[k] *= inv;
            return w;
        }

        void learnCorrection(LivingEntity t, boolean attackSample, float baseLr, int corrType) {
            if (corrType < 0 || corrType >= CORR_TYPES) return;

            int i = idxFor(t, attackSample);
            if (i < 0) return;

            int off = i * CORR_TYPES;

            float s0 = 0.0f;
            for (int k = 0; k < CORR_TYPES; k++) s0 += corrW[off + k];
            if (s0 < 1.0E-6f) {
                float u = 1.0f / (float) CORR_TYPES;
                for (int k = 0; k < CORR_TYPES; k++) corrW[off + k] = u;
            }

            float lr = MathHelper.clamp(baseLr * (attackSample ? 0.42f : 0.32f), 0.010f, 0.18f);

            for (int k = 0; k < CORR_TYPES; k++) {
                float target = (k == corrType) ? 1.0f : 0.0f;
                float v = corrW[off + k];
                v = v + (target - v) * lr;
                corrW[off + k] = MathHelper.clamp(v, 0.0f, 1.0f);
            }

            float s = 0.0f;
            for (int k = 0; k < CORR_TYPES; k++) s += corrW[off + k];
            if (s < 1.0E-6f) {
                float u = 1.0f / (float) CORR_TYPES;
                for (int k = 0; k < CORR_TYPES; k++) corrW[off + k] = u;
            } else {
                float inv = 1.0f / s;
                for (int k = 0; k < CORR_TYPES; k++) corrW[off + k] *= inv;
            }

            dirty = true;
        }

        private void fallbackLearn(int i, float dy, float dp, float err, boolean attackSample, float baseLrAdj) {
            float lr = MathHelper.clamp(baseLrAdj * (attackSample ? 0.75f : 0.55f), 0.028f, 0.26f);
            int c = fbCount[i];

            if (c == 0) {
                fbYawAvg[i] = dy;
                fbPitchAvg[i] = dp;
                fbErrEma[i] = err;
                fbCount[i] = 1;
                dirty = true;
                return;
            }

            float ey = fbYawAvg[i];
            float ep = fbPitchAvg[i];

            float ry = MathHelper.clamp(dy - ey, -22.0f, 22.0f);
            float rp = MathHelper.clamp(dp - ep, -14.0f, 14.0f);

            fbYawAvg[i] = ey + ry * lr;
            fbPitchAvg[i] = ep + rp * lr;

            float e = fbErrEma[i];
            fbErrEma[i] = e + (err - e) * MathHelper.clamp(lr * 0.85f, 0.06f, 0.22f);

            fbCount[i] = Math.min(c + 1, 2_000_000);
            dirty = true;
        }

        void learn(LivingEntity t, Angle baseAngle, Angle playerAngle, Box hb, Vec3d aimedPoint, float baseLr, boolean attackSample, float camVel, float stepYaw, float stepPitch, float jitterYaw, float jitterPitch) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || t == null) return;

            Vec3d tv = t.getVelocity();
            float sp = (float) tv.horizontalLength();
            float dist = mc.player.distanceTo(t);

            boolean airborne = mc.player.isGliding() || t.isGliding() || !mc.player.isOnGround();

            float dyBlocks;
            try {
                dyBlocks = (float) (t.getEyeY() - mc.player.getEyeY());
            } catch (Exception e) {
                dyBlocks = 0.0f;
            }

            float baseYaw = baseAngle.getYaw();
            float basePitch = baseAngle.getPitch();

            float plyYaw = playerAngle.getYaw();
            float plyPitch = playerAngle.getPitch();

            float dyRaw = MathHelper.wrapDegrees(plyYaw - baseYaw);
            float dpRaw = MathHelper.clamp(plyPitch - basePitch, -90.0f, 90.0f);

            float dy = MathHelper.clamp(dyRaw, -65.0f, 65.0f);
            float dp = MathHelper.clamp(dpRaw, -45.0f, 45.0f);

            float outMul = 1.0f;
            float ody = Math.abs(dyRaw);
            float odp = Math.abs(dpRaw);
            if (ody > 65.0f || odp > 45.0f) {
                float m1 = 65.0f / Math.max(ody, 0.001f);
                float m2 = 45.0f / Math.max(odp, 0.001f);
                outMul = MathHelper.clamp(Math.min(m1, m2), 0.02f, 1.0f);
            }

            float baseLrAdj = MathHelper.clamp(baseLr * outMul, 0.0015f, 1.0f);

            int db = distBin(dist);
            int sb = speedBin(sp);
            int ab = airborne ? 1 : 0;
            int mb = attackSample ? 1 : 0;
            int xb = sideBin(tv, mc.player.getYaw());
            int yb = dyBin(dyBlocks);

            int i = idxBins(db, sb, ab, mb, xb, yb);

            float px = 0.5f;
            float py = 0.5f;
            float pz = 0.5f;

            if (hb != null && aimedPoint != null) {
                double lx = hb.maxX - hb.minX;
                double ly = hb.maxY - hb.minY;
                double lz = hb.maxZ - hb.minZ;

                if (lx > 1.0E-6 && ly > 1.0E-6 && lz > 1.0E-6) {
                    px = (float) ((aimedPoint.x - hb.minX) / lx);
                    py = (float) ((aimedPoint.y - hb.minY) / ly);
                    pz = (float) ((aimedPoint.z - hb.minZ) / lz);

                    px = MathHelper.clamp(px, 0.0f, 1.0f);
                    py = MathHelper.clamp(py, 0.0f, 1.0f);
                    pz = MathHelper.clamp(pz, 0.0f, 1.0f);
                }
            }

            ensureAnchorInitIfEmpty(i, px, py, pz);

            float curY = yawDeltaAvg[i];
            float curP = pitchDeltaAvg[i];

            float err = (float) Math.sqrt((dy - curY) * (dy - curY) + (dp - curP) * (dp - curP));
            float amp = (float) Math.sqrt(dy * dy + dp * dp);

            fallbackLearn(i, dy, dp, err, attackSample, baseLrAdj);

            if (count[i] == 0) {
                yawDeltaAvg[i] = dy;
                pitchDeltaAvg[i] = dp;
                errEma[i] = err;
                count[i] = 1;

                pXAvg[i] = px;
                pYAvg[i] = py;
                pZAvg[i] = pz;
                pErrEma[i] = 0.45f;

                yawSpeedAvg[i] = Math.abs(stepYaw);
                pitchSpeedAvg[i] = Math.abs(stepPitch);
                yawJitterAvg[i] = Math.abs(jitterYaw);
                pitchJitterAvg[i] = Math.abs(jitterPitch);
                gcdYawAvg[i] = Math.abs(stepYaw);
                gcdPitchAvg[i] = Math.abs(stepPitch);
                ampAvg[i] = amp;

                float[] aw = softAnchorWeights(px, py, pz);
                int off = i * ANCHORS;
                for (int k = 0; k < ANCHORS; k++) anchorW[off + k] = aw[k];

                dirty = true;
                return;
            }

            errEma[i] = errEma[i] + (err - errEma[i]) * 0.10f;

            float lr = adaptiveLr(baseLrAdj, count[i], errEma[i]);
            if (attackSample) lr = MathHelper.clamp(lr * 1.08f, 0.012f, 0.26f);

            float ry = MathHelper.clamp(dy - curY, -18.0f, 18.0f);
            float rp = MathHelper.clamp(dp - curP, -12.0f, 12.0f);

            yawDeltaAvg[i] = curY + ry * lr;
            pitchDeltaAvg[i] = curP + rp * lr;

            float cx = pXAvg[i];
            float cy = pYAvg[i];
            float cz = pZAvg[i];

            float pErr = (float) Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy) + (pz - cz) * (pz - cz));
            pErrEma[i] = pErrEma[i] + (pErr - pErrEma[i]) * 0.12f;

            float pr = MathHelper.clamp(lr * 0.92f, 0.010f, 0.20f);

            pXAvg[i] = cx + MathHelper.clamp(px - cx, -0.25f, 0.25f) * pr;
            pYAvg[i] = cy + MathHelper.clamp(py - cy, -0.25f, 0.25f) * pr;
            pZAvg[i] = cz + MathHelper.clamp(pz - cz, -0.25f, 0.25f) * pr;

            float[] aw = softAnchorWeights(px, py, pz);
            int off = i * ANCHORS;

            float ar = MathHelper.clamp(lr * (attackSample ? 0.95f : 0.72f), 0.010f, 0.22f);
            for (int k = 0; k < ANCHORS; k++) {
                float old = anchorW[off + k];
                anchorW[off + k] = old + (aw[k] - old) * ar;
            }
            normalizeAnchorsInPlace(off);

            float speedLr = MathHelper.clamp(baseLrAdj * 0.75f, 0.010f, 0.20f);
            float jitterLr = MathHelper.clamp(baseLrAdj * 0.60f, 0.008f, 0.18f);
            float gcdLr = MathHelper.clamp(baseLrAdj * 0.40f, 0.004f, 0.12f);
            float ampLr = MathHelper.clamp(baseLrAdj * 0.85f, 0.012f, 0.24f);

            float sYaw = Math.abs(stepYaw);
            float sPitch = Math.abs(stepPitch);
            if (sYaw > 0.0005f) {
                float v = MathHelper.clamp(sYaw, 0.0005f, 45.0f);
                float cur = yawSpeedAvg[i];
                if (cur == 0.0f) yawSpeedAvg[i] = v;
                else yawSpeedAvg[i] = cur + (v - cur) * speedLr;
            }
            if (sPitch > 0.0005f) {
                float v = MathHelper.clamp(sPitch, 0.0005f, 45.0f);
                float cur = pitchSpeedAvg[i];
                if (cur == 0.0f) pitchSpeedAvg[i] = v;
                else pitchSpeedAvg[i] = cur + (v - cur) * speedLr;
            }

            float jYawAbs = Math.abs(jitterYaw);
            float jPitchAbs = Math.abs(jitterPitch);
            if (jYawAbs > 0.0005f) {
                float v = MathHelper.clamp(jYawAbs, 0.0005f, 35.0f);
                float cur = yawJitterAvg[i];
                if (cur == 0.0f) yawJitterAvg[i] = v;
                else yawJitterAvg[i] = cur + (v - cur) * jitterLr;
            }
            if (jPitchAbs > 0.0005f) {
                float v = MathHelper.clamp(jPitchAbs, 0.0005f, 35.0f);
                float cur = pitchJitterAvg[i];
                if (cur == 0.0f) pitchJitterAvg[i] = v;
                else pitchJitterAvg[i] = cur + (v - cur) * jitterLr;
            }

            if (sYaw > 0.0005f) {
                float gy = gcdYawAvg[i];
                float v = MathHelper.clamp(sYaw, 0.0005f, 45.0f);
                if (gy == 0.0f) {
                    gy = v;
                } else {
                    float ratio = v / Math.max(gy, 1.0E-4f);
                    float nearest = Math.max(1.0f, Math.round(ratio));
                    float cand = v / nearest;
                    gy = gy + (cand - gy) * gcdLr;
                }
                gcdYawAvg[i] = gy;
            }
            if (sPitch > 0.0005f) {
                float gp = gcdPitchAvg[i];
                float v = MathHelper.clamp(sPitch, 0.0005f, 45.0f);
                if (gp == 0.0f) {
                    gp = v;
                } else {
                    float ratio = v / Math.max(gp, 1.0E-4f);
                    float nearest = Math.max(1.0f, Math.round(ratio));
                    float cand = v / nearest;
                    gp = gp + (cand - gp) * gcdLr;
                }
                gcdPitchAvg[i] = gp;
            }

            if (amp > 0.0005f) {
                float v = MathHelper.clamp(amp, 0.0005f, 90.0f);
                float cur = ampAvg[i];
                if (cur == 0.0f) ampAvg[i] = v;
                else ampAvg[i] = cur + (v - cur) * ampLr;
            }

            count[i] = Math.min(count[i] + 1, 2_000_000);
            dirty = true;
        }

        private void normalizeAnchorsInPlace(int off) {
            float s = 0.0f;
            for (int k = 0; k < ANCHORS; k++) s += anchorW[off + k];
            if (s < 1.0E-8f) {
                float u = 1.0f / (float) ANCHORS;
                for (int k = 0; k < ANCHORS; k++) anchorW[off + k] = u;
                return;
            }
            float inv = 1.0f / s;
            for (int k = 0; k < ANCHORS; k++) anchorW[off + k] *= inv;
        }

        void save() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!dirty && !loadedLegacy) return;
            try {
                int floatsCount = (yawDeltaAvg.length + pitchDeltaAvg.length + errEma.length
                        + pXAvg.length + pYAvg.length + pZAvg.length + pErrEma.length
                        + yawSpeedAvg.length + pitchSpeedAvg.length + yawJitterAvg.length + pitchJitterAvg.length
                        + gcdYawAvg.length + gcdPitchAvg.length + ampAvg.length
                        + anchorW.length + corrW.length
                        + fbYawAvg.length + fbPitchAvg.length + fbErrEma.length);

                int bytes = floatsCount * 4 + (count.length + fbCount.length) * 4;

                ByteBuffer bb = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);

                for (float v : yawDeltaAvg) bb.putFloat(v);
                for (float v : pitchDeltaAvg) bb.putFloat(v);
                for (float v : errEma) bb.putFloat(v);
                for (int v : count) bb.putInt(v);

                for (float v : pXAvg) bb.putFloat(v);
                for (float v : pYAvg) bb.putFloat(v);
                for (float v : pZAvg) bb.putFloat(v);
                for (float v : pErrEma) bb.putFloat(v);

                for (float v : yawSpeedAvg) bb.putFloat(v);
                for (float v : pitchSpeedAvg) bb.putFloat(v);
                for (float v : yawJitterAvg) bb.putFloat(v);
                for (float v : pitchJitterAvg) bb.putFloat(v);
                for (float v : gcdYawAvg) bb.putFloat(v);
                for (float v : gcdPitchAvg) bb.putFloat(v);
                for (float v : ampAvg) bb.putFloat(v);

                for (float v : anchorW) bb.putFloat(v);
                for (float v : corrW) bb.putFloat(v);

                for (float v : fbYawAvg) bb.putFloat(v);
                for (float v : fbPitchAvg) bb.putFloat(v);
                for (float v : fbErrEma) bb.putFloat(v);
                for (int v : fbCount) bb.putInt(v);

                String b64 = Base64.getEncoder().encodeToString(bb.array());
                String json = "{\"v\":10,\"b64\":\"" + b64 + "\"}";

                Files.writeString(
                        filePath(mc),
                        json,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );

                dirty = false;
                loadedLegacy = false;
            } catch (Exception ignored) {
            }
        }

        private int parseVersion(String json) {
            if (json == null) return -1;
            int p = json.indexOf("\"v\":");
            if (p < 0) return -1;
            int s = p + 4;
            int e = s;
            while (e < json.length()) {
                char c = json.charAt(e);
                if (c < '0' || c > '9') break;
                e++;
            }
            if (e <= s) return -1;
            try {
                return Integer.parseInt(json.substring(s, e));
            } catch (Exception ignored) {
            }
            return -1;
        }

        void load() {
            MinecraftClient mc = MinecraftClient.getInstance();
            dirty = false;
            loadedLegacy = false;

            try {
                var p = filePath(mc);
                if (!Files.exists(p)) return;

                String json = Files.readString(p, StandardCharsets.UTF_8);

                int b0 = json.indexOf("\"b64\":\"");
                if (b0 < 0) return;

                int s = b0 + 7;
                int e = json.indexOf('"', s);
                if (e <= s) return;

                int ver = parseVersion(json);

                String b64 = json.substring(s, e);
                byte[] data = Base64.getDecoder().decode(b64);

                int floatsCountNew = (yawDeltaAvg.length + pitchDeltaAvg.length + errEma.length
                        + pXAvg.length + pYAvg.length + pZAvg.length + pErrEma.length
                        + yawSpeedAvg.length + pitchSpeedAvg.length + yawJitterAvg.length + pitchJitterAvg.length
                        + gcdYawAvg.length + gcdPitchAvg.length + ampAvg.length
                        + anchorW.length + corrW.length
                        + fbYawAvg.length + fbPitchAvg.length + fbErrEma.length);

                int bytesNew = floatsCountNew * 4 + (count.length + fbCount.length) * 4;

                int sizeOld = SIZE_NO_DY;

                int oldYawPitchErrFloats = sizeOld + sizeOld + sizeOld;
                int oldPxyzFloats = sizeOld + sizeOld + sizeOld + sizeOld;
                int oldAnchorFloats = sizeOld * ANCHORS;

                int oldV7ExtraFloats = sizeOld * 7;
                int oldV8ExtraFloats = oldV7ExtraFloats + (sizeOld * CORR_TYPES);
                int oldV9ExtraFloats = oldV8ExtraFloats + (sizeOld * 3);

                int bytesOld = (oldYawPitchErrFloats + oldPxyzFloats + oldAnchorFloats) * 4 + sizeOld * 4;
                int bytesV7 = (oldYawPitchErrFloats + oldPxyzFloats + oldV7ExtraFloats + oldAnchorFloats) * 4 + sizeOld * 4;
                int bytesV8 = (oldYawPitchErrFloats + oldPxyzFloats + oldV8ExtraFloats + oldAnchorFloats) * 4 + sizeOld * 4;
                int bytesV9 = (oldYawPitchErrFloats + oldPxyzFloats + oldV9ExtraFloats + oldAnchorFloats) * 4 + (sizeOld + sizeOld) * 4;

                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

                if (data.length == bytesNew) {
                    for (int i = 0; i < yawDeltaAvg.length; i++) yawDeltaAvg[i] = bb.getFloat();
                    for (int i = 0; i < pitchDeltaAvg.length; i++) pitchDeltaAvg[i] = bb.getFloat();
                    for (int i = 0; i < errEma.length; i++) errEma[i] = bb.getFloat();
                    for (int i = 0; i < count.length; i++) count[i] = bb.getInt();

                    for (int i = 0; i < pXAvg.length; i++) pXAvg[i] = bb.getFloat();
                    for (int i = 0; i < pYAvg.length; i++) pYAvg[i] = bb.getFloat();
                    for (int i = 0; i < pZAvg.length; i++) pZAvg[i] = bb.getFloat();
                    for (int i = 0; i < pErrEma.length; i++) pErrEma[i] = bb.getFloat();

                    for (int i = 0; i < yawSpeedAvg.length; i++) yawSpeedAvg[i] = bb.getFloat();
                    for (int i = 0; i < pitchSpeedAvg.length; i++) pitchSpeedAvg[i] = bb.getFloat();
                    for (int i = 0; i < yawJitterAvg.length; i++) yawJitterAvg[i] = bb.getFloat();
                    for (int i = 0; i < pitchJitterAvg.length; i++) pitchJitterAvg[i] = bb.getFloat();
                    for (int i = 0; i < gcdYawAvg.length; i++) gcdYawAvg[i] = bb.getFloat();
                    for (int i = 0; i < gcdPitchAvg.length; i++) gcdPitchAvg[i] = bb.getFloat();
                    for (int i = 0; i < ampAvg.length; i++) ampAvg[i] = bb.getFloat();

                    for (int i = 0; i < anchorW.length; i++) anchorW[i] = bb.getFloat();
                    for (int i = 0; i < corrW.length; i++) corrW[i] = bb.getFloat();

                    for (int i = 0; i < fbYawAvg.length; i++) fbYawAvg[i] = bb.getFloat();
                    for (int i = 0; i < fbPitchAvg.length; i++) fbPitchAvg[i] = bb.getFloat();
                    for (int i = 0; i < fbErrEma.length; i++) fbErrEma[i] = bb.getFloat();
                    for (int i = 0; i < fbCount.length; i++) fbCount[i] = bb.getInt();

                    for (int i = 0; i < SIZE; i++) {
                        float px = pXAvg[i] == 0.0f ? 0.5f : pXAvg[i];
                        float py = pYAvg[i] == 0.0f ? 0.5f : pYAvg[i];
                        float pz = pZAvg[i] == 0.0f ? 0.5f : pZAvg[i];
                        ensureAnchorInitIfEmpty(i, px, py, pz);
                        normalizeAnchorsInPlace(i * ANCHORS);
                    }
                    return;
                }

                if (data.length == bytesV9 || data.length == bytesV8 || data.length == bytesV7 || data.length == bytesOld || ver == 9 || ver == 8 || ver == 7) {
                    float[] yawOld = new float[sizeOld];
                    float[] pitOld = new float[sizeOld];
                    float[] errOld = new float[sizeOld];
                    int[] cntOld = new int[sizeOld];

                    float[] pxOld = new float[sizeOld];
                    float[] pyOld = new float[sizeOld];
                    float[] pzOld = new float[sizeOld];
                    float[] peOld = new float[sizeOld];

                    float[] ySpOld = new float[sizeOld];
                    float[] pSpOld = new float[sizeOld];
                    float[] yJOld = new float[sizeOld];
                    float[] pJOld = new float[sizeOld];
                    float[] gyOld = new float[sizeOld];
                    float[] gpOld = new float[sizeOld];
                    float[] ampOld = new float[sizeOld];

                    float[] ancOld = new float[sizeOld * ANCHORS];
                    float[] corOld = new float[sizeOld * CORR_TYPES];

                    float[] fbyOld = new float[sizeOld];
                    float[] fbpOld = new float[sizeOld];
                    float[] fbeOld = new float[sizeOld];
                    int[] fbcOld = new int[sizeOld];

                    for (int i = 0; i < sizeOld; i++) yawOld[i] = bb.getFloat();
                    for (int i = 0; i < sizeOld; i++) pitOld[i] = bb.getFloat();
                    for (int i = 0; i < sizeOld; i++) errOld[i] = bb.getFloat();
                    for (int i = 0; i < sizeOld; i++) cntOld[i] = bb.getInt();

                    for (int i = 0; i < sizeOld; i++) pxOld[i] = bb.getFloat();
                    for (int i = 0; i < sizeOld; i++) pyOld[i] = bb.getFloat();
                    for (int i = 0; i < sizeOld; i++) pzOld[i] = bb.getFloat();
                    for (int i = 0; i < sizeOld; i++) peOld[i] = bb.getFloat();

                    boolean hasV7 = data.length == bytesV7 || data.length == bytesV8 || data.length == bytesV9 || ver >= 7;
                    boolean hasV8 = data.length == bytesV8 || data.length == bytesV9 || ver >= 8;
                    boolean hasV9 = data.length == bytesV9 || ver >= 9;

                    if (hasV7) {
                        for (int i = 0; i < sizeOld; i++) ySpOld[i] = bb.getFloat();
                        for (int i = 0; i < sizeOld; i++) pSpOld[i] = bb.getFloat();
                        for (int i = 0; i < sizeOld; i++) yJOld[i] = bb.getFloat();
                        for (int i = 0; i < sizeOld; i++) pJOld[i] = bb.getFloat();
                        for (int i = 0; i < sizeOld; i++) gyOld[i] = bb.getFloat();
                        for (int i = 0; i < sizeOld; i++) gpOld[i] = bb.getFloat();
                        for (int i = 0; i < sizeOld; i++) ampOld[i] = bb.getFloat();
                    }

                    for (int i = 0; i < sizeOld * ANCHORS; i++) ancOld[i] = bb.getFloat();

                    if (hasV8) {
                        for (int i = 0; i < sizeOld * CORR_TYPES; i++) corOld[i] = bb.getFloat();
                    }

                    if (hasV9) {
                        for (int i = 0; i < sizeOld; i++) fbyOld[i] = bb.getFloat();
                        for (int i = 0; i < sizeOld; i++) fbpOld[i] = bb.getFloat();
                        for (int i = 0; i < sizeOld; i++) fbeOld[i] = bb.getFloat();
                        for (int i = 0; i < sizeOld; i++) fbcOld[i] = bb.getInt();
                    } else {
                        for (int i = 0; i < sizeOld; i++) {
                            fbyOld[i] = yawOld[i];
                            fbpOld[i] = pitOld[i];
                            fbeOld[i] = errOld[i];
                            fbcOld[i] = Math.min(cntOld[i], 160);
                        }
                    }

                    for (int base = 0; base < sizeOld; base++) {
                        int baseAncOff = base * ANCHORS;
                        int baseCorOff = base * CORR_TYPES;

                        for (int yb = 0; yb < DY_BINS; yb++) {
                            int ni = base * DY_BINS + yb;

                            yawDeltaAvg[ni] = yawOld[base];
                            pitchDeltaAvg[ni] = pitOld[base];
                            errEma[ni] = errOld[base];
                            count[ni] = cntOld[base];

                            pXAvg[ni] = pxOld[base];
                            pYAvg[ni] = pyOld[base];
                            pZAvg[ni] = pzOld[base];
                            pErrEma[ni] = peOld[base];

                            yawSpeedAvg[ni] = ySpOld[base];
                            pitchSpeedAvg[ni] = pSpOld[base];
                            yawJitterAvg[ni] = yJOld[base];
                            pitchJitterAvg[ni] = pJOld[base];
                            gcdYawAvg[ni] = gyOld[base];
                            gcdPitchAvg[ni] = gpOld[base];
                            ampAvg[ni] = ampOld[base];

                            fbYawAvg[ni] = fbyOld[base];
                            fbPitchAvg[ni] = fbpOld[base];
                            fbErrEma[ni] = fbeOld[base];
                            fbCount[ni] = fbcOld[base];

                            int newAncOff = ni * ANCHORS;
                            for (int k = 0; k < ANCHORS; k++) anchorW[newAncOff + k] = ancOld[baseAncOff + k];

                            int newCorOff = ni * CORR_TYPES;
                            for (int k = 0; k < CORR_TYPES; k++) corrW[newCorOff + k] = corOld[baseCorOff + k];
                        }
                    }

                    for (int i = 0; i < SIZE; i++) {
                        float px = pXAvg[i] == 0.0f ? 0.5f : pXAvg[i];
                        float py = pYAvg[i] == 0.0f ? 0.5f : pYAvg[i];
                        float pz = pZAvg[i] == 0.0f ? 0.5f : pZAvg[i];
                        ensureAnchorInitIfEmpty(i, px, py, pz);
                        normalizeAnchorsInPlace(i * ANCHORS);
                    }

                    loadedLegacy = true;
                }
            } catch (Exception ignored) {
            }
        }

        Vec3d vecFromYawPitch(float yawDeg, float pitchDeg) {
            double yaw = Math.toRadians(yawDeg);
            double pitch = Math.toRadians(pitchDeg);
            double cp = Math.cos(pitch);
            return new Vec3d(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
        }
    }
}
