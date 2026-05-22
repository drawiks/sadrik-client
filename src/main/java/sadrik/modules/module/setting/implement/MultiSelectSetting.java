package sadrik.modules.module.setting.implement;

import sadrik.modules.module.setting.Setting;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Getter
@Setter
public class MultiSelectSetting extends Setting {
    private List<String> list, selected = new ArrayList<>();

    public MultiSelectSetting(String name, String description) {
        super(name, description);
    }

    public MultiSelectSetting value(String... settings) {
        list = new ArrayList<>(Arrays.asList(settings));
        return this;
    }

    public void addValue(String value) {
        if (list == null) list = new ArrayList<>();
        if (!list.contains(value)) {
            list.add(value);
        }
        if (!selected.contains(value)) {
            selected.add(value);
        }
    }

    public void removeValue(String value) {
        if (list != null) list.remove(value);
        selected.remove(value);
    }

    public MultiSelectSetting selected(String... settings) {
        selected = new ArrayList<>(Arrays.asList(settings));
        return this;
    }

    public MultiSelectSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public boolean isSelected(String name) {
        return selected.contains(name);
    }
}