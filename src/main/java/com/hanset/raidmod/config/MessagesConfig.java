package com.hanset.raidmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.network.chat.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Тексты мода. Файл: config/raidmod_messages.json
 * Поддержка плейсхолдеров: {0}, {1}, ...
 */
public class MessagesConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static MessagesConfig INSTANCE;

    public Map<String, String> messages = new HashMap<>();

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("error.player_only", "Команда только для игроков"),
            Map.entry("error.no_raid_in_nether", "§cКоманду /expedition нельзя использовать в аду!"),
            Map.entry("error.already_in_nether", "§cВы уже в аду!"),
            Map.entry("error.cooldown", "§cКулдаун: подождите ещё §f{0} §cсек."),
            Map.entry("error.nether_unavailable", "Измерение ада недоступно"),
            Map.entry("error.no_spawns", "§cНет точек спавна! Добавьте их через /expedition spawncreate <x> <y> <z> <название>"),
            Map.entry("error.no_available_spawns", "§cНет доступных точек для спавна, ожидайте."),
            Map.entry("error.no_spawn_consumed", "§7Вы не потратили использование команды."),
            Map.entry("error.pending_exists", "§cУ вас уже есть ожидающее подтверждение. Используйте §f/expedition confirm §cили §f/expedition cancel§c."),
            Map.entry("confirm.single_prefix", "§e§lПодтвердите выход в экспедицию §r§7— нажмите "),
            Map.entry("confirm.paired_inviter", "§e§lПодтвердите совместную вылазку с §f{0}§e §r§7— нажмите "),
            Map.entry("confirm.paired_target", "§e§l{0} §eприглашает вас в совместную вылазку §r§7— нажмите "),
            Map.entry("confirm.btn_confirm", "[Подтвердить]"),
            Map.entry("confirm.btn_cancel", "[Отменить]"),
            Map.entry("confirm.hover_confirm", "Нажмите для подтверждения"),
            Map.entry("confirm.hover_cancel", "Нажмите для отмены"),
            Map.entry("confirm.suffix", " §7— можно нажать в чате"),
            Map.entry("confirm.or", " §7или "),
            Map.entry("error.nether_only", "§cЭту команду можно использовать только в аду!"),
            Map.entry("error.aim_at_door", "§cНаведите на дверь и используйте команду снова"),
            Map.entry("error.aim_at_door_block", "§cНаведите на блок двери!"),
            Map.entry("door.created", "§aДверь §f{0} §aпомечена! Радиус: {1}, время: {2} сек{3}"),
            Map.entry("door.shared_suffix", ", совместный выход"),
            Map.entry("door.already_marked", "§eЭта дверь уже помечена. Удалите её и создайте заново."),
            Map.entry("spawn.added", "§aТочка спавна §f{0} §aдобавлена: {1} {2} {3}"),
            Map.entry("spawn.exists", "§eТочка спавна с таким названием уже существует"),
            Map.entry("spawn.removed", "§aТочка спавна §f{0} §aудалена"),
            Map.entry("spawn.not_found", "§cТочка спавна не найдена"),
            Map.entry("spawnlist.header", "§6§lТочки спавна §7({0} шт):"),
            Map.entry("spawnlist.line", "§7{0}. §f{1} §7— §a{2} §7{3} §7{4}"),
            Map.entry("door.cleared", "§aУдалено выходных дверей: §f{0}"),
            Map.entry("door.no_marked", "§cЗдесь нет помеченной выходной двери"),
            Map.entry("door.static_on", "§aДверь помечена как §fстатичный §aвыход (всегда доступна)"),
            Map.entry("door.static_off", "§aДверь снята со статичного выхода (теперь переменная)"),
            Map.entry("door.removed", "§aВыходная дверь удалена"),
            Map.entry("reload.done", "§aКонфиг перезагружен. Время: §f{0} §aсек, кулдаун: §f{1} §aсек, кулдаун спавна: §f{2} §aсек, переменных выходов: §f{3}§a."),
            Map.entry("timeraid.set", "§aВремя экспедиции установлено: §f{0} §aсек. Используйте §f/expedition reload §aдля применения."),
            Map.entry("servercooldown.set", "§aКулдаун /expedition установлен: §f{0} §aсек. Используйте §f/expedition reload §aдля применения."),
            Map.entry("spawncooldown.set", "§aКулдаун точки спавна установлен: §f{0} §aсек."),
            Map.entry("spawncooldown.cleared", "§aКулдауны всех точек спавна сброшены."),
            Map.entry("variableexits.set", "§aИнтервал смены переменных выходов: §f{0} §aсек ({1} мин)"),
            Map.entry("variableexits.count_set", "§aКоличество доступных переменных выходов: §f{0}"),
            Map.entry("addtime.done", "§aИгроку §f{0} §aдобавлено §f+{1} §aсек. Всего доп. времени: §f{2} §aсек."),
            Map.entry("hidenametags.enabled", "§aНики в аду: §fскрыты§a."),
            Map.entry("hidenametags.disabled", "§aНики в аду: §fвидны§a."),
            Map.entry("variableexits.refreshed", "§aПеременные выходы обновлены"),
            Map.entry("with.self", "§cНельзя пригласить самого себя"),
            Map.entry("with.target_in_nether", "§cИгрок §f{0} §cуже в аду"),
            Map.entry("with.cooldown_self", "§cКулдаун: подождите ещё перед экспедицией вдвоём"),
            Map.entry("with.cooldown_target", "§cУ игрока §f{0} §cкулдаун"),
            Map.entry("with.pending_both", "§cУ вас или у приглашённого уже есть ожидающее подтверждение."),
            Map.entry("confirm.pending", "§aВы подтвердили. Ожидайте подтверждения от второго игрока."),
            Map.entry("confirm.no_pending", "§cУ вас нет ожидающего подтверждения экспедиции"),
            Map.entry("confirm.other_unavailable", "§cВторой игрок недоступен"),
            Map.entry("cancel.done", "§aВы отменили выход в экспедицию. Кулдаун не потрачен."),
            Map.entry("cancel.paired", "§7{0} отменил совместную вылазку."),
            Map.entry("stat.no_data", "§cДанные недоступны"),
            Map.entry("stat.player_not_found", "§cИгрок §f{0} §cне найден в статистике"),
            Map.entry("stat.header", "§6§l═══ §fСтатистика §e{0} §6§l═══"),
            Map.entry("stat.raids", "§7Экспедиций: §f{0}"),
            Map.entry("stat.success", "§7Успешных выходов: §a{0}"),
            Map.entry("stat.time", "§7Время в экспедициях: §f{0} §7(ч:м)"),
            Map.entry("stat.kd", "§7Убийств / Смертей: §c{0} §7/ §c{1}"),
            Map.entry("top.header_raid", "§6§l═══ Топ-10 по §eэкспедициям §6§l═══"),
            Map.entry("top.header_kill", "§6§l═══ Топ-10 по §cубийствам §6§l═══"),
            Map.entry("top.no_data", "§7Нет данных"),
            Map.entry("raid.teleported_single", "§aВы телепортированы в ад: §f{0}§a. Найдите выходную дверь!"),
            Map.entry("raid.teleported_paired", "§aВы телепортированы в ад с §f{0}§a: §f{1}§a. Найдите выходную дверь!"),
            Map.entry("spawn.cleared", "§aУдалено точек спавна: {0}"),
            Map.entry("raid.time_expired", "§cВремя экспедиции истекло!"),
            Map.entry("raid.time_expired_offline", "§cВремя экспедиции истекло, пока вы были оффлайн!"),
            Map.entry("raid.absent_too_long", "§cВы отсутствовали в экспедиции слишком долго!"),
            Map.entry("raid.bossbar", "§cЭкспедиция: §f{0} §cосталось"),
            Map.entry("raid.bossbar_title", "Экспедиция"),
            Map.entry("exit.cancelled", "§cВыход отменён!"),
            Map.entry("exit.countdown", "§e{0} §a— выход через §f{1} §aсек. §7(не отходите)"),
            Map.entry("exit.need_more", "§e{0} §7— §cдля этого выхода нужен ещё один игрок в радиусе"),
            Map.entry("exit.returned", "§aВы вернулись на точку респавна!"),
            Map.entry("nocd.added", "§aИгроку §f{0} §aвыдан доступ к экспедициям без кулдауна."),
            Map.entry("nocd.removed", "§aУ игрока §f{0} §aудалён обход кулдауна."),
            Map.entry("addtime.cleared", "§aДополнительное время игрока §f{0} §aсброшено."),
            Map.entry("statclear.all", "§aСтатистика экспедиций очищена для всех игроков."),
            Map.entry("statclear.player", "§aСтатистика экспедиций очищена для игрока §f{0}§a."),
            Map.entry("confirm.expired_single", "§7Запрос на экспедицию истёк."),
            Map.entry("confirm.expired_paired", "§7Запрос на совместную вылазку истёк."),
            Map.entry("paired.disconnect", "§7Игрок отключился. Совместная вылазка отменена."),
            Map.entry("scoreboard.title", "§6§lExpedition"),
            Map.entry("scoreboard.no_active", "§7  Нет активных выходов"),
            Map.entry("scoreboard.type_paired", "§b§o2 игрока"),
            Map.entry("scoreboard.type_single", "§a§oодиночный"),
            Map.entry("scoreboard.static_suffix", " §8[статичный]"),
            Map.entry("scoreboard.exit_line", "§7> §e{0} §8- {1}{2}"),
            Map.entry("scoreboard.rotation_timer", "§7Смена выходов через §f{0}"),
            Map.entry("top.line", "{0} §f{1} §7— §f{2}"),
            Map.entry("lock.open", "§a§l✦ Доступ к экспедициям открыт. §r§7Удачных вам Голодных Игр! §a✦"),
            Map.entry("lock.close", "§c§l✦ Доступ к экспедициям закрыт. §r§7Экспедиции будут доступны в скором времени. §c✦"),
            Map.entry("error.raids_closed", "§cЭкспедиции временно закрыты. Попробуйте позже.")
    );

    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("raidmod_messages.json");
    }

    public static MessagesConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static MessagesConfig load() {
        Path path = getConfigPath();
        MessagesConfig config = new MessagesConfig();
        config.messages = new HashMap<>(DEFAULTS);

        if (Files.exists(path)) {
            try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                @SuppressWarnings("unchecked")
                Map<String, String> loaded = GSON.fromJson(reader, Map.class);
                if (loaded != null) {
                    for (Map.Entry<String, String> entry : loaded.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            config.messages.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("raidmod").error("Failed to read RaidMod messages {}", path, e);
            }
        }
        config.save();
        return config;
    }

    public void save() {
        Path path = getConfigPath();
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(temp), StandardCharsets.UTF_8)) {
                GSON.toJson(messages, writer);
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Failed to save messages config", e);
        }
    }

    public String get(String key) {
        String s = messages.get(key);
        return s != null ? s : key;
    }

    public String get(String key, String... args) {
        String s = get(key);
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", args[i] != null ? args[i] : "");
        }
        return s;
    }

    public static Component msg(String key) {
        return Component.literal(get().get(key));
    }

    public static Component msg(String key, String... args) {
        return Component.literal(get().get(key, args));
    }

    public static void reload() {
        INSTANCE = load();
    }
}
