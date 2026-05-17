package sadrik.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import sadrik.events.api.events.callables.EventCancellable;

@Getter
@AllArgsConstructor
public class PushEvent extends EventCancellable {
    private Type type;

    public enum Type {
        COLLISION, BLOCK, WATER
    }
}
