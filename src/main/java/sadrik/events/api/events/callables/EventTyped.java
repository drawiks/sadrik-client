package sadrik.events.api.events.callables;

import sadrik.events.api.events.Event;
import sadrik.events.api.events.Typed;

public abstract class EventTyped implements Event, Typed {

    private final byte type;

    protected EventTyped(byte eventType) {
        type = eventType;
    }

    @Override
    public byte getType() {
        return type;
    }

}