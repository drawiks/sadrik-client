package fun.rich.commands.defaults;

import fun.rich.utils.client.managers.api.command.Command;
import fun.rich.utils.client.managers.api.command.argument.IArgConsumer;
import fun.rich.utils.client.managers.api.command.exception.CommandException;
import fun.rich.utils.client.managers.api.command.helpers.TabCompleteHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class FpCommand extends Command {

    public FpCommand() {
        super("fp");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            logDirect("Использование: " + label + " add <default|nether|diamond> [count] | del [all|id]");
            return;
        }

        String act = args.getString().toLowerCase(Locale.US);

        if (act.equals("add") || act.equals("spawn")) {
            String mode = args.hasAny() ? args.getString().toLowerCase(Locale.US) : "default";
            int count = 1;
            if (args.hasAny()) count = parseInt(args.getString(), 1);
            args.requireMax(3);
            FakePlayerCommand.add(mode, count);
            return;
        }

        if (act.equals("del") || act.equals("remove") || act.equals("despawn")) {
            if (!args.hasAny()) {
                args.requireMax(1);
                FakePlayerCommand.delAll();
                return;
            }
            String a = args.getString().toLowerCase(Locale.US);
            args.requireMax(2);
            if (a.equals("all")) {
                FakePlayerCommand.delAll();
            } else {
                int id = parseInt(a, -1);
                if (id > 0) FakePlayerCommand.del(id);
                else FakePlayerCommand.delAll();
            }
            return;
        }

        args.requireMax(3);
        logDirect("Использование: " + label + " add <default|nether|diamond> [count] | del [all|id]");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) return Stream.empty();

        String a0 = args.getString();

        if (!args.hasAny()) {
            return new TabCompleteHelper()
                    .sortAlphabetically()
                    .prepend("add", "del")
                    .filterPrefix(a0)
                    .stream();
        }

        String a1 = args.getString();

        if (a0.equalsIgnoreCase("add") || a0.equalsIgnoreCase("spawn")) {
            if (!args.hasAny()) {
                return new TabCompleteHelper()
                        .sortAlphabetically()
                        .prepend("default", "nether", "diamond")
                        .filterPrefix(a1)
                        .stream();
            }
            String a2 = args.getString();
            return new TabCompleteHelper()
                    .sortAlphabetically()
                    .prepend("1", "2", "3", "4", "5")
                    .filterPrefix(a2)
                    .stream();
        }

        if (a0.equalsIgnoreCase("del") || a0.equalsIgnoreCase("remove") || a0.equalsIgnoreCase("despawn")) {
            return new TabCompleteHelper()
                    .sortAlphabetically()
                    .prepend("all", "1", "2", "3", "4", "5")
                    .filterPrefix(a1)
                    .stream();
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Алиас для fakeplayer";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Алиас команды fakeplayer.",
                "",
                "Использование:",
                "> .fp add default",
                "> .fp add nether 2",
                "> .fp add diamond 3",
                "> .fp del",
                "> .fp del all",
                "> .fp del 2"
        );
    }

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable ignored) {
            return def;
        }
    }
}
