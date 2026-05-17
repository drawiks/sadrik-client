package sadrik.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import sadrik.events.api.events.Event;

@Getter
@AllArgsConstructor
public class RotationUpdateEvent implements Event {
    byte type;
}
