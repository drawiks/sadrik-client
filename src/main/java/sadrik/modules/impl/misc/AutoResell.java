package sadrik.modules.impl.misc;

import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.PacketEvent;
import sadrik.events.impl.TickEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.BooleanSetting;
import sadrik.util.string.chat.ChatMessage;
import sadrik.util.timer.TimerUtil;

import static sadrik.IMinecraft.mc;

public class AutoResell extends ModuleStructure {

    private static AutoResell instance;

    private final BooleanSetting messages = new BooleanSetting("Сообщения", "Показывать уведомления в чате").setValue(true);

    private final TimerUtil intervalTimer = TimerUtil.create();
    private final TimerUtil actionTimer = TimerUtil.create();

    private enum Phase {
        IDLE, SEND_AH, CLICK_47, WAIT, CLICK_53, CLOSE
    }

    private Phase phase = Phase.IDLE;

    public AutoResell() {
        super("AutoResell", "Auto Resell", ModuleCategory.MISC);
        settings(messages);
        instance = this;
    }

    public static AutoResell getInstance() {
        return instance;
    }

    @Override
    public void activate() {
        super.activate();
        reset();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        reset();
    }

    private void reset() {
        phase = Phase.IDLE;
        intervalTimer.resetCounter();
        actionTimer.resetCounter();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        switch (phase) {
            case IDLE -> {
                if (intervalTimer.hasTimeElapsed(60000)) {
                    intervalTimer.resetCounter();
                    mc.player.networkHandler.sendChatCommand("ah");
                    phase = Phase.SEND_AH;
                    actionTimer.resetCounter();
                }
            }

            case SEND_AH -> {
                if (actionTimer.hasTimeElapsed(500)) {
                    actionTimer.resetCounter();
                    phase = Phase.CLICK_47;
                }
            }

            case CLICK_47 -> {
                if (mc.currentScreen != null) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 46, 0, SlotActionType.PICKUP, mc.player);
                }
                phase = Phase.WAIT;
                actionTimer.resetCounter();
            }

            case WAIT -> {
                if (actionTimer.hasTimeElapsed(500)) {
                    phase = Phase.CLICK_53;
                    actionTimer.resetCounter();
                }
            }

            case CLICK_53 -> {
                if (mc.currentScreen != null) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 52, 0, SlotActionType.PICKUP, mc.player);
                }
                phase = Phase.CLOSE;
                actionTimer.resetCounter();
            }

            case CLOSE -> {
                if (actionTimer.hasTimeElapsed(300)) {
                    mc.player.closeHandledScreen();
                    phase = Phase.IDLE;
                    sendSuccess("Предметы перевыставлены");
                }
            }
        }
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (e.getType() != PacketEvent.Type.RECEIVE) return;
        if (!(e.getPacket() instanceof GameMessageS2CPacket msg)) return;

        String text = msg.content().getString().toLowerCase();
        if (text.contains("Предметы успешно перевыставлены")) {
            sendSuccess("Предметы перевыставлены");
        }
    }

    private void sendMessage(String msg) {
        if (messages.isValue()) {
            ChatMessage.autoresellmessage(msg);
        }
    }

    private void sendSuccess(String msg) {
        if (messages.isValue()) {
            ChatMessage.autoresellmessageSuccess(msg);
        }
    }
}
