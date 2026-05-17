package sadrik.modules.impl.misc;

import antidaunleak.api.annotation.Native;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.math.BlockPos;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.PacketEvent;
import sadrik.events.impl.TickEvent;
import sadrik.events.impl.WorldChangeEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.BooleanSetting;
import sadrik.modules.module.setting.implement.ButtonSetting;
import sadrik.modules.module.setting.implement.SliderSettings;
import sadrik.util.repository.way.WayRepository;
import sadrik.util.sounds.SoundManager;
import sadrik.util.string.chat.ChatMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventWayPoint extends ModuleStructure {

    private static final Pattern STRIP_COLOR = Pattern.compile("§[0-9a-fk-or]");
    private static final Pattern EVENT_NAME = Pattern.compile("\\[([^]]+)]");
    private static final Pattern COORDS = Pattern.compile("Появился на координатах\\s*\\[?\\s*(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*\\]?");

    SliderSettings removeMinutes = new SliderSettings("Время удаления", "Минуты до автоудаления метки")
            .range(1, 30).setValue(5);
    BooleanSetting soundAlert = new BooleanSetting("Звук", "Звук при создании метки")
            .setValue(true);
    ButtonSetting clearBtn = new ButtonSetting("Очистить", "Удалить все метки ивентов")
            .setButtonName("Очистить")
            .setRunnable(this::clearEventWaypoints);

    @NonFinal
    ConcurrentHashMap<String, Long> waypointTimers = new ConcurrentHashMap<>();

    public EventWayPoint() {
        super("EventWayPoint", "Автосоздание waypoint из ивентов FunTime", ModuleCategory.MISC);
        settings(removeMinutes, soundAlert, clearBtn);
    }

    @Override
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void deactivate() {
        waypointTimers.clear();
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    private void clearEventWaypoints() {
        WayRepository repo = WayRepository.getInstance();
        for (String name : waypointTimers.keySet()) {
            repo.deleteWayAndSave(name);
        }
        waypointTimers.clear();
        ChatMessage.brandmessage("§aВсе метки ивентов удалены");
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onPacket(PacketEvent e) {
        if (e.getType() != PacketEvent.Type.RECEIVE) return;
        if (!(e.getPacket() instanceof GameMessageS2CPacket msg)) return;

        String raw = msg.content().getString();
        String clean = STRIP_COLOR.matcher(raw).replaceAll("");

        if (clean.contains("Появится уже через 3 минуты")) return;

        if (!clean.contains("Появился на координатах")) return;

        Matcher nm = EVENT_NAME.matcher(clean);
        if (!nm.find()) return;
        String eventName = nm.group(1).trim();

        Matcher cm = COORDS.matcher(clean);
        if (!cm.find()) return;
        BlockPos pos = new BlockPos(
                Integer.parseInt(cm.group(1)),
                Integer.parseInt(cm.group(2)),
                Integer.parseInt(cm.group(3))
        );

        WayRepository repo = WayRepository.getInstance();
        String server = repo.getCurrentServer();
        if (server.isEmpty()) return;

        if (repo.hasWay(eventName)) {
            repo.deleteWayAndSave(eventName);
        }
        repo.addWayAndSave(eventName, pos, server);
        waypointTimers.put(eventName, System.currentTimeMillis());

        ChatMessage.brandmessage("§aМетка §f" + eventName + " §aсоздана: §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ());

        if (soundAlert.isValue()) {
            SoundManager.playSoundDirect(SoundManager.ON, 1, 1);
        }
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onTick(TickEvent e) {
        if (waypointTimers.isEmpty()) return;

        long now = System.currentTimeMillis();
        long timeout = removeMinutes.getInt() * 60 * 1000L;
        WayRepository repo = WayRepository.getInstance();

        waypointTimers.entrySet().removeIf(entry -> {
            if (now - entry.getValue() >= timeout) {
                repo.deleteWayAndSave(entry.getKey());
                return true;
            }
            return false;
        });
    }

    @EventHandler
    public void onWorldChange(WorldChangeEvent e) {
        waypointTimers.clear();
    }
}
