package com.hanset.raidmod.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class PermissionHelper {
    public static final String RAID_COOLDOWN_BYPASS = "raidmod.cooldown.bypass";
    public static final String ADMIN = "raidmod.admin";

    public static boolean hasAdminPermission(CommandSourceStack source) {
        return source.hasPermission(2) || source.getEntity() instanceof ServerPlayer player && hasPermission(player, ADMIN);
    }

    public static boolean hasPermission(ServerPlayer player, String permission) {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Class<?> apiType = Class.forName("net.luckperms.api.LuckPerms");
            Object manager = apiType.getMethod("getUserManager").invoke(api);
            Class<?> managerType = Class.forName("net.luckperms.api.model.user.UserManager");
            Object user = managerType.getMethod("getUser", java.util.UUID.class).invoke(manager, player.getUUID());
            if (user == null) return false;
            Class<?> userType = Class.forName("net.luckperms.api.model.user.User");
            Object cached = userType.getMethod("getCachedData").invoke(user);
            Class<?> cachedType = Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
            Object permissions = cachedType.getMethod("getPermissionData").invoke(cached);
            Class<?> permissionType = Class.forName("net.luckperms.api.cacheddata.CachedPermissionData");
            Object result = permissionType.getMethod("checkPermission", String.class).invoke(permissions, permission);
            return (boolean) Class.forName("net.luckperms.api.util.Tristate").getMethod("asBoolean").invoke(result);
        } catch (ReflectiveOperationException | LinkageError | IllegalStateException e) {
            return false;
        }
    }
}
