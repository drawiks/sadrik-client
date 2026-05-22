package sadrik.modules.impl.combat;

import antidaunleak.api.annotation.Native;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.NonFinal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import net.minecraft.util.Pair;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import sadrik.Initialization;
import sadrik.events.api.EventHandler;
import sadrik.events.api.types.EventType;
import sadrik.events.impl.InputEvent;
import sadrik.events.impl.PacketEvent;
import sadrik.events.impl.RotationUpdateEvent;
import sadrik.events.impl.TickEvent;

import sadrik.modules.impl.combat.aura.Angle;
import sadrik.modules.impl.combat.aura.AngleConfig;
import sadrik.modules.impl.combat.aura.AngleConnection;
import sadrik.modules.impl.combat.aura.MathAngle;
import sadrik.modules.impl.combat.aura.attack.StrikeManager;
import sadrik.modules.impl.combat.aura.attack.StrikerConstructor;
import sadrik.modules.impl.combat.aura.impl.LinearConstructor;
import sadrik.modules.impl.combat.aura.impl.RotateConstructor;
import sadrik.modules.impl.combat.aura.rotations.FTAngle;
import sadrik.modules.impl.combat.aura.rotations.FTAngleBypass;
import sadrik.modules.impl.combat.aura.rotations.MatrixAngle;
import sadrik.modules.impl.combat.aura.rotations.SPAngle;
import sadrik.modules.impl.combat.aura.rotations.SnapAngle;
import sadrik.modules.impl.combat.aura.target.MultiPoint;
import sadrik.modules.impl.combat.aura.target.TargetFinder;
import sadrik.modules.impl.movement.ElytraTarget;
import sadrik.modules.impl.movement.TargetStrafe;
import sadrik.modules.impl.render.Hud;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.BooleanSetting;
import sadrik.modules.module.setting.implement.MultiSelectSetting;
import sadrik.modules.module.setting.implement.SelectSetting;
import sadrik.modules.module.setting.implement.SliderSettings;
import sadrik.util.Instance;
import sadrik.util.math.TaskPriority;

import sadrik.modules.impl.combat.aura.neuro.NeuroAuraExec;
import sadrik.modules.impl.combat.aura.neuro.NeuroAuraLearn;
import sadrik.screens.hud.Notifications;
import sadrik.util.string.PlayerInteractionHelper;

import java.util.List;
import java.util.Objects;
public class Aura extends ModuleStructure {

    @Native(type = Native.Type.VMProtectBeginUltra)
    public static Aura getInstance() {
        return Instance.get(Aura.class);
    }

    private final SelectSetting mode = new SelectSetting("Режим наводки", "Select aim mode")
            .value("Matrix", "FunTime Snap", "Snap", "SpookyTime", "FunTime HolderGD", "Neuro Aura")
            .selected("Matrix");

    private final SelectSetting moveFix = new SelectSetting("Коррекция движения", "Select move fix mode")
            .value("Сфокусированная", "Свободная", "Преследование", "Таргет", "Отключена")
            .selected("Сфокусированная");

    @Getter
    public final SliderSettings attackrange = new SliderSettings("Дистанция удара", "Set range value")
            .range(2.0f, 6.0f)
            .setValue(3.0f)
            .visible(() -> !neuroEnabled() || neuroExec());

    private final SliderSettings lookrange = new SliderSettings("Дистанция поиска", "Set look range value")
            .range(0.0f, 10.0f)
            .setValue(1.5f)
            .visible(() -> !neuroEnabled() || neuroExec());

    public final MultiSelectSetting options = new MultiSelectSetting("Настройки", "Select settings")
            .value("Бить сквозь стены", "Рандомизация крита", "Не бить если ешь")
            .selected("Бить сквозь стены", "Рандомизация крита", "Не бить если ешь");

    private final MultiSelectSetting targetType = new MultiSelectSetting("Настройка целей", "Select target settings")
            .value("Игроки", "Мобы", "Животные", "Друзья", "Стойки для брони")
            .selected("Игроки", "Мобы", "Животные");

    @Getter
    private final SelectSetting resetSprintMode = new SelectSetting("Сброс спринта", "Reset sprint mode")
            .value("Легитный", "Пакетный")
            .selected("Легитный");

    @Getter
    private final BooleanSetting checkCrit = new BooleanSetting("Только криты", "Only critical hits")
            .setValue(true);

    @Getter
    private final BooleanSetting smartCrits = new BooleanSetting("Умные криты",
            "Smart crits - attack on ground when possible")
            .setValue(true)
            .visible(() -> checkCrit.isValue());

    private final SelectSetting neuroMode = new SelectSetting("Нейро режим", "Режим Neuro Aura")
            .value("Запоминающий", "Выполняющий")
            .selected("Запоминающий")
            .visible(() -> mode.isSelected("Neuro Aura"));

    private final BooleanSetting neuroShakeOnLoss = new BooleanSetting("Нейро шейк", "Шейк как FunTime V2 при потере цели/выключении")
            .setValue(true)
            .visible(() -> mode.isSelected("Neuro Aura") && neuroMode.isSelected("Выполняющий"));

    private final SliderSettings neuroSmooth = new SliderSettings("Нейро плавность", "Плавность наводки/доводки/возврата в Neuro Aura")
            .setValue(0.76f).range(0.20f, 0.95f)
            .visible(() -> mode.isSelected("Neuro Aura") && neuroMode.isSelected("Выполняющий"));

    private final SelectSetting correctionType = new SelectSetting("Коррекции движения", "Выбор коррекции движения игрока")
            .value("Free", "Focused", "Focus V2", "Not visible", "Neuro")
            .selected("Neuro");

    @Getter
    @NonFinal
    public static LivingEntity target;

    @NonFinal
    public LivingEntity lastTarget;

    TargetFinder targetSelector = new TargetFinder();
    MultiPoint pointFinder = new MultiPoint();

    @NonFinal
    StrikerConstructor.AttackPerpetratorConfigurable cachedConfig;
    @NonFinal
    Box cachedHitbox;
    @NonFinal
    Vec3d cachedPoint;

    @NonFinal
    int smoothPointTid = Integer.MIN_VALUE;
    @NonFinal
    Vec3d smoothPointCur = null;
    @NonFinal
    long smoothPointLastMs = 0L;

    public static boolean shouldRotate;
    public static boolean fakeRotate;

    final NeuroAuraExec neuroExecEngine = new NeuroAuraExec();
    final NeuroAuraLearn neuroLearnEngine = new NeuroAuraLearn();

    public Aura() {
        super("Aura", ModuleCategory.COMBAT);
        settings(
                mode, neuroMode, neuroShakeOnLoss, neuroSmooth,
                attackrange, lookrange, options, targetType, moveFix,
                correctionType,
                resetSprintMode, checkCrit, smartCrits
        );
    }

    public LivingEntity getTarget() { return target; }
    public StrikerConstructor.AttackPerpetratorConfigurable getCachedConfig() { return cachedConfig; }
    public Box getCachedHitbox() { return cachedHitbox; }
    public Vec3d getCachedPoint() { return cachedPoint; }

    float effectiveAttackRange() {
        if (!neuroEnabled()) return attackrange.getValue();
        return neuroExec() ? attackrange.getValue() : 3.0f;
    }

    float effectiveLookRange() {
        if (!neuroEnabled()) return lookrange.getValue();
        return neuroExec() ? lookrange.getValue() : 1.5f;
    }

    boolean effectiveIgnoreWalls() {
        if (!neuroEnabled()) return options.isSelected("Бить сквозь стены");
        return neuroExec() && options.isSelected("Бить сквозь стены");
    }

    public boolean neuroEnabled() {
        return mode.isSelected("Neuro Aura");
    }

    public boolean neuroExec() {
        return neuroEnabled() && neuroMode.isSelected("Выполняющий");
    }

    public boolean neuroShakeEnabled() {
        return neuroExec() && neuroShakeOnLoss.isValue();
    }

    public boolean neuroModeIsLearning() {
        return neuroEnabled() && neuroMode.isSelected("Запоминающий");
    }

    public float neuroSmoothValue() {
        float v = neuroSmooth.getValue();
        if (v < 0.20f) v = 0.20f;
        if (v > 0.95f) v = 0.95f;
        return v;
    }

    public SelectSetting getCorrectionType() {
        return correctionType;
    }

    public SelectSetting getNeuroMode() {
        return neuroMode;
    }

    public boolean isNeuroLoaded() {
        return neuroLearnEngine.isLoaded();
    }

    public int getNeuroSampleCount() {
        return neuroLearnEngine.getSampleCount();
    }

    public boolean isNeuroDirty() {
        return neuroLearnEngine.isDirty();
    }

    @Override
    public void deactivate() {
        try {
            if (neuroShakeEnabled()) {
                neuroExecEngine.onDeactivate(this);
            } else {
                AngleConnection.INSTANCE.startReturning();
            }
        } catch (Exception ignored) {
        }

        Initialization.getInstance().getManager()
                .getAttackPerpetrator()
                .getAttackHandler()
                .resetPendingState();

        target = null;
        lastTarget = null;
        cachedConfig = null;
        cachedHitbox = null;
        cachedPoint = null;
        smoothPointTid = Integer.MIN_VALUE;
        smoothPointCur = null;
        smoothPointLastMs = 0L;
    }

    @Override
    public void activate() {
        super.activate();
        targetSelector.releaseTarget();
        target = null;
        lastTarget = null;
        cachedConfig = null;
        cachedHitbox = null;
        cachedPoint = null;
        smoothPointTid = Integer.MIN_VALUE;
        smoothPointCur = null;
        smoothPointLastMs = 0L;
        neuroExecEngine.onActivate(this);
        neuroLearnEngine.onActivate(this);

        if (neuroEnabled()) {
            int samples = neuroLearnEngine.getSampleCount();
            String modeName = neuroMode.getSelected();
            String msg = String.format("Neuro Aura: %s | Samples: %,d", modeName, samples);
            Notifications.getInstance().addNotification(msg, 3000);
        }
    }

    @EventHandler
    private void tick(TickEvent event) {
        if (PlayerInteractionHelper.nullCheck()) return;

        if (neuroEnabled()) {
            neuroExecEngine.onTick(this);
        }

        if (target == null) return;

        if (neuroEnabled() && neuroMode.isSelected("Запоминающий")) {
            neuroLearnEngine.learnStep(this, false);
        }
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent e) {
        switch (e.getType()) {
            case EventType.PRE -> {
                if (neuroExecEngine.handlePreRotation(this)) {
                    cachedConfig = null;
                    cachedHitbox = null;
                    cachedPoint = null;
                    target = null;
                    lastTarget = null;
                    return;
                }

                target = updateTarget();

                cachedConfig = null;
                cachedHitbox = null;
                cachedPoint = null;

                if (target == null) {
                    boolean handled = false;
                    if (neuroShakeEnabled()) {
                        handled = neuroExecEngine.handleNoTarget(this, lastTarget);
                    } else {
                        AngleConnection.INSTANCE.startReturning();
                    }
                    if (handled) {
                        lastTarget = null;
                        return;
                    }
                    lastTarget = null;
                    return;
                }

                neuroExecEngine.onTargetSelected(this, target);

                cachedConfig = getConfig();

                if (neuroEnabled() && neuroMode.isSelected("Запоминающий")) {
                    neuroLearnEngine.learnStep(this, false);
                    lastTarget = target;
                } else {
                    rotateToTarget(cachedConfig);
                    lastTarget = target;
                }
            }
            case EventType.POST -> {
                if (neuroExecEngine.isBusy(this)) return;
                if (neuroEnabled() && target != null && neuroMode.isSelected("Запоминающий")) {
                    neuroLearnEngine.learnStep(this, true);
                    return;
                }
                if (target != null) {
                    Initialization.getInstance().getManager()
                            .getAttackPerpetrator()
                            .performAttack(cachedConfig != null ? cachedConfig : getConfig());
                }
            }
        }
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    public StrikerConstructor.AttackPerpetratorConfigurable getConfig() {
        float baseRange = effectiveAttackRange() + 0.253F;

        Vec3d rv = neuroExecEngine.getMpRv(this);

        Angle baseRotForPoint = mc.player != null
                ? new Angle(mc.player.getYaw(), mc.player.getPitch())
                : AngleConnection.INSTANCE.getRotation();

        Pair<Vec3d, Box> pointData = pointFinder.computeVector(
                target,
                baseRange,
                baseRotForPoint,
                rv,
                effectiveIgnoreWalls());

        Vec3d computedPoint = pointData.getLeft();
        Box hitbox = pointData.getRight();

        long now = System.currentTimeMillis();

        if (neuroExec()) {
            computedPoint = neuroExecEngine.adjustPointForExec(this, target, hitbox, computedPoint);
        } else {
            int tid = target != null ? target.getId() : -1;
            computedPoint = smoothPointGeneric(tid, computedPoint, hitbox, now);
        }

        if (mc.player.isGliding() && target != null && target.isGliding()) {
            Vec3d targetVelocity = target.getVelocity();
            double targetSpeed = targetVelocity.horizontalLength();

            float leadTicks = 0;
            if (ElytraTarget.shouldElytraTarget && ElytraTarget.getInstance() != null
                    && ElytraTarget.getInstance().isState()) {
                leadTicks = ElytraTarget.getInstance().elytraForward.getValue();
            }

            if (targetSpeed > 0.35) {
                Vec3d predictedPos = target.getEntityPos().add(targetVelocity.multiply(leadTicks));
                computedPoint = predictedPos.add(0, target.getHeight() / 2, 0);

                hitbox = new Box(
                        predictedPos.x - target.getWidth() / 2,
                        predictedPos.y,
                        predictedPos.z - target.getWidth() / 2,
                        predictedPos.x + target.getWidth() / 2,
                        predictedPos.y + target.getHeight(),
                        predictedPos.z + target.getWidth() / 2);

                computedPoint = smoothPointGeneric(target.getId(), computedPoint, hitbox, now);
            }
        }

        cachedPoint = computedPoint;
        cachedHitbox = hitbox;

        Angle angle = MathAngle.fromVec3d(
                computedPoint.subtract(Objects.requireNonNull(mc.player).getEyePos()));

        return new StrikerConstructor.AttackPerpetratorConfigurable(
                target,
                angle,
                baseRange,
                options.getSelected(),
                mode,
                hitbox);
    }

    private Vec3d smoothPointGeneric(int tid, Vec3d desired, Box hb, long now) {
        if (desired == null) return null;

        Vec3d d = desired;
        if (hb != null) {
            d = new Vec3d(
                    clampD(d.x, hb.minX + 1.0E-4, hb.maxX - 1.0E-4),
                    clampD(d.y, hb.minY + 1.0E-4, hb.maxY - 1.0E-4),
                    clampD(d.z, hb.minZ + 1.0E-4, hb.maxZ - 1.0E-4)
            );
        }

        if (tid != smoothPointTid || smoothPointCur == null) {
            smoothPointTid = tid;
            smoothPointCur = d;
            smoothPointLastMs = now;
            return d;
        }

        long dtMs = now - smoothPointLastMs;
        if (dtMs < 1L) dtMs = 1L;
        if (dtMs > 90L) dtMs = 90L;
        smoothPointLastMs = now;

        float s = neuroEnabled() ? (neuroExec() ? neuroSmoothValue() : 0.70f) : 0.70f;
        float tau = MathHelper.lerp(s, 70.0f, 160.0f);
        float a = (float) dtMs / tau;
        a = MathHelper.clamp(a, 0.0f, 1.0f);
        a = a * a * (3.0f - 2.0f * a);

        smoothPointCur = new Vec3d(
                lerpD(smoothPointCur.x, d.x, a),
                lerpD(smoothPointCur.y, d.y, a),
                lerpD(smoothPointCur.z, d.z, a)
        );

        return smoothPointCur;
    }

    private static double lerpD(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clampD(double v, double mn, double mx) {
        return v < mn ? mn : Math.min(v, mx);
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    public AngleConfig getRotationConfig() {
        boolean neuro = neuroEnabled();

        if (neuroExec() && correctionType.isSelected("Neuro") && target != null) {
            int pred = neuroLearnEngine.getModel().predictCorrectionType(target, false);
            if (pred >= 0) {
                boolean visibleCorrection = pred != 3;
                boolean freeCorrection = (pred == 0 || pred == 3);
                return new AngleConfig(getSmoothMode(), visibleCorrection, freeCorrection);
            }
        }

        boolean visibleCorrection = neuro || !correctionType.isSelected("Not visible");
        boolean freeCorrection;

        if (neuro) {
            freeCorrection = true;
            if (neuroExec() && correctionType.isSelected("Focus V2")) {
                freeCorrection = false;
            } else if (correctionType.isSelected("Focused")) {
                freeCorrection = false;
            } else if (correctionType.isSelected("Free")) {
                freeCorrection = true;
            } else if (correctionType.isSelected("Not visible")) {
                freeCorrection = true;
            } else if (correctionType.isSelected("Neuro")) {
                freeCorrection = true;
            }
        } else {
            freeCorrection = correctionType.isSelected("Free") || correctionType.isSelected("Neuro");
            if (correctionType.isSelected("Focused") || correctionType.isSelected("Focus V2")) {
                freeCorrection = false;
            }
        }

        if (!neuro && TargetStrafe.getInstance().isState()
                && TargetStrafe.getInstance().mode.isSelected("Grim") && target != null) {
            freeCorrection = false;
        }

        return new AngleConfig(getSmoothMode(), visibleCorrection, freeCorrection);
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    private void rotateToTarget(StrikerConstructor.AttackPerpetratorConfigurable config) {
        AngleConnection controller = AngleConnection.INSTANCE;
        AngleConfig rotationConfig = getRotationConfig();

        boolean elytraMode = mc.player.isGliding() && ElytraTarget.getInstance() != null
                && ElytraTarget.getInstance().isState();

        boolean neuro = neuroEnabled();
        boolean exec = neuro && neuroMode.isSelected("Выполняющий");

        if (neuro && !exec) return;

        Angle effectiveAngle = config.getAngle();

        if (neuroExecEngine.tryApplyExecRotation(this, config,
                Initialization.getInstance().getManager().getAttackPerpetrator().getAttackHandler(),
                controller, rotationConfig))
            return;

        if (fakeRotate && target != null) {
            Angle.VecRotation rotation0 = new Angle.VecRotation(effectiveAngle, effectiveAngle.toVector());
            controller.setFakeRotation(rotation0.getAngle());
        }
        fakeRotate = false;

        if (target != null) {
            Angle.VecRotation rotation = new Angle.VecRotation(effectiveAngle, effectiveAngle.toVector());
            controller.rotateTo(rotation, target, 1, rotationConfig, TaskPriority.HIGH_IMPORTANCE_1, this);
        }

        if (shouldRotate && target != null) {
            Angle.VecRotation rotation = new Angle.VecRotation(effectiveAngle, effectiveAngle.toVector());
            controller.rotateTo(rotation, target, 1, rotationConfig, TaskPriority.HIGH_IMPORTANCE_1, this);
        }

        if (elytraMode && target != null) {
            Angle.VecRotation rotation = new Angle.VecRotation(effectiveAngle, effectiveAngle.toVector());
            controller.rotateTo(rotation, target, 1, rotationConfig, TaskPriority.HIGH_IMPORTANCE_1, this);
        }
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (mc.player == null || mc.world == null) return;
        PlayerInput input = event.getInput();
        if (input == null) return;
        if (!isState()) return;
        if (target == null || !target.isAlive()) return;

        boolean w = mc.options.forwardKey.isPressed();
        boolean s = mc.options.backKey.isPressed();
        boolean a = mc.options.leftKey.isPressed();
        boolean d = mc.options.rightKey.isPressed();

        if (moveFix.isSelected("Таргет")) {
            Vec3d playerPos = mc.player.getEntityPos();
            Vec3d targetPos = target.getEntityPos();
            Vec3d moveTarget = new Vec3d(targetPos.x, playerPos.y, targetPos.z);
            Vec3d dir = moveTarget.subtract(playerPos).normalize();
            float yaw = AngleConnection.INSTANCE.getRotation().getYaw();
            float moveAngle = (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90F;
            float angleDiff = MathHelper.wrapDegrees(moveAngle - yaw);

            boolean forward = false, back = false, left = false, right = false;
            if (angleDiff >= -22.5 && angleDiff < 22.5) forward = true;
            else if (angleDiff >= 22.5 && angleDiff < 67.5) { forward = true; right = true; }
            else if (angleDiff >= 67.5 && angleDiff < 112.5) right = true;
            else if (angleDiff >= 112.5 && angleDiff < 157.5) { back = true; right = true; }
            else if (angleDiff >= -67.5 && angleDiff < -22.5) { forward = true; left = true; }
            else if (angleDiff >= -112.5 && angleDiff < -67.5) { left = true; }
            else if (angleDiff >= -157.5 && angleDiff < -112.5) { back = true; left = true; }
            else back = true;

            event.setDirectionalLow(forward, back, left, right);
            return;
        }

        if (moveFix.isSelected("Преследование")) {
            if (!w && !s && !a && !d) return;

            Vec3d playerPos = mc.player.getEntityPos();
            Box targetBox = target.getBoundingBox();
            Vec3d center = targetBox.getCenter();
            float targetYaw = target.getYaw();
            double rad = Math.toRadians(targetYaw);
            Vec3d forwardDir = new Vec3d(-Math.sin(rad), 0, Math.cos(rad)).normalize();
            Vec3d rightDir = new Vec3d(-forwardDir.z, 0, forwardDir.x).normalize();
            Vec3d leftDir = rightDir.multiply(-1);
            double halfWidth = target.getWidth() / 2.0;
            double offset = halfWidth + 0.1;
            Vec3d moveTargetVec = center;
            Vec3d offsetVec = Vec3d.ZERO;

            if (w) offsetVec = offsetVec.add(forwardDir);
            if (s) offsetVec = offsetVec.add(forwardDir.multiply(-1.0));
            if (a) offsetVec = offsetVec.add(leftDir);
            if (d) offsetVec = offsetVec.add(rightDir);

            if (offsetVec.lengthSquared() > 0) {
                offsetVec = offsetVec.normalize().multiply(offset);
                moveTargetVec = center.add(offsetVec);
            }

            moveTargetVec = new Vec3d(moveTargetVec.x, playerPos.y, moveTargetVec.z);
            Vec3d dir = moveTargetVec.subtract(playerPos).normalize();
            float yaw = AngleConnection.INSTANCE.getRotation().getYaw();
            float moveAngle = (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90F;
            float angleDiff = MathHelper.wrapDegrees(moveAngle - yaw);

            boolean forward = false, back = false, left = false, right = false;
            if (angleDiff >= -22.5 && angleDiff < 22.5) forward = true;
            else if (angleDiff >= 22.5 && angleDiff < 67.5) { forward = true; right = true; }
            else if (angleDiff >= 67.5 && angleDiff < 112.5) right = true;
            else if (angleDiff >= 112.5 && angleDiff < 157.5) { back = true; right = true; }
            else if (angleDiff >= -67.5 && angleDiff < -22.5) { forward = true; left = true; }
            else if (angleDiff >= -112.5 && angleDiff < -67.5) { left = true; }
            else if (angleDiff >= -157.5 && angleDiff < -112.5) { back = true; left = true; }
            else back = true;

            event.setDirectionalLow(forward, back, left, right);
        }
    }

    private LivingEntity updateTarget() {
        TargetFinder.EntityFilter filter = new TargetFinder.EntityFilter(
                targetType.getSelected());

        float range = effectiveAttackRange() + 0.253F
                + (mc.player.isGliding() && ElytraTarget.getInstance() != null
                && ElytraTarget.getInstance().isState()
                ? ElytraTarget.getInstance().elytraFindRange.getValue()
                : effectiveLookRange());

        float dynamicFov = 360;

        targetSelector.searchTargets(mc.world.getEntities(), range, dynamicFov, effectiveIgnoreWalls());
        targetSelector.validateTarget(filter::isValid);
        return targetSelector.getCurrentTarget();
    }

    public RotateConstructor getSmoothMode() {
        if (neuroEnabled()) return new LinearConstructor();
        if (mc.player.isGliding() && ElytraTarget.getInstance() != null
                && ElytraTarget.getInstance().isState()) {
            return new LinearConstructor();
        }
        return switch (mode.getSelected()) {
            case "FunTime Snap" -> new FTAngle();
            case "SpookyTime" -> new SPAngle();
            case "Snap" -> new SnapAngle();
            case "Matrix" -> new MatrixAngle();
            case "FunTime HolderGD" -> new FTAngleBypass();
            default -> new LinearConstructor();
        };
    }

}
