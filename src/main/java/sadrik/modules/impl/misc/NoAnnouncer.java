package sadrik.modules.impl.misc;

import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.util.Instance;

public class NoAnnouncer extends ModuleStructure {

    public static NoAnnouncer getInstance() {
        return Instance.get(NoAnnouncer.class);
    }

    public NoAnnouncer() {
        super("NoAnnouncer", "Отключает диктора", ModuleCategory.MISC);
    }
}
