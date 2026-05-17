package sadrik.events.impl;

import sadrik.events.api.events.callables.EventCancellable;

public class ChunkOcclusionEvent extends EventCancellable {
    private static final ChunkOcclusionEvent INSTANCE = new ChunkOcclusionEvent();

    public static ChunkOcclusionEvent get() {
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }
}