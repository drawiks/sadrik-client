package sadrik.events.impl;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.item.ItemStack;
import sadrik.events.api.events.Event;

@Getter
@Setter
public class HotbarItemRenderEvent implements Event {
    private ItemStack stack;
    private final int hotbarIndex;

    public HotbarItemRenderEvent(ItemStack stack, int hotbarIndex) {
        this.stack = stack;
        this.hotbarIndex = hotbarIndex;
    }
}