package sadrik.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.TickEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.SelectSetting;
import sadrik.util.Instance;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ItemPhysic extends ModuleStructure {
    public static ItemPhysic getInstance() {
        return Instance.get(ItemPhysic.class);
    }

    public SelectSetting mode = new SelectSetting("Физика", "").value("Обычная").selected("Обычная");

    public ItemPhysic() {
        super("ItemPhysic", "Item Physic", ModuleCategory.RENDER);
//        setup(mode);
    }

    @EventHandler
    public void onTick(TickEvent e) {
    }
}