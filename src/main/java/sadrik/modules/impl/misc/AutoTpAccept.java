package sadrik.modules.impl.misc;

import antidaunleak.api.annotation.Native;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.PacketEvent;
import sadrik.events.impl.TickEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.BooleanSetting;
import sadrik.util.network.Network;
import sadrik.util.repository.friend.FriendUtils;

import java.util.Arrays;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoTpAccept extends ModuleStructure {
    private final String[] teleportMessages = new String[]{
            "has requested teleport",
            "просит телепортироваться",
            "хочет телепортироваться к вам",
            "просит к вам телепортироваться"
    };
    private boolean canAccept;
    private String senderName;

    private final BooleanSetting friendSetting = new BooleanSetting("Только друзья", "Будет принимать запросы только от друзей").setValue(true);

    public AutoTpAccept() {
        super("AutoTpAccept", "Auto Tp Accept", ModuleCategory.MISC);
        settings(friendSetting);
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onPacket(PacketEvent e) {
        if (e.getType() != PacketEvent.Type.RECEIVE) return;
        if (e.getPacket() instanceof GameMessageS2CPacket m) {
            String message = m.content().getString();
            if (!isTeleportMessage(message)) return;
            String nick = extractSenderName(message);
            if (nick == null) return;
            boolean valid = !friendSetting.isValue() || FriendUtils.isFriend(nick);
            if (valid) {
                canAccept = true;
                senderName = nick;
            }
        }
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onTick(TickEvent e) {
        if (!Network.isPvp() && canAccept) {
            mc.player.networkHandler.sendChatCommand(friendSetting.isValue() ? "tpaccept " + senderName : "tpaccept");
            canAccept = false;
            senderName = null;
        }
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    private boolean isTeleportMessage(String message) {
        String lower = message.toLowerCase();
        return Arrays.stream(teleportMessages).anyMatch(lower::contains);
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    private String extractSenderName(String message) {
        String lower = message.toLowerCase();
        for (String trigger : teleportMessages) {
            int idx = lower.indexOf(trigger);
            if (idx == -1) continue;
            String before = message.substring(0, idx).trim();
            String[] parts = before.split("\\s+");
            if (parts.length == 0) continue;
            String candidate = parts[parts.length - 1].replaceAll("[^\\w]", "");
            if (candidate.length() >= 3 && candidate.length() <= 16) {
                return candidate;
            }
        }
        return null;
    }
}
