package sadrik.screens.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import sadrik.client.draggables.AbstractHudElement;
import sadrik.util.animations.Direction;
import sadrik.util.render.Render2D;
import sadrik.util.render.font.Fonts;
import sadrik.util.render.item.ItemRender;
import sadrik.util.render.shader.Scissor;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class Armor extends AbstractHudElement {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private static final float ANIMATION_SPEED = 8.0f;

    private final Map<EquipmentSlot, Float> slotAnimations = new LinkedHashMap<>();
    private final Map<EquipmentSlot, ItemStack> currentStacks = new LinkedHashMap<>();
    private final Map<EquipmentSlot, Boolean> activeSlots = new LinkedHashMap<>();

    private float animatedWidth = 80;
    private float animatedHeight = 23;
    private long lastUpdateTime = System.currentTimeMillis();

    public Armor() {
        super("Armor", 400, 100, 80, 23, true);
        stopAnimation();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            slotAnimations.put(slot, 0f);
            currentStacks.put(slot, ItemStack.EMPTY);
            activeSlots.put(slot, false);
        }
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                activeSlots.put(slot, false);
                currentStacks.put(slot, ItemStack.EMPTY);
            }
            stopAnimation();
            return;
        }

        boolean hasAny = false;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            currentStacks.put(slot, stack);
            boolean isActive = !stack.isEmpty();
            activeSlots.put(slot, isActive);
            if (isActive) hasAny = true;
        }

        if (hasAny || isChat(mc.currentScreen)) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    private float lerp(float current, float target, float deltaTime) {
        float factor = (float) (1.0 - Math.pow(0.001, deltaTime * ANIMATION_SPEED));
        return current + (target - current) * factor;
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;

        float alphaFactor = alpha / 255.0f;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        deltaTime = Math.min(deltaTime, 0.1f);

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            float currentAnim = slotAnimations.get(slot);
            float targetAnim = activeSlots.get(slot) ? 1f : 0f;
            float newAnim = lerp(currentAnim, targetAnim, deltaTime);
            if (Math.abs(newAnim - targetAnim) < 0.01f) {
                newAnim = targetAnim;
            }
            slotAnimations.put(slot, newAnim);
        }

        float x = getX();
        float y = getY();

        int offset = 23;
        float targetWidth = 80;

        boolean hasArmor = false;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (slotAnimations.get(slot) > 0) {
                hasArmor = true;
                break;
            }
        }

        boolean showExample = !hasArmor && isChat(mc.currentScreen);

        if (showExample) {
            offset += 11;
            String durability = "500/500";
            float durabilityWidth = Fonts.BOLD.getWidth(durability, 6);
            targetWidth = Math.max(durabilityWidth + 50, targetWidth);
        } else if (hasArmor) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                float anim = slotAnimations.get(slot);
                if (anim <= 0) continue;

                ItemStack stack = currentStacks.get(slot);
                if (stack == null || stack.isEmpty()) continue;

                offset += (int) (anim * 11);

                String durability = "";
                if (stack.getMaxDamage() > 0) {
                    durability = (stack.getMaxDamage() - stack.getDamage()) + "/" + stack.getMaxDamage();
                }
                float durabilityWidth = Fonts.BOLD.getWidth(durability, 6);
                targetWidth = Math.max(durabilityWidth + 50, targetWidth);
            }
        }

        float targetHeight = offset + 2;

        animatedWidth = lerp(animatedWidth, targetWidth, deltaTime);
        animatedHeight = lerp(animatedHeight, targetHeight, deltaTime);

        if (Math.abs(animatedWidth - targetWidth) < 0.3f) {
            animatedWidth = targetWidth;
        }
        if (Math.abs(animatedHeight - targetHeight) < 0.3f) {
            animatedHeight = targetHeight;
        }

        setWidth((int) Math.ceil(animatedWidth));
        setHeight((int) Math.ceil(animatedHeight));

        float contentHeight = animatedHeight;
        int bgAlpha = (int) (255 * alphaFactor);

        if (contentHeight > 0) {
            Render2D.gradientRect(x, y, getWidth(), contentHeight,
                    new int[]{
                            new Color(52, 52, 52, bgAlpha).getRGB(),
                            new Color(32, 32, 32, bgAlpha).getRGB(),
                            new Color(52, 52, 52, bgAlpha).getRGB(),
                            new Color(32, 32, 32, bgAlpha).getRGB()
                    }, 5);
            Render2D.outline(x, y, getWidth(), contentHeight, 0.35f, new Color(90, 90, 90, bgAlpha).getRGB(), 5);
        }

        Scissor.enable(x, y, getWidth(), contentHeight, 2);

        int armorCount = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!currentStacks.get(slot).isEmpty()) armorCount++;
        }
        String countText = String.valueOf(armorCount);
        float countTextWidth = Fonts.BOLD.getWidth(countText, 6);
        float armorTextWidth = Fonts.BOLD.getWidth("Armor", 6);

        Render2D.gradientRect(x + getWidth() - countTextWidth - armorTextWidth + 3, y + 5, 14, 12,
                new int[]{
                        new Color(52, 52, 52, bgAlpha).getRGB(),
                        new Color(52, 52, 52, bgAlpha).getRGB(),
                        new Color(52, 52, 52, bgAlpha).getRGB(),
                        new Color(52, 52, 52, bgAlpha).getRGB()
                }, 3);

        Fonts.HUD_ICONS.draw("a", x + getWidth() - countTextWidth - armorTextWidth + 5, y + 6, 10,
                new Color(165, 165, 165, bgAlpha).getRGB());

        Fonts.BOLD.draw("Armor", x + 8, y + 6.5f, 6,
                new Color(255, 255, 255, bgAlpha).getRGB());

        int moduleOffset = 23;

        if (showExample) {
            String durability = "500/500";
            float durabilityWidth = Fonts.BOLD.getWidth(durability, 6);

            Fonts.BOLD.draw(durability, x + 22, y + moduleOffset - 1, 6,
                    new Color(165, 165, 165, bgAlpha).getRGB());
        } else if (hasArmor) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                float anim = slotAnimations.get(slot);
                if (anim <= 0) continue;

                ItemStack stack = currentStacks.get(slot);
                if (stack == null || stack.isEmpty()) continue;

                int maxDmg = stack.getMaxDamage();
                int curDmg = maxDmg - stack.getDamage();
                String durability = maxDmg > 0 ? curDmg + "/" + maxDmg : "";

                int textAlpha = (int) (255 * anim * alphaFactor);

                float itemX = x + 8;
                float itemY = y + moduleOffset - 1f;

                if (ItemRender.needsContextRender(stack)) {
                    ItemRender.drawItemWithContext(context, stack, itemX, itemY, 0.5f, anim * alphaFactor);
                } else {
                    ItemRender.drawItem(stack, itemX, itemY, 0.5f, anim * alphaFactor);
                }

                if (maxDmg > 0) {
                    Fonts.BOLD.draw(durability, x + 22, y + moduleOffset - 1, 6,
                            new Color(165, 165, 165, textAlpha).getRGB());
                }

                moduleOffset += (int) (anim * 11);
            }
        }

        Scissor.disable();
    }
}
