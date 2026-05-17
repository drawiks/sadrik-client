package sadrik.screens.hud;

import net.minecraft.client.gui.DrawContext;
import sadrik.client.draggables.AbstractHudElement;
import sadrik.modules.impl.misc.EventFinder;
import sadrik.util.animations.Direction;
import sadrik.util.render.Render2D;
import sadrik.util.render.font.Fonts;

import java.awt.*;
import java.util.*;
import java.util.List;

public class EventFinderHud extends AbstractHudElement {

    private static final List<Integer> EXAMPLE_SERVERS = List.of(303, 304, 305);
    private static final float ANIMATION_SPEED = 8f;
    private static final float ENTRY_HEIGHT = 11f;

    private float animatedWidth = 80;
    private float animatedHeight = 23;
    private long lastUpdateTime = System.currentTimeMillis();
    private final Map<Integer, Float> entryAnimations = new LinkedHashMap<>();
    private int exampleIndex = 0;
    private long lastExampleSwitch = 0;

    public EventFinderHud() {
        super("Events", 300, 200, 80, 23, true);
        stopAnimation();
    }

    @Override
    public boolean visible() {
        return !scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    private float lerp(float current, float target, float deltaTime) {
        float factor = (float) (1.0 - Math.pow(0.001, deltaTime * ANIMATION_SPEED));
        return current + (target - current) * factor;
    }

    @Override
    public void tick() {
        Map<Integer, EventFinder.EventInfo> results = EventFinder.getResults();
        boolean completed = EventFinder.isScanCompleted();
        long now = System.currentTimeMillis();

        List<Integer> expired = new ArrayList<>();
        for (Map.Entry<Integer, EventFinder.EventInfo> entry : results.entrySet()) {
            EventFinder.EventInfo info = entry.getValue();
            if (info.getTotalSeconds() < 0) continue;
            long remaining = info.getTotalSeconds() - (now - info.getFetchTime()) / 1000;
            if (remaining <= 0) {
                expired.add(entry.getKey());
            }
        }
        expired.forEach(results.keySet()::remove);

        boolean hasResults = results.values().stream()
                .anyMatch(info -> {
                    if (info.getTotalSeconds() < 0) return false;
                    long remaining = info.getTotalSeconds() - (now - info.getFetchTime()) / 1000;
                    return remaining > 0 && remaining <= EventFinder.MIN_VALID_SECONDS;
                });

        if (completed && hasResults) {
            startAnimation();
            for (Integer server : results.keySet()) {
                entryAnimations.putIfAbsent(server, 0f);
            }
        } else if (isChat(mc.currentScreen)) {
            startAnimation();
            if (now - lastExampleSwitch > 2000) {
                exampleIndex = (exampleIndex + 1) % EXAMPLE_SERVERS.size();
                lastExampleSwitch = now;
            }
        } else {
            stopAnimation();
        }
    }

    @Override
    public void drawDraggable(DrawContext context, int alpha) {
        if (alpha <= 0) return;

        float alphaFactor = alpha / 255.0f;
        long now = System.currentTimeMillis();
        float deltaTime = Math.min((now - lastUpdateTime) / 1000.0f, 0.1f);
        lastUpdateTime = now;

        Map<Integer, EventFinder.EventInfo> results = EventFinder.getResults();
        boolean completed = EventFinder.isScanCompleted();

        List<Integer> sortedServers = results.entrySet().stream()
                .filter(e -> {
                    EventFinder.EventInfo info = e.getValue();
                    if (info.getTotalSeconds() < 0) return false;
                    long remaining = info.getTotalSeconds() - (now - info.getFetchTime()) / 1000;
                    return remaining > 0 && remaining <= EventFinder.MIN_VALID_SECONDS;
                })
                .map(Map.Entry::getKey)
                .sorted()
                .limit(3)
                .toList();

        List<Integer> displayOrder = new ArrayList<>(sortedServers);
        Collections.reverse(displayOrder);

        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, Float> entry : entryAnimations.entrySet()) {
            int server = entry.getKey();
            float current = entry.getValue();
            float target = sortedServers.contains(server) ? 1f : 0f;
            float newAnim = lerp(current, target, deltaTime);

            if (Math.abs(newAnim - target) < 0.01f) newAnim = target;

            if (newAnim <= 0.01f && target == 0f) {
                toRemove.add(server);
            } else {
                entryAnimations.put(server, newAnim);
            }
        }
        toRemove.forEach(entryAnimations::remove);

        boolean hasData = !sortedServers.isEmpty();
        boolean showExample = !hasData && isChat(mc.currentScreen);

        int offset = 23;
        float targetWidth = 80;

        if (showExample) {
            offset += ENTRY_HEIGHT;
            String exampleLine = "an" + EXAMPLE_SERVERS.get(exampleIndex);
            float lineWidth = Fonts.BOLD.getWidth(exampleLine, 6);
            targetWidth = Math.max(lineWidth + 50, targetWidth);
        } else if (hasData) {
            for (int server : sortedServers) {
                float anim = entryAnimations.getOrDefault(server, 0f);
                if (anim <= 0) continue;
                offset += (int) (anim * ENTRY_HEIGHT);

                String line = "an" + server;
                float lineWidth = Fonts.BOLD.getWidth(line, 6);
                targetWidth = Math.max(lineWidth + 50, targetWidth);
            }
        }

        float targetHeight = offset + 2;

        animatedWidth = lerp(animatedWidth, targetWidth, deltaTime);
        animatedHeight = lerp(animatedHeight, targetHeight, deltaTime);

        if (Math.abs(animatedWidth - targetWidth) < 0.3f) animatedWidth = targetWidth;
        if (Math.abs(animatedHeight - targetHeight) < 0.3f) animatedHeight = targetHeight;

        setWidth((int) Math.ceil(animatedWidth));
        setHeight((int) Math.ceil(animatedHeight));

        float x = getX();
        float y = getY();
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

        Render2D.gradientRect(x + getWidth() - 40, y + 5, 14, 12,
                new int[]{
                        new Color(52, 52, 52, bgAlpha).getRGB(),
                        new Color(52, 52, 52, bgAlpha).getRGB(),
                        new Color(52, 52, 52, bgAlpha).getRGB(),
                        new Color(52, 52, 52, bgAlpha).getRGB()
                }, 3);

        Fonts.ICONS.draw("E", x + getWidth() - 37, y + 7.5f, 8, new Color(165, 165, 165, bgAlpha).getRGB());
        Fonts.BOLD.draw("Events", x + 8, y + 6.5f, 6, new Color(255, 255, 255, bgAlpha).getRGB());

        int moduleOffset = 23;

        if (showExample) {
            int server = EXAMPLE_SERVERS.get(exampleIndex);
            String name = "an" + server;
            String timer = "25:30";

            drawEntry(name, timer, x, y, getWidth(), moduleOffset, bgAlpha, 1f, deltaTime);
        } else if (hasData) {
            for (int server : displayOrder) {
                float anim = entryAnimations.getOrDefault(server, 0f);
                if (anim <= 0) continue;

                EventFinder.EventInfo info = results.get(server);
                if (info == null) continue;

                long remaining = info.getTotalSeconds() - (now - info.getFetchTime()) / 1000;

                String name = "an" + server;
                String timer = EventFinder.formatTime(remaining);

                int textAlpha = (int) (255 * anim * alphaFactor);
                drawEntry(name, timer, x, y, getWidth(), moduleOffset, textAlpha, anim, deltaTime);

                moduleOffset += (int) (anim * ENTRY_HEIGHT);
            }
        }
    }

    private void drawEntry(String name, String timer, float x, float y, float width, int offset, int alpha, float anim, float deltaTime) {
        if (alpha <= 0 || anim <= 0.01f) return;

        float timerWidth = Fonts.BOLD.getWidth(timer, 6);
        float timerBoxX = x + width - timerWidth - 14f;

        Render2D.gradientRect(timerBoxX, y + offset - 2f, timerWidth + 6, 9,
                new int[]{
                        new Color(52, 52, 52, alpha).getRGB(),
                        new Color(52, 52, 52, alpha).getRGB(),
                        new Color(52, 52, 52, alpha).getRGB(),
                        new Color(52, 52, 52, alpha).getRGB()
                }, 3);

        Render2D.outline(timerBoxX, y + offset - 2f, timerWidth + 6, 9, 0.35f,
                new Color(132, 132, 132, alpha).getRGB(), 2);

        Fonts.BOLD.draw(name, x + 8, y + offset - 1.5f, 6, new Color(255, 255, 255, alpha).getRGB());
        Fonts.BOLD.draw(timer, timerBoxX + 3, y + offset - 1f, 6, new Color(165, 165, 165, alpha).getRGB());
    }
}
