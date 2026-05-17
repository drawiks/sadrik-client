package sadrik.util.discord;

import dev.firstdark.rpc.DiscordRpc;
import dev.firstdark.rpc.enums.ErrorCode;
import dev.firstdark.rpc.exceptions.PipeAccessDenied;
import dev.firstdark.rpc.exceptions.UnsupportedOsType;
import dev.firstdark.rpc.handlers.DiscordEventHandler;
import dev.firstdark.rpc.models.DiscordRichPresence;
import dev.firstdark.rpc.models.User;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class DiscordRPCManager {

    static final DiscordRPCManager INSTANCE = new DiscordRPCManager();
    static final Logger LOG = LoggerFactory.getLogger(DiscordRPCManager.class);

    DiscordRpc rpc;
    boolean running;

    public static DiscordRPCManager getInstance() {
        return INSTANCE;
    }

    public void start(String applicationId) {
        if (running) return;
        running = true;

        rpc = new DiscordRpc();
        rpc.setDebugMode(false);

        DiscordEventHandler handler = new DiscordEventHandler() {
            @Override
            public void ready(User user) {}

            @Override
            public void disconnected(ErrorCode errorCode, String message) {}

            @Override
            public void errored(ErrorCode errorCode, String message) {}

            @Override
            public void joinGame(String joinSecret) {}

            @Override
            public void spectateGame(String spectateSecret) {}

            @Override
            public void joinRequest(dev.firstdark.rpc.models.DiscordJoinRequest joinRequest) {}
        };

        try {
            rpc.init(applicationId, handler, false);
        } catch (UnsupportedOsType | PipeAccessDenied e) {
            LOG.error("Failed to initialize Discord RPC", e);
            running = false;
            rpc = null;
        }
    }

    public void updatePresence(DiscordRichPresence presence) {
        if (running && rpc != null) {
            rpc.updatePresence(presence);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void shutdown() {
        if (!running || rpc == null) return;
        running = false;
        rpc.shutdown();
        rpc = null;
    }
}
