package sadrik.modules.impl.combat.aura.neuro;

import sadrik.modules.impl.combat.aura.attack.StrikeManager;
import sadrik.modules.impl.combat.aura.attack.StrikerConstructor;
import sadrik.modules.impl.combat.aura.MathAngle;
import sadrik.modules.impl.combat.aura.Angle;
import sadrik.modules.impl.combat.aura.AngleConfig;
import sadrik.modules.impl.combat.aura.AngleConnection;
import sadrik.modules.impl.combat.Aura;
import sadrik.util.math.TaskPriority;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class NeuroAuraExec {

    static final float NEURO_ASSIST_WHEN_MANUAL = 0.35f;

    static final float NEURO_BASE_STRENGTH = 0.88f;
    static final float NEURO_RAMP_MS = 360f;

    static final float NEURO_OVERSHOOT_PROB = 0.21f;
    static final float NEURO_OVERSHOOT_MIN = 0.94f;
    static final float NEURO_OVERSHOOT_MAX = 1.15f;

    static final float NEURO_NOISE_YAW_MIN = 0.06f;
    static final float NEURO_NOISE_YAW_MAX = 0.38f;

    static final float NEURO_NOISE_PITCH_MIN = 0.04f;
    static final float NEURO_NOISE_PITCH_MAX = 0.22f;

    static final float NEURO_NOISE_FREQ_MIN = 0.55f;
    static final float NEURO_NOISE_FREQ_MAX = 1.65f;

    static final float NEURO_MANUAL_CATCH_MIN = 0.005f;
    static final float NEURO_MANUAL_CATCH_MAX = 0.014f;

    static final long NEURO_TOGGLE_BLEND_MS = 480L;

    static final long NEURO_LOST_SHAKE_MS = 480L;
    static final float NEURO_LOST_SHAKE_YAW_MIN = 4.0f;
    static final float NEURO_LOST_SHAKE_YAW_MAX = 11.0f;

    static final float AIM_MAX_YAW_DELTA = 38.0f;
    static final float AIM_MAX_PITCH_DELTA = 28.0f;

    static final float FALLBACK_GATE_CONF = 0.28f;
    static final float FALLBACK_POINT_GATE = 0.32f;
    static final float FALLBACK_MIN_CONFIDENCE = 0.22f;

    static final float HUMAN_JITTER_SCALE = 7.5f;
    static final float HUMAN_SPEED_SCALE = 18.0f;
    static final float HUMAN_AMP_SCALE = 26.0f;

    static final float GCD_MIN = 0.006f;
    static final float GCD_MAX = 0.45f;

    static final float NEURO_LEARN_RATE_TRACK = 0.10f;
    static final float NEURO_LEARN_RATE_ATTACK = 0.16f;

    static final long NEURO_SAVE_EVERY_MS = 4500L;

    NeuroRotationModel model = new NeuroRotationModel();
    final PerlinNoise perlin = new PerlinNoise(1337);

    boolean loaded;

    float manualFactor;
    float noiseYaw;
    float noisePitch;
    float noiseFreqYaw;
    float noiseFreqPitch;

    float noisePhaseYaw;
    float noisePhasePitch;

    long toggleBlendStart;
    long toggleBlendUntil;
    boolean toggleBlend;

    boolean savedViewValid;
    float savedYaw;
    float savedPitch;

    boolean returnActive;
    long returnStartTime;
    long returnEndTime;
    long returnLastTime;
    float returnYaw;
    float returnPitch;
    float returnVelYaw;
    float returnVelPitch;

    boolean postReturnRelease;

    boolean lostShakeActive;
    long lostShakeStartTime;
    long lostShakeEndTime;
    long lostShakeLastTime;
    float lostShakeYaw;
    float lostShakePitch;
    int lostShakePhase;
    boolean lostShakeArmed;

    private int lostShakePrevSwingTicks;
    private int execPrevSwingTicks;

    private int hitPendingTargetId = -1;
    private int hitPendingPrevHurt = 0;
    private long hitPendingUntil = 0L;
    private long lastHitConfirmMs = 0L;

    int aimSmoothTargetId = -1;
    long aimSmoothLastTime;
    float aimSmoothedDy;
    float aimSmoothedDp;

    long aimEngageStart;
    long aimEngageUntil;
    int aimEngageTargetId = -1;
    boolean aimEngage;

    NeuroRotationModel.Prediction predCache;
    long predCacheTime;
    int predTargetId;
    boolean predCacheAttack;

    private Vec3d mpRvCur = Vec3d.ZERO;
    private Vec3d mpRvFrom = Vec3d.ZERO;
    private Vec3d mpRvTo = Vec3d.ZERO;
    private long mpRvStartMs = 0L;
    private long mpRvEndMs = 0L;
    private int mpRvLastTid = Integer.MIN_VALUE;
    private float mpRvOrbit = 0.0f;

    private int mpPointTid = Integer.MIN_VALUE;
    private Vec3d mpPointCurRv = Vec3d.ZERO;
    private Vec3d mpPointFromRv = Vec3d.ZERO;
    private Vec3d mpPointToRv = Vec3d.ZERO;
    private long mpPointStartMs = 0L;
    private long mpPointEndMs = 0L;
    private float mpPointOrbit = 0.0f;
    private Vec3d mpPointCur = null;
    private long mpPointLastMs = 0L;

    private long lastModelSaveMs = 0L;
    private boolean modelDirty = false;

    private long learnLastMs = 0L;
    private float learnPrevDy = 0.0f;
    private float learnPrevDp = 0.0f;

    public void onActivate(Aura a) {
        manualFactor = 0.0f;
        noiseYaw = 0.0f;
        noisePitch = 0.0f;
        noiseFreqYaw = 1.0f;
        noiseFreqPitch = 1.0f;

        noisePhaseYaw = rnd(0.0f, 999.0f);
        noisePhasePitch = rnd(0.0f, 999.0f);

        toggleBlendStart = 0L;
        toggleBlendUntil = 0L;
        toggleBlend = false;

        savedViewValid = false;
        savedYaw = 0.0f;
        savedPitch = 0.0f;

        returnActive = false;
        returnStartTime = 0L;
        returnEndTime = 0L;
        returnLastTime = 0L;
        returnYaw = 0.0f;
        returnPitch = 0.0f;
        returnVelYaw = 0.0f;
        returnVelPitch = 0.0f;

        postReturnRelease = false;

        lostShakeActive = false;
        lostShakeStartTime = 0L;
        lostShakeEndTime = 0L;
        lostShakeLastTime = 0L;
        lostShakeYaw = 0.0f;
        lostShakePitch = 0.0f;
        lostShakePhase = 0;
        lostShakeArmed = false;
        lostShakePrevSwingTicks = 0;

        execPrevSwingTicks = 0;

        hitPendingTargetId = -1;
        hitPendingPrevHurt = 0;
        hitPendingUntil = 0L;
        lastHitConfirmMs = 0L;

        aimSmoothTargetId = -1;
        aimSmoothLastTime = 0L;
        aimSmoothedDy = 0.0f;
        aimSmoothedDp = 0.0f;

        aimEngageStart = 0L;
        aimEngageUntil = 0L;
        aimEngageTargetId = -1;
        aimEngage = false;

        predCache = null;
        predCacheTime = 0L;
        predTargetId = -1;
        predCacheAttack = false;

        mpRvCur = Vec3d.ZERO;
        mpRvFrom = Vec3d.ZERO;
        mpRvTo = Vec3d.ZERO;
        mpRvStartMs = 0L;
        mpRvEndMs = 0L;
        mpRvLastTid = Integer.MIN_VALUE;
        mpRvOrbit = 0.0f;

        mpPointTid = Integer.MIN_VALUE;
        mpPointCurRv = Vec3d.ZERO;
        mpPointFromRv = Vec3d.ZERO;
        mpPointToRv = Vec3d.ZERO;
        mpPointStartMs = 0L;
        mpPointEndMs = 0L;
        mpPointOrbit = 0.0f;
        mpPointCur = null;
        mpPointLastMs = 0L;

        lastModelSaveMs = 0L;
        modelDirty = false;

        learnLastMs = 0L;
        learnPrevDy = 0.0f;
        learnPrevDp = 0.0f;

        loaded = false;
        try {
            model.load();
            loaded = true;
        } catch (Exception ignored) {
        }
    }

    public void onDeactivate(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        trySaveModel(true);

        try {
            if (a != null && a.neuroShakeEnabled() && lostShakeArmed && !lostShakeActive && !returnActive) {
                lostShakeArmed = false;
                startLostShake(a, true);
                lostShakeStep(a);
            } else {
                if (savedViewValid) startReturnToSaved(a, savedYaw, savedPitch);
                else scheduleReturnToSaved(a);
                postReturnRelease = true;
            }
        } catch (Exception ignored) {
        }
    }

    public void onTick(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (!loaded) {
            loaded = true;
            try {
                model.load();
            } catch (Exception ignored) {
            }

            predCache = null;
            predCacheTime = 0L;
            predTargetId = -1;
            predCacheAttack = false;

            execPrevSwingTicks = neuroSwingTicks(mc.player);
            lostShakePrevSwingTicks = neuroSwingTicks(mc.player);
            lostShakeArmed = false;

            hitPendingTargetId = -1;
            hitPendingPrevHurt = 0;
            hitPendingUntil = 0L;
            lastHitConfirmMs = 0L;

            mpRvCur = Vec3d.ZERO;
            mpRvFrom = Vec3d.ZERO;
            mpRvTo = Vec3d.ZERO;
            mpRvStartMs = 0L;
            mpRvEndMs = 0L;
            mpRvLastTid = Integer.MIN_VALUE;
            mpRvOrbit = 0.0f;

            mpPointTid = Integer.MIN_VALUE;
            mpPointCurRv = Vec3d.ZERO;
            mpPointFromRv = Vec3d.ZERO;
            mpPointToRv = Vec3d.ZERO;
            mpPointStartMs = 0L;
            mpPointEndMs = 0L;
            mpPointOrbit = 0.0f;
            mpPointCur = null;
            mpPointLastMs = 0L;

            return;
        }

        long now = System.currentTimeMillis();

        updateSavedViewAuto(mc, a);

        LivingEntity tt = a != null ? a.getTarget() : null;
        if (tt != null) {
            int tid = tt.getId();
            float k = 0.035f;
            noisePhaseYaw += k * (0.65f + 0.35f * ((tid * 1103515245) & 255) / 255.0f);
            noisePhasePitch += k * (0.65f + 0.35f * ((tid * 1664525) & 255) / 255.0f);
        } else {
            noisePhaseYaw += 0.020f;
            noisePhasePitch += 0.020f;
        }

        boolean exec = a != null && a.neuroExec();
        boolean neuroOn = a != null && a.neuroEnabled();

        if (exec) {
            updateManualFactor(mc);
        } else {
            manualFactor = 0.0f;
            aimEngage = false;
            aimEngageStart = 0L;
            aimEngageUntil = 0L;
            aimEngageTargetId = -1;
        }

        int cur = neuroSwingTicks(mc.player);

        boolean justAttacked = false;
        if (execPrevSwingTicks == 0) {
            execPrevSwingTicks = cur;
        } else {
            justAttacked = neuroSwingStarted(execPrevSwingTicks, cur);
            execPrevSwingTicks = cur;

            if (justAttacked) {
                LivingEntity t = exec ? a.getTarget() : null;
                if (t != null) {
                    hitPendingTargetId = t.getId();
                    hitPendingPrevHurt = getHurtTime(t);
                    hitPendingUntil = now + 240L;
                }
            }
        }

        if (lostShakePrevSwingTicks == 0) {
            lostShakePrevSwingTicks = cur;
        } else {
            lostShakePrevSwingTicks = cur;
        }

        if (hitPendingUntil != 0L) {
            if (now <= hitPendingUntil) {
                LivingEntity t = exec ? a.getTarget() : null;
                if (t != null && t.getId() == hitPendingTargetId) {
                    int ht = getHurtTime(t);
                    if (ht > hitPendingPrevHurt && ht > 0) {
                        lostShakeArmed = true;
                        lastHitConfirmMs = now;
                        hitPendingUntil = 0L;
                        hitPendingTargetId = -1;
                    }
                } else {
                    hitPendingUntil = 0L;
                    hitPendingTargetId = -1;
                }
            } else {
                hitPendingUntil = 0L;
                hitPendingTargetId = -1;
            }
        }

        if (neuroOn && mc.player != null) {
            if (!toggleBlend && exec) {
                toggleBlend = true;
                toggleBlendStart = now;
                toggleBlendUntil = now + toggleMsFromSmooth(a);
            } else if (toggleBlend && !exec) {
                toggleBlend = false;
                toggleBlendStart = 0L;
                toggleBlendUntil = 0L;
            }
        }

        if (neuroOn && !exec && tt != null) {
            boolean attackSample = neuroSwingStartedSafe(cur);
            if (learnStep(a, tt, attackSample)) {
                trySaveModel(false);
            }
        } else {
            trySaveModel(false);
        }
    }

    public boolean handlePreRotation(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        if (lostShakeActive && (a == null || !a.neuroExec())) {
            stopLostShake();
        }
        if (returnActive && a != null && a.neuroExec() && a.getTarget() != null) {
            stopReturnToSaved();
        }

        if (lostShakeActive) {
            lostShakeStep(a);
            return true;
        }
        if (returnActive) {
            returnStep(a);
            return true;
        }

        return false;
    }

    public boolean isBusy(Aura a) {
        return lostShakeActive || returnActive;
    }

    public boolean handleNoTarget(Aura a, LivingEntity lastTarget) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (a != null && a.neuroShakeEnabled() && lastTarget != null && !lostShakeActive && !returnActive && lostShakeArmed) {
            lostShakeArmed = false;
            startLostShake(a, false);
            lostShakeStep(a);
            return true;
        }

        if (!returnActive && savedViewValid && mc.player != null) {
            startReturnToSaved(a, savedYaw, savedPitch);
            return true;
        } else if (!returnActive) {
            releaseCameraLockNow();
        }

        return false;
    }

    public void onTargetSelected(Aura a, LivingEntity t) {
        if (t != null) {
            long now = System.currentTimeMillis();
            startAimEngage(t.getId(), now, a);
            predCache = null;
            predCacheTime = 0L;
            predTargetId = -1;
            predCacheAttack = false;

            noisePhaseYaw = rnd(0.0f, 999.0f);
            noisePhasePitch = rnd(0.0f, 999.0f);
        }
    }

    public boolean tryApplyExecRotation(Aura a,
                                 StrikerConstructor.AttackPerpetratorConfigurable config,
                                 StrikeManager attackHandler,
                                 AngleConnection controller,
                                 AngleConfig rotationConfig) {
        if (a == null || !a.neuroExec()) return false;
        if (a.getTarget() == null) return false;
        if (controller == null || rotationConfig == null) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        LivingEntity target = a.getTarget();
        int tid = target.getId();

        long now = System.currentTimeMillis();

        boolean plannedAttack = attackHandler != null && attackHandler.canAttack(config, 5);

        NeuroRotationModel.Prediction pr;
        if (predCache != null && predTargetId == tid && predCacheAttack == plannedAttack && (now - predCacheTime) <= 200L) {
            pr = predCache;
        } else {
            pr = model.predict(target, plannedAttack);
        }

        NeuroRotationModel.FallbackPrediction fb = model.fallbackPredict(target, plannedAttack);

        Angle baseAngle = config != null ? config.getAngle() : null;

        Box box = target.getBoundingBox();

        Vec3d predPoint = null;
        if (pr != null) {
            try {
                predPoint = model.pointFromPrediction(target, pr);
            } catch (Exception ignored) {
            }
        }

        Vec3d rv = null;
        try {
            rv = getMpRv(a);
        } catch (Exception ignored) {
        }

        Vec3d mpPoint = null;
        if (rv != null) {
            double fx = 0.5 + rv.x * 0.5;
            double fy = rv.y;
            double fz = 0.5 + rv.z * 0.5;

            fx = clampD(fx, 0.0, 1.0);
            fy = clampD(fy, 0.0, 1.0);
            fz = clampD(fz, 0.0, 1.0);

            if (fy < 0.36) fy = 0.36;
            if (fy > 0.82) fy = 0.82;

            double x = box.minX + (box.maxX - box.minX) * fx;
            double y = box.minY + (box.maxY - box.minY) * fy;
            double z = box.minZ + (box.maxZ - box.minZ) * fz;

            mpPoint = new Vec3d(
                    clampD(x, box.minX + 1.0E-4, box.maxX - 1.0E-4),
                    clampD(y, box.minY + 1.0E-4, box.maxY - 1.0E-4),
                    clampD(z, box.minZ + 1.0E-4, box.maxZ - 1.0E-4)
            );
        }

        Vec3d point = null;

        if (mpPoint != null && predPoint != null && pr != null) {
            float pc = MathHelper.clamp(pr.pointConfidence / 100.0f, 0.0f, 1.0f);
            float c = MathHelper.clamp(pr.confidence, 0.0f, 1.0f);

            if (pc < 0.35f || c < 0.25f) {
                point = mpPoint;
            } else {
                float w = 0.55f + 0.35f * (1.0f - (pc * 0.65f + c * 0.35f));
                w = MathHelper.clamp(w, 0.45f, 0.90f);
                point = new Vec3d(
                        predPoint.x + (mpPoint.x - predPoint.x) * w,
                        predPoint.y + (mpPoint.y - predPoint.y) * w,
                        predPoint.z + (mpPoint.z - predPoint.z) * w
                );
            }
        } else if (mpPoint != null) {
            point = mpPoint;
        } else if (predPoint != null) {
            point = predPoint;
        }

        if (point != null) {
            Angle ang = angleFromPlayerView(point);
            if (ang != null) baseAngle = ang;
        }

        if (baseAngle == null) baseAngle = config != null ? config.getAngle() : new Angle(0.0f, 0.0f);

        NeuroRotationModel.AngleDeg base = model.angleDegFromAngle(baseAngle);

        float s = smooth(a);

        float manualScale = 1.0f - manualFactor * (1.0f - NEURO_ASSIST_WHEN_MANUAL);
        manualScale = MathHelper.clamp(manualScale, 0.15f, 1.0f);

        float conf = pr != null ? pr.confidence : 0.0f;
        float pc = pr != null ? MathHelper.clamp(pr.pointConfidence / 100.0f, 0.0f, 1.0f) : 0.0f;

        float fbConf = fb != null ? fb.confidence : 0.0f;

        boolean preferFallback = pr == null
                || conf < FALLBACK_GATE_CONF
                || pc < FALLBACK_POINT_GATE
                || (fbConf > conf + 0.10f && fbConf >= FALLBACK_MIN_CONFIDENCE);

        float dyNeed = pr != null ? MathHelper.clamp(pr.yawDeltaDeg, -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA) : 0.0f;
        float dpNeed = pr != null ? MathHelper.clamp(pr.pitchDeltaDeg, -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA) : 0.0f;

        if (fb != null) {
            float fdy = MathHelper.clamp(fb.yawDeltaDeg, -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA);
            float fdp = MathHelper.clamp(fb.pitchDeltaDeg, -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA);

            if (preferFallback) {
                dyNeed = fdy;
                dpNeed = fdp;
                conf = fbConf;
                pc = Math.max(pc, MathHelper.clamp(fbConf, 0.0f, 1.0f));
            } else {
                float w = MathHelper.clamp((FALLBACK_GATE_CONF - conf) / FALLBACK_GATE_CONF, 0.0f, 1.0f);
                w *= MathHelper.clamp(fbConf, 0.0f, 1.0f);
                w *= MathHelper.lerp(s, 1.0f, 0.82f);
                dyNeed = dyNeed + (fdy - dyNeed) * w;
                dpNeed = dpNeed + (fdp - dpNeed) * w;
            }
        }

        float confGate = MathHelper.clamp(0.25f + 0.75f * MathHelper.clamp(conf, 0.0f, 1.0f), 0.25f, 1.0f);

        float strength = NEURO_BASE_STRENGTH * manualScale * confGate;
        strength = MathHelper.clamp(strength, 0.05f, 1.0f);

        float ramp = 1.0f;
        if (toggleBlend && now <= toggleBlendUntil && toggleBlendUntil > toggleBlendStart) {
            float raw = (float) (now - toggleBlendStart) / (float) (toggleBlendUntil - toggleBlendStart);
            ramp = easeOutCubic(raw);
        }
        strength *= ramp;

        if (!aimEngage || aimEngageTargetId != tid) startAimEngage(tid, now, a);
        if (aimEngage && now <= aimEngageUntil && aimEngageUntil > aimEngageStart) {
            float raw = (float) (now - aimEngageStart) / (float) (aimEngageUntil - aimEngageStart);
            strength *= easeOutCubic(raw);
        }

        float overshoot = 1.0f;
        float osProb = NEURO_OVERSHOOT_PROB;
        if (plannedAttack) osProb *= 1.12f;
        osProb *= MathHelper.lerp(s, 1.0f, 0.92f);
        if (ThreadLocalRandom.current().nextFloat() < osProb) {
            overshoot = rnd(NEURO_OVERSHOOT_MIN, NEURO_OVERSHOOT_MAX);
        }

        if (aimSmoothTargetId != tid) {
            aimSmoothTargetId = tid;
            aimSmoothedDy = 0.0f;
            aimSmoothedDp = 0.0f;
            aimSmoothLastTime = 0L;
            startAimEngage(tid, now, a);
        }

        long dtMs = now - aimSmoothLastTime;
        if (aimSmoothLastTime == 0L) dtMs = 50L;
        if (dtMs < 1) dtMs = 1;
        if (dtMs > 120) dtMs = 120;
        aimSmoothLastTime = now;

        float yawPer50 = MathHelper.lerp(s, plannedAttack ? 16.5f : 18.0f, plannedAttack ? 4.0f : 4.2f);
        float pitchPer50 = MathHelper.lerp(s, plannedAttack ? 12.8f : 14.0f, plannedAttack ? 3.0f : 3.4f);

        float dyMax = yawPer50 * ((float) dtMs / 50.0f);
        float dpMax = pitchPer50 * ((float) dtMs / 50.0f);

        float dyErr = MathHelper.wrapDegrees(dyNeed - aimSmoothedDy);
        float dpErr = MathHelper.wrapDegrees(dpNeed - aimSmoothedDp);

        aimSmoothedDy += MathHelper.clamp(dyErr, -dyMax, dyMax);
        aimSmoothedDp += MathHelper.clamp(dpErr, -dpMax, dpMax);

        aimSmoothedDy = MathHelper.clamp(aimSmoothedDy, -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA);
        aimSmoothedDp = MathHelper.clamp(aimSmoothedDp, -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA);

        float nYaw = base.yaw + aimSmoothedDy * strength * overshoot;
        float nPitch = base.pitch + aimSmoothedDp * strength * overshoot;

        float human = 1.0f - MathHelper.clamp(conf, 0.0f, 1.0f);
        human = human * human;
        if (plannedAttack) human *= 0.55f;
        human *= MathHelper.lerp(s, 1.05f, 0.85f);

        float mf = MathHelper.clamp(manualFactor, 0.0f, 1.0f);
        human *= (1.0f - mf * 0.55f);

        float lj = 0.0f;
        float ls = 0.0f;
        float la = 0.0f;
        float gcdYaw = 0.0f;
        float gcdPitch = 0.0f;

        if (pr != null) {
            lj = MathHelper.clamp((Math.abs(pr.yawJitter) + Math.abs(pr.pitchJitter)) / HUMAN_JITTER_SCALE, 0.0f, 1.0f);
            ls = MathHelper.clamp((Math.abs(pr.yawSpeed) + Math.abs(pr.pitchSpeed)) / HUMAN_SPEED_SCALE, 0.0f, 1.0f);
            la = MathHelper.clamp(Math.abs(pr.amp) / HUMAN_AMP_SCALE, 0.0f, 1.0f);
            gcdYaw = pr.gcdYaw;
            gcdPitch = pr.gcdPitch;
        }

        float learnHumanBoost = 0.20f + 0.80f * (0.55f * lj + 0.30f * ls + 0.15f * la);
        learnHumanBoost = MathHelper.clamp(learnHumanBoost, 0.20f, 1.15f);

        float aYaw = MathHelper.lerp(human, NEURO_NOISE_YAW_MIN, NEURO_NOISE_YAW_MAX) * learnHumanBoost;
        float aPitch = MathHelper.lerp(human, NEURO_NOISE_PITCH_MIN, NEURO_NOISE_PITCH_MAX) * learnHumanBoost;

        float fYaw = MathHelper.lerp(human, NEURO_NOISE_FREQ_MIN, NEURO_NOISE_FREQ_MAX) * (0.85f + 0.55f * ls);
        float fPitch = MathHelper.lerp(human, NEURO_NOISE_FREQ_MIN, NEURO_NOISE_FREQ_MAX) * (0.85f + 0.55f * ls);

        float tt = (float) ((now % 100000L) / 1000.0);

        float ny = perlin.fbm((tt * fYaw + noisePhaseYaw), (float) (tid * 0.013 + 0.11), 4, 2.0f, 0.55f);
        float np = perlin.fbm((tt * fPitch + noisePhasePitch), (float) (tid * 0.017 + 37.7), 4, 2.0f, 0.55f);

        float sway = perlin.noise((tt * 0.22f + noisePhaseYaw * 0.07f), (float) (tid * 0.009 + 9.3));
        float spn = perlin.noise((tt * 0.19f + noisePhasePitch * 0.07f), (float) (tid * 0.011 + 3.7));

        float nyOut = (ny * 0.72f + sway * 0.28f) * aYaw;
        float npOut = (np * 0.72f + spn * 0.28f) * aPitch;

        float kStr = strength * (plannedAttack ? 0.62f : 0.72f);
        nyOut *= kStr;
        npOut *= kStr;

        nYaw += nyOut;
        nPitch += npOut;

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        float outYaw = nYaw;
        float outPitch = nPitch;

        float gYaw = MathHelper.clamp(gcdYaw == 0.0f ? 0.0f : Math.abs(gcdYaw), GCD_MIN, GCD_MAX);
        float gPitch = MathHelper.clamp(gcdPitch == 0.0f ? 0.0f : Math.abs(gcdPitch), GCD_MIN, GCD_MAX);

        if (gYaw > 0.0f) {
            float dy = MathHelper.wrapDegrees(outYaw - curYaw);
            float q = quantize(dy, gYaw);
            outYaw = curYaw + q;
        }
        if (gPitch > 0.0f) {
            float dp = MathHelper.wrapDegrees(outPitch - curPitch);
            float q = quantize(dp, gPitch);
            outPitch = curPitch + q;
        }

        outPitch = MathHelper.clamp(outPitch, -90.0f, 90.0f);

        Angle out = model.turnsFromAngleDeg(outYaw, outPitch);

        Angle.VecRotation rotation = new Angle.VecRotation(out, out.toVector());

        int speed = plannedAttack ? 40 : 1;
        if (plannedAttack) controller.clear();

        controller.rotateTo(rotation, target, speed, rotationConfig, TaskPriority.HIGH_IMPORTANCE_1, a);

        Aura.shouldRotate = true;
        Aura.fakeRotate = false;

        predCache = pr;
        predCacheTime = now;
        predTargetId = tid;
        predCacheAttack = plannedAttack;

        return true;
    }

    public Vec3d getMpRv(Aura a) {
        long now = System.currentTimeMillis();

        LivingEntity t = a != null ? a.getTarget() : null;
        if (t == null) {
            mpRvCur = Vec3d.ZERO;
            mpRvFrom = Vec3d.ZERO;
            mpRvTo = Vec3d.ZERO;
            mpRvStartMs = 0L;
            mpRvEndMs = 0L;
            mpRvLastTid = Integer.MIN_VALUE;
            mpRvOrbit = 0.0f;
            return Vec3d.ZERO;
        }

        int tid;
        try {
            tid = t.getId();
        } catch (Exception e) {
            tid = System.identityHashCode(t);
        }

        if (tid != mpRvLastTid) {
            mpRvLastTid = tid;
            mpRvCur = Vec3d.ZERO;
            mpRvFrom = Vec3d.ZERO;
            mpRvTo = Vec3d.ZERO;
            mpRvStartMs = now;
            mpRvEndMs = 0L;
            mpRvOrbit = rnd(0.0f, (float) (Math.PI * 2.0));
        }

        long dtMs = now - mpRvStartMs;
        if (dtMs < 1) dtMs = 1;
        if (dtMs > 60) dtMs = 60;
        mpRvStartMs = now;

        float s = smooth(a);

        float orbitSpeed = MathHelper.lerp(s, 0.46f, 0.28f);
        float dtScale = (float) dtMs / 50.0f;
        mpRvOrbit += orbitSpeed * dtScale + rnd(-0.018f, 0.018f) * dtScale;

        Vec3d desired = nextMpRvTarget(a);

        float tau = MathHelper.lerp(s, 140.0f, 85.0f);
        float alpha = (float) dtMs / tau;
        alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);
        alpha = alpha * alpha * (3.0f - 2.0f * alpha);

        if (mpRvCur == Vec3d.ZERO) {
            mpRvCur = desired;
            return mpRvCur;
        }

        mpRvCur = new Vec3d(
                lerpD(mpRvCur.x, desired.x, alpha),
                lerpD(mpRvCur.y, desired.y, alpha),
                lerpD(mpRvCur.z, desired.z, alpha)
        );

        return mpRvCur;
    }

    private Vec3d nextMpRvTarget(Aura a) {
        float s = smooth(a);

        float o = mpRvOrbit;

        float y = 0.56f
                + 0.10f * (float) Math.sin(o * 0.74f + 0.9f)
                + 0.06f * (float) Math.sin(o * 1.28f + 2.2f)
                - 0.05f * (float) (0.5 + 0.5 * Math.sin(o * 0.35f + 1.7f));

        float x = (0.22f * (float) Math.sin(o))
                + (0.06f * (float) Math.sin(o * 1.85f + 0.25f));

        float z = (0.08f * (float) Math.cos(o * 0.92f + 0.4f))
                + (0.03f * (float) Math.sin(o * 1.35f + 1.1f));

        float jitter = MathHelper.lerp(s, 0.030f, 0.014f);
        x += rnd(-jitter, jitter);
        z += rnd(-jitter, jitter);
        y += rnd(-jitter * 0.55f, jitter * 0.55f);

        float sideClamp = MathHelper.lerp(s, 0.50f, 0.42f);
        float fwdClamp = MathHelper.lerp(s, 0.40f, 0.34f);

        x = MathHelper.clamp(x, -sideClamp, sideClamp);
        z = MathHelper.clamp(z, -fwdClamp, fwdClamp);
        y = MathHelper.clamp(y, 0.36f, 0.82f);

        if (a == null || !a.neuroExec()) {
            x *= 0.75f;
            z *= 0.75f;
            y = MathHelper.clamp(y, 0.40f, 0.80f);
        }

        return new Vec3d(x, y, z);
    }

    public Vec3d adjustPointForExec(Aura a, LivingEntity target, Box hb, Vec3d rawPoint) {
        if (target == null) return rawPoint;
        if (rawPoint == null) return null;

        Box box = hb != null ? hb : target.getBoundingBox();

        boolean plannedAttack = false;

        NeuroRotationModel.Prediction pr;
        long now = System.currentTimeMillis();
        if (predCache != null && predTargetId == target.getId() && (now - predCacheTime) <= 220L) {
            pr = predCache;
        } else {
            pr = model.predict(target, plannedAttack);
        }

        Vec3d predPoint = null;
        if (pr != null) {
            try {
                predPoint = model.pointFromPrediction(target, pr);
            } catch (Exception ignored) {
            }
        }

        float s = smooth(a);

        double x = rawPoint.x;
        double y = rawPoint.y;
        double z = rawPoint.z;

        if (predPoint != null) {
            float conf = pr.confidence;
            float pc = MathHelper.clamp(pr.pointConfidence / 100.0f, 0.0f, 1.0f);
            float w = 0.20f + 0.80f * (conf * 0.65f + pc * 0.35f);
            w = MathHelper.clamp(w, 0.08f, 0.95f);

            w *= MathHelper.lerp(s, 1.0f, 0.85f);

            x = x + (predPoint.x - x) * w;
            y = y + (predPoint.y - y) * w;
            z = z + (predPoint.z - z) * w;
        }

        if (a != null) {
            Vec3d rv = getMpRv(a);
            if (rv != null && rv != Vec3d.ZERO) {
                double fx = 0.5 + rv.x * 0.5;
                double fy = rv.y;
                double fz = 0.5 + rv.z * 0.5;

                fx = clampD(fx, 0.0, 1.0);
                fy = clampD(fy, 0.0, 1.0);
                fz = clampD(fz, 0.0, 1.0);

                double mpX = box.minX + (box.maxX - box.minX) * fx;
                double mpY = box.minY + (box.maxY - box.minY) * fy;
                double mpZ = box.minZ + (box.maxZ - box.minZ) * fz;

                float wMp = MathHelper.lerp(s, 0.62f, 0.45f);
                if (!a.neuroExec()) wMp = MathHelper.lerp(s, 0.72f, 0.52f);

                x = x + (mpX - x) * wMp;
                y = y + (mpY - y) * wMp;
                z = z + (mpZ - z) * wMp;
            }
        }

        x = clampD(x, box.minX + 1.0E-4, box.maxX - 1.0E-4);
        y = clampD(y, box.minY + 1.0E-4, box.maxY - 1.0E-4);
        z = clampD(z, box.minZ + 1.0E-4, box.maxZ - 1.0E-4);

        return new Vec3d(x, y, z);
    }

    public void scheduleReturnToSaved(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        savedViewValid = true;
        startReturnToSaved(a, savedYaw, savedPitch);
    }

    public void startReturnToSaved(Aura a, float toYaw, float toPitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        returnActive = true;
        returnStartTime = System.currentTimeMillis();
        returnEndTime = returnStartTime + returnMsFromSmooth(a);
        returnLastTime = returnStartTime;

        returnYaw = mc.player.getYaw();
        returnPitch = mc.player.getPitch();

        returnVelYaw = 0.0f;
        returnVelPitch = 0.0f;

        postReturnRelease = false;

        Aura.shouldRotate = true;
        Aura.fakeRotate = false;
    }

    void stopReturnToSaved() {
        returnActive = false;
        returnStartTime = 0L;
        returnEndTime = 0L;
        returnLastTime = 0L;
        returnVelYaw = 0.0f;
        returnVelPitch = 0.0f;
    }

    public void returnStep(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            stopReturnToSaved();
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= returnEndTime || returnEndTime <= returnStartTime) {
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
            stopReturnToSaved();
            if (postReturnRelease) {
                postReturnRelease = false;
                releaseCameraLockNow();
            }
            return;
        }

        long dtMs = now - returnLastTime;
        if (dtMs < 1) dtMs = 1;
        if (dtMs > 120) dtMs = 120;
        returnLastTime = now;

        float dt = (float) dtMs / 1000.0f;

        float targetYaw = savedYaw;
        float targetPitch = savedPitch;

        float s = smooth(a);

        float omega = 14.0f - 4.0f * s;
        if (omega < 8.0f) omega = 8.0f;
        if (omega > 14.0f) omega = 14.0f;
        float zeta = 1.0f;

        float dy = MathHelper.wrapDegrees(targetYaw - returnYaw);
        float dp = MathHelper.wrapDegrees(targetPitch - returnPitch);

        float ay = omega * omega * dy - 2.0f * zeta * omega * returnVelYaw;
        float ap = omega * omega * dp - 2.0f * zeta * omega * returnVelPitch;

        returnVelYaw += ay * dt;
        returnVelPitch += ap * dt;

        float lim = 210.0f;
        returnVelYaw = MathHelper.clamp(returnVelYaw, -lim, lim);
        returnVelPitch = MathHelper.clamp(returnVelPitch, -lim, lim);

        returnYaw += returnVelYaw * dt;
        returnPitch += returnVelPitch * dt;

        mc.player.setYaw(returnYaw);
        mc.player.setPitch(MathHelper.clamp(returnPitch, -90.0f, 90.0f));
    }

    public void startLostShake(Aura a, boolean releaseAfter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        lostShakeActive = true;
        lostShakeStartTime = System.currentTimeMillis();
        lostShakeEndTime = lostShakeStartTime + NEURO_LOST_SHAKE_MS;
        lostShakeLastTime = lostShakeStartTime;

        lostShakeYaw = mc.player.getYaw();
        lostShakePitch = mc.player.getPitch();
        lostShakePhase = 0;

        postReturnRelease = releaseAfter;

        Aura.shouldRotate = true;
        Aura.fakeRotate = false;
    }

    void stopLostShake() {
        lostShakeActive = false;
        lostShakeStartTime = 0L;
        lostShakeEndTime = 0L;
        lostShakeLastTime = 0L;
        lostShakePhase = 0;
    }

    public void lostShakeStep(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            stopLostShake();
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= lostShakeEndTime || lostShakeEndTime <= lostShakeStartTime) {
            stopLostShake();
            if (savedViewValid) {
                startReturnToSaved(a, savedYaw, savedPitch);
            } else {
                scheduleReturnToSaved(a);
            }
            return;
        }

        long dtMs = now - lostShakeLastTime;
        if (dtMs < 1) dtMs = 1;
        if (dtMs > 120) dtMs = 120;
        lostShakeLastTime = now;

        float raw = (float) (now - lostShakeStartTime) / (float) (lostShakeEndTime - lostShakeStartTime);
        float k = 1.0f - raw;

        float amp = rnd(NEURO_LOST_SHAKE_YAW_MIN, NEURO_LOST_SHAKE_YAW_MAX) * k;
        float sign = (lostShakePhase++ % 2 == 0) ? 1.0f : -1.0f;

        float jitter = rnd(-0.9f, 0.9f) * 0.65f;
        float dy = (amp + jitter) * sign;

        float dp = rnd(-0.55f, 0.35f) * k;

        lostShakeYaw += dy;
        lostShakePitch += dp;

        mc.player.setYaw(lostShakeYaw);
        mc.player.setPitch(MathHelper.clamp(lostShakePitch, -90.0f, 90.0f));
    }

    private void updateManualFactor(MinecraftClient mc) {
        if (mc.player == null) return;

        float d = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - savedYaw));
        float p = Math.abs(MathHelper.wrapDegrees(mc.player.getPitch() - savedPitch));

        float e = (d + p) * 0.5f;

        float catchRate = rnd(NEURO_MANUAL_CATCH_MIN, NEURO_MANUAL_CATCH_MAX);
        float k = MathHelper.clamp(e * catchRate, 0.0f, 1.0f);

        manualFactor = manualFactor + (k - manualFactor) * 0.12f;
        manualFactor = MathHelper.clamp(manualFactor, 0.0f, 1.0f);
    }

    private void updateSavedViewAuto(MinecraftClient mc, Aura a) {
        if (mc.player == null) return;
        if (!savedViewValid) {
            savedViewValid = true;
            savedYaw = mc.player.getYaw();
            savedPitch = mc.player.getPitch();
            return;
        }
        float mf = MathHelper.clamp(manualFactor, 0.0f, 1.0f);
        LivingEntity t = a != null ? a.getTarget() : null;
        if (mf > 0.10f || t == null) {
            savedYaw = mc.player.getYaw();
            savedPitch = mc.player.getPitch();
        }
    }

    private void startAimEngage(int tid, long now, Aura a) {
        aimEngage = true;
        aimEngageTargetId = tid;
        aimEngageStart = now;
        aimEngageUntil = now + aimEngageMsFromSmooth(a);
    }

    private long aimEngageMsFromSmooth(Aura a) {
        float s = smooth(a);
        long v = (long) MathHelper.lerp(s, 520.0f, 240.0f);
        if (v < 180L) v = 180L;
        if (v > 650L) v = 650L;
        return v;
    }

    Angle angleFromPlayerView(Vec3d point) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || point == null) return new Angle(0.0f, 0.0f);
        Vec3d eye = mc.player.getCameraPosVec(1.0f);
        Vec3d dir = point.subtract(eye);
        return MathAngle.fromVec3d(dir);
    }

    private long toggleMsFromSmooth(Aura a) {
        float s = smooth(a);
        long v = (long) MathHelper.lerp(s, 560.0f, 420.0f);
        if (v < 320L) v = 320L;
        if (v > 720L) v = 720L;
        return v;
    }

    private long returnMsFromSmooth(Aura a) {
        float s = smooth(a);
        long v = (long) MathHelper.lerp(s, 540.0f, 420.0f);
        if (v < 360L) v = 360L;
        if (v > 680L) v = 680L;
        return v;
    }

    private static float easeOutCubic(float t) {
        float x = MathHelper.clamp(t, 0.0f, 1.0f);
        float u = 1.0f - x;
        return 1.0f - u * u * u;
    }

    private void releaseCameraLockNow() {
        try {
            AngleConnection.INSTANCE.clear();
        } catch (Exception ignored) {
        }
        Aura.fakeRotate = false;
        Aura.shouldRotate = false;
        returnActive = false;
        returnStartTime = 0L;
        returnEndTime = 0L;
        returnLastTime = 0L;
        lostShakeActive = false;
        lostShakeStartTime = 0L;
        lostShakeEndTime = 0L;
        lostShakeLastTime = 0L;
        aimEngage = false;
        aimEngageStart = 0L;
        aimEngageUntil = 0L;
        aimEngageTargetId = -1;
    }

    private float smooth(Aura a) {
        float v = 0.76f;
        if (a != null) {
            try {
                v = a.neuroSmoothValue();
            } catch (Exception ignored) {
            }
        }
        if (v < 0.20f) v = 0.20f;
        if (v > 0.95f) v = 0.95f;
        return v;
    }

    private boolean learnStep(Aura a, LivingEntity target, boolean attackSample) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) return false;

        long now = System.currentTimeMillis();

        Vec3d aimPoint = aimPointFromView(mc, target);
        if (aimPoint == null) aimPoint = target.getBoundingBox().getCenter();

        Angle baseAngle = angleFromPlayerView(aimPoint);
        NeuroRotationModel.AngleDeg base = model.angleDegFromAngle(baseAngle);

        float pyaw = mc.player.getYaw();
        float ppitch = mc.player.getPitch();

        float dy = MathHelper.wrapDegrees(pyaw - base.yaw);
        float dp = MathHelper.wrapDegrees(ppitch - base.pitch);

        dy = MathHelper.clamp(dy, -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA);
        dp = MathHelper.clamp(dp, -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA);

        Box hb = target.getBoundingBox();
        double lx = hb.maxX - hb.minX;
        double ly = hb.maxY - hb.minY;
        double lz = hb.maxZ - hb.minZ;

        double fx = lx <= 1.0E-9 ? 0.5 : (aimPoint.x - hb.minX) / lx;
        double fy = ly <= 1.0E-9 ? 0.5 : (aimPoint.y - hb.minY) / ly;
        double fz = lz <= 1.0E-9 ? 0.5 : (aimPoint.z - hb.minZ) / lz;

        float px = (float) MathHelper.clamp(fx, 0.0, 1.0);
        float py = (float) MathHelper.clamp(fy, 0.0, 1.0);
        float pz = (float) MathHelper.clamp(fz, 0.0, 1.0);

        long dtMs = now - learnLastMs;
        if (learnLastMs == 0L) dtMs = 50L;
        if (dtMs < 1) dtMs = 1;
        if (dtMs > 120) dtMs = 120;
        learnLastMs = now;

        float dt = (float) dtMs / 1000.0f;
        float yawSp = dt <= 0.0f ? 0.0f : Math.abs(MathHelper.wrapDegrees(dy - learnPrevDy)) / dt;
        float pitSp = dt <= 0.0f ? 0.0f : Math.abs(MathHelper.wrapDegrees(dp - learnPrevDp)) / dt;

        float yJ = MathHelper.wrapDegrees(dy - learnPrevDy);
        float pJ = MathHelper.wrapDegrees(dp - learnPrevDp);

        float amp = (float) Math.sqrt(dy * dy + dp * dp);

        learnPrevDy = dy;
        learnPrevDp = dp;

        float rate = attackSample ? NEURO_LEARN_RATE_ATTACK : NEURO_LEARN_RATE_TRACK;

        boolean ok = model.learn(target, attackSample, dy, dp, px, py, pz, yawSp, pitSp, yJ, pJ, 0.0f, 0.0f, amp, rate);
        if (ok) {
            modelDirty = true;
            return true;
        }
        return false;
    }

    private boolean neuroSwingStartedSafe(int curSwingTicks) {
        if (curSwingTicks <= 0) return false;
        return ThreadLocalRandom.current().nextFloat() < 0.18f;
    }

    private Vec3d aimPointFromView(MinecraftClient mc, LivingEntity target) {
        if (mc == null || mc.player == null || target == null) return null;
        Vec3d eye = mc.player.getCameraPosVec(1.0f);
        Vec3d look = mc.player.getRotationVec(1.0f);
        double reach = 8.0;
        Vec3d end = eye.add(look.x * reach, look.y * reach, look.z * reach);

        Box box = target.getBoundingBox();
        try {
            Optional<Vec3d> hit = box.raycast(eye, end);
            if (hit != null && hit.isPresent()) return hit.get();
        } catch (Exception ignored) {
        }

        Vec3d c = box.getCenter();
        Vec3d p = new Vec3d(
                clampD(c.x, box.minX + 1.0E-4, box.maxX - 1.0E-4),
                clampD(c.y, box.minY + 1.0E-4, box.maxY - 1.0E-4),
                clampD(c.z, box.minZ + 1.0E-4, box.maxZ - 1.0E-4)
        );
        return p;
    }

    private void trySaveModel(boolean force) {
        if (!modelDirty && !force) return;

        long now = System.currentTimeMillis();
        if (!force && lastModelSaveMs != 0L && (now - lastModelSaveMs) < NEURO_SAVE_EVERY_MS) return;

        lastModelSaveMs = now;
        try {
            model.save();
            modelDirty = false;
        } catch (Exception ignored) {
        }
    }

    public static int neuroSwingTicks(Object player) {
        Integer v = readIntField(player, "handSwingTicks");
        if (v != null) return v;
        v = readIntField(player, "handSwingProgressInt");
        if (v != null) return v;
        v = readIntField(player, "handSwingingTicks");
        if (v != null) return v;
        v = readIntField(player, "ticksSinceLastSwing");
        if (v != null) return v;
        return 0;
    }

    public static boolean neuroSwingStarted(int prev, int cur) {
        if (cur < prev) return true;
        if (prev == 0 && cur > 0) return true;
        if (prev > 0 && cur == 0) return true;
        return false;
    }

    public static int getHurtTime(Object entity) {
        if (entity == null) return 0;
        Integer v = readIntField(entity, "hurtTime");
        if (v != null) return v;
        v = readIntField(entity, "maxHurtTime");
        if (v != null) return v;
        return 0;
    }

    public static Integer readIntField(Object obj, String name) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (Integer) f.get(obj);
        } catch (Exception ignored) {
        }
        try {
            var f = obj.getClass().getField(name);
            f.setAccessible(true);
            return (Integer) f.get(obj);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static float rnd(float a, float b) {
        if (b <= a) return a;
        return a + ThreadLocalRandom.current().nextFloat() * (b - a);
    }

    private static double lerpD(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clampD(double v, double mn, double mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }

    private static float quantize(float v, float step) {
        if (step <= 0.0f) return v;
        float s = Math.abs(step);
        float q = Math.round(v / s) * s;
        return q;
    }

    public static final class NeuroRotationModel {

        private static final int DIST_BINS = 14;
        private static final int SPEED_BINS = 10;
        private static final int AIR_BINS = 2;
        private static final int MODE_BINS = 2;
        private static final int SIDE_BINS = 3;

        private static final int ANCHORS = 7;
        private static final int CORR_TYPES = 4;

        private static final int SIZE = DIST_BINS * SPEED_BINS * AIR_BINS * MODE_BINS * SIDE_BINS;

        private static final float DIST_MAX = 6.0f;
        private static final float SPEED_MAX = 0.75f;

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

        private java.nio.file.Path filePath(MinecraftClient mc) {
            File dir = new File(mc.runDirectory, "sadrik");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, "neuro_aura_exec.json").toPath();
        }

        private static int sideBin(Vec3d tv, float playerYawDeg) {
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

        private int idxBins(int db, int sb, int ab, int mb, int xb) {
            int i = MathHelper.clamp(db, 0, DIST_BINS - 1);
            i = i * SPEED_BINS + MathHelper.clamp(sb, 0, SPEED_BINS - 1);
            i = i * AIR_BINS + MathHelper.clamp(ab, 0, AIR_BINS - 1);
            i = i * MODE_BINS + MathHelper.clamp(mb, 0, MODE_BINS - 1);
            i = i * SIDE_BINS + MathHelper.clamp(xb, 0, SIDE_BINS - 1);
            return i;
        }

        private int idxFor(LivingEntity target, boolean attackSample) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || target == null) return -1;

            float dist = mc.player.distanceTo(target);
            Vec3d v = target.getVelocity();
            float sp = (float) Math.sqrt(v.x * v.x + v.z * v.z);

            boolean air = mc.player.isGliding() || target.isGliding() || !mc.player.isOnGround();
            int ab = air ? 1 : 0;

            float distN = MathHelper.clamp(dist / DIST_MAX, 0.0f, 1.0f);
            float speedN = MathHelper.clamp(sp / SPEED_MAX, 0.0f, 1.0f);

            int db = MathHelper.clamp((int) Math.floor(distN * (DIST_BINS - 1)), 0, DIST_BINS - 1);
            int sb = MathHelper.clamp((int) Math.floor(speedN * (SPEED_BINS - 1)), 0, SPEED_BINS - 1);

            int mode = attackSample ? 1 : 0;
            int side = sideBin(v, mc.player.getYaw());

            return idxBins(db, sb, ab, mode, side);
        }

        boolean learn(LivingEntity target,
                      boolean attackSample,
                      float yawDeltaDeg,
                      float pitchDeltaDeg,
                      float px, float py, float pz,
                      float yawSpeed, float pitchSpeed,
                      float yawJitter, float pitchJitter,
                      float gcdYaw, float gcdPitch,
                      float amp,
                      float rate) {
            int i = idxFor(target, attackSample);
            if (i < 0) return false;

            float r = MathHelper.clamp(rate, 0.01f, 0.40f);

            float dy = MathHelper.clamp(yawDeltaDeg, -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA);
            float dp = MathHelper.clamp(pitchDeltaDeg, -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA);

            float err = (Math.abs(dy) + Math.abs(dp)) * 0.5f;

            yawDeltaAvg[i] = yawDeltaAvg[i] + MathHelper.wrapDegrees(dy - yawDeltaAvg[i]) * r;
            pitchDeltaAvg[i] = pitchDeltaAvg[i] + MathHelper.wrapDegrees(dp - pitchDeltaAvg[i]) * r;

            float pErr = (float) (Math.abs(px - pXAvg[i]) + Math.abs(py - pYAvg[i]) + Math.abs(pz - pZAvg[i])) * 0.33f;

            pXAvg[i] = pXAvg[i] + (px - pXAvg[i]) * r;
            pYAvg[i] = pYAvg[i] + (py - pYAvg[i]) * r;
            pZAvg[i] = pZAvg[i] + (pz - pZAvg[i]) * r;

            errEma[i] = errEma[i] + (err - errEma[i]) * (r * 0.85f);
            pErrEma[i] = pErrEma[i] + (pErr - pErrEma[i]) * (r * 0.75f);

            yawSpeedAvg[i] = yawSpeedAvg[i] + (yawSpeed - yawSpeedAvg[i]) * (r * 0.65f);
            pitchSpeedAvg[i] = pitchSpeedAvg[i] + (pitchSpeed - pitchSpeedAvg[i]) * (r * 0.65f);

            yawJitterAvg[i] = yawJitterAvg[i] + (yawJitter - yawJitterAvg[i]) * (r * 0.55f);
            pitchJitterAvg[i] = pitchJitterAvg[i] + (pitchJitter - pitchJitterAvg[i]) * (r * 0.55f);

            if (gcdYaw != 0.0f) gcdYawAvg[i] = gcdYawAvg[i] + (gcdYaw - gcdYawAvg[i]) * (r * 0.25f);
            if (gcdPitch != 0.0f) gcdPitchAvg[i] = gcdPitchAvg[i] + (gcdPitch - gcdPitchAvg[i]) * (r * 0.25f);

            ampAvg[i] = ampAvg[i] + (amp - ampAvg[i]) * (r * 0.45f);

            int c = count[i];
            if (c < 2000000000) count[i] = c + 1;

            fbYawAvg[i] = fbYawAvg[i] + MathHelper.wrapDegrees(dy - fbYawAvg[i]) * (r * 0.55f);
            fbPitchAvg[i] = fbPitchAvg[i] + MathHelper.wrapDegrees(dp - fbPitchAvg[i]) * (r * 0.55f);
            fbErrEma[i] = fbErrEma[i] + (err - fbErrEma[i]) * (r * 0.65f);
            int fc = fbCount[i];
            if (fc < 2000000000) fbCount[i] = fc + 1;

            return true;
        }

        public Prediction predict(LivingEntity target) {
            return predict(target, true);
        }

        public Prediction predict(LivingEntity target, boolean attackSample) {
            MinecraftClient mc = MinecraftClient.getInstance();

            float dist = 3.0f;
            float sp = 0.0f;
            boolean air = false;
            int side = 1;

            if (mc.player != null && target != null) {
                dist = mc.player.distanceTo(target);
                Vec3d v = target.getVelocity();
                sp = (float) Math.sqrt(v.x * v.x + v.z * v.z);
                air = mc.player.isGliding() || target.isGliding() || !mc.player.isOnGround();
                side = sideBin(v, mc.player.getYaw());
            }

            int ab = air ? 1 : 0;
            int mode = attackSample ? 1 : 0;

            float distN = MathHelper.clamp(dist / DIST_MAX, 0.0f, 1.0f);
            float speedN = MathHelper.clamp(sp / SPEED_MAX, 0.0f, 1.0f);

            float fDb = distN * (DIST_BINS - 1);
            float fSb = speedN * (SPEED_BINS - 1);

            int db0 = MathHelper.clamp((int) Math.floor(fDb), 0, DIST_BINS - 1);
            int sb0 = MathHelper.clamp((int) Math.floor(fSb), 0, SPEED_BINS - 1);

            int db1 = Math.min(DIST_BINS - 1, db0 + 1);
            int sb1 = Math.min(SPEED_BINS - 1, sb0 + 1);

            float dFrac = MathHelper.clamp(fDb - db0, 0.0f, 1.0f);
            float sFrac = MathHelper.clamp(fSb - sb0, 0.0f, 1.0f);

            Sample p00 = sample(db0, sb0, ab, mode, side);
            Sample p10 = sample(db1, sb0, ab, mode, side);
            Sample p01 = sample(db0, sb1, ab, mode, side);
            Sample p11 = sample(db1, sb1, ab, mode, side);

            float w00 = (1.0f - dFrac) * (1.0f - sFrac);
            float w10 = dFrac * (1.0f - sFrac);
            float w01 = (1.0f - dFrac) * sFrac;
            float w11 = dFrac * sFrac;

            float yaw = p00.yaw * w00 + p10.yaw * w10 + p01.yaw * w01 + p11.yaw * w11;
            float pitch = p00.pitch * w00 + p10.pitch * w10 + p01.pitch * w01 + p11.pitch * w11;

            float px = p00.px * w00 + p10.px * w10 + p01.px * w01 + p11.px * w11;
            float py = p00.py * w00 + p10.py * w10 + p01.py * w01 + p11.py * w11;
            float pz = p00.pz * w00 + p10.pz * w10 + p01.pz * w01 + p11.pz * w11;

            float err = p00.err * w00 + p10.err * w10 + p01.err * w01 + p11.err * w11;
            float c = p00.c * w00 + p10.c * w10 + p01.c * w01 + p11.c * w11;

            float ySp = p00.yawSpeed * w00 + p10.yawSpeed * w10 + p01.yawSpeed * w01 + p11.yawSpeed * w11;
            float pSp = p00.pitchSpeed * w00 + p10.pitchSpeed * w10 + p01.pitchSpeed * w01 + p11.pitchSpeed * w11;

            float yJ = p00.yawJitter * w00 + p10.yawJitter * w10 + p01.yawJitter * w01 + p11.yawJitter * w11;
            float pJ = p00.pitchJitter * w00 + p10.pitchJitter * w10 + p01.pitchJitter * w01 + p11.pitchJitter * w11;

            float gY = p00.gcdYaw * w00 + p10.gcdYaw * w10 + p01.gcdYaw * w01 + p11.gcdYaw * w11;
            float gP = p00.gcdPitch * w00 + p10.gcdPitch * w10 + p01.gcdPitch * w01 + p11.gcdPitch * w11;

            float amp = p00.amp * w00 + p10.amp * w10 + p01.amp * w01 + p11.amp * w11;

            float conf = MathHelper.clamp((float) Math.exp(-err * 0.10f) * MathHelper.clamp(c / 40.0f, 0.0f, 1.0f), 0.0f, 1.0f);
            int pointConf = (int) (MathHelper.clamp((float) Math.exp(-err * 0.08f) * MathHelper.clamp(c / 55.0f, 0.0f, 1.0f), 0.0f, 1.0f) * 100.0f);

            Prediction pr = new Prediction();
            pr.yawDeltaDeg = yaw;
            pr.pitchDeltaDeg = pitch;
            pr.confidence = conf;
            pr.px = MathHelper.clamp(px, 0.0f, 1.0f);
            pr.py = MathHelper.clamp(py, 0.0f, 1.0f);
            pr.pz = MathHelper.clamp(pz, 0.0f, 1.0f);
            pr.pointConfidence = pointConf;

            pr.yawSpeed = ySp;
            pr.pitchSpeed = pSp;
            pr.yawJitter = yJ;
            pr.pitchJitter = pJ;

            pr.gcdYaw = MathHelper.clamp(Math.abs(gY), GCD_MIN, GCD_MAX);
            pr.gcdPitch = MathHelper.clamp(Math.abs(gP), GCD_MIN, GCD_MAX);

            pr.amp = amp;

            return pr;
        }

        FallbackPrediction fallbackPredict(LivingEntity target, boolean attackSample) {
            MinecraftClient mc = MinecraftClient.getInstance();

            float dist = 3.0f;
            float sp = 0.0f;
            boolean air = false;
            int side = 1;

            if (mc.player != null && target != null) {
                dist = mc.player.distanceTo(target);
                Vec3d v = target.getVelocity();
                sp = (float) Math.sqrt(v.x * v.x + v.z * v.z);
                air = mc.player.isGliding() || target.isGliding() || !mc.player.isOnGround();
                side = sideBin(v, mc.player.getYaw());
            }

            int ab = air ? 1 : 0;
            int mode = attackSample ? 1 : 0;

            float distN = MathHelper.clamp(dist / DIST_MAX, 0.0f, 1.0f);
            float speedN = MathHelper.clamp(sp / SPEED_MAX, 0.0f, 1.0f);

            float fDb = distN * (DIST_BINS - 1);
            float fSb = speedN * (SPEED_BINS - 1);

            int db0 = MathHelper.clamp((int) Math.floor(fDb), 0, DIST_BINS - 1);
            int sb0 = MathHelper.clamp((int) Math.floor(fSb), 0, SPEED_BINS - 1);

            int db1 = Math.min(DIST_BINS - 1, db0 + 1);
            int sb1 = Math.min(SPEED_BINS - 1, sb0 + 1);

            float dFrac = MathHelper.clamp(fDb - db0, 0.0f, 1.0f);
            float sFrac = MathHelper.clamp(fSb - sb0, 0.0f, 1.0f);

            FbSample p00 = fbSample(db0, sb0, ab, mode, side);
            FbSample p10 = fbSample(db1, sb0, ab, mode, side);
            FbSample p01 = fbSample(db0, sb1, ab, mode, side);
            FbSample p11 = fbSample(db1, sb1, ab, mode, side);

            float w00 = (1.0f - dFrac) * (1.0f - sFrac);
            float w10 = dFrac * (1.0f - sFrac);
            float w01 = (1.0f - dFrac) * sFrac;
            float w11 = dFrac * sFrac;

            float yaw = p00.yaw * w00 + p10.yaw * w10 + p01.yaw * w01 + p11.yaw * w11;
            float pitch = p00.pitch * w00 + p10.pitch * w10 + p01.pitch * w01 + p11.pitch * w11;

            float err = p00.err * w00 + p10.err * w10 + p01.err * w01 + p11.err * w11;
            float c = p00.c * w00 + p10.c * w10 + p01.c * w01 + p11.c * w11;

            float conf = MathHelper.clamp((float) Math.exp(-err * 0.10f) * MathHelper.clamp(c / 18.0f, 0.0f, 1.0f), 0.0f, 1.0f);

            if (c < 1.0f) conf = 0.0f;

            FallbackPrediction out = new FallbackPrediction();
            out.yawDeltaDeg = yaw;
            out.pitchDeltaDeg = pitch;
            out.confidence = conf;
            return out;
        }

        Vec3d pointFromPrediction(LivingEntity target, Prediction pr) {
            if (target == null || pr == null) return null;
            Box hb = target.getBoundingBox();
            double x = hb.minX + (hb.maxX - hb.minX) * pr.px;
            double y = hb.minY + (hb.maxY - hb.minY) * pr.py;
            double z = hb.minZ + (hb.maxZ - hb.minZ) * pr.pz;
            return new Vec3d(x, y, z);
        }

        AngleDeg angleDegFromAngle(Angle angle) {
            AngleDeg a = new AngleDeg();
            if (angle == null) {
                a.yaw = 0.0f;
                a.pitch = 0.0f;
                return a;
            }
            Vec3d v = angle.toVector();
            double x = v.x;
            double y = v.y;
            double z = v.z;

            float yaw = (float) (Math.toDegrees(Math.atan2(z, x)) - 90.0);
            float pitch = (float) (-Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z))));

            a.yaw = yaw;
            a.pitch = pitch;
            return a;
        }

        Angle turnsFromAngleDeg(float yaw, float pitch) {
            return new Angle(yaw, pitch);
        }

        private Sample sample(int d, int s, int ab, int mb, int side) {
            int idx = idxBins(d, s, ab, mb, side);

            float y = yawDeltaAvg[idx];
            float p = pitchDeltaAvg[idx];
            float e = errEma[idx];

            float px = pXAvg[idx];
            float py = pYAvg[idx];
            float pz = pZAvg[idx];

            if (px == 0.0f) px = 0.5f;
            if (py == 0.0f) py = 0.5f;
            if (pz == 0.0f) pz = 0.5f;

            float c = count[idx];

            float ySp = yawSpeedAvg[idx];
            float pSp = pitchSpeedAvg[idx];
            float yJ = yawJitterAvg[idx];
            float pJ = pitchJitterAvg[idx];
            float gY = gcdYawAvg[idx];
            float gP = gcdPitchAvg[idx];
            float amp = ampAvg[idx];

            Sample out = new Sample();
            out.yaw = y;
            out.pitch = p;
            out.err = e + pErrEma[idx] * 0.5f;
            out.px = px;
            out.py = py;
            out.pz = pz;
            out.c = c;

            out.yawSpeed = ySp;
            out.pitchSpeed = pSp;
            out.yawJitter = yJ;
            out.pitchJitter = pJ;
            out.gcdYaw = gY;
            out.gcdPitch = gP;
            out.amp = amp;

            return out;
        }

        private FbSample fbSample(int d, int s, int ab, int mb, int side) {
            int idx = idxBins(d, s, ab, mb, side);

            float y = fbYawAvg[idx];
            float p = fbPitchAvg[idx];
            float e = fbErrEma[idx];
            float c = fbCount[idx];

            if (c <= 0.0f) {
                y = yawDeltaAvg[idx];
                p = pitchDeltaAvg[idx];
                e = errEma[idx];
                c = Math.min(count[idx], 120);
            }

            FbSample out = new FbSample();
            out.yaw = y;
            out.pitch = p;
            out.err = e;
            out.c = c;
            return out;
        }

        void load() throws Exception {
            MinecraftClient mc = MinecraftClient.getInstance();
            File f = filePath(mc).toFile();
            if (!f.exists()) return;

            String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);

            String b64 = findJsonStringValue(json, "b64");
            if (b64 == null) b64 = findJsonStringValue(json, "data");
            if (b64 == null) b64 = findJsonStringValue(json, "blob");
            if (b64 == null || b64.isEmpty()) return;

            byte[] data;
            try {
                data = Base64.getDecoder().decode(b64);
            } catch (Exception ignored) {
                return;
            }

            if (data.length < 16) return;

            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

            readFloatArray(bb, yawDeltaAvg);
            readFloatArray(bb, pitchDeltaAvg);
            readFloatArray(bb, errEma);
            readIntArray(bb, count);

            readFloatArray(bb, pXAvg);
            readFloatArray(bb, pYAvg);
            readFloatArray(bb, pZAvg);
            readFloatArray(bb, pErrEma);

            if (bb.remaining() >= SIZE * 7 * 4) {
                readFloatArray(bb, yawSpeedAvg);
                readFloatArray(bb, pitchSpeedAvg);
                readFloatArray(bb, yawJitterAvg);
                readFloatArray(bb, pitchJitterAvg);
                readFloatArray(bb, gcdYawAvg);
                readFloatArray(bb, gcdPitchAvg);
                readFloatArray(bb, ampAvg);
            } else {
                for (int i = 0; i < SIZE; i++) {
                    yawSpeedAvg[i] = 0.0f;
                    pitchSpeedAvg[i] = 0.0f;
                    yawJitterAvg[i] = 0.0f;
                    pitchJitterAvg[i] = 0.0f;
                    gcdYawAvg[i] = 0.0f;
                    gcdPitchAvg[i] = 0.0f;
                    ampAvg[i] = 0.0f;
                }
            }

            readFloatArray(bb, anchorW);

            if (bb.remaining() >= corrW.length * 4) {
                readFloatArray(bb, corrW);
            }

            if (bb.remaining() >= fbYawAvg.length * 4 + fbPitchAvg.length * 4 + fbErrEma.length * 4 + fbCount.length * 4) {
                readFloatArray(bb, fbYawAvg);
                readFloatArray(bb, fbPitchAvg);
                readFloatArray(bb, fbErrEma);
                readIntArray(bb, fbCount);
            } else {
                for (int i = 0; i < SIZE; i++) {
                    fbYawAvg[i] = yawDeltaAvg[i];
                    fbPitchAvg[i] = pitchDeltaAvg[i];
                    fbErrEma[i] = errEma[i];
                    fbCount[i] = Math.min(count[i], 160);
                }
            }
        }

        void save() throws Exception {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;

            ByteBuffer bb = ByteBuffer.allocate(
                    (yawDeltaAvg.length + pitchDeltaAvg.length + errEma.length) * 4
                            + count.length * 4
                            + (pXAvg.length + pYAvg.length + pZAvg.length + pErrEma.length) * 4
                            + (yawSpeedAvg.length + pitchSpeedAvg.length + yawJitterAvg.length + pitchJitterAvg.length + gcdYawAvg.length + gcdPitchAvg.length + ampAvg.length) * 4
                            + anchorW.length * 4
                            + corrW.length * 4
                            + (fbYawAvg.length + fbPitchAvg.length + fbErrEma.length) * 4
                            + fbCount.length * 4
            ).order(ByteOrder.LITTLE_ENDIAN);

            writeFloatArray(bb, yawDeltaAvg);
            writeFloatArray(bb, pitchDeltaAvg);
            writeFloatArray(bb, errEma);
            writeIntArray(bb, count);

            writeFloatArray(bb, pXAvg);
            writeFloatArray(bb, pYAvg);
            writeFloatArray(bb, pZAvg);
            writeFloatArray(bb, pErrEma);

            writeFloatArray(bb, yawSpeedAvg);
            writeFloatArray(bb, pitchSpeedAvg);
            writeFloatArray(bb, yawJitterAvg);
            writeFloatArray(bb, pitchJitterAvg);
            writeFloatArray(bb, gcdYawAvg);
            writeFloatArray(bb, gcdPitchAvg);
            writeFloatArray(bb, ampAvg);

            writeFloatArray(bb, anchorW);
            writeFloatArray(bb, corrW);

            writeFloatArray(bb, fbYawAvg);
            writeFloatArray(bb, fbPitchAvg);
            writeFloatArray(bb, fbErrEma);
            writeIntArray(bb, fbCount);

            byte[] data = new byte[bb.position()];
            bb.rewind();
            bb.get(data);

            String b64 = Base64.getEncoder().encodeToString(data);

            String json = "{\"v\":6,\"b64\":\"" + b64 + "\"}";

            File f = filePath(mc).toFile();
            Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
        }

        private static void readFloatArray(ByteBuffer bb, float[] arr) {
            for (int i = 0; i < arr.length; i++) {
                if (bb.remaining() < 4) return;
                arr[i] = bb.getFloat();
            }
        }

        private static void readIntArray(ByteBuffer bb, int[] arr) {
            for (int i = 0; i < arr.length; i++) {
                if (bb.remaining() < 4) return;
                arr[i] = bb.getInt();
            }
        }

        private static void writeFloatArray(ByteBuffer bb, float[] arr) {
            for (float v : arr) bb.putFloat(v);
        }

        private static void writeIntArray(ByteBuffer bb, int[] arr) {
            for (int v : arr) bb.putInt(v);
        }

        private static String findJsonStringValue(String json, String key) {
            int k = json.indexOf("\"" + key + "\"");
            if (k < 0) return null;
            int colon = json.indexOf(":", k);
            if (colon < 0) return null;
            int q1 = json.indexOf("\"", colon + 1);
            if (q1 < 0) return null;
            int q2 = json.indexOf("\"", q1 + 1);
            if (q2 < 0) return null;
            String s = json.substring(q1 + 1, q2).trim();
            return s.isEmpty() ? null : s;
        }

        static final class Sample {
            float yaw;
            float pitch;
            float err;
            float px;
            float py;
            float pz;
            float c;

            float yawSpeed;
            float pitchSpeed;
            float yawJitter;
            float pitchJitter;
            float gcdYaw;
            float gcdPitch;
            float amp;
        }

        static final class FbSample {
            float yaw;
            float pitch;
            float err;
            float c;
        }

        static final class AngleDeg {
            float yaw;
            float pitch;
        }

        public static final class Prediction {
            float yawDeltaDeg;
            float pitchDeltaDeg;
            float confidence;

            float px;
            float py;
            float pz;

            int pointConfidence;

            float yawSpeed;
            float pitchSpeed;
            float yawJitter;
            float pitchJitter;
            float gcdYaw;
            float gcdPitch;
            float amp;
        }

        static final class FallbackPrediction {
            float yawDeltaDeg;
            float pitchDeltaDeg;
            float confidence;
        }
    }

    static final class PerlinNoise {
        private final int[] p = new int[512];

        PerlinNoise(int seed) {
            int[] perm = new int[256];
            for (int i = 0; i < 256; i++) perm[i] = i;

            Random r = new Random(seed);
            for (int i = 255; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int t = perm[i];
                perm[i] = perm[j];
                perm[j] = t;
            }

            for (int i = 0; i < 256; i++) {
                int v = perm[i] & 255;
                p[i] = v;
                p[i + 256] = v;
            }
        }

        private static float fade(float t) {
            return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
        }

        private static float lerp(float t, float a, float b) {
            return a + t * (b - a);
        }

        private static float grad(int hash, float x, float y, float z) {
            int h = hash & 15;
            float u = h < 8 ? x : y;
            float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }

        float noise(float x, float y) {
            float z = 0.0f;

            int X = ((int) Math.floor(x)) & 255;
            int Y = ((int) Math.floor(y)) & 255;
            int Z = ((int) Math.floor(z)) & 255;

            x = (float) (x - Math.floor(x));
            y = (float) (y - Math.floor(y));
            z = (float) (z - Math.floor(z));

            float u = fade(x);
            float v = fade(y);
            float w = fade(z);

            int A = p[X] + Y;
            int AA = p[A] + Z;
            int AB = p[A + 1] + Z;
            int B = p[X + 1] + Y;
            int BA = p[B] + Z;
            int BB = p[B + 1] + Z;

            return lerp(w,
                    lerp(v,
                            lerp(u, grad(p[AA], x, y, z), grad(p[BA], x - 1.0f, y, z)),
                            lerp(u, grad(p[AB], x, y - 1.0f, z), grad(p[BB], x - 1.0f, y - 1.0f, z))
                    ),
                    lerp(v,
                            lerp(u, grad(p[AA + 1], x, y, z - 1.0f), grad(p[BA + 1], x - 1.0f, y, z - 1.0f)),
                            lerp(u, grad(p[AB + 1], x, y - 1.0f, z - 1.0f), grad(p[BB + 1], x - 1.0f, y - 1.0f, z - 1.0f))
                    )
            );
        }

        float fbm(float x, float y, int octaves, float lacunarity, float gain) {
            float sum = 0.0f;
            float amp = 0.5f;
            float fx = x;
            float fy = y;
            for (int i = 0; i < octaves; i++) {
                sum += noise(fx, fy) * amp;
                fx *= lacunarity;
                fy *= lacunarity;
                amp *= gain;
            }
            return sum;
        }
    }
}
