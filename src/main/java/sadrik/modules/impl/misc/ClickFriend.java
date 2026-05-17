package sadrik.modules.impl.misc;

import antidaunleak.api.annotation.Native;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.KeyEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.BindSetting;
import sadrik.screens.clickgui.impl.settingsrender.BindComponent;
import sadrik.util.repository.friend.FriendUtils;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClickFriend extends ModuleStructure {

    BindSetting friendBind = new BindSetting("Добавить друга", "Добавить/удалить друга");

    public ClickFriend() {
        super("ClickFriend", "Click Friend", ModuleCategory.MISC);
        settings(friendBind);
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onKey(KeyEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (e.action() != 1) return;
        if (!matchesBind(e)) return;
        if (mc.crosshairTarget instanceof EntityHitResult result && result.getEntity() instanceof PlayerEntity player) {
            if (FriendUtils.isFriend(player)) FriendUtils.removeFriend(player);
            else FriendUtils.addFriend(player);
        }
    }

    private boolean matchesBind(KeyEvent e) {
        int bindKey = friendBind.getKey();
        int bindType = friendBind.getType();

        if (bindKey == GLFW.GLFW_KEY_UNKNOWN || bindKey == -1) return false;

        if (bindType == 2) {
            if (bindKey == BindComponent.MIDDLE_MOUSE_BIND
                    && e.type() == InputUtil.Type.MOUSE
                    && e.key() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                return true;
            }
        } else if (bindType == 0 && e.type() == InputUtil.Type.MOUSE) {
            return e.key() == bindKey;
        } else if (bindType == 1 && e.type() == InputUtil.Type.KEYSYM) {
            return e.key() == bindKey;
        }
        return false;
    }
}