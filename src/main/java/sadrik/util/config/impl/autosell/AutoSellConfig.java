package sadrik.util.config.impl.autosell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class AutoSellConfig {
    private static AutoSellConfig instance;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    @Getter
    private ConfigData data = new ConfigData();

    @Getter
    @Setter
    public static class ConfigData {
        private Map<String, String> items = new LinkedHashMap<>();
    }

    private AutoSellConfig() {
        Path configDir = Paths.get("Sadrik", "configs", "autosell");
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) {}
        configPath = configDir.resolve("autosell.json");
        load();
    }

    public static AutoSellConfig getInstance() {
        if (instance == null) {
            instance = new AutoSellConfig();
        }
        return instance;
    }

    public void load() {
        try {
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                ConfigData loaded = gson.fromJson(json, ConfigData.class);
                if (loaded != null) {
                    this.data = loaded;
                    if (this.data.getItems() == null) {
                        this.data.setItems(new LinkedHashMap<>());
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    public void save() {
        try {
            String json = gson.toJson(data);
            Files.writeString(configPath, json);
        } catch (IOException ignored) {}
    }

    public Map<String, String> getItems() {
        return data.getItems();
    }

    public void putItem(String displayName, String itemKey) {
        data.getItems().put(displayName, itemKey);
    }

    public void removeItem(String displayName) {
        data.getItems().remove(displayName);
    }
}
