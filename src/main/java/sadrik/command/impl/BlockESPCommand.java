package sadrik.command.impl;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import sadrik.Initialization;
import sadrik.command.Command;
import sadrik.command.CommandManager;
import sadrik.command.helpers.TabCompleteHelper;
import sadrik.modules.impl.render.BlockESP;
import sadrik.util.config.impl.blockesp.BlockESPConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static sadrik.command.impl.HelpCommand.getLine;

public class BlockESPCommand extends Command {

    public BlockESPCommand() {
        super("blockesp", "Управление группами контейнеров для BlockESP", "besp");
    }

    @Override
    public void execute(String label, String[] args) {
        String action = args.length > 0 ? args[0].toLowerCase(Locale.US) : "help";

        switch (action) {
            case "list" -> executeList(label);
            case "toggle" -> executeToggle(args);
            case "color" -> executeColor(args);
            default -> executeHelp(label);
        }
    }

    private void executeList(String label) {
        BlockESPConfig config = BlockESPConfig.getInstance();

        logDirectRaw(Text.literal(getLine()));
        logDirect("§f§lГРУППЫ BLOCKESP");

        for (String groupName : BlockESPConfig.GROUP_NAMES) {
            boolean enabled = config.isGroupEnabled(groupName);
            int color = config.getGroupColor(groupName);
            String hex = String.format("#%06X", color & 0xFFFFFF);
            String status = enabled ? "§a✓" : "§8✗";
            String displayName = getGroupDisplayName(groupName);

            MutableText component = Text.literal("  " + status + " §f" + displayName)
                    .append(Text.literal(" §8(" + hex + ")"));

            String toggleCommand = CommandManager.getInstance().getPrefix() + "blockesp toggle " + groupName;
            MutableText hoverText = Text.literal(enabled ? "§7Нажмите чтобы выключить" : "§7Нажмите чтобы включить");

            component.setStyle(component.getStyle()
                    .withHoverEvent(new HoverEvent.ShowText(hoverText))
                    .withClickEvent(new ClickEvent.RunCommand(toggleCommand)));

            logDirectRaw(component);
        }

        logDirectRaw(Text.literal(getLine()));
        logDirect("§7Всего включено: §f" + config.getEnabledGroupCount() + "§7/§f" + BlockESPConfig.GROUP_NAMES.size());
        logDirect("§7> " + CommandManager.getInstance().getPrefix() + "blockesp toggle <группа> §8- §fвкл/выкл группу");
        logDirect("§7> " + CommandManager.getInstance().getPrefix() + "blockesp color <группа> <hex> §8- §fустановить цвет");
    }

    private void executeToggle(String[] args) {
        if (args.length < 2) {
            logDirect("Использование: blockesp toggle <группа>", Formatting.RED);
            logDirect("Группы: shulker_box, chest, trapped_chest, ender_chest, barrel, hopper, dropper, dispenser", Formatting.GRAY);
            return;
        }

        String groupName = args[1].toLowerCase(Locale.US);
        BlockESPConfig config = BlockESPConfig.getInstance();

        if (config.getGroup(groupName) == null) {
            logDirect("Группа " + groupName + " не найдена!", Formatting.RED);
            return;
        }

        boolean newState = !config.isGroupEnabled(groupName);
        config.setGroupEnabled(groupName, newState);
        config.save();
        syncWithModule();

        String displayName = getGroupDisplayName(groupName);
        logDirect("§aГруппа §f" + displayName + " §a" + (newState ? "включена" : "выключена"), Formatting.GREEN);
    }

    private void executeColor(String[] args) {
        if (args.length < 3) {
            logDirect("Использование: blockesp color <группа> <hex>", Formatting.RED);
            logDirect("Пример: §7blockesp color chest §e#FFAA00", Formatting.GRAY);
            logDirect("Группы: shulker_box, chest, trapped_chest, ender_chest, barrel, hopper, dropper, dispenser", Formatting.GRAY);
            return;
        }

        String groupName = args[1].toLowerCase(Locale.US);
        BlockESPConfig config = BlockESPConfig.getInstance();

        if (config.getGroup(groupName) == null) {
            logDirect("Группа " + groupName + " не найдена!", Formatting.RED);
            return;
        }

        String hexStr = args[2];
        if (hexStr.startsWith("#")) {
            hexStr = hexStr.substring(1);
        }

        try {
            int rgb = Integer.parseInt(hexStr, 16);
            int color = 0xFF000000 | rgb;
            config.setGroupColor(groupName, color);
            config.save();
            syncWithModule();

            String displayName = getGroupDisplayName(groupName);
            logDirect("§aЦвет группы §f" + displayName + " §aустановлен на §f#" + hexStr.toUpperCase(), Formatting.GREEN);
        } catch (NumberFormatException e) {
            logDirect("Некорректный hex-цвет: " + args[2], Formatting.RED);
        }
    }

    private void executeHelp(String label) {
        logDirectRaw(Text.literal(getLine()));
        logDirect("§f§lBLOCKESP");
        logDirectRaw(Text.literal(getLine()));
        logDirect("§7> blockesp list §8- §fПоказать группы и их статус");
        logDirect("§7> blockesp toggle <группа> §8- §fВключить/выключить группу");
        logDirect("§7> blockesp color <группа> <hex> §8- §fУстановить цвет подсветки");
        logDirectRaw(Text.literal(getLine()));
        logDirect("§7Группы:");
        logDirect("§8  shulker_box §7- §fШалкеры (все 17 цветов)");
        logDirect("§8  chest §7- §fСундук");
        logDirect("§8  trapped_chest §7- §fСундук-ловушка");
        logDirect("§8  ender_chest §7- §fЭндер-сундук");
        logDirect("§8  barrel §7- §fБочка");
        logDirect("§8  hopper §7- §fВоронка");
        logDirect("§8  dropper §7- §fВыбрасыватель");
        logDirect("§8  dispenser §7- §fРаздатчик");
        logDirectRaw(Text.literal(getLine()));
        logDirect("§7Примеры:");
        logDirect("§8  > blockesp toggle ender_chest");
        logDirect("§8  > blockesp color chest #FFAA00");
        logDirectRaw(Text.literal(getLine()));
    }

    private void syncWithModule() {
        BlockESP module = getBlockESPModule();
        if (module != null) {
            module.rebuildCache();
        }
    }

    private BlockESP getBlockESPModule() {
        if (Initialization.getInstance() == null || Initialization.getInstance().getManager() == null) {
            return null;
        }
        return Initialization.getInstance().getManager().getModuleRepository().modules().stream()
                .filter(m -> m instanceof BlockESP)
                .map(m -> (BlockESP) m)
                .findFirst()
                .orElse(null);
    }

    private String getGroupDisplayName(String groupName) {
        return switch (groupName) {
            case "shulker_box" -> "Шалкер";
            case "chest" -> "Сундук";
            case "trapped_chest" -> "Сундук-ловушка";
            case "ender_chest" -> "Эндер-сундук";
            case "barrel" -> "Бочка";
            case "hopper" -> "Воронка";
            case "dropper" -> "Выбрасыватель";
            case "dispenser" -> "Раздатчик";
            default -> groupName;
        };
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1) {
            return new TabCompleteHelper()
                    .append("list", "toggle", "color")
                    .sortAlphabetically()
                    .filterPrefix(args[0])
                    .stream();
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase();
            if (action.equals("toggle") || action.equals("color")) {
                return new TabCompleteHelper()
                        .append(BlockESPConfig.GROUP_NAMES.toArray(new String[0]))
                        .filterPrefix(args[1])
                        .stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Управление группами контейнеров для BlockESP";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Команда для управления группами контейнеров BlockESP",
                "Использование:",
                "> blockesp list - Показать группы",
                "> blockesp toggle <группа> - Вкл/выкл группу",
                "> blockesp color <группа> <hex> - Установить цвет"
        );
    }
}
