package sadrik.util.config.impl.blockesp;

import com.google.gson.*;
import sadrik.util.config.impl.consolelogger.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BlockESPConfig {
    private static BlockESPConfig instance;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private final Map<String, GroupConfig> groups = new LinkedHashMap<>();

    public static final Map<String, List<String>> GROUP_BLOCKS = new LinkedHashMap<>();
    public static final List<String> GROUP_NAMES = List.of(
            "shulker_box", "chest", "trapped_chest", "ender_chest",
            "barrel", "hopper", "dropper", "dispenser"
    );

    static {
        GROUP_BLOCKS.put("shulker_box", List.of(
                "minecraft:shulker_box",
                "minecraft:white_shulker_box",
                "minecraft:orange_shulker_box",
                "minecraft:magenta_shulker_box",
                "minecraft:light_blue_shulker_box",
                "minecraft:yellow_shulker_box",
                "minecraft:lime_shulker_box",
                "minecraft:pink_shulker_box",
                "minecraft:gray_shulker_box",
                "minecraft:light_gray_shulker_box",
                "minecraft:cyan_shulker_box",
                "minecraft:purple_shulker_box",
                "minecraft:blue_shulker_box",
                "minecraft:brown_shulker_box",
                "minecraft:green_shulker_box",
                "minecraft:red_shulker_box",
                "minecraft:black_shulker_box"
        ));
        GROUP_BLOCKS.put("chest", List.of("minecraft:chest"));
        GROUP_BLOCKS.put("trapped_chest", List.of("minecraft:trapped_chest"));
        GROUP_BLOCKS.put("ender_chest", List.of("minecraft:ender_chest"));
        GROUP_BLOCKS.put("barrel", List.of("minecraft:barrel"));
        GROUP_BLOCKS.put("hopper", List.of("minecraft:hopper"));
        GROUP_BLOCKS.put("dropper", List.of("minecraft:dropper"));
        GROUP_BLOCKS.put("dispenser", List.of("minecraft:dispenser"));
    }

    public static class GroupConfig {
        private boolean enabled = true;
        private int color;

        public GroupConfig() {
        }

        public GroupConfig(boolean enabled, int color) {
            this.enabled = enabled;
            this.color = color;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getColor() {
            return color;
        }

        public void setColor(int color) {
            this.color = color;
        }
    }

    private BlockESPConfig() {
        Path configDir = Paths.get("Sadrik", "configs");
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) {
        }
        configPath = configDir.resolve("blockesp.json");
        initDefaults();
    }

    private void initDefaults() {
        groups.put("shulker_box", new GroupConfig(true, 0xFFFF0000));
        groups.put("chest", new GroupConfig(true, 0xFFFFAA00));
        groups.put("trapped_chest", new GroupConfig(true, 0xFFFF5500));
        groups.put("ender_chest", new GroupConfig(true, 0xFFAA00FF));
        groups.put("barrel", new GroupConfig(true, 0xFF8B4513));
        groups.put("hopper", new GroupConfig(true, 0xFF555555));
        groups.put("dropper", new GroupConfig(true, 0xFF888888));
        groups.put("dispenser", new GroupConfig(true, 0xFF888888));
    }

    public static BlockESPConfig getInstance() {
        if (instance == null) {
            instance = new BlockESPConfig();
        }
        return instance;
    }

    public GroupConfig getGroup(String name) {
        return groups.get(name);
    }

    public boolean isGroupEnabled(String name) {
        GroupConfig g = groups.get(name);
        return g != null && g.isEnabled();
    }

    public int getGroupColor(String name) {
        GroupConfig g = groups.get(name);
        return g != null ? g.getColor() : 0xFFFF0000;
    }

    public void setGroupEnabled(String name, boolean enabled) {
        GroupConfig g = groups.get(name);
        if (g != null) g.setEnabled(enabled);
    }

    public void setGroupColor(String name, int color) {
        GroupConfig g = groups.get(name);
        if (g != null) g.setColor(color);
    }

    public Map<String, GroupConfig> getGroups() {
        return groups;
    }

    public List<String> getAllEnabledBlocks() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : GROUP_BLOCKS.entrySet()) {
            GroupConfig g = groups.get(entry.getKey());
            if (g != null && g.isEnabled()) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    public String getGroupByBlock(String blockId) {
        for (Map.Entry<String, List<String>> entry : GROUP_BLOCKS.entrySet()) {
            if (entry.getValue().contains(blockId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public int getBlockColor(String blockId) {
        String group = getGroupByBlock(blockId);
        if (group != null) {
            GroupConfig g = groups.get(group);
            if (g != null) return g.getColor();
        }
        return 0xFFFF0000;
    }

    public boolean isBlockEnabled(String blockId) {
        String group = getGroupByBlock(blockId);
        if (group != null) {
            GroupConfig g = groups.get(group);
            return g != null && g.isEnabled();
        }
        return false;
    }

    public int getEnabledGroupCount() {
        int count = 0;
        for (GroupConfig g : groups.values()) {
            if (g.isEnabled()) count++;
        }
        return count;
    }

    public void save() {
        try {
            JsonObject root = new JsonObject();
            JsonObject groupsObj = new JsonObject();
            for (Map.Entry<String, GroupConfig> entry : groups.entrySet()) {
                JsonObject gObj = new JsonObject();
                gObj.addProperty("enabled", entry.getValue().isEnabled());
                gObj.addProperty("color", entry.getValue().getColor());
                groupsObj.add(entry.getKey(), gObj);
            }
            root.add("groups", groupsObj);
            Files.writeString(configPath, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logger.error("BlockESPConfig: Save failed! " + e.getMessage());
        }
    }

    public void load() {
        try {
            if (!Files.exists(configPath)) {
                return;
            }
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            JsonElement element = JsonParser.parseString(json);

            if (element.isJsonObject()) {
                JsonObject root = element.getAsJsonObject();
                if (root.has("groups")) {
                    JsonObject groupsObj = root.getAsJsonObject("groups");
                    for (Map.Entry<String, JsonElement> entry : groupsObj.entrySet()) {
                        String name = entry.getKey();
                        if (groups.containsKey(name)) {
                            JsonObject gObj = entry.getValue().getAsJsonObject();
                            GroupConfig g = groups.get(name);
                            if (gObj.has("enabled")) {
                                g.setEnabled(gObj.get("enabled").getAsBoolean());
                            }
                            if (gObj.has("color")) {
                                g.setColor(gObj.get("color").getAsInt());
                            }
                        }
                    }
                }
            }
            Logger.success("BlockESPConfig: blockesp.json loaded successfully!");
        } catch (Exception e) {
            Logger.error("BlockESPConfig: Load failed! " + e.getMessage());
        }
    }
}
