package com.hanset.raidmod.util;

import java.lang.reflect.Method;

/**
 * Проверка пермишенов через fabric-permissions-api (LuckPerms).
 * Использует reflection — мод работает без LuckPerms, при наличии — проверяет пермишены.
 */
public final class PermissionHelper {

    /** Пермишен для обхода кулдауна на /expedition (для донатеров). */
    public static final String RAID_COOLDOWN_BYPASS = "raidmod.cooldown.bypass";

    /** Пермишен для всех админских команд (запасной — OP 2). */
    public static final String ADMIN = "raidmod.admin";

    /** Проверяет право на админские команды: OP 2 или raidmod.admin. */
    public static boolean hasAdminPermission(net.minecraft.commands.CommandSourceStack source) {
        return source.hasPermission(2)
                || (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer p && hasPermission(p, ADMIN));
    }

    private static Boolean permissionsApiAvailable = null;

    /**
     * Проверяет, есть ли у игрока пермишен. Работает с LuckPerms через fabric-permissions-api.
     * @return true если пермишен есть, false если нет или API недоступен
     */
    public static boolean hasPermission(net.minecraft.server.level.ServerPlayer player, String permission) {
        if (permissionsApiAvailable == Boolean.FALSE) return false;
        try {
            Class<?> permClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Method check = permClass.getMethod("check", net.minecraft.world.entity.Entity.class, String.class);
            permissionsApiAvailable = true;
            return Boolean.TRUE.equals(check.invoke(null, player, permission));
        } catch (Throwable e) {
            permissionsApiAvailable = false;
            return false;
        }
    }
}
