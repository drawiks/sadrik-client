package sadrik.util.entity.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

import java.lang.reflect.Method;

public class FakePlayerEntity extends OtherClientPlayerEntity {

    private final int forcedId;

    public FakePlayerEntity(ClientWorld world, GameProfile profile, int forcedId) {
        super(world, profile);
        this.forcedId = forcedId;
    }

    public void spawn() {
        try {
            this.unsetRemoved();
        } catch (Throwable ignored) {
        }
        Object w = this.getEntityWorld();
        if (w == null) return;
        addEntityReflect(w, this, forcedId);
    }

    public void remove() {
        Object w = this.getEntityWorld();
        if (w == null) return;
        removeEntityReflect(w, this.getId());
        try {
            this.onRemoved();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
    }

    private static void addEntityReflect(Object world, Entity e, int id) {
        Method m2 = null;
        Method m1 = null;

        try {
            for (Method m : world.getClass().getMethods()) {
                if (!"addEntity".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[0] == int.class && Entity.class.isAssignableFrom(p[1])) {
                    m2 = m;
                    break;
                }
                if (p.length == 1 && Entity.class.isAssignableFrom(p[0])) {
                    m1 = m;
                }
            }
        } catch (Throwable ignored) {
        }

        if (m2 != null) {
            try {
                m2.invoke(world, id, e);
                return;
            } catch (Throwable ignored) {
            }
        }

        if (m1 != null) {
            try {
                m1.invoke(world, e);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void removeEntityReflect(Object world, int id) {
        Method m2 = null;
        Method m1 = null;

        try {
            for (Method m : world.getClass().getMethods()) {
                if (!"removeEntity".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[0] == int.class) {
                    m2 = m;
                    break;
                }
                if (p.length == 1 && p[0] == int.class) {
                    m1 = m;
                }
            }
        } catch (Throwable ignored) {
        }

        if (m2 != null) {
            try {
                Object reason = discardedReason(m2.getParameterTypes()[1]);
                m2.invoke(world, id, reason);
                return;
            } catch (Throwable ignored) {
            }
        }

        if (m1 != null) {
            try {
                m1.invoke(world, id);
            } catch (Throwable ignored) {
            }
        }
    }

    private static Object discardedReason(Class<?> enumClass) {
        if (enumClass == null) return null;
        try {
            if (!enumClass.isEnum()) return null;
            Object[] c = enumClass.getEnumConstants();
            if (c == null || c.length == 0) return null;
            for (Object o : c) if (o != null && "DISCARDED".equals(String.valueOf(o))) return o;
            return c[0];
        } catch (Throwable ignored) {
            return null;
        }
    }
}
