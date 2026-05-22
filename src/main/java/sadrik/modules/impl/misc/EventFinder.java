package sadrik.modules.impl.misc;

import antidaunleak.api.annotation.Native;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.PacketEvent;
import sadrik.events.impl.TickEvent;
import sadrik.events.impl.WorldChangeEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.BooleanSetting;
import sadrik.modules.module.setting.implement.ButtonSetting;
import sadrik.modules.module.setting.implement.SelectSetting;
import sadrik.util.string.chat.ChatMessage;
import sadrik.util.timer.StopWatch;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventFinder extends ModuleStructure {

    private static final List<Integer> SOLO_SERVERS = List.of(101, 102, 103, 104, 105, 108, 109, 110, 111, 112, 113, 114);
    private static final List<Integer> DUO_SERVERS = List.of(201, 202, 203, 204, 205, 208, 209, 210, 211, 212, 213, 214);
    private static final List<Integer> TRIO_SERVERS = List.of(303, 304, 305, 306, 307, 310, 311, 312, 313, 314);
    public static final int MIN_VALID_SECONDS = 300;
    private static final Pattern STRIP_COLOR = Pattern.compile("§[0-9a-fk-or]");
    private static final Pattern EVENT_LINE = Pattern.compile("До следующего ивента:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PARTS = Pattern.compile("(\\d+)\\s*мин(?:(?:\\s+(\\d+))?\\s*сек)?");
    private static final long MESSAGE_DELAY_MS = 10_000;

    @Getter
    private static final Map<Integer, EventInfo> results = new LinkedHashMap<>();
    @Getter
    private static boolean scanCompleted = false;

    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class EventInfo {
        long totalSeconds;
        long fetchTime;
    }

    SelectSetting mode = new SelectSetting("Режим", "Тип серверов для поиска ивентов")
            .value("Соло", "Дуо", "Трио")
            .selected("Соло");
    BooleanSetting autoScan = new BooleanSetting("Автоскан", "Автоматический скан при включении")
            .setValue(true);
    BooleanSetting soundAlert = new BooleanSetting("Звук", "Звуковой сигнал при завершении скана")
            .setValue(true);
    BooleanSetting notifyChat = new BooleanSetting("Сообщения", "Оповещать в чат после сканирования")
            .setValue(true);
    ButtonSetting rescanBtn = new ButtonSetting("Сканировать", "Начать сканирование заново")
            .setButtonName("Скан")
            .setRunnable(this::startScan);
    ButtonSetting clearBtn = new ButtonSetting("Очистить", "Очистить результаты скана")
            .setButtonName("Очистить")
            .setRunnable(this::clearResults);

    @NonFinal
    ScanState scanState = ScanState.IDLE;
    @NonFinal
    int currentServerIndex = 0;
    @NonFinal
    int readyTicks = 0;
    @NonFinal
    int responseTimeout = 0;
    StopWatch serverTimer = new StopWatch();
    @NonFinal
    StopWatch messageDelayTimer = new StopWatch();
    @NonFinal
    String pendingMessage = null;

    private List<Integer> getCurrentServerList() {
        if (mode.isSelected("Соло")) return SOLO_SERVERS;
        if (mode.isSelected("Дуо")) return DUO_SERVERS;
        return TRIO_SERVERS;
    }

    private enum ScanState {
        IDLE, SENDING_JOIN, WAITING_JOIN, WAITING_READY, SENDING_CHECK, WAITING_RESPONSE, WAITING_MESSAGE
    }

    public EventFinder() {
        super("EventFinder", "Поиск ивентов на анархиях FunTime", ModuleCategory.MISC);
        settings(mode, autoScan, soundAlert, notifyChat, rescanBtn, clearBtn);
    }

    @Override
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void activate() {
        if (autoScan.isValue()) {
            startScan();
        }
    }

    @Override
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void deactivate() {
        scanState = ScanState.IDLE;
        currentServerIndex = 0;
        readyTicks = 0;
        responseTimeout = 0;
        pendingMessage = null;
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    public void clearResults() {
        results.clear();
        scanCompleted = false;
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    public void startScan() {
        if (scanState != ScanState.IDLE) return;

        if (!state) {
            setState(true);
            if (scanState != ScanState.IDLE) return;
        }

        scanCompleted = false;
        results.clear();
        currentServerIndex = 0;
        pendingMessage = null;
        scanState = ScanState.SENDING_JOIN;
        serverTimer.reset();
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onWorldChange(WorldChangeEvent e) {
        if (scanState == ScanState.WAITING_JOIN) {
            readyTicks = 60;
            scanState = ScanState.WAITING_READY;
        }
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onTick(TickEvent e) {
        if (scanState == ScanState.IDLE) return;

        switch (scanState) {
            case SENDING_JOIN -> {
                if (!serverTimer.finished(100)) break;

                int server = getCurrentServerList().get(currentServerIndex);
                mc.player.networkHandler.sendChatCommand("an" + server);
                scanState = ScanState.WAITING_JOIN;
                serverTimer.reset();
            }
            case WAITING_JOIN -> {
                if (serverTimer.finished(30000)) {
                    skipServer();
                }
            }
            case WAITING_READY -> {
                readyTicks--;
                if (readyTicks <= 0) {
                    mc.player.networkHandler.sendChatCommand("event delay");
                    scanState = ScanState.WAITING_RESPONSE;
                    responseTimeout = 100;
                    serverTimer.reset();
                }
            }
            case WAITING_RESPONSE -> {
                responseTimeout--;
                if (responseTimeout <= 0) {
                    skipServer();
                }
            }
            case WAITING_MESSAGE -> {
                if (messageDelayTimer.finished(MESSAGE_DELAY_MS)) {
                    if (pendingMessage != null) {
                        ChatMessage.brandmessage(pendingMessage);
                    }
                    pendingMessage = null;
                    scanState = ScanState.IDLE;
                    setState(false);
                }
            }
        }
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onPacket(PacketEvent e) {
        if (scanState != ScanState.WAITING_RESPONSE) return;
        if (e.getType() != PacketEvent.Type.RECEIVE) return;
        if (!(e.getPacket() instanceof GameMessageS2CPacket msg)) return;

        String raw = msg.content().getString();
        String clean = STRIP_COLOR.matcher(raw).replaceAll("");

        Matcher m = EVENT_LINE.matcher(clean);
        if (!m.find()) return;

        String timeStr = m.group(1);
        Matcher tm = TIME_PARTS.matcher(timeStr);
        if (!tm.find()) {
            skipServer();
            return;
        }

        int minutes = Integer.parseInt(tm.group(1));
        int seconds = tm.group(2) != null ? Integer.parseInt(tm.group(2)) : 0;
        long totalSeconds = minutes * 60L + seconds;

        int server = getCurrentServerList().get(currentServerIndex);
        results.put(server, new EventInfo(totalSeconds, System.currentTimeMillis()));

        nextServer();
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    private void nextServer() {
        currentServerIndex++;
        List<Integer> servers = getCurrentServerList();
        if (currentServerIndex >= servers.size()) {
            scanCompleted = true;

            long totalResponses = results.values().stream()
                .filter(info -> info.getTotalSeconds() >= 0)
                .count();

            if (totalResponses < 1) {
                pendingMessage = "§cИвенты не найдены";
            } else if (notifyChat.isValue()) {
                long now = System.currentTimeMillis();
                long minRemaining = Long.MAX_VALUE;
                int bestServer = -1;

                for (Map.Entry<Integer, EventInfo> entry : results.entrySet()) {
                    int server = entry.getKey();
                    EventInfo info = entry.getValue();

                    if (info.getTotalSeconds() < 0) continue;

                    long remaining = info.getTotalSeconds() - (now - info.getFetchTime()) / 1000;
                    if (remaining > 0 && remaining < minRemaining) {
                        minRemaining = remaining;
                        bestServer = server;
                    }
                }

                if (bestServer != -1) {
                    pendingMessage = "До ближайшего ивента: " + minRemaining + " сек (an" + bestServer + ")";
                }
            }

            messageDelayTimer.reset();
            scanState = ScanState.WAITING_MESSAGE;
        } else {
            scanState = ScanState.SENDING_JOIN;
            serverTimer.setMs(500);
        }
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    private void skipServer() {
        results.put(getCurrentServerList().get(currentServerIndex), new EventInfo(-1, 0));
        nextServer();
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    private long countValidResults() {
        long now = System.currentTimeMillis();
        return results.values().stream()
                .filter(info -> info.totalSeconds >= 0 && info.totalSeconds <= MIN_VALID_SECONDS)
                .filter(info -> {
                    long remaining = info.totalSeconds - (now - info.fetchTime) / 1000;
                    return remaining > 0;
                })
                .count();
    }

    public static String formatTime(long totalSeconds) {
        if (totalSeconds < 0) return "--:--";
        long min = totalSeconds / 60;
        long sec = totalSeconds % 60;
        return String.format("%02d:%02d", min, sec);
    }
}
