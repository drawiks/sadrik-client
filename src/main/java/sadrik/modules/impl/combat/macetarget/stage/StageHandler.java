package sadrik.modules.impl.combat.macetarget.stage;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import sadrik.modules.impl.combat.macetarget.armor.ArmorSwapHandler;
import sadrik.modules.impl.combat.macetarget.armor.FireworkHandler;
import sadrik.modules.impl.combat.macetarget.attack.AttackHandler;
import sadrik.modules.impl.combat.macetarget.state.MaceState.Stage;
import sadrik.util.inventory.InventoryUtils;
import sadrik.util.timer.StopWatch;

@Getter
public class StageHandler {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final ArmorSwapHandler armorSwapHandler;
    private final FireworkHandler fireworkHandler;
    private final AttackHandler attackHandler;
    private final StopWatch fireworkTimer;

    @Setter
    private Stage stage = Stage.PREPARE;
    @Setter
    private boolean silentMode = true;
    @Setter
    private boolean reallyWorldMode = false;
    @Setter
    private float height = 30.0f;

    public StageHandler(ArmorSwapHandler armorSwapHandler, FireworkHandler fireworkHandler, 
                        AttackHandler attackHandler, StopWatch fireworkTimer) {
        this.armorSwapHandler = armorSwapHandler;
        this.fireworkHandler = fireworkHandler;
        this.attackHandler = attackHandler;
        this.fireworkTimer = fireworkTimer;
    }

    public void handlePrepare(boolean hasElytra) {
        if (!hasElytra) {
            int slot = InventoryUtils.findElytraSlot();
            if (slot != -1) {
                armorSwapHandler.startSwap(slot, silentMode);
            }
            return;
        }
        stage = Stage.FLYING_UP;
        fireworkTimer.reset();
    }

    public void handleFlyingUp(LivingEntity target, boolean hasElytra) {
        if (!hasElytra) {
            stage = Stage.PREPARE;
            return;
        }

        if (mc.player.isGliding() && fireworkTimer.finished(300)) {
            fireworkHandler.useFirework(silentMode);
            fireworkTimer.reset();
        }

        if (mc.player.getY() - target.getY() >= height) {
            stage = Stage.TARGETTING;
        }
    }

    public void handleTargetting(LivingEntity target) {
        float swapDistance = 12.0f;

        if (InventoryUtils.hasElytra() && mc.player.distanceTo(target) < swapDistance
                && !armorSwapHandler.isActive()) {
            int slot = InventoryUtils.findChestArmorSlot();
            if (slot != -1) {
                armorSwapHandler.startSwap(slot, silentMode);
            }
        }

        if (mc.player.distanceTo(target) < 16.0f) {
            stage = Stage.ATTACKING;
        }
    }

    public void handleAttacking(LivingEntity target, boolean hasElytra) {
        if (hasElytra && !armorSwapHandler.isActive()) {
            int slot = InventoryUtils.findChestArmorSlot();
            if (slot != -1) {
                armorSwapHandler.startSwap(slot, silentMode);
            }
            return;
        }

        if (!hasElytra && !armorSwapHandler.isActive() && mc.player.distanceTo(target) < 5) {
            attackHandler.setPendingAttack(true);

            if (reallyWorldMode) {
                attackHandler.setShouldDisableAfterAttack(true);
            } else {
                stage = Stage.FLYING_UP;
                fireworkTimer.reset();
            }
        }
    }

    public void reset() {
        stage = Stage.PREPARE;
    }
}