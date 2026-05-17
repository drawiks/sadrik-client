package sadrik.modules.impl.movement;

import antidaunleak.api.annotation.Native;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.KeyEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.BindSetting;
import sadrik.modules.module.setting.implement.SliderSettings;
import sadrik.util.Instance;
import sadrik.util.sounds.SoundManager;

public class ElytraTarget extends ModuleStructure {

    public static ElytraTarget getInstance() {
        return Instance.get(ElytraTarget.class);
    }

    public SliderSettings elytraFindRange = new SliderSettings("Дистанция наводки", "Дальность поиска цели во время полета на элитре")
            .setValue(32).range(6F, 64F);

    public SliderSettings elytraForward = new SliderSettings("Значение перегона", "заебался")
            .setValue(3).range(0F, 6F);

    final BindSetting forward = new BindSetting("Кнопка вкл/выкл перегона", "");

    public static boolean shouldElytraTarget = false;

    public ElytraTarget() {
        super("ElytraTarget", "Elytra Target", ModuleCategory.MOVEMENT);
        settings(elytraFindRange, elytraForward, forward);
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    private void onEventKey(KeyEvent e) {
        if (e.isKeyDown(forward.getKey())) {
            shouldElytraTarget = !shouldElytraTarget;
            SoundManager.playSound(shouldElytraTarget ? SoundManager.MODULE_ENABLE : SoundManager.MODULE_DISABLE, 1, 1.0f);
        }
    }
}