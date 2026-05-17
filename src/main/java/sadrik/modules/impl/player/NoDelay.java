package sadrik.modules.impl.player;

import antidaunleak.api.annotation.Native;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.TickEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.MultiSelectSetting;

public class NoDelay extends ModuleStructure {

    public MultiSelectSetting ignoreSetting = new MultiSelectSetting("Тип", "")
            .value("Прыжок", "Правый клик", "Задержка ломания").selected("Прыжок");

    public NoDelay() {
        super("NoDelay", "No Delay", ModuleCategory.PLAYER);
        settings(ignoreSetting);
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onTick(TickEvent e) {
        if (mc.player == null) return;
        if (ignoreSetting.isSelected("Задержка ломания")) mc.interactionManager.blockBreakingCooldown = 0;
        if (ignoreSetting.isSelected("Прыжок")) mc.player.jumpingCooldown = 0;
        if (ignoreSetting.isSelected("Правый клик")) mc.itemUseCooldown = 0;
    }
}