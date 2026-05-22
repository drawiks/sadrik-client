package sadrik.command.impl;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import sadrik.command.Command;
import sadrik.modules.impl.combat.Aura;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static sadrik.command.impl.HelpCommand.getLine;

public class NeuroCommand extends Command {

    public NeuroCommand() {
        super("neuro", "Neuro Aura status", "n");
    }

    @Override
    public void execute(String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("status")) {
            logDirectRaw(Text.literal(getLine()));
            logDirect("§f§lNEURO AURA");
            logDirectRaw(Text.literal(getLine()));
            logDirect("§7> neuro status §8- §fПоказать статус Neuro Aura");
            logDirectRaw(Text.literal(getLine()));
            return;
        }

        Aura aura = Aura.getInstance();
        if (aura == null || !aura.isState()) {
            logDirect("Neuro Aura выключен", Formatting.RED);
            return;
        }

        boolean enabled = aura.neuroEnabled();
        if (!enabled) {
            logDirect("Режим наводки: Neuro Aura не выбран", Formatting.RED);
            return;
        }

        String modeName = aura.getNeuroMode().getSelected();
        String exec = aura.neuroExec() ? "§aДа" : "§7Нет";
        String loaded = aura.isNeuroLoaded() ? "§aДа" : "§7Нет";
        int samples = aura.getNeuroSampleCount();
        String dirty = aura.isNeuroDirty() ? "§eДа" : "§7Нет";

        logDirectRaw(Text.literal(getLine()));
        logDirect("§f§lСТАТУС NEURO AURA");
        logDirectRaw(Text.literal(getLine()));
        logDirect("§7Режим: §f" + modeName);
        logDirect("§7Выполняющий: " + exec);
        logDirect("§7Модель загружена: " + loaded);
        logDirect("§7Сэмплов: §f" + String.format("%,d", samples));
        logDirect("§7Несохранено: " + dirty);
        logDirectRaw(Text.literal(getLine()));
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1) {
            return Stream.of("status").filter(s -> s.startsWith(args[0].toLowerCase()));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Neuro Aura status";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Команда для просмотра статуса Neuro Aura",
                "",
                "Использование:",
                "> neuro - Показать справку",
                "> neuro status - Показать статус Neuro Aura"
        );
    }
}
