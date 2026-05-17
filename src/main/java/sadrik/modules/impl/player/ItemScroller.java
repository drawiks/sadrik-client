package sadrik.modules.impl.player;

import antidaunleak.api.annotation.Native;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.HandledScreenClickEvent;
import sadrik.events.impl.HandledScreenDragEvent;
import sadrik.events.impl.HandledScreenReleaseEvent;
import sadrik.events.impl.HandledScreenScrollEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.BooleanSetting;
import sadrik.modules.module.setting.implement.SelectSetting;
import sadrik.modules.module.setting.implement.SliderSettings;
import sadrik.util.inventory.InventoryUtils;
import sadrik.util.string.PlayerInteractionHelper;
import sadrik.util.timer.StopWatch;

import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ItemScroller extends ModuleStructure {

    // -- Настройки --
    BooleanSetting lmbWithoutItem = new BooleanSetting("ЛКМ без предмета", "Shift + ЛКМ драг: перемещать предметы").setValue(true);
    BooleanSetting lmbWithItem = new BooleanSetting("ЛКМ с предметом", "ЛКМ драг: собирать одинаковые предметы").setValue(true);
    BooleanSetting wheelTweak = new BooleanSetting("Колесо мыши", "Скролл: перемещать предметы между инвентарями").setValue(true);
    SliderSettings delay = new SliderSettings("Задержка", "Задержка между кликами (мс)").setValue(25).range(0, 200);
    SelectSetting wheelDirection = new SelectSetting("Направление колеса", "Направление скролла")
            .value("Normal", "Inverted").selected("Normal");
    SelectSetting wheelSearchOrder = new SelectSetting("Порядок поиска", "Откуда искать предметы")
            .value("Last to First", "First to Last").selected("Last to First");

    // -- Состояние драга --
    boolean dragActive = false;
    Slot previousSlot = null;
    final StopWatch stopWatch = new StopWatch();

    public ItemScroller() {
        super("ItemScroller", "Item Scroller", ModuleCategory.PLAYER);
        settings(lmbWithoutItem, lmbWithItem, wheelTweak, delay, wheelDirection, wheelSearchOrder);
    }

    // ============================================================
    //  CLICK — начало драга
    // ============================================================
    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onMouseClick(HandledScreenClickEvent e) {
        if (mc.player == null) return;

        if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            dragActive = true;
            previousSlot = null;
        }
    }

    // ============================================================
    //  DRAG — вход в новый слот = клик
    // ============================================================
    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onMouseDrag(HandledScreenDragEvent e) {
        if (mc.player == null || !dragActive) return;

        Slot slot = e.getHoveredSlot();
        if (slot == null || slot == previousSlot) return;
        if (!stopWatch.every(delay.getValue())) return;

        boolean shiftHeld = PlayerInteractionHelper.isKey(mc.options.sneakKey);
        ItemStack cursorStack = mc.player.currentScreenHandler.getCursorStack();

        handleDrag(slot, cursorStack, shiftHeld);

        previousSlot = slot;
    }

    // ============================================================
    //  RELEASE — сброс драга
    // ============================================================
    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onMouseRelease(HandledScreenReleaseEvent e) {
        if (e.getButton() == 0) {
            dragActive = false;
            previousSlot = null;
        }
    }

    // ============================================================
    //  SCROLL — Wheel Tweak
    // ============================================================
    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onMouseScroll(HandledScreenScrollEvent e) {
        if (mc.player == null || !wheelTweak.isValue()) return;

        Slot slot = e.getHoveredSlot();
        if (slot == null) return;

        boolean isInverted = wheelDirection.isSelected("Inverted");
        int direction = isInverted ? -1 : 1;
        int delta = (int) Math.round(e.getVertical() * direction);

        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;

        if (delta > 0) {
            pushItems(slot);
            e.cancel();
        } else if (delta < 0) {
            pullItems(slot);
            e.cancel();
        }
    }

    // ============================================================
    //  Drag: пустой курсор → QUICK_MOVE, с предметом → merge
    // ============================================================
    @Native(type = Native.Type.VMProtectBeginUltra)
    private void handleDrag(Slot slot, ItemStack cursorStack, boolean shiftHeld) {
        if (!slot.hasStack()) return;

        if (cursorStack.isEmpty()) {
            if (!lmbWithoutItem.isValue() || !shiftHeld) return;
            InventoryUtils.click(slot.id, 0, SlotActionType.QUICK_MOVE);

        } else {
            if (!lmbWithItem.isValue()) return;

            ItemStack slotStack = slot.getStack();
            if (!areCompatible(cursorStack, slotStack)) return;

            if (shiftHeld) {
                InventoryUtils.click(slot.id, 0, SlotActionType.QUICK_MOVE);
            } else {
                int maxFit = cursorStack.getMaxCount();
                if (cursorStack.getCount() + slotStack.getCount() > maxFit) return;

                InventoryUtils.click(slot.id, 0, SlotActionType.PICKUP);

                InventoryUtils.click(slot.id, 0, SlotActionType.PICKUP);
            }
        }
    }

    // ============================================================
    //  Wheel Push: вытолкнуть предметы из слота в другой инвентарь
    // ============================================================
    @Native(type = Native.Type.VMProtectBeginUltra)
    private void pushItems(Slot sourceSlot) {
        if (!sourceSlot.hasStack()) return;

        List<Slot> targets = findTargetSlots(sourceSlot, true);
        if (targets.isEmpty()) return;

        InventoryUtils.click(sourceSlot.id, 0, SlotActionType.PICKUP);

        ItemStack cursorStack = mc.player.currentScreenHandler.getCursorStack();
        if (cursorStack.isEmpty()) return;

        int remaining = cursorStack.getCount();
        for (Slot target : targets) {
            if (remaining <= 0) break;
            ItemStack targetStack = target.getStack();
            int maxFit = targetStack.isEmpty() ? cursorStack.getMaxCount() : targetStack.getMaxCount();
            int fit = maxFit - targetStack.getCount();
            int place = Math.min(remaining, fit);
            for (int i = 0; i < place; i++) {
                InventoryUtils.click(target.id, 1, SlotActionType.PICKUP);
            }
            remaining -= place;
        }

        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            InventoryUtils.click(sourceSlot.id, 0, SlotActionType.PICKUP);
        }
    }

    // ============================================================
    //  Wheel Pull: втянуть предметы из другого инвентаря в слот
    // ============================================================
    @Native(type = Native.Type.VMProtectBeginUltra)
    private void pullItems(Slot targetSlot) {
        List<Slot> sources = findSourceSlots(targetSlot);
        if (sources.isEmpty()) return;

        Slot sourceSlot = sources.getFirst();
        InventoryUtils.click(sourceSlot.id, 0, SlotActionType.PICKUP);

        ItemStack cursorStack = mc.player.currentScreenHandler.getCursorStack();
        if (cursorStack.isEmpty()) return;

        ItemStack targetStack = targetSlot.getStack();
        int maxFit = targetStack.isEmpty() ? cursorStack.getMaxCount() : targetStack.getMaxCount();
        int fit = maxFit - targetStack.getCount();
        int place = Math.min(cursorStack.getCount(), fit);

        for (int i = 0; i < place; i++) {
            InventoryUtils.click(targetSlot.id, 1, SlotActionType.PICKUP);
        }

        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            InventoryUtils.click(sourceSlot.id, 0, SlotActionType.PICKUP);
        }
    }

    // ============================================================
    //  Поиск слотов для push (в другом инвентаре)
    // ============================================================
    @Native(type = Native.Type.VMProtectBeginMutation)
    private List<Slot> findTargetSlots(Slot sourceSlot, boolean allowEmpty) {
        List<Slot> result = new ArrayList<>();
        if (mc.player == null) return result;

        boolean sourceInPlayer = sourceSlot.inventory == mc.player.getInventory();
        var slots = mc.player.currentScreenHandler.slots;
        ItemStack sourceStack = sourceSlot.getStack();

        boolean lastToFirst = wheelSearchOrder.isSelected("Last to First");
        int start = lastToFirst ? slots.size() - 1 : 0;
        int end = lastToFirst ? -1 : slots.size();
        int step = lastToFirst ? -1 : 1;

        for (int i = start; i != end; i += step) {
            Slot slot = slots.get(i);
            boolean slotInPlayer = slot.inventory == mc.player.getInventory();
            if (slotInPlayer == sourceInPlayer) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                if (allowEmpty) {
                    result.add(slot);
                }
            } else if (areCompatible(sourceStack, stack) && stack.getCount() < stack.getMaxCount()) {
                result.add(slot);
            }
        }

        return result;
    }

    // ============================================================
    //  Поиск слотов для pull (в другом инвентаре с таким же предметом)
    // ============================================================
    @Native(type = Native.Type.VMProtectBeginMutation)
    private List<Slot> findSourceSlots(Slot targetSlot) {
        List<Slot> result = new ArrayList<>();
        if (mc.player == null) return result;

        boolean targetInPlayer = targetSlot.inventory == mc.player.getInventory();
        var slots = mc.player.currentScreenHandler.slots;
        ItemStack targetStack = targetSlot.getStack();

        boolean lastToFirst = wheelSearchOrder.isSelected("Last to First");
        int start = lastToFirst ? slots.size() - 1 : 0;
        int end = lastToFirst ? -1 : slots.size();
        int step = lastToFirst ? -1 : 1;

        for (int i = start; i != end; i += step) {
            Slot slot = slots.get(i);
            boolean slotInPlayer = slot.inventory == mc.player.getInventory();
            if (slotInPlayer == targetInPlayer) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            if (areCompatible(targetStack, stack)) {
                result.add(slot);
            }
        }

        return result;
    }

    // ============================================================
    //  Проверка совместимости стеков
    // ============================================================
    @Native(type = Native.Type.VMProtectBeginMutation)
    private boolean areCompatible(ItemStack a, ItemStack b) {
        return a.isEmpty() || b.isEmpty() || a.getItem() == b.getItem();
    }
}
