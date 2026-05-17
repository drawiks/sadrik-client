package sadrik.mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sadrik.events.api.EventManager;
import sadrik.events.impl.HandledScreenClickEvent;
import sadrik.events.impl.HandledScreenDragEvent;
import sadrik.events.impl.HandledScreenReleaseEvent;
import sadrik.events.impl.HandledScreenScrollEvent;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMouseMixin {

    @Shadow
    @Nullable
    protected Slot focusedSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        HandledScreenClickEvent event = new HandledScreenClickEvent(click.button(), focusedSlot);
        EventManager.callEvent(event);
        if (event.isCancelled()) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(Click click, CallbackInfoReturnable<Boolean> cir) {
        HandledScreenReleaseEvent event = new HandledScreenReleaseEvent(click.button(), focusedSlot);
        EventManager.callEvent(event);
        if (event.isCancelled()) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(Click click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        HandledScreenDragEvent event = new HandledScreenDragEvent(click.button(), focusedSlot, click.x(), click.y());
        EventManager.callEvent(event);
        if (event.isCancelled()) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        HandledScreenScrollEvent event = new HandledScreenScrollEvent(focusedSlot, vertical);
        EventManager.callEvent(event);
        if (event.isCancelled()) cir.setReturnValue(true);
    }
}
