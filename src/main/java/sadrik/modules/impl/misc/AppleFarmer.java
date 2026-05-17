package sadrik.modules.impl.misc;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import sadrik.modules.impl.combat.aura.Angle;
import sadrik.modules.impl.combat.aura.MathAngle;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import sadrik.events.api.EventHandler;
import sadrik.events.impl.TickEvent;
import sadrik.modules.module.ModuleStructure;
import sadrik.modules.module.category.ModuleCategory;
import sadrik.modules.module.setting.implement.BooleanSetting;
import sadrik.modules.module.setting.implement.SliderSettings;
import sadrik.util.inventory.InventoryUtils;
import sadrik.util.string.chat.ChatMessage;
import sadrik.util.timer.StopWatch;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AppleFarmer extends ModuleStructure {

    private enum State {
        IDLE, PLANT_SAPLING, GROW_TREE, BREAK, REPAIR_HOE
    }

    final BooleanSetting autoRepair = new BooleanSetting("Автопочинка", "Чинить мотыгу XP бутылками (только Mending)")
            .setValue(false);
    final SliderSettings actionDelay = new SliderSettings("Задержка", "Задержка между действиями (ms)")
            .range(50, 500).setValue(100);

    State currentState = State.IDLE;
    BlockPos farmPos;
    BlockPos currentBreakPos;
    final StopWatch delayTimer = new StopWatch();
    final StopWatch breakDelay = new StopWatch();
    Angle targetAngle;
    boolean rotating;

    public AppleFarmer() {
        super("AppleFarmer", "Автоматический фарм яблок", ModuleCategory.MISC);
        settings(autoRepair, actionDelay);
    }

    @Override
    public void activate() {
        if (mc.player != null) {
            Direction facing = mc.player.getHorizontalFacing();
            farmPos = mc.player.getBlockPos().offset(facing, 2);
            currentBreakPos = null;
            currentState = State.IDLE;
            delayTimer.reset();
            rotating = false;
            targetAngle = null;
        }
    }

    @Override
    public void deactivate() {
        currentState = State.IDLE;
        farmPos = null;
        currentBreakPos = null;
        rotating = false;
        targetAngle = null;
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null || farmPos == null) return;

        if (autoRepair.isValue() && shouldRepairHoe()) {
            currentState = State.REPAIR_HOE;
            delayTimer.reset();
        }

        BlockState stateAtPos = mc.world.getBlockState(farmPos);
        BlockState stateBelow = mc.world.getBlockState(farmPos.down());

        switch (currentState) {
            case IDLE:
                if (isLog(stateAtPos)) {
                    currentState = State.BREAK;
                    delayTimer.reset();
                } else if (stateAtPos.getBlock() == Blocks.OAK_SAPLING) {
                    currentState = State.GROW_TREE;
                    delayTimer.reset();
                } else if (stateAtPos.isAir() && isDirtLike(stateBelow)) {
                    currentState = State.PLANT_SAPLING;
                    delayTimer.reset();
                }
                break;

            case PLANT_SAPLING:
                if (!delayTimer.finished(randomizedDelay())) return;
                handlePlant();
                break;

            case GROW_TREE:
                if (!delayTimer.finished(randomizedDelay())) return;
                handleGrow();
                break;

            case BREAK:
                handleBreak();
                break;

            case REPAIR_HOE:
                if (!delayTimer.finished(randomizedDelay())) return;
                handleRepair();
                break;
        }
    }

    private void handlePlant() {
        if (!rotating) {
            int saplingSlot = InventoryUtils.findItemInHotbar(Items.OAK_SAPLING);
            if (saplingSlot == -1) {
                ChatMessage.brandmessage("§cСаженцы не найдены в хотбаре!");
                setState(false);
                return;
            }
            InventoryUtils.selectSlot(saplingSlot);
            startRotate(farmPos.down());
            return;
        }

        if (!tickRotate()) return;

        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(farmPos), Direction.UP, farmPos.down(), false
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        currentState = State.IDLE;
        delayTimer.reset();
    }

    private void handleGrow() {
        BlockState state = mc.world.getBlockState(farmPos);
        if (isLog(state)) {
            currentState = State.BREAK;
            delayTimer.reset();
            return;
        }

        if (!rotating) {
            int boneMealSlot = InventoryUtils.findItemInHotbar(Items.BONE_MEAL);
            if (boneMealSlot == -1) {
                ChatMessage.brandmessage("§cКостная мука не найдена в хотбаре!");
                setState(false);
                return;
            }
            InventoryUtils.selectSlot(boneMealSlot);
            startRotate(farmPos);
            return;
        }

        if (!tickRotate()) return;

        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(farmPos), Direction.UP, farmPos, false
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        delayTimer.reset();
    }

    private void handleBreak() {
        if (currentBreakPos == null || mc.world.getBlockState(currentBreakPos).isAir()) {
            if (currentBreakPos != null && !breakDelay.finished(270)) return;
            currentBreakPos = null;

            BlockPos target = findNearestLeaf();
            if (target == null) target = findNearestLog();
            if (target == null) {
                currentState = State.IDLE;
                delayTimer.reset();
                return;
            }

            BlockState targetState = mc.world.getBlockState(target);
            if (targetState.getBlock() == Blocks.OAK_LEAVES) {
                int hoeSlot = findHoeInHotbar();
                if (hoeSlot == -1) {
                    ChatMessage.brandmessage("§cМотыга не найдена в хотбаре!");
                    setState(false);
                    return;
                }
                InventoryUtils.selectSlot(hoeSlot);
            } else if (isLog(targetState)) {
                int axeSlot = findAxeInHotbar();
                if (axeSlot != -1) {
                    InventoryUtils.selectSlot(axeSlot);
                }
            }

            currentBreakPos = target;
            breakDelay.reset();
            rotating = false;
            targetAngle = null;
        }

        if (!rotating) {
            startRotate(currentBreakPos);
            return;
        }

        if (!tickRotate()) return;

        mc.interactionManager.updateBlockBreakingProgress(currentBreakPos, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void handleRepair() {
        if (!rotating) {
            int hoeSlot = findHoeInHotbar();
            if (hoeSlot == -1) {
                currentState = State.IDLE;
                delayTimer.reset();
                return;
            }

            ItemStack hoe = mc.player.getInventory().getStack(hoeSlot);
            if (hoe.isEmpty() || !(hoe.getItem() instanceof HoeItem)) {
                currentState = State.IDLE;
                delayTimer.reset();
                return;
            }

            if (hoe.getMaxDamage() - hoe.getDamage() > hoe.getMaxDamage() * 0.9) {
                currentState = State.IDLE;
                delayTimer.reset();
                return;
            }

            int xpSlot = InventoryUtils.findItemInHotbar(Items.EXPERIENCE_BOTTLE);
            if (xpSlot == -1) {
                ChatMessage.brandmessage("§eНет XP бутылок для починки мотыги!");
                currentState = State.IDLE;
                delayTimer.reset();
                return;
            }

            InventoryUtils.selectSlot(xpSlot);
            startRotate(mc.player.getBlockPos().up(10));
            return;
        }

        if (!tickRotate()) return;

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);

        delayTimer.reset();
    }

    private void startRotate(BlockPos pos) {
        targetAngle = MathAngle.calculateAngle(Vec3d.ofCenter(pos));
        rotating = true;
    }

    private boolean tickRotate() {
        if (targetAngle == null) return true;
        float step = 15f;
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        float yawDiff = targetAngle.getYaw() - yaw;
        float pitchDiff = targetAngle.getPitch() - pitch;

        float newYaw = yaw + Math.max(-step, Math.min(step, yawDiff));
        float newPitch = pitch + Math.max(-step, Math.min(step, pitchDiff));
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                newYaw, newPitch, mc.player.isOnGround(), false
        ));

        if (Math.abs(yawDiff) <= 1f && Math.abs(pitchDiff) <= 1f) {
            mc.player.setYaw(targetAngle.getYaw());
            mc.player.setPitch(targetAngle.getPitch());
            targetAngle = null;
            rotating = false;
            return true;
        }
        return false;
    }

    private long randomizedDelay() {
        return (long) Math.max(10, actionDelay.getValue() + (Math.random() - 0.5) * 120);
    }

    private boolean shouldRepairHoe() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof HoeItem && hasMending(stack)) {
                if (stack.getMaxDamage() - stack.getDamage() < 15) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMending(ItemStack stack) {
        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments == null) return false;
        for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
            if (entry.matchesKey(Enchantments.MENDING)) {
                return true;
            }
        }
        return false;
    }

    private int findHoeInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof HoeItem) {
                return i;
            }
        }
        return -1;
    }

    private int findAxeInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    private BlockPos findNearestLeaf() {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (int x = -4; x <= 4; x++) {
            for (int y = 0; y <= 8; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = farmPos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.getBlock() == Blocks.OAK_LEAVES) {
                        double dist = mc.player.getBlockPos().getSquaredDistance(pos);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = pos;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private BlockPos findNearestLog() {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (int x = -4; x <= 4; x++) {
            for (int y = 0; y <= 8; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = farmPos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    if (isLog(state)) {
                        double dist = mc.player.getBlockPos().getSquaredDistance(pos);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = pos;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private boolean isLog(BlockState state) {
        return state.getBlock() == Blocks.OAK_LOG;
    }

    private boolean isDirtLike(BlockState state) {
        return state.getBlock() == Blocks.DIRT || state.getBlock() == Blocks.GRASS_BLOCK
                || state.getBlock() == Blocks.COARSE_DIRT || state.getBlock() == Blocks.PODZOL
                || state.getBlock() == Blocks.ROOTED_DIRT;
    }
}
