package sadrik.command.impl;

import sadrik.command.Command;
import sadrik.command.helpers.TabCompleteHelper;
import sadrik.modules.impl.misc.AutoSell;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static sadrik.command.impl.HelpCommand.getLine;

public class AutoSellCommand extends Command {

    public AutoSellCommand() {
        super("autosell", "Продажа предметов на аукционе", "as");
    }

    @Override
    public void execute(String label, String[] args) {
        AutoSell autoSell = AutoSell.getInstance();
        if (autoSell == null) {
            logDirect("§cМодуль AutoSell не загружен");
            return;
        }

        if (args.length < 3) {
            showHelp();
            return;
        }

        StringBuilder itemName = new StringBuilder();
        for (int i = 0; i < args.length - 2; i++) {
            if (itemName.length() > 0) itemName.append(" ");
            itemName.append(args[i]);
        }

        int quantity;
        int price;

        try {
            quantity = Integer.parseInt(args[args.length - 2]);
            price = Integer.parseInt(args[args.length - 1]);
        } catch (NumberFormatException e) {
            logDirect("§cКоличество и цена должны быть числами");
            return;
        }

        autoSell.startSell(itemName.toString(), quantity, price);
    }

    private void showHelp() {
        logDirectRaw(net.minecraft.text.Text.literal(getLine()));
        logDirect("§f§lAUTOSELL");
        logDirectRaw(net.minecraft.text.Text.literal(getLine()));
        logDirect("§7> autosell <предмет> <количество> <цена> §8- §fПродать предмет на аукционе");
        logDirectRaw(net.minecraft.text.Text.literal(getLine()));
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        AutoSell autoSell = AutoSell.getInstance();
        if (autoSell == null) return Stream.empty();

        if (args.length == 1) {
            return new TabCompleteHelper()
                    .append(autoSell.getModeNames())
                    .sortAlphabetically()
                    .filterPrefix(args[0])
                    .stream();
        }

        if (args.length == 2) {
            return new TabCompleteHelper()
                    .append("1", "16", "32", "64")
                    .filterPrefix(args[1])
                    .stream();
        }

        if (args.length == 3) {
            return new TabCompleteHelper()
                    .append("100", "500", "1000", "5000", "10000", "50000", "100000", "200000")
                    .filterPrefix(args[2])
                    .stream();
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Продажа предметов на аукционе";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Команда для продажи предметов на аукционе",
                "",
                "Использование:",
                "> autosell <предмет> <количество> <цена>",
                "",
                "Примеры:",
                "> autosell Чарки 1 200000",
                "",
                "Предмет выбирается из списка в настройках модуля",
                "Перед использованием включите нужные режимы в модуле AutoSell"
        );
    }
}
