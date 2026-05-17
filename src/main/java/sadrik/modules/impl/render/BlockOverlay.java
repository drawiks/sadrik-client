package sadrik.modules.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.WorldRenderEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.BooleanSetting;
import sadrik.modules.module.setting.implement.ColorSetting;
import sadrik.modules.module.setting.implement.SliderSettings;
import sadrik.util.ColorUtil;
import sadrik.util.Instance;
import sadrik.util.render.Render3D;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class BlockOverlay extends ModuleStructure {
    public static BlockOverlay getInstance() {
        return Instance.get(BlockOverlay.class);
    }

    public ColorSetting color = new ColorSetting("Цвет", "Цвет оверлея")
            .value(ColorUtil.getColor(109, 252, 255, 230));

    public SliderSettings lineWidth = new SliderSettings("Толщина линии", "Толщина обводки")
            .range(0.5f, 5.0f).setValue(1.5f);

    public BooleanSetting fill = new BooleanSetting("Заливка", "Заливка блока")
            .setValue(true);

    public BlockOverlay() {
        super("BlockOverlay", "Block Overlay", ModuleCategory.RENDER);
        settings(color, lineWidth, fill);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.crosshairTarget instanceof BlockHitResult result && result.getType().equals(HitResult.Type.BLOCK)) {
            BlockPos pos = result.getBlockPos();
            Render3D.drawShapeAlternative(pos, mc.world.getBlockState(pos).getOutlineShape(mc.world, pos),
                    color.getColor(), lineWidth.getValue(), fill.isValue(), true);
        }
    }
}
