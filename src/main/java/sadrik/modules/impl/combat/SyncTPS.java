package sadrik.modules.impl.combat;

import sadrik.events.api.EventHandler;
import sadrik.events.impl.PacketEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.util.Instance;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.math.MathHelper;

public class SyncTPS extends ModuleStructure {

    public static float TPS = 20.0F;
    public static float adjustTicks = 0.0F;
    public static float tickAdjustmentFactor = 1.0F;

    private long timestamp;
    private float emaTPS = 20.0F;
    private static final float SMOOTHING_FACTOR = 0.1F;
    private static final float MAX_TPS = 20.0F;

    public static SyncTPS getInstance() {
        return Instance.get(SyncTPS.class);
    }

    public SyncTPS() {
        super("SyncTPS", "Синхронизирует удары на клиенте и сервере, помогая при серверных лагах",
                ModuleCategory.COMBAT);
    }

    @EventHandler
    private void onPacket(PacketEvent e) {
        if (e.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            long delay = System.nanoTime() - timestamp;
            float rawTPS = MAX_TPS * (1.0E9F / (float) delay);
            float boundedTPS = MathHelper.clamp(rawTPS, 0.0F, MAX_TPS);
            emaTPS += SMOOTHING_FACTOR * (boundedTPS - emaTPS);
            TPS = (float) round(emaTPS);
            adjustTicks = emaTPS - MAX_TPS;
            tickAdjustmentFactor = MAX_TPS / TPS;
            timestamp = System.nanoTime();
        }
    }

    private double round(double input) {
        return Math.round(input * 100.0) / 100.0;
    }

    @Override
    public void activate() {
        timestamp = System.nanoTime();
        emaTPS = 20.0F;
        TPS = 20.0F;
        adjustTicks = 0.0F;
        tickAdjustmentFactor = 1.0F;
    }

    @Override
    public void deactivate() {
        TPS = 20.0F;
        adjustTicks = 0.0F;
        tickAdjustmentFactor = 1.0F;
    }
}
