package sadrik.util.entity.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import sadrik.IMinecraft;
import sadrik.events.api.EventHandler;
import sadrik.events.api.EventManager;
import sadrik.events.impl.InteractEntityEvent;
import sadrik.events.impl.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FakePlayerManager implements IMinecraft {

    private static final UUID FP_UUID_BASE = UUID.fromString("66123666-6666-6666-6666-666666666600");
    private static final String FP_NAME_BASE = "Fake Player";

    private static final float HIT_DAMAGE_HP = 2.0F;
    private static final float LOOK_SMOOTH = 0.35F;

    private static final int MAX_FAKES = 12;
    private static final int ID_BASE = 1450000;

    private final Map<Integer, FakePlayerEntity> fakes = new ConcurrentHashMap<>();

    private static FakePlayerManager instance;

    public static FakePlayerManager getInstance() {
        if (instance == null) {
            instance = new FakePlayerManager();
        }
        return instance;
    }

    public void init() {
        EventManager.register(this);
    }

    public void add(String mode, int count) {
        if (mc.world == null || mc.player == null) return;

        int want = MathHelper.clamp(count, 1, MAX_FAKES);

        for (int i = 1; i <= want; i++) {
            FakePlayerEntity fp = fakes.get(i);
            if (fp == null) {
                fp = spawnOne(i, want, mode);
                if (fp != null) fakes.put(i, fp);
            } else {
                positionOne(fp, i, want);
            }
        }

        int cur = want + 1;
        while (true) {
            FakePlayerEntity fp = fakes.remove(cur);
            if (fp == null) break;
            try { fp.remove(); } catch (Throwable ignored) { }
            cur++;
        }
    }

    public void delAll() {
        for (FakePlayerEntity fp : fakes.values()) {
            if (fp == null) continue;
            try { fp.remove(); } catch (Throwable ignored) { }
        }
        fakes.clear();
    }

    public void del(int id) {
        FakePlayerEntity fp = fakes.remove(id);
        if (fp == null) return;
        try { fp.remove(); } catch (Throwable ignored) { }
    }

    @EventHandler
    public void onInteractEntity(InteractEntityEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.getEntity() instanceof FakePlayerEntity fake)) return;
        if (!isOurFake(fake)) return;

        event.setCancelled(true);
        handleDamage(fake);
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (fakes.isEmpty()) return;

        for (FakePlayerEntity fp : fakes.values()) {
            if (fp == null) continue;

            fp.setVelocity(0.0, fp.getVelocity().y, 0.0);
            fp.setSprinting(false);

            try {
                fp.move(MovementType.SELF, Vec3d.ZERO);
            } catch (Throwable ignored) { }

            try {
                fp.limbAnimator.setSpeed(0.0F);
            } catch (Throwable ignored) { }

            tryLookAtPlayer(fp);
        }
    }

    public boolean isOurFake(Entity entity) {
        if (!(entity instanceof FakePlayerEntity)) return false;
        return fakes.containsValue(entity);
    }

    private void handleDamage(FakePlayerEntity fp) {
        if (fp == null) return;

        playSoundAt(fp, SoundEvents.ENTITY_PLAYER_HURT);
        playSoundAt(fp, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT);
        setHurtFlash(fp, 6);

        try {
            float next = fp.getHealth() - HIT_DAMAGE_HP;

            if (next > 0.0F) {
                fp.setHealth(next);
                return;
            }

            if (popTotemAndReset(fp)) return;

            fp.setHealth(0.0F);
        } catch (Throwable ignored) { }
    }

    private FakePlayerEntity spawnOne(int slot, int total, String mode) {
        if (mc.world == null || mc.player == null) return null;

        UUID uuid = new UUID(FP_UUID_BASE.getMostSignificantBits(), FP_UUID_BASE.getLeastSignificantBits() + (slot - 1));
        String name = slot == 1 ? FP_NAME_BASE : (FP_NAME_BASE + " " + slot);
        int forcedId = ID_BASE + slot;

        FakePlayerEntity fp;
        try {
            fp = new FakePlayerEntity((ClientWorld) mc.world, new GameProfile(uuid, name), forcedId);
        } catch (Throwable t) {
            return null;
        }

        fp.copyPositionAndRotation(mc.player);
        positionOne(fp, slot, total);

        String m = mode == null ? "default" : mode.trim().toLowerCase(java.util.Locale.US);
        if (m.isEmpty()) m = "default";

        switch (m) {
            case "nether":
            case "netherite":
                equipNether(fp);
                break;
            case "diamond":
                equipDiamond(fp);
                break;
            default:
                equipDefault(fp);
                break;
        }

        try {
            float max = mc.player.getMaxHealth();
            float hp = mc.player.getHealth();
            if (hp < 0.0F) hp = 0.0F;
            if (hp > max) hp = max;
            fp.setHealth(hp);
        } catch (Throwable ignored) { }

        try {
            fp.setAbsorptionAmount(0.0F);
        } catch (Throwable ignored) { }

        fp.spawn();
        return fp;
    }

    private void positionOne(FakePlayerEntity fp, int slot, int total) {
        if (fp == null || mc.player == null) return;

        double r = 2.2;

        double a;
        if (total <= 1) a = 0.0;
        else if (total == 2) a = slot == 1 ? 0.0 : Math.PI;
        else a = (slot - 1) * (Math.PI * 2.0) / (double) total;

        Vec3d p = mc.player.getEntityPos();
        double x = p.x + Math.cos(a) * r;
        double y = p.y;
        double z = p.z + Math.sin(a) * r;

        setPosCompat(fp, x, y, z);

        try {
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            fp.setYaw(yaw);
            fp.setBodyYaw(yaw);
            fp.setHeadYaw(yaw);
            fp.setPitch(pitch);
        } catch (Throwable ignored) { }
    }

    private void setPosCompat(Entity e, double x, double y, double z) {
        if (e == null) return;

        try {
            Method m = e.getClass().getMethod("setPos", double.class, double.class, double.class);
            m.invoke(e, x, y, z);
            return;
        } catch (Throwable ignored) { }

        try {
            Method m = e.getClass().getMethod("setPosition", double.class, double.class, double.class);
            m.invoke(e, x, y, z);
            return;
        } catch (Throwable ignored) { }

        try {
            Method m = e.getClass().getMethod("updatePosition", double.class, double.class, double.class);
            m.invoke(e, x, y, z);
        } catch (Throwable ignored) { }
    }

    private void equipDefault(FakePlayerEntity fp) {
        try {
            fp.setStackInHand(Hand.MAIN_HAND, mc.player.getMainHandStack().copy());
        } catch (Throwable ignored) { }

        try {
            ItemStack off = mc.player.getOffHandStack();
            if (off == null || off.isEmpty() || off.getItem() != Items.TOTEM_OF_UNDYING) {
                fp.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            } else {
                fp.setStackInHand(Hand.OFF_HAND, off.copy());
            }
        } catch (Throwable ignored) {
            try {
                fp.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            } catch (Throwable ignored2) { }
        }

        tryCopyArmor(fp);
    }

    private void equipNether(FakePlayerEntity fp) {
        try {
            ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
            addEnchant(sword, Enchantments.SHARPNESS, 5);
            addEnchant(sword, Enchantments.UNBREAKING, 3);
            addEnchant(sword, Enchantments.MENDING, 1);
            addEnchant(sword, Enchantments.FIRE_ASPECT, 2);
            addEnchant(sword, Enchantments.LOOTING, 3);
            fp.setStackInHand(Hand.MAIN_HAND, sword);
        } catch (Throwable ignored) { }

        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        addEnchant(totem, Enchantments.UNBREAKING, 3);
        addEnchant(totem, Enchantments.MENDING, 1);
        try {
            fp.setStackInHand(Hand.OFF_HAND, totem);
        } catch (Throwable ignored) { }

        try {
            ItemStack h = new ItemStack(Items.NETHERITE_HELMET);
            addEnchant(h, Enchantments.PROTECTION, 4);
            addEnchant(h, Enchantments.UNBREAKING, 3);
            addEnchant(h, Enchantments.MENDING, 1);
            addEnchant(h, Enchantments.RESPIRATION, 3);
            addEnchant(h, Enchantments.AQUA_AFFINITY, 1);
            fp.equipStack(EquipmentSlot.HEAD, h);

            ItemStack c = new ItemStack(Items.NETHERITE_CHESTPLATE);
            addEnchant(c, Enchantments.PROTECTION, 4);
            addEnchant(c, Enchantments.UNBREAKING, 3);
            addEnchant(c, Enchantments.MENDING, 1);
            fp.equipStack(EquipmentSlot.CHEST, c);

            ItemStack l = new ItemStack(Items.NETHERITE_LEGGINGS);
            addEnchant(l, Enchantments.PROTECTION, 4);
            addEnchant(l, Enchantments.UNBREAKING, 3);
            addEnchant(l, Enchantments.MENDING, 1);
            fp.equipStack(EquipmentSlot.LEGS, l);

            ItemStack b = new ItemStack(Items.NETHERITE_BOOTS);
            addEnchant(b, Enchantments.PROTECTION, 4);
            addEnchant(b, Enchantments.UNBREAKING, 3);
            addEnchant(b, Enchantments.MENDING, 1);
            addEnchant(b, Enchantments.FEATHER_FALLING, 4);
            fp.equipStack(EquipmentSlot.FEET, b);
        } catch (Throwable ignored) { }
    }

    private void equipDiamond(FakePlayerEntity fp) {
        try {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            addEnchant(sword, Enchantments.SHARPNESS, 5);
            addEnchant(sword, Enchantments.UNBREAKING, 3);
            addEnchant(sword, Enchantments.MENDING, 1);
            addEnchant(sword, Enchantments.FIRE_ASPECT, 2);
            addEnchant(sword, Enchantments.LOOTING, 3);
            fp.setStackInHand(Hand.MAIN_HAND, sword);
        } catch (Throwable ignored) { }

        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        addEnchant(totem, Enchantments.UNBREAKING, 3);
        addEnchant(totem, Enchantments.MENDING, 1);
        try {
            fp.setStackInHand(Hand.OFF_HAND, totem);
        } catch (Throwable ignored) { }

        try {
            ItemStack h = new ItemStack(Items.DIAMOND_HELMET);
            addEnchant(h, Enchantments.PROTECTION, 4);
            addEnchant(h, Enchantments.UNBREAKING, 3);
            addEnchant(h, Enchantments.MENDING, 1);
            addEnchant(h, Enchantments.RESPIRATION, 3);
            addEnchant(h, Enchantments.AQUA_AFFINITY, 1);
            fp.equipStack(EquipmentSlot.HEAD, h);

            ItemStack c = new ItemStack(Items.DIAMOND_CHESTPLATE);
            addEnchant(c, Enchantments.PROTECTION, 4);
            addEnchant(c, Enchantments.UNBREAKING, 3);
            addEnchant(c, Enchantments.MENDING, 1);
            fp.equipStack(EquipmentSlot.CHEST, c);

            ItemStack l = new ItemStack(Items.DIAMOND_LEGGINGS);
            addEnchant(l, Enchantments.PROTECTION, 4);
            addEnchant(l, Enchantments.UNBREAKING, 3);
            addEnchant(l, Enchantments.MENDING, 1);
            fp.equipStack(EquipmentSlot.LEGS, l);

            ItemStack b = new ItemStack(Items.DIAMOND_BOOTS);
            addEnchant(b, Enchantments.PROTECTION, 4);
            addEnchant(b, Enchantments.UNBREAKING, 3);
            addEnchant(b, Enchantments.MENDING, 1);
            addEnchant(b, Enchantments.FEATHER_FALLING, 4);
            fp.equipStack(EquipmentSlot.FEET, b);
        } catch (Throwable ignored) { }
    }

    private void tryCopyArmor(FakePlayerEntity fp) {
        if (mc.player == null) return;
        try {
            fp.equipStack(EquipmentSlot.HEAD, mc.player.getEquippedStack(EquipmentSlot.HEAD).copy());
            fp.equipStack(EquipmentSlot.CHEST, mc.player.getEquippedStack(EquipmentSlot.CHEST).copy());
            fp.equipStack(EquipmentSlot.LEGS, mc.player.getEquippedStack(EquipmentSlot.LEGS).copy());
            fp.equipStack(EquipmentSlot.FEET, mc.player.getEquippedStack(EquipmentSlot.FEET).copy());
        } catch (Throwable ignored) { }
    }

    private boolean hasDiamondOrNetherKit(FakePlayerEntity fp) {
        if (fp == null) return false;
        try {
            ItemStack mh = fp.getMainHandStack();
            if (mh != null && !mh.isEmpty()) {
                if (mh.getItem() == Items.NETHERITE_SWORD || mh.getItem() == Items.DIAMOND_SWORD) return true;
            }
        } catch (Throwable ignored) { }
        try {
            EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (EquipmentSlot slot : armorSlots) {
                ItemStack s = fp.getEquippedStack(slot);
                if (s == null || s.isEmpty()) continue;
                if (s.getItem() == Items.NETHERITE_HELMET || s.getItem() == Items.NETHERITE_CHESTPLATE
                        || s.getItem() == Items.NETHERITE_LEGGINGS || s.getItem() == Items.NETHERITE_BOOTS)
                    return true;
                if (s.getItem() == Items.DIAMOND_HELMET || s.getItem() == Items.DIAMOND_CHESTPLATE
                        || s.getItem() == Items.DIAMOND_LEGGINGS || s.getItem() == Items.DIAMOND_BOOTS)
                    return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private void tryLookAtPlayer(FakePlayerEntity fp) {
        if (mc.player == null) return;

        Vec3d from = fp.getEntityPos().add(0.0, fp.getStandingEyeHeight(), 0.0);
        Vec3d to = mc.player.getEntityPos().add(0.0, mc.player.getStandingEyeHeight(), 0.0);
        Vec3d d = to.subtract(from);

        double distXZ = Math.sqrt(d.x * d.x + d.z * d.z);
        if (distXZ < 1.0E-4) return;

        float targetYaw = (float) (MathHelper.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
        float targetPitch = (float) (-(MathHelper.atan2(d.y, distXZ) * (180.0 / Math.PI)));

        float yaw = lerpAngle(fp.getYaw(), targetYaw);
        float pitch = MathHelper.clamp(lerp(fp.getPitch(), targetPitch), -89.9f, 89.9f);

        fp.setYaw(yaw);
        fp.setBodyYaw(yaw);
        fp.setHeadYaw(yaw);
        fp.setPitch(pitch);
    }

    private float lerp(float a, float b) {
        return a + (b - a) * LOOK_SMOOTH;
    }

    private float lerpAngle(float a, float b) {
        float d = MathHelper.wrapDegrees(b - a);
        return a + d * LOOK_SMOOTH;
    }

    private boolean popTotemAndReset(FakePlayerEntity fp) {
        if (fp == null || mc.player == null || mc.world == null) return false;

        try {
            ItemStack off = fp.getOffHandStack();
            boolean offTotem = off != null && !off.isEmpty() && off.getItem() == Items.TOTEM_OF_UNDYING;

            ItemStack main = fp.getMainHandStack();
            boolean mainTotem = main != null && !main.isEmpty() && main.getItem() == Items.TOTEM_OF_UNDYING;

            if (!offTotem && !mainTotem) return false;

            if (offTotem) fp.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            else fp.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);

            playSoundAt(fp, SoundEvents.ITEM_TOTEM_USE);

            try {
                if (mc.player.networkHandler != null) {
                    new EntityStatusS2CPacket(fp, (byte) 35).apply(mc.player.networkHandler);
                }
            } catch (Throwable ignored) { }

            try {
                fp.setAbsorptionAmount(0.0F);
            } catch (Throwable ignored) { }

            float max = 20.0F;
            try {
                max = fp.getMaxHealth();
            } catch (Throwable ignored) {
                try {
                    if (mc.player != null) max = mc.player.getMaxHealth();
                } catch (Throwable ignored2) { }
            }
            if (max < 1.0F) max = 20.0F;

            fp.setHealth(max);

            try {
                ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
                if (hasDiamondOrNetherKit(fp)) {
                    addEnchant(totem, Enchantments.UNBREAKING, 3);
                    addEnchant(totem, Enchantments.MENDING, 1);
                }
                fp.setStackInHand(Hand.OFF_HAND, totem);
            } catch (Throwable ignored) { }

            setHurtFlash(fp, 10);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void addEnchant(ItemStack stack, Object enchantKeyOrEntryOrEnch, int level) {
        if (stack == null || stack.isEmpty() || enchantKeyOrEntryOrEnch == null) return;

        if (tryInvokeAddEnchantment(stack, enchantKeyOrEntryOrEnch, level)) return;

        Object resolved = resolveEnchantmentParam(enchantKeyOrEntryOrEnch);
        if (resolved != null && resolved != enchantKeyOrEntryOrEnch) {
            if (tryInvokeAddEnchantment(stack, resolved, level)) return;
        }

        Object v = tryCallNoArgs(resolved, "value");
        if (v != null && v != resolved) {
            tryInvokeAddEnchantment(stack, v, level);
        }
    }

    private static boolean tryInvokeAddEnchantment(ItemStack stack, Object enchantParam, int level) {
        if (stack == null || stack.isEmpty() || enchantParam == null) return false;
        try {
            for (Method m : ItemStack.class.getMethods()) {
                if (!"addEnchantment".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2 || p[1] != int.class) continue;
                if (!p[0].isInstance(enchantParam)) continue;
                m.invoke(stack, enchantParam, level);
                return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static Object tryCallNoArgs(Object obj, String name) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(name);
            if (m.getParameterCount() != 0) return null;
            return m.invoke(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object unwrapOptionalLike(Object obj) {
        if (obj == null) return null;
        if ("java.util.Optional".equals(obj.getClass().getName())) {
            try {
                Method isPresent = obj.getClass().getMethod("isPresent");
                Object pres = isPresent.invoke(obj);
                if (!(pres instanceof Boolean) || !((Boolean) pres)) return null;
                Method get = obj.getClass().getMethod("get");
                return get.invoke(obj);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return obj;
    }

    private static Object resolveEnchantmentParam(Object keyOrEntryOrEnch) {
        if (keyOrEntryOrEnch == null) return null;
        if (keyOrEntryOrEnch instanceof Enchantment) return keyOrEntryOrEnch;

        Object v = tryCallNoArgs(keyOrEntryOrEnch, "value");
        if (v instanceof Enchantment) return v;

        String cn = keyOrEntryOrEnch.getClass().getName();
        boolean looksLikeKey = cn.contains("RegistryKey") || cn.contains("registry.RegistryKey");
        if (!looksLikeKey) return keyOrEntryOrEnch;

        MinecraftClient mcc = MinecraftClient.getInstance();
        if (mcc.world == null) return keyOrEntryOrEnch;

        Object enchRegKey = null;
        try {
            Class<?> rk = Class.forName("net.minecraft.registry.RegistryKeys");
            Field f = rk.getField("ENCHANTMENT");
            enchRegKey = f.get(null);
        } catch (Throwable ignored) { }
        if (enchRegKey == null) return keyOrEntryOrEnch;

        Object rm = null;
        try {
            Method m = mcc.world.getClass().getMethod("getRegistryManager");
            rm = m.invoke(mcc.world);
        } catch (Throwable ignored) { }
        if (rm == null) return keyOrEntryOrEnch;

        Object reg = null;
        reg = invokeByName1(rm, "getOrThrow", enchRegKey);
        if (reg == null) reg = invokeByName1(rm, "get", enchRegKey);
        if (reg == null) return keyOrEntryOrEnch;

        Object out = null;
        out = invokeByName1(reg, "getOptional", keyOrEntryOrEnch);
        out = unwrapOptionalLike(out);
        if (out != null) return out;

        out = invokeByName1(reg, "getOrEmpty", keyOrEntryOrEnch);
        out = unwrapOptionalLike(out);
        if (out != null) return out;

        out = invokeByName1(reg, "getEntry", keyOrEntryOrEnch);
        out = unwrapOptionalLike(out);
        if (out != null) return out;

        out = invokeByName1(reg, "get", keyOrEntryOrEnch);
        out = unwrapOptionalLike(out);
        if (out != null) return out;

        return keyOrEntryOrEnch;
    }

    private static Object invokeByName1(Object target, String name, Object arg) {
        if (target == null || name == null || arg == null) return null;
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!name.equals(m.getName())) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> p0 = m.getParameterTypes()[0];
                if (!p0.isInstance(arg) && !p0.isAssignableFrom(arg.getClass())) continue;
                return m.invoke(target, arg);
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private void playSoundAt(Entity e, net.minecraft.sound.SoundEvent sound) {
        if (mc.player == null || mc.world == null || e == null || sound == null) return;
        try {
            mc.world.playSoundFromEntity(mc.player, e, sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
        } catch (Throwable ignored) { }
    }

    private void setHurtFlash(Object entity, int ticks) {
        if (entity == null) return;
        if (ticks < 1) ticks = 1;
        trySetIntField(entity, "hurtTime", ticks);
        trySetIntField(entity, "maxHurtTime", ticks);
        trySetIntField(entity, "timeUntilRegen", 0);
    }

    private void trySetIntField(Object obj, String name, int v) {
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null) return;
            f.setAccessible(true);
            f.setInt(obj, v);
        } catch (Throwable ignored) { }
    }

    private Field findField(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (Throwable ignored) { }
            cur = cur.getSuperclass();
        }
        return null;
    }
}
