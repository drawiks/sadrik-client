package sadrik.mixin;

import net.minecraft.client.util.NarratorManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sadrik.modules.impl.misc.NoAnnouncer;

@Mixin(NarratorManager.class)
public class NarratorManagerMixin {

    @Inject(method = "say", at = @At("HEAD"), cancellable = true)
    private void onSay(String text, boolean interrupt, CallbackInfo ci) {
        NoAnnouncer noAnnouncer = NoAnnouncer.getInstance();
        if (noAnnouncer != null && noAnnouncer.isState()) {
            ci.cancel();
        }
    }

    @Inject(method = "narrate", at = @At("HEAD"), cancellable = true)
    private void onNarrate(net.minecraft.text.Text message, CallbackInfo ci) {
        NoAnnouncer noAnnouncer = NoAnnouncer.getInstance();
        if (noAnnouncer != null && noAnnouncer.isState()) {
            ci.cancel();
        }
    }
}
