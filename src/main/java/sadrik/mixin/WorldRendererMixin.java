package sadrik.mixin;

import net.minecraft.client.gl.DynamicUniforms;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sadrik.IMinecraft;
import sadrik.modules.impl.render.ChunkAnimator;
import sadrik.modules.impl.render.NoRender;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin implements IMinecraft {

    @ModifyArg(method = "renderBlockLayers", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0), index = 0)
    private Object modifyChunkSectionsValue(Object value) {
        if (value instanceof DynamicUniforms.ChunkSectionsValue original) {
            ChunkAnimator chunkAnimator = ChunkAnimator.getInstance();
            if (chunkAnimator != null && chunkAnimator.isState()) {
                float visibility = original.visibility();
                float animOffset = (1.0f - visibility) * 100f;
                int newY = original.y() - (int) animOffset;
                return new DynamicUniforms.ChunkSectionsValue(
                        original.modelView(),
                        original.x(),
                        newY,
                        original.z(),
                        original.visibility(),
                        original.textureAtlasWidth(),
                        original.textureAtlasHeight()
                );
            }
        }
        return value;
    }

    @Inject(method = "hasBlindnessOrDarkness", at = @At("HEAD"), cancellable = true)
    private void onHasBlindnessOrDarkness(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        NoRender noRender = NoRender.getInstance();
        if (noRender == null || !noRender.isState()) return;

        Entity entity = camera.getFocusedEntity();
        if (!(entity instanceof LivingEntity livingEntity)) return;

        boolean hasBlindness = livingEntity.hasStatusEffect(StatusEffects.BLINDNESS);
        boolean hasDarkness = livingEntity.hasStatusEffect(StatusEffects.DARKNESS);

        if (noRender.modeSetting.isSelected("Bad Effects") && hasBlindness && !hasDarkness) {
            cir.setReturnValue(false);
        }

        if (noRender.modeSetting.isSelected("Darkness") && hasDarkness && !hasBlindness) {
            cir.setReturnValue(false);
        }

        if (noRender.modeSetting.isSelected("Bad Effects") && noRender.modeSetting.isSelected("Darkness")) {
            cir.setReturnValue(false);
        }
    }
}