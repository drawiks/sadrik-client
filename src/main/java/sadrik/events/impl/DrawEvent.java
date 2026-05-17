package sadrik.events.impl;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;
import sadrik.events.api.events.Event;
import sadrik.util.render.draw.DrawEngine;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DrawEvent implements Event {
    DrawContext drawContext;
    DrawEngine drawEngine;
    float partialTicks;
}
