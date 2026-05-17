package sadrik.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sadrik.events.api.EventManager;
import sadrik.events.impl.AttackEvent;
import sadrik.events.impl.InteractEntityEvent;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "attackEntity", at = @At("HEAD"),cancellable = true)
    public void attackEntityHook(PlayerEntity player, Entity target, CallbackInfo info) {
        InteractEntityEvent event = new InteractEntityEvent(target);
        EventManager.callEvent(event);
        if (event.isCancelled()) info.cancel();
    }

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        AttackEvent event = new AttackEvent(target);
        EventManager.callEvent(event);
    }

}