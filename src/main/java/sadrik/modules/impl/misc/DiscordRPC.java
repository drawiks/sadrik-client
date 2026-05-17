package sadrik.modules.impl.misc;

import dev.firstdark.rpc.enums.ActivityType;
import dev.firstdark.rpc.models.DiscordRichPresence;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.GameLeftEvent;
import sadrik.events.impl.TickEvent;
import sadrik.events.impl.WorldLoadEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.util.discord.DiscordRPCManager;
import sadrik.util.timer.TimerUtil;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class DiscordRPC extends ModuleStructure {

    static final String APPLICATION_ID = "1504750386928685211";

    final DiscordRPCManager rpc = DiscordRPCManager.getInstance();
    final TimerUtil updateTimer = TimerUtil.create();
    long startTimestamp;
    boolean inGame;
    boolean rpcReady;

    public DiscordRPC() {
        super("DiscordRPC", "Discord Rich Presence", ModuleCategory.MISC);
    }

    @Override
    public void activate() {
        startRpc();
        if (mc.world != null && mc.player != null) {
            inGame = true;
            startTimestamp = System.currentTimeMillis() / 1000L;
            refresh();
        } else {
            inGame = false;
            startTimestamp = 0L;
            updatePresence("In Main Menu", "Sadrik Client");
        }
    }

    void startRpc() {
        if (rpcReady) return;
        rpcReady = true;
        rpc.start(APPLICATION_ID);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        startRpc();
        inGame = true;
        startTimestamp = System.currentTimeMillis() / 1000L;
        refresh();
    }

    @EventHandler
    public void onGameLeft(GameLeftEvent e) {
        inGame = false;
        startTimestamp = 0L;
        updatePresence("In Main Menu", "Sadrik Client");
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (!rpcReady) {
            startRpc();
            if (mc.world != null && mc.player != null) {
                inGame = true;
                startTimestamp = System.currentTimeMillis() / 1000L;
                refresh();
            } else {
                inGame = false;
                startTimestamp = 0L;
                updatePresence("In Main Menu", "Sadrik Client");
            }
            return;
        }
        if (!updateTimer.isReached(5000L)) return;
        updateTimer.resetCounter();
        if (inGame && mc.world != null && mc.player != null) {
            refresh();
        }
    }

    void refresh() {
        String state;

        if (mc.isIntegratedServerRunning()) {
            state = "Singleplayer";
        } else if (mc.getCurrentServerEntry() != null) {
            state = "Playing on " + mc.getCurrentServerEntry().address;
        } else {
            state = "Playing";
        }

        updatePresence(state, "");
    }

    void updatePresence(String state, String details) {
        var builder = DiscordRichPresence.builder()
                .state(state)
                .startTimestamp(startTimestamp > 0 ? startTimestamp : 0)
                .largeImageKey("sadrik")
                .largeImageText("Sadrik Client")
                .activityType(ActivityType.PLAYING);
        if (!details.isEmpty()) {
            builder.details(details);
        }
        rpc.updatePresence(builder.build());
    }

    @Override
    public void deactivate() {
        rpc.shutdown();
        inGame = false;
        rpcReady = false;
    }
}
