package sadrik.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import sadrik.events.api.events.Event;
import sadrik.modules.module.ModuleStructure;

@Getter
@AllArgsConstructor
public class ModuleToggleEvent implements Event {
    private final ModuleStructure module;
    private final boolean enabled;
}