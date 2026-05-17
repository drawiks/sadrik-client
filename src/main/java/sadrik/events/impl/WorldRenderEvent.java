package sadrik.events.impl;

import lombok.*;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.util.math.MatrixStack;
import sadrik.events.api.events.Event;

@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@Getter
public class WorldRenderEvent implements Event {
    MatrixStack stack;
    float partialTicks;
}
