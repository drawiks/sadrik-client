package sadrik.command.impl;

import sadrik.command.Command;
import sadrik.command.helpers.TabCompleteHelper;
import sadrik.util.entity.fakeplayer.FakePlayerManager;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class FakePlayerCommand extends Command {

    public FakePlayerCommand() {
        super("fakeplayer", "Создаёт фейкового игрока (клиент-сайд)", "fp");
    }

    @Override
    public void execute(String label, String[] args) {
        if (args.length == 0) {
            logDirect("Использование: " + label + " add <default|nether|diamond> [count] | del [all|id]");
            return;
        }

        String act = args[0].toLowerCase(Locale.US);

        if (act.equals("add") || act.equals("spawn")) {
            String mode = args.length > 1 ? args[1].toLowerCase(Locale.US) : "default";
            int count = 1;
            if (args.length > 2) {
                try {
                    count = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                }
            }
            FakePlayerManager.getInstance().add(mode, count);
            logDirect("Создано фейковых игроков: " + count + " (" + mode + ")");
            return;
        }

        if (act.equals("del") || act.equals("remove") || act.equals("despawn")) {
            if (args.length < 2) {
                FakePlayerManager.getInstance().delAll();
                logDirect("Все фейковые игроки удалены");
                return;
            }
            String a = args[1].toLowerCase(Locale.US);
            if (a.equals("all")) {
                FakePlayerManager.getInstance().delAll();
                logDirect("Все фейковые игроки удалены");
            } else {
                try {
                    int id = Integer.parseInt(a);
                    FakePlayerManager.getInstance().del(id);
                    logDirect("Фейковый игрок #" + id + " удалён");
                } catch (NumberFormatException e) {
                    FakePlayerManager.getInstance().delAll();
                    logDirect("Все фейковые игроки удалены");
                }
            }
            return;
        }

        logDirect("Использование: " + label + " add <default|nether|diamond> [count] | del [all|id]");
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1) {
            return new TabCompleteHelper()
                    .append("add", "del")
                    .sortAlphabetically()
                    .filterPrefix(args[0])
                    .stream();
        }

        if (args.length == 2) {
            String a0 = args[0].toLowerCase(Locale.US);

            if (a0.equals("add") || a0.equals("spawn")) {
                return new TabCompleteHelper()
                        .append("default", "nether", "diamond")
                        .sortAlphabetically()
                        .filterPrefix(args[1])
                        .stream();
            }

            if (a0.equals("del") || a0.equals("remove") || a0.equals("despawn")) {
                return new TabCompleteHelper()
                        .append("all", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
                        .filterPrefix(args[1])
                        .stream();
            }
        }

        if (args.length == 3) {
            String a0 = args[0].toLowerCase(Locale.US);
            if (a0.equals("add") || a0.equals("spawn")) {
                return new TabCompleteHelper()
                        .append("1", "2", "3", "4", "5")
                        .filterPrefix(args[2])
                        .stream();
            }
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Создаёт фейкового игрока (клиент-сайд)";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Создаёт/удаляет фейковых игроков рядом с вами.",
                "Руками: любой ваш удар по фейку = крит и -1 сердечко.",
                "При срабатывании тотема: эффект+звук, хп сразу фулл и новый тотем.",
                "",
                "Использование:",
                "> .fakeplayer add default",
                "> .fakeplayer add nether 2",
                "> .fakeplayer add diamond 3",
                "> .fakeplayer del",
                "> .fakeplayer del all",
                "> .fakeplayer del 2"
        );
    }
}
