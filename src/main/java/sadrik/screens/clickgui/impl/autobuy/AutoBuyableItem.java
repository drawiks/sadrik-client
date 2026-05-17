package sadrik.screens.clickgui.impl.autobuy;

import net.minecraft.item.ItemStack;
import sadrik.screens.clickgui.impl.autobuy.settings.AutoBuyItemSettings;

public interface AutoBuyableItem {
    String getDisplayName();
    ItemStack createItemStack();
    int getPrice();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    AutoBuyItemSettings getSettings();
}