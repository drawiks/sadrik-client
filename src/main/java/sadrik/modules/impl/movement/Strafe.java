package sadrik.modules.impl.movement;

import antidaunleak.api.annotation.Native;
import net.minecraft.client.MinecraftClient;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.TickEvent;
import sadrik.modules.impl.combat.Aura;
import sadrik.modules.impl.combat.aura.Angle;
import sadrik.modules.impl.combat.aura.AngleConfig;
import sadrik.modules.impl.combat.aura.AngleConnection;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.SelectSetting;
import sadrik.modules.module.setting.implement.SliderSettings;
import sadrik.util.Instance;
import sadrik.util.math.TaskPriority;
import sadrik.util.move.MoveUtil;

public class Strafe extends ModuleStructure {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public SelectSetting mode = new SelectSetting("Режим", "Выберите тип стрейфов")
            .value("Matrix", "Grim")
            .selected("Matrix");
    SliderSettings speed = new SliderSettings("Скорость", "Выберите скорость для стрейфа")
            .setValue(0.42F).range(0F, 1F).visible(() -> mode.isSelected("Matrix"));

    private float lastYaw, lastPitch;
    private final Angle rot = new Angle(0, 0);

    public Strafe() {
        super("Strafe", "Strafe", ModuleCategory.MOVEMENT);
        settings(mode, speed);
    }

    public static Strafe getInstance() {
        return Instance.get(Strafe.class);
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        boolean moving = MoveUtil.hasPlayerMovement();

        float yaw = mc.player.getYaw();

        if (mode.isSelected("Matrix")) {
            handleMatrixMode(moving, yaw);
        } else if (mode.isSelected("Grim")) {
            handleGrimMode(moving, yaw);
        }

        lastYaw = yaw;
        lastPitch = 0;
    }

    @Native(type = Native.Type.VMProtectBeginUltra)
    private void handleMatrixMode(boolean moving, float yaw) {
        if (moving) {
            yaw = MoveUtil.moveYaw(mc.player.getYaw());
            double motion = speed.getValue() * 1.5f;
            MoveUtil.setVelocity(motion);
        } else {
            MoveUtil.setVelocity(0);
        }
        mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y, mc.player.getVelocity().z);
    }

    @Native(type = Native.Type.VMProtectBeginUltra)
    private void handleGrimMode(boolean moving, float yaw) {
        if (moving) {
            AngleConfig.freeCorrection = true;
            yaw = MoveUtil.moveYaw(mc.player.getYaw());
            rot.setYaw(yaw);
            rot.setPitch(mc.player.getPitch());
            if (Aura.getInstance().target == null) {
                AngleConnection.INSTANCE.rotateTo(rot, AngleConfig.DEFAULT, TaskPriority.HIGH_IMPORTANCE_1, this);
            }
        }
    }

    @Override
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void activate() {
        super.activate();
        lastYaw = mc.player != null ? mc.player.getYaw() : 0;
        lastPitch = mc.player != null ? mc.player.getPitch() : 0;
    }
}