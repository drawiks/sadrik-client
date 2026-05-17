package sadrik.events.impl;

import sadrik.events.api.events.callables.EventCancellable;

public class TabCompleteEvent extends EventCancellable {
    public final String prefix;
    public String[] completions;

    public TabCompleteEvent(String prefix) {
        this.prefix = prefix;
        this.completions = null;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setCompletions(String[] completions) {
        this.completions = completions;
    }
}