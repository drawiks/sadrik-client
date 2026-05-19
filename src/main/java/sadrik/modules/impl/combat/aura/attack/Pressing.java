package sadrik.modules.impl.combat.aura.attack;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.item.ItemStack;
import sadrik.IMinecraft;
import sadrik.modules.impl.combat.SyncTPS;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Pressing implements IMinecraft {

    long lastClickTime = System.currentTimeMillis();

    public boolean isCooldownComplete(int ticks) {
        if (mc.player == null)
            return false;

        if (isHoldingMace()) {
            return lastClickPassed() >= (long)(50 * SyncTPS.tickAdjustmentFactor);
        }

        float cooldownProgress = mc.player.getAttackCooldownProgress(ticks);
        float threshold = Math.min(1.0F, 0.95F * SyncTPS.tickAdjustmentFactor);
        return cooldownProgress >= threshold;
    }

    public boolean isMaceFastAttack() {
        return isHoldingMace() && lastClickPassed() >= (long)(50 * SyncTPS.tickAdjustmentFactor);
    }

    public long lastClickPassed() {
        return System.currentTimeMillis() - lastClickTime;
    }

    public void recalculate() {
        lastClickTime = System.currentTimeMillis();
    }

    public boolean isHoldingMace() {
        if (mc.player == null)
            return false;
        ItemStack mainHand = mc.player.getMainHandStack();
        return mainHand.getItem().getTranslationKey().toLowerCase().contains("mace");
    }

    public boolean isWeapon() {
        if (mc.player == null)
            return false;
        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isEmpty())
            return false;
        String itemName = mainHand.getItem().getTranslationKey().toLowerCase();
        return itemName.contains("sword") || itemName.contains("axe") || itemName.contains("trident");
    }
}