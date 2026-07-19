# RaidMod

[English](#english) · [Русский](#русский)

## Русский

RaidMod — серверный мод для Fabric 1.20.1, превращающий Nether в зону ограниченных по времени экспедиций. Обычный портал работает как обычно и не начинает экспедицию. Рейд запускается только командой `/expedition`.

### Возможности

- одиночные и совместные экспедиции с подтверждением;
- настраиваемые точки появления и их кулдауны;
- постоянные и автоматически меняющиеся выходы;
- bossbar, sidebar, таймер выхода и частицы;
- сохранение активных экспедиций после перезапуска;
- статистика, таблицы лидеров, убийства и успешные выходы;
- серверная установка — клиентам мод не требуется;
- поддержка OP и необязательных permissions `raidmod.admin` и `raidmod.cooldown.bypass`.

### Установка

1. Установите Fabric Loader и Fabric API для Minecraft 1.20.1.
2. Поместите JAR RaidMod в серверную папку `mods`.
3. Запустите сервер. Конфиги будут созданы в папке `config`.
4. Создайте точки входа и выходные двери административными командами.

### Основные команды

- `/expedition` — запросить одиночную экспедицию;
- `/expedition with <игрок>` — совместная экспедиция;
- `/expedition confirm`, `/expedition cancel` — подтвердить или отменить;
- `/expedition stat [игрок]` — статистика;
- `/expedition top <expedition|kill>` — таблица лидеров;
- `/expedition spawncreate <x> <y> <z> <название>` — создать точку появления;
- `/expedition spawnremove <название>`, `spawnlist`, `spawnclear`;
- `/expedition doorcreate <радиус> <время> <shared> <название>` — создать выход;
- `/expedition doorstatic`, `doorremove`, `doorclear`;
- `/expedition availableexits <количество>` — изменить число активных переменных выходов;
- `/expedition addtime`, `addtimeclear`, `nocd`, `nocdremove` — исключения для игроков;
- `/expedition open`, `/expedition close`, `/expedition reload`.

Остальные параметры можно менять в конфиге или административными подкомандами `/expedition`.

### Конфигурация

`raidmod.json` управляет длительностью, кулдаунами, количеством доступных выходов, ротацией, sidebar, bossbar, частицами и поведением при отключении. `raidmod_messages.json` содержит все отображаемые тексты. Цвета задаются стандартными кодами Minecraft `§`.

Перед обновлением рекомендуется сделать резервную копию мира и конфигов.

## English

RaidMod is a server-side Fabric 1.20.1 mod that turns the Nether into a timed expedition zone. Vanilla Nether portals continue to work and do not start an expedition. A raid starts only through `/expedition`.

### Features

- confirmed solo and cooperative expeditions;
- configurable spawn points and cooldowns;
- permanent and automatically rotating exits;
- boss bar, sidebar, exit countdown and particles;
- active expedition persistence across restarts;
- statistics, leaderboards, kills and successful exits;
- server-side installation — clients do not need the mod;
- OP support and optional `raidmod.admin` and `raidmod.cooldown.bypass` permissions.

### Installation

1. Install Fabric Loader and Fabric API for Minecraft 1.20.1.
2. Put the RaidMod JAR into the server `mods` directory.
3. Start the server. Configuration files are generated in `config`.
4. Create expedition spawn points and exit doors with the admin commands.

### Main commands

- `/expedition` — request a solo expedition;
- `/expedition with <player>` — request a cooperative expedition;
- `/expedition confirm`, `/expedition cancel` — accept or cancel;
- `/expedition stat [player]` — show statistics;
- `/expedition top <expedition|kill>` — show leaderboards;
- `/expedition spawncreate <x> <y> <z> <name>` — create a spawn point;
- `/expedition spawnremove <name>`, `spawnlist`, `spawnclear`;
- `/expedition doorcreate <radius> <time> <shared> <name>` — create an exit;
- `/expedition doorstatic`, `doorremove`, `doorclear`;
- `/expedition availableexits <count>` — change the number of active rotating exits;
- `/expedition addtime`, `addtimeclear`, `nocd`, `nocdremove` — player overrides;
- `/expedition open`, `/expedition close`, `/expedition reload`.

Additional values can be changed in the configuration or through the `/expedition` admin subcommands.

### Configuration

`raidmod.json` controls durations, cooldowns, the number of available exits, rotation, sidebar, boss bar, particles and disconnect behavior. `raidmod_messages.json` contains all visible text. Standard Minecraft `§` formatting codes are supported.

Back up the world and configuration before updating.

