package sadrik.modules.impl.movement;

import antidaunleak.api.annotation.Native;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Blocks;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.TickEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.SelectSetting;
import sadrik.util.Instance;
import sadrik.util.move.MoveUtil;
import sadrik.util.string.PlayerInteractionHelper;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class NoWeb extends ModuleStructure {
    public static NoWeb getInstance() {
        return Instance.get(NoWeb.class);
    }

    public final SelectSetting webMode = new SelectSetting("Режим", "Выберите режим обхода").value("Grim");

    public NoWeb() {
        super("NoWeb", "No Web", ModuleCategory.MOVEMENT);
        settings(webMode);
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onTick(TickEvent e) {
        if (PlayerInteractionHelper.isPlayerInBlock(Blocks.COBWEB)) {
            double[] speed = MoveUtil.calculateDirection(0.35);
            mc.player.addVelocity(speed[0], 0, speed[1]);
            mc.player.setVelocity(speed[0], mc.options.jumpKey.isPressed() ? 0.65f : mc.options.sneakKey.isPressed() ? -0.65f : 0, speed[1]);
        }
    }
}