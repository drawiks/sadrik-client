package sadrik.modules.impl.misc;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.PacketEvent;
import sadrik.events.impl.TickEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.BooleanSetting;
import sadrik.modules.module.setting.implement.MultiSelectSetting;
import sadrik.modules.module.setting.implement.SliderSettings;
import sadrik.util.timer.TimerUtil;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import sadrik.util.config.impl.autosell.AutoSellConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static sadrik.IMinecraft.mc;

public class AutoSell extends ModuleStructure {

    private static AutoSell instance;

    public final MultiSelectSetting modes = new MultiSelectSetting("Режимы", "Какие предметы продавать");
    public final SliderSettings delayMin = new SliderSettings("Мин. задержка", "ms")
            .range(50, 2000).setValue(200);
    public final SliderSettings delayMax = new SliderSettings("Макс. задержка", "ms")
            .range(50, 2000).setValue(1000);
    public final BooleanSetting messages = new BooleanSetting("Сообщения", "Уведомления в чате").setValue(true);

    private final Map<String, String> itemMap = new HashMap<>();
    private final AutoSellConfig config;

    private final TimerUtil timer = TimerUtil.create();
    private final TimerUtil idleTimer = TimerUtil.create();

    private enum Phase { IDLE, FIND_ITEM, PICKUP_ITEMS, SELECT_SLOT, SELL, WAIT_RESPONSE, COLLECT, SHIFT_HOTBAR, ERROR }

    private enum CollectStep { OPEN_AH, WAIT_AH, CLICK_46, CLICK_0, CLOSE }
    private CollectStep collectStep = CollectStep.OPEN_AH;

    private Phase phase = Phase.IDLE;
    private String currentItemName;
    private String currentItemId;
    private int currentQuantity;
    private int currentPrice;
    private int currentHotbarSlot;
    private int retryCount;

    public AutoSell() {
        super("AutoSell", "Автоматическая продажа предметов на аукционе", ModuleCategory.MISC);
        settings(modes, delayMin, delayMax, messages);
        instance = this;
        this.config = AutoSellConfig.getInstance();
        loadItemsFromConfig();
    }

    public static AutoSell getInstance() {
        return instance;
    }

    public void startSell(String itemName, int quantity, int price) {
        String itemId = itemMap.get(itemName);
        if (itemId == null) {
            message("§cПредмет \"" + itemName + "\" не найден в списке доступных");
            return;
        }
        if (!modes.isSelected(itemName)) {
            message("§cРежим \"" + itemName + "\" не включён в настройках модуля");
            return;
        }
        if (quantity < 1 || quantity > 64) {
            message("§cКоличество должно быть от 1 до 64");
            return;
        }
        if (price <= 0) {
            message("§cЦена должна быть положительным числом");
            return;
        }

        this.currentItemName = itemName;
        this.currentItemId = itemId;
        this.currentQuantity = quantity;
        this.currentPrice = price;
        this.retryCount = 0;
        this.currentHotbarSlot = -1;
        this.phase = Phase.FIND_ITEM;
        timer.resetCounter();

        if (!isState()) setState(true);
        message("§aПродаю §f" + itemName + " §a×" + quantity + " §aза §f$" + price);
    }

    @Override
    public void activate() {
        super.activate();
        if (phase == Phase.IDLE) {
            message("§eИспользуйте .autosell start <предмет> <количество> <цена>");
        }
    }

    @Override
    public void deactivate() {
        super.deactivate();
        phase = Phase.IDLE;
    }

    private void loadItemsFromConfig() {
        Map<String, String> items = config.getItems();
        if (items.isEmpty()) {
            items.put("Чарки", "minecraft:enchanted_golden_apple|");
            items.put("Булыжник", "minecraft:cobblestone|");
            items.put("Камень", "minecraft:stone|");
            items.put("Редстоуновая пыль", "minecraft:redstone|");
            config.save();
        }
        for (Map.Entry<String, String> e : items.entrySet()) {
            itemMap.put(e.getKey(), e.getValue());
            modes.addValue(e.getKey());
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (!isState()) return;

        switch (phase) {
            case IDLE -> {
                if (idleTimer.hasTimeElapsed(15000)) {
                    collectStep = CollectStep.OPEN_AH;
                    phase = Phase.COLLECT;
                    timer.resetCounter();
                }
            }
            case FIND_ITEM -> findItem();
            case PICKUP_ITEMS -> pickupItems();
            case SELECT_SLOT -> selectSlot();
            case SELL -> sellItem();
            case WAIT_RESPONSE -> timeoutCheck();
            case COLLECT -> collect();
            case SHIFT_HOTBAR -> shiftHotbar();
            case ERROR -> errorEnd();
        }
    }

    private long getRandomDelay() {
        int min = delayMin.getInt();
        int max = delayMax.getInt();
        if (min >= max) return min;
        return min + (long) (Math.random() * (max - min + 1));
    }

    public void addHandItem() {
        if (mc.player == null) return;
        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) {
            message("§cВозьмите предмет в руку");
            return;
        }

        String regId = Registries.ITEM.getId(stack.getItem()).toString();

        Text customNameText = stack.get(DataComponentTypes.CUSTOM_NAME);
        String customName = customNameText != null ? customNameText.getString() : "";
        String displayName = stack.getName().getString();

        LoreComponent loreComp = stack.get(DataComponentTypes.LORE);
        String lore = "";
        if (loreComp != null && !loreComp.lines().isEmpty()) {
            lore = loreComp.lines().stream()
                    .map(t -> t.getString())
                    .collect(java.util.stream.Collectors.joining(";;"));
        }

        String itemKey = regId + "|" + customName;
        if (!lore.isEmpty()) itemKey += "||" + lore;

        if (itemMap.containsKey(displayName)) {
            message("§eПредмет §f" + displayName + " §eуже в списке");
            return;
        }

        itemMap.put(displayName, itemKey);
        modes.addValue(displayName);
        config.putItem(displayName, itemKey);
        config.save();
        message("§aПредмет §f" + displayName + " §aдобавлен в список");
    }

    public void removeItem(String name) {
        if (!itemMap.containsKey(name)) {
            message("§cПредмет \"" + name + "\" не найден в списке");
            return;
        }
        itemMap.remove(name);
        modes.removeValue(name);
        config.removeItem(name);
        config.save();
        message("§aПредмет §f" + name + " §aудалён из списка");
    }

    private String getRegistryId(String itemKey) {
        int pipe = itemKey.indexOf('|');
        return pipe == -1 ? itemKey : itemKey.substring(0, pipe);
    }

    private boolean matchesKey(ItemStack stack, String itemKey) {
        String[] parts = itemKey.split("\\|\\|", 2);
        String regAndName = parts[0];
        String lorePart = parts.length > 1 ? parts[1] : "";
        if (lorePart.equals("|")) lorePart = "";

        String[] regParts = regAndName.split("\\|", 2);
        String regId = regParts[0];
        String expectedName = regParts.length > 1 ? regParts[1] : "";

        if (!Registries.ITEM.getId(stack.getItem()).toString().equals(regId)) return false;

        Text stackName = stack.get(DataComponentTypes.CUSTOM_NAME);
        String actualName = stackName != null ? stackName.getString() : "";
        if (!expectedName.equals(actualName)) return false;

        if (!lorePart.isEmpty()) {
            String[] expectedLines = lorePart.split(";;");
            LoreComponent stackLore = stack.get(DataComponentTypes.LORE);
            if (stackLore == null) return false;
            List<Text> actualLines = stackLore.lines();
            if (actualLines.size() != expectedLines.length) return false;
            for (int i = 0; i < expectedLines.length; i++) {
                if (!actualLines.get(i).getString().equals(expectedLines[i])) return false;
            }
        }

        return true;
    }

    private void findItem() {
        if (!timer.hasTimeElapsed(getRandomDelay())) return;

        String regId = getRegistryId(currentItemId);
        Item targetItem = Registries.ITEM.get(Identifier.of(regId));
        if (targetItem == null || targetItem == Items.AIR) {
            message("§cОшибка: предмет не найден в игре");
            phase = Phase.ERROR;
            return;
        }

        int totalFound = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && matchesKey(stack, currentItemId)) {
                totalFound += stack.getCount();
            }
        }

        ItemStack held = mc.player.getMainHandStack();
        if (!held.isEmpty() && matchesKey(held, currentItemId)) {
            phase = Phase.SELL;
            timer.resetCounter();
            return;
        }

        if (totalFound == 0) {
            message("§aВсе предметы \"" + currentItemName + "\" проданы");
            phase = Phase.IDLE;
            idleTimer.resetCounter();
            return;
        }
        if (totalFound < currentQuantity) {
            message("§cНедостаточно: нужно " + currentQuantity + ", есть " + totalFound);
            phase = Phase.ERROR;
            return;
        }

        currentHotbarSlot = findHotbarTargetSlot(targetItem);
        if (currentHotbarSlot == -1) {
            message("§cНет места в хотбаре");
            phase = Phase.ERROR;
            return;
        }

        phase = Phase.PICKUP_ITEMS;
        timer.resetCounter();
    }

    private void pickupItems() {
        if (!timer.hasTimeElapsed(getRandomDelay())) return;

        int remaining = currentQuantity;

        ItemStack hotbarStack = mc.player.getInventory().getStack(currentHotbarSlot);
        if (!hotbarStack.isEmpty() && matchesKey(hotbarStack, currentItemId)) {
            remaining -= hotbarStack.getCount();
            if (remaining <= 0) {
                phase = Phase.SELECT_SLOT;
                timer.resetCounter();
                return;
            }
        }

        boolean moved = false;
        for (int i = 9; i < 36 && remaining > 0; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !matchesKey(stack, currentItemId)) continue;

            int targetScreen = 36 + currentHotbarSlot;
            int syncId = mc.player.currentScreenHandler.syncId;

            if (stack.getCount() <= remaining) {
                mc.interactionManager.clickSlot(syncId, i, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(syncId, targetScreen, 0, SlotActionType.PICKUP, mc.player);
                remaining -= stack.getCount();
                moved = true;
            } else {
                int needed = remaining;
                mc.interactionManager.clickSlot(syncId, i, 0, SlotActionType.PICKUP, mc.player);
                for (int j = 0; j < needed; j++) {
                    mc.interactionManager.clickSlot(syncId, targetScreen, 1, SlotActionType.PICKUP, mc.player);
                }
                mc.interactionManager.clickSlot(syncId, i, 0, SlotActionType.PICKUP, mc.player);
                remaining = 0;
                moved = true;
            }
        }

        if (!moved) {
            message("§cНе удалось переместить предметы");
            phase = Phase.ERROR;
            return;
        }

        phase = Phase.SELECT_SLOT;
        timer.resetCounter();
    }

    private void selectSlot() {
        if (!timer.hasTimeElapsed(getRandomDelay())) return;

        if (currentHotbarSlot >= 0 && currentHotbarSlot < 9) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(currentHotbarSlot));
        }
        phase = Phase.SELL;
        timer.resetCounter();
    }

    private void sellItem() {
        if (!timer.hasTimeElapsed(getRandomDelay())) return;

        mc.player.networkHandler.sendChatCommand("ah sell " + currentPrice);
        phase = Phase.WAIT_RESPONSE;
        timer.resetCounter();
        message("§eОтправляю /ah sell " + currentPrice);
    }

    private void collect() {
        if (!timer.hasTimeElapsed(getRandomDelay())) return;

        switch (collectStep) {
            case OPEN_AH -> {
                mc.player.networkHandler.sendChatCommand("ah");
                collectStep = CollectStep.WAIT_AH;
                timer.resetCounter();
            }
            case WAIT_AH -> {
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    collectStep = CollectStep.CLICK_46;
                    timer.resetCounter();
                }
            }
            case CLICK_46 -> {
                if (mc.currentScreen != null) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 46, 0, SlotActionType.PICKUP, mc.player);
                }
                collectStep = CollectStep.CLICK_0;
                timer.resetCounter();
            }
            case CLICK_0 -> {
                if (mc.currentScreen == null) {
                    collectStep = CollectStep.OPEN_AH;
                    timer.resetCounter();
                    return;
                }
                var slot0 = mc.player.currentScreenHandler.slots.get(0);
                if (slot0 != null && !slot0.getStack().isEmpty()) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 0, 0, SlotActionType.PICKUP, mc.player);
                } else {
                    collectStep = CollectStep.CLOSE;
                }
                timer.resetCounter();
            }
            case CLOSE -> {
                mc.player.closeHandledScreen();
                phase = Phase.SHIFT_HOTBAR;
                timer.resetCounter();
                message("§aСбор завершён, сбрасываю хотбар...");
            }
        }
    }

    private void shiftHotbar() {
        if (!timer.hasTimeElapsed(getRandomDelay())) return;

        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 36 + i, 0, SlotActionType.QUICK_MOVE, mc.player);
                timer.resetCounter();
                return;
            }
        }

        phase = Phase.FIND_ITEM;
        timer.resetCounter();
    }

    private void timeoutCheck() {
        if (timer.hasTimeElapsed(8000)) {
            message("§cТаймаут ожидания ответа аукциона");
            phase = Phase.ERROR;
        }
    }

    private void errorEnd() {
        phase = Phase.IDLE;
        idleTimer.resetCounter();
        timer.resetCounter();
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (e.getType() != PacketEvent.Type.RECEIVE) return;
        if (!(e.getPacket() instanceof GameMessageS2CPacket packet)) return;

        String msg = packet.content().getString();

        if (phase == Phase.WAIT_RESPONSE) {
            if (msg.contains("выставлен на продажу")) {
                phase = Phase.FIND_ITEM;
                timer.resetCounter();
                message("§a✓ " + currentItemName + " продан за $" + currentPrice + ", ищу ещё...");
            } else if (msg.contains("Не удалось выставить")) {
                phase = Phase.IDLE;
                idleTimer.resetCounter();
                message("§c✗ Хранилище заполнено, ожидание покупок...");
            } else if (msg.contains("подождать")) {
                retryCount++;
                if (retryCount < 3) {
                    message("§eОжидание перед повтором...");
                    timer.setLastMS(5000);
                    phase = Phase.SELL;
                } else {
                    message("§cПревышено число попыток");
                    phase = Phase.ERROR;
                }
            }
        }

        if (msg.contains("слишком часто")) {
            timer.resetCounter();
            phase = Phase.FIND_ITEM;
            message("§eRate limit, повторяю...");
            return;
        }

        if (msg.contains("У Вас купили")) {
            if (phase == Phase.IDLE) {
                idleTimer.resetCounter();
                phase = Phase.FIND_ITEM;
                timer.resetCounter();
                message("§aПредмет куплен, продаю следующий...");
            }
        }
    }

    private int findHotbarTargetSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item && stack.getCount() < stack.getMaxCount()) {
                return i;
            }
        }
        return -1;
    }

    public String[] getModeNames() {
        return modes.getList().toArray(new String[0]);
    }

    public Map<String, String> getItemMap() {
        return itemMap;
    }

    private void message(String text) {
        if (!messages.isValue() || mc.player == null) return;
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("§d[AutoSell] §r" + text), false);
            }
        });
    }
}
