package sadrik.modules.impl.render;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.WorldLoadEvent;
import sadrik.events.impl.WorldRenderEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.ColorSetting;
import sadrik.modules.module.setting.implement.MultiSelectSetting;
import sadrik.modules.module.setting.implement.SliderSettings;
import sadrik.util.Instance;
import sadrik.util.config.impl.blockesp.BlockESPConfig;
import sadrik.util.render.Render3D;

import java.util.*;

public class BlockESP extends ModuleStructure {
    public static BlockESP getInstance() {
        return Instance.get(BlockESP.class);
    }

    SliderSettings range = new SliderSettings("Радиус", "Радиус поиска контейнеров").range(1, 128).setValue(32);

    MultiSelectSetting groups = new MultiSelectSetting("Группы", "Группы контейнеров для отображения")
            .value("shulker_box", "chest", "trapped_chest", "ender_chest", "barrel", "hopper", "dropper", "dispenser")
            .selected("shulker_box", "chest", "trapped_chest", "ender_chest", "barrel", "hopper", "dropper", "dispenser");

    ColorSetting shulkerColor = new ColorSetting("Цвет шалкера", "Цвет")
            .value(0xFFFF0000)
            .visible(() -> groups.isSelected("shulker_box"));

    ColorSetting chestColor = new ColorSetting("Цвет сундука", "Цвет")
            .value(0xFFFFAA00)
            .visible(() -> groups.isSelected("chest"));

    ColorSetting trappedChestColor = new ColorSetting("Цвет ловушки", "Цвет")
            .value(0xFFFF5500)
            .visible(() -> groups.isSelected("trapped_chest"));

    ColorSetting enderChestColor = new ColorSetting("Цвет эндера", "Цвет")
            .value(0xFFAA00FF)
            .visible(() -> groups.isSelected("ender_chest"));

    ColorSetting barrelColor = new ColorSetting("Цвет бочки", "Цвет")
            .value(0xFF8B4513)
            .visible(() -> groups.isSelected("barrel"));

    ColorSetting hopperColor = new ColorSetting("Цвет воронки", "Цвет")
            .value(0xFF555555)
            .visible(() -> groups.isSelected("hopper"));

    ColorSetting dropperColor = new ColorSetting("Цвет выбрасывателя", "Цвет")
            .value(0xFF888888)
            .visible(() -> groups.isSelected("dropper"));

    ColorSetting dispenserColor = new ColorSetting("Цвет раздатчика", "Цвет")
            .value(0xFF888888)
            .visible(() -> groups.isSelected("dispenser"));

    private final Set<String> enabledBlockIds = new HashSet<>();
    Map<BlockPos, Integer> renderBlocks = new HashMap<>();
    long lastScanTime = 0;
    int checkCounter = 0;

    public BlockESP() {
        super("BlockESP", "BlockESP — подсветка контейнеров", ModuleCategory.RENDER);
        settings(range, groups, shulkerColor, chestColor, trappedChestColor, enderChestColor, barrelColor, hopperColor, dropperColor, dispenserColor);
    }

    @Override
    public void activate() {
        rebuildCache();
    }

    @Override
    public void deactivate() {
        renderBlocks.clear();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        if (state) {
            rebuildCache();
        }
    }

    public void rebuildCache() {
        enabledBlockIds.clear();
        for (String groupName : BlockESPConfig.GROUP_NAMES) {
            if (groups.isSelected(groupName)) {
                List<String> ids = BlockESPConfig.GROUP_BLOCKS.get(groupName);
                if (ids != null) {
                    enabledBlockIds.addAll(ids);
                }
            }
        }
        renderBlocks.clear();
    }

    private int getBlockColor(String blockId) {
        String group = BlockESPConfig.getInstance().getGroupByBlock(blockId);
        if (group == null) return 0xFFFFFFFF;
        switch (group) {
            case "shulker_box": return shulkerColor.getColor();
            case "chest": return chestColor.getColor();
            case "trapped_chest": return trappedChestColor.getColor();
            case "ender_chest": return enderChestColor.getColor();
            case "barrel": return barrelColor.getColor();
            case "hopper": return hopperColor.getColor();
            case "dropper": return dropperColor.getColor();
            case "dispenser": return dispenserColor.getColor();
            default: return 0xFFFFFFFF;
        }
    }

    @EventHandler
    public void onRender3D(WorldRenderEvent event) {
        if (!state || mc.world == null || mc.player == null) {
            renderBlocks.clear();
            return;
        }
        if (enabledBlockIds.isEmpty()) {
            renderBlocks.clear();
            return;
        }
        BlockPos playerPos = mc.player.getBlockPos();
        long currentTime = System.nanoTime() / 1_000_000;
        if (currentTime - lastScanTime >= 2000) {
            renderBlocks.clear();
            int chunkRange = 2;
            int yRange = 48;
            for (int x = -chunkRange; x <= chunkRange; x++) {
                for (int z = -chunkRange; z <= chunkRange; z++) {
                    int chunkX = (playerPos.getX() >> 4) + x;
                    int chunkZ = (playerPos.getZ() >> 4) + z;
                    if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) continue;
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                    if (chunk == null) continue;
                    int cx = chunk.getPos().x << 4;
                    int cz = chunk.getPos().z << 4;
                    for (int bx = 0; bx < 16; bx++) {
                        for (int bz = 0; bz < 16; bz++) {
                            int minY = Math.max(mc.world.getBottomY(), playerPos.getY() - yRange);
                            int maxY = Math.min(mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, cx + bx, cz + bz), playerPos.getY() + yRange);
                            for (int by = minY; by <= maxY; by++) {
                                BlockPos pos = new BlockPos(cx + bx, by, cz + bz);
                                double dist = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                                if (dist > range.getValue() * range.getValue()) continue;
                                Block block = mc.world.getBlockState(pos).getBlock();
                                String blockName = Registries.BLOCK.getId(block).toString();
                                if (enabledBlockIds.contains(blockName)) {
                                    renderBlocks.put(pos.toImmutable(), getBlockColor(blockName));
                                }
                            }
                        }
                    }
                }
            }
            lastScanTime = currentTime;
            checkCounter = 0;
        }
        if (checkCounter % 5 == 0) {
            int nearChunkRange = 1;
            for (int x = -nearChunkRange; x <= nearChunkRange; x++) {
                for (int z = -nearChunkRange; z <= nearChunkRange; z++) {
                    int chunkX = (playerPos.getX() >> 4) + x;
                    int chunkZ = (playerPos.getZ() >> 4) + z;
                    if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) continue;
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                    if (chunk == null) continue;
                    int cx = chunk.getPos().x << 4;
                    int cz = chunk.getPos().z << 4;
                    for (int bx = 0; bx < 16; bx++) {
                        for (int bz = 0; bz < 16; bz++) {
                            int minY = Math.max(mc.world.getBottomY(), playerPos.getY() - 24);
                            int maxY = Math.min(mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, cx + bx, cz + bz), playerPos.getY() + 24);
                            for (int by = minY; by <= maxY; by++) {
                                BlockPos pos = new BlockPos(cx + bx, by, cz + bz);
                                double dist = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                                if (dist > 4 * 4) continue;
                                Block block = mc.world.getBlockState(pos).getBlock();
                                String blockName = Registries.BLOCK.getId(block).toString();
                                if (enabledBlockIds.contains(blockName) && !renderBlocks.containsKey(pos)) {
                                    renderBlocks.put(pos.toImmutable(), getBlockColor(blockName));
                                }
                            }
                        }
                    }
                }
            }
        }
        if (checkCounter % 60 == 0) {
            renderBlocks.entrySet().removeIf(entry -> {
                BlockPos pos = entry.getKey();
                Block block = mc.world.getBlockState(pos).getBlock();
                String blockName = Registries.BLOCK.getId(block).toString();
                return !enabledBlockIds.contains(blockName);
            });
        }
        checkCounter++;
        renderBlocks.forEach((pos, color) -> {
            Render3D.drawBoxThroughBlocks(new Box(pos), color, 1);
        });
    }
}
