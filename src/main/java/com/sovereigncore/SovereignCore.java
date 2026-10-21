package com.sovereigncore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Mod(SovereignCore.MOD_ID)
public final class SovereignCore {
    public static final String MOD_ID = "sovereigncore";
    public static final String VERSION = "1.1.0-forge-1.20.1";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Pattern SAFE_ITEM = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private static MinecraftServer server;
    private static Path dataFile;
    private static final Map<UUID, Map<String, SavedPos>> HOMES = new HashMap<>();
    private static final Map<String, SavedPos> WARPS = new LinkedHashMap<>();
    private static final Map<UUID, String> RANKS = new HashMap<>();
    private static final Map<UUID, String> KNOWN_NAMES = new HashMap<>();
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = new LinkedHashMap<>();
    private static final Map<String, String> ROLE_LABELS = new LinkedHashMap<>();
    private static final Map<UUID, SavedPos> FROZEN = new HashMap<>();
    private static final Map<UUID, TeleportRequest> TPA_REQUESTS = new HashMap<>();
    private static SavedPos spawn;

    public SovereignCore() {
        NetworkHandler.register();
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerTick);
        LOGGER.info("SovereignCore Forge {} loaded", VERSION);
    }

    // -------------------- Lifecycle and storage --------------------

    private void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("sovereigncore.properties");
        loadData();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        saveData();
        FROZEN.clear();
        TPA_REQUESTS.clear();
        server = null;
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        KNOWN_NAMES.put(player.getUUID(), player.getGameProfile().getName());
        saveData();
    }

    private void loadData() {
        HOMES.clear(); WARPS.clear(); RANKS.clear(); KNOWN_NAMES.clear(); ROLE_PERMISSIONS.clear(); ROLE_LABELS.clear(); spawn = null;
        Properties p = new Properties();
        try {
            if (dataFile != null && Files.exists(dataFile)) {
                try (InputStream in = Files.newInputStream(dataFile)) { p.load(in); }
            }
        } catch (IOException ex) {
            LOGGER.error("Could not load SovereignCore data", ex);
        }
        for (String key : p.stringPropertyNames()) {
            String value = p.getProperty(key, "");
            try {
                if (key.startsWith("home.")) {
                    String[] bits = key.split("\\.", 3);
                    if (bits.length == 3) {
                        UUID id = UUID.fromString(bits[1]);
                        SavedPos pos = SavedPos.parse(value);
                        if (pos != null) HOMES.computeIfAbsent(id, k -> new LinkedHashMap<>()).put(bits[2].toLowerCase(Locale.ROOT), pos);
                    }
                } else if (key.startsWith("warp.")) {
                    SavedPos pos = SavedPos.parse(value);
                    if (pos != null) WARPS.put(key.substring(5).toLowerCase(Locale.ROOT), pos);
                } else if (key.startsWith("rank.")) {
                    RANKS.put(UUID.fromString(key.substring(5)), value.toLowerCase(Locale.ROOT));
                } else if (key.startsWith("name.")) {
                    KNOWN_NAMES.put(UUID.fromString(key.substring(5)), value);
                } else if (key.startsWith("permissions.")) {
                    ROLE_PERMISSIONS.put(key.substring("permissions.".length()).toLowerCase(Locale.ROOT), new HashSet<>(Arrays.asList(splitCsv(value))));
                } else if (key.startsWith("label.")) {
                    ROLE_LABELS.put(key.substring(6).toLowerCase(Locale.ROOT), value);
                } else if (key.equals("spawn")) {
                    spawn = SavedPos.parse(value);
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("Ignoring malformed SovereignCore data entry {}", key);
            }
        }
        addDefaultRole("owner", "Owner", "*");
        addDefaultRole("admin", "Admin", "*");
        addDefaultRole("moderator", "Moderator", "players,teleport,homes,warps,moderation,freeze,heal,feed,spawn,home,warp,tpa");
        addDefaultRole("vip", "VIP", "home,warp,tpa,spawn");
        addDefaultRole("member", "Member", "home,warp,tpa,spawn");
        saveData();
        LOGGER.info("SovereignCore data loaded: {} warps, {} players with homes, {} custom ranks", WARPS.size(), HOMES.size(), ROLE_PERMISSIONS.size());
    }

    private static void addDefaultRole(String key, String label, String permissions) {
        ROLE_LABELS.putIfAbsent(key, label);
        ROLE_PERMISSIONS.putIfAbsent(key, new HashSet<>(Arrays.asList(splitCsv(permissions))));
    }

    private static void saveData() {
        if (dataFile == null) return;
        Properties p = new Properties();
        for (Map.Entry<UUID, Map<String, SavedPos>> user : HOMES.entrySet()) {
            for (Map.Entry<String, SavedPos> home : user.getValue().entrySet()) p.setProperty("home." + user.getKey() + "." + home.getKey(), home.getValue().encode());
        }
        WARPS.forEach((name, pos) -> p.setProperty("warp." + name, pos.encode()));
        RANKS.forEach((id, rank) -> p.setProperty("rank." + id, rank));
        KNOWN_NAMES.forEach((id, name) -> p.setProperty("name." + id, name));
        ROLE_PERMISSIONS.forEach((role, perms) -> p.setProperty("permissions." + role, String.join(",", perms)));
        ROLE_LABELS.forEach((role, label) -> p.setProperty("label." + role, label));
        if (spawn != null) p.setProperty("spawn", spawn.encode());
        try {
            Files.createDirectories(dataFile.getParent());
            try (OutputStream out = Files.newOutputStream(dataFile)) { p.store(out, "SovereignCore persistent server data. Back up before editing."); }
        } catch (IOException ex) {
            LOGGER.error("Could not save SovereignCore data", ex);
        }
    }

    // -------------------- Permission model --------------------

    private static String rankOf(ServerPlayer player) {
        return RANKS.getOrDefault(player.getUUID(), "member").toLowerCase(Locale.ROOT);
    }

    private static boolean can(ServerPlayer player, String permission) {
        if (player.hasPermissions(2)) return true;
        Set<String> perms = ROLE_PERMISSIONS.getOrDefault(rankOf(player), ROLE_PERMISSIONS.getOrDefault("member", Collections.emptySet()));
        return perms.contains("*") || perms.contains(permission.toLowerCase(Locale.ROOT));
    }

    private static boolean canManageRanks(ServerPlayer player) { return can(player, "ranks"); }
    private static boolean canGrantOwner(ServerPlayer player) { return player.hasPermissions(4); }
    private static int homeLimit(ServerPlayer player) {
        // The 'homes' permission controls staff access to other players' homes;
        // it should not accidentally grant unlimited personal homes.
        String role = rankOf(player);
        if (player.hasPermissions(2) || role.equals("owner") || role.equals("admin")) return 99;
        if (role.equals("moderator")) return 10;
        if (role.equals("vip")) return 5;
        return 3;
    }

    private static void tell(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("§8[§5Sovereign§dCore§8] §f" + text));
    }

    private static void sendUi(ServerPlayer player, String page, String data) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NetworkHandler.UiPacket(page, data == null ? "" : data));
    }

    private static void deny(ServerPlayer player, String permission) {
        tell(player, "You do not have permission: " + permission + ".");
        sendUi(player, "notice", "Permission denied: " + permission);
    }

    private static ServerPlayer findPlayer(String name) {
        if (server == null || name == null || name.isBlank()) return null;
        return server.getPlayerList().getPlayerByName(name.trim());
    }

    private static UUID findUuidByName(String name) {
        if (server == null || name == null || name.isBlank()) return null;
        ServerPlayer online = findPlayer(name);
        if (online != null) return online.getUUID();
        for (Map.Entry<UUID, String> entry : KNOWN_NAMES.entrySet()) if (entry.getValue().equalsIgnoreCase(name.trim())) return entry.getKey();
        return null;
    }

    private static String displayName(UUID id) {
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(id);
        return online != null ? online.getGameProfile().getName() : KNOWN_NAMES.getOrDefault(id, id.toString().substring(0, 8));
    }

    private static SavedPos currentPos(ServerPlayer player) {
        return new SavedPos(player.serverLevel().dimension().location().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    private static ServerLevel dimension(MinecraftServer s, String id) {
        if (s == null || id == null) return null;
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return null;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, location);
        return s.getLevel(key);
    }

    private static boolean teleport(ServerPlayer player, SavedPos pos) {
        if (server == null || pos == null) return false;
        ServerLevel level = dimension(server, pos.dimension);
        if (level == null) {
            tell(player, "That dimension is not loaded: " + pos.dimension);
            return false;
        }
        player.teleportTo(level, pos.x, pos.y, pos.z, pos.yaw, pos.pitch);
        return true;
    }

    private static boolean safeKey(String s) { return s != null && SAFE_NAME.matcher(s.trim()).matches(); }
    private static String key(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    private static String[] splitCsv(String s) {
        if (s == null || s.isBlank()) return new String[0];
        return Arrays.stream(s.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toArray(String[]::new);
    }

    // -------------------- UI action router (all server-authoritative) --------------------

    public static void handleAction(ServerPlayer actor, String action, String arg1, String arg2) {
        if (actor == null || server == null) return;
        String a = action == null ? "" : action.toUpperCase(Locale.ROOT);
        String x = arg1 == null ? "" : arg1.trim();
        String y = arg2 == null ? "" : arg2.trim();
        try {
            switch (a) {
                case "REQ_PLAYERS" -> {
                    if (!can(actor, "players")) { deny(actor, "players"); return; }
                    List<String> rows = new ArrayList<>();
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) rows.add(p.getUUID() + "\t" + p.getGameProfile().getName() + "\t" + rankOf(p) + "\t" + p.serverLevel().dimension().location());
                    rows.sort(String.CASE_INSENSITIVE_ORDER);
                    sendUi(actor, "players", String.join("\n", rows.subList(0, Math.min(rows.size(), 60))));
                }
                case "REQ_HOMES" -> {
                    UUID ownerId = x.isBlank() ? actor.getUUID() : findUuidByName(x);
                    if (ownerId == null) { tell(actor, "Player not found in saved name history."); return; }
                    if (!ownerId.equals(actor.getUUID()) && !can(actor, "homes")) { deny(actor, "homes"); return; }
                    Map<String, SavedPos> map = HOMES.getOrDefault(ownerId, Collections.emptyMap());
                    List<String> rows = new ArrayList<>();
                    for (Map.Entry<String, SavedPos> h : map.entrySet()) rows.add(ownerId + "\t" + displayName(ownerId) + "\t" + h.getKey() + "\t" + h.getValue().shortDisplay());
                    sendUi(actor, "homes", String.join("\n", rows));
                }
                case "REQ_WARPS" -> {
                    if (!can(actor, "warp") && !can(actor, "warps")) { deny(actor, "warp"); return; }
                    List<String> rows = new ArrayList<>();
                    for (Map.Entry<String, SavedPos> w : WARPS.entrySet()) rows.add(w.getKey() + "\t" + w.getValue().shortDisplay());
                    sendUi(actor, "warps", String.join("\n", rows));
                }
                case "REQ_RANKS" -> {
                    if (!canManageRanks(actor)) { deny(actor, "ranks"); return; }
                    List<String> rows = new ArrayList<>();
                    ROLE_LABELS.keySet().stream().sorted().forEach(r -> rows.add(r + "\t" + ROLE_LABELS.getOrDefault(r, r) + "\t" + ROLE_PERMISSIONS.getOrDefault(r, Collections.emptySet()).size()));
                    sendUi(actor, "ranks", String.join("\n", rows));
                }
                case "REQ_ROLE_PERMS" -> {
                    String role = key(x);
                    if (!canManageRanks(actor)) { deny(actor, "ranks"); return; }
                    if (!ROLE_PERMISSIONS.containsKey(role)) { tell(actor, "Unknown rank: " + role); return; }
                    if (role.equals("owner") && !canGrantOwner(actor)) { deny(actor, "editing owner permissions"); return; }
                    List<String> rows = new ArrayList<>();
                    for (String perm : permissionCatalog()) rows.add(perm + "\t" + (ROLE_PERMISSIONS.get(role).contains("*") || ROLE_PERMISSIONS.get(role).contains(perm)));
                    sendUi(actor, "roleperms", role + "\n" + String.join("\n", rows));
                }
                case "REQ_INSPECT" -> {
                    if (!can(actor, "inventory")) { deny(actor, "inventory"); return; }
                    ServerPlayer target = findPlayer(x);
                    if (target == null) { tell(actor, "That player is not online."); return; }
                    List<String> rows = new ArrayList<>();
                    rows.add("Inventory: " + target.getGameProfile().getName());
                    for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
                        ItemStack stack = target.getInventory().getItem(i);
                        if (!stack.isEmpty()) rows.add("Slot " + i + ": " + stack.getCount() + " x " + stack.getHoverName().getString());
                    }
                    rows.add("Ender Chest:");
                    for (int i = 0; i < target.getEnderChestInventory().getContainerSize(); i++) {
                        ItemStack stack = target.getEnderChestInventory().getItem(i);
                        if (!stack.isEmpty()) rows.add("Ender " + i + ": " + stack.getCount() + " x " + stack.getHoverName().getString());
                    }
                    if (rows.size() == 2) rows.add("Inventory and ender chest are empty.");
                    sendUi(actor, "inspect", target.getGameProfile().getName() + "\n" + String.join("\n", rows));
                }
                case "SET_HOME" -> setHome(actor, x.isBlank() ? "home" : x);
                case "DEL_HOME" -> deleteHome(actor, actor.getUUID(), x);
                case "DEL_HOME_OTHER" -> {
                    String[] bits = x.split("\\|", 2);
                    if (bits.length != 2) { tell(actor, "Invalid home selector."); return; }
                    UUID owner = UUID.fromString(bits[0]);
                    deleteHome(actor, owner, bits[1]);
                }
                case "HOME" -> {
                    if (!can(actor, "home")) { deny(actor, "home"); return; }
                    String[] bits = x.split("\\|", 2);
                    UUID owner = actor.getUUID(); String homeName = x;
                    if (bits.length == 2) { owner = UUID.fromString(bits[0]); homeName = bits[1]; if (!owner.equals(actor.getUUID()) && !can(actor, "homes")) { deny(actor, "homes"); return; } }
                    SavedPos pos = HOMES.getOrDefault(owner, Collections.emptyMap()).get(key(homeName));
                    if (pos == null) tell(actor, "Home not found: " + homeName);
                    else if (teleport(actor, pos)) tell(actor, "Teleported to " + displayName(owner) + "'s home " + homeName + ".");
                }
                case "SET_WARP" -> {
                    if (!can(actor, "world")) { deny(actor, "world"); return; }
                    if (!safeKey(x)) { tell(actor, "Warp name must use 1-32 letters, digits, _ or -."); return; }
                    WARPS.put(key(x), currentPos(actor)); saveData(); tell(actor, "Warp saved: " + key(x));
                }
                case "DEL_WARP" -> {
                    if (!can(actor, "world")) { deny(actor, "world"); return; }
                    if (WARPS.remove(key(x)) == null) tell(actor, "Warp not found: " + x); else { saveData(); tell(actor, "Warp deleted: " + key(x)); }
                }
                case "WARP" -> {
                    if (!can(actor, "warp")) { deny(actor, "warp"); return; }
                    SavedPos pos = WARPS.get(key(x));
                    if (pos == null) tell(actor, "Warp not found: " + x); else teleport(actor, pos);
                }
                case "SET_SPAWN" -> {
                    if (!can(actor, "world")) { deny(actor, "world"); return; }
                    spawn = currentPos(actor); saveData(); tell(actor, "Server spawn saved.");
                }
                case "SPAWN" -> {
                    if (!can(actor, "spawn")) { deny(actor, "spawn"); return; }
                    if (spawn == null) {
                        ServerLevel overworld = server.overworld();
                        actor.teleportTo(overworld, overworld.getSharedSpawnPos().getX() + 0.5, overworld.getSharedSpawnPos().getY(), overworld.getSharedSpawnPos().getZ() + 0.5, actor.getYRot(), actor.getXRot());
                    } else teleport(actor, spawn);
                }
                case "TP_TO" -> {
                    if (!can(actor, "teleport")) { deny(actor, "teleport"); return; }
                    ServerPlayer target = findPlayer(x); if (target == null) { tell(actor, "Player is not online: " + x); return; }
                    actor.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                }
                case "TP_HERE" -> {
                    if (!can(actor, "teleport")) { deny(actor, "teleport"); return; }
                    ServerPlayer target = findPlayer(x); if (target == null) { tell(actor, "Player is not online: " + x); return; }
                    target.teleportTo(actor.serverLevel(), actor.getX(), actor.getY(), actor.getZ(), actor.getYRot(), actor.getXRot());
                    tell(actor, "Brought " + target.getGameProfile().getName() + " to you.");
                }
                case "TP_COORDS" -> {
                    if (!can(actor, "teleport")) { deny(actor, "teleport"); return; }
                    String[] pieces = x.split("\\|", 2); String[] coords = pieces[0].split(",");
                    if (coords.length != 3) { tell(actor, "Enter coordinates as x,y,z. To change dimension, append |minecraft:the_nether for example."); return; }
                    try {
                        double px = Double.parseDouble(coords[0].trim()), py = Double.parseDouble(coords[1].trim()), pz = Double.parseDouble(coords[2].trim());
                        if (!Double.isFinite(px) || !Double.isFinite(py) || !Double.isFinite(pz) || Math.abs(px) > 30_000_000 || Math.abs(pz) > 30_000_000 || py < -2048 || py > 4096) throw new NumberFormatException();
                        String dimId = pieces.length > 1 && !pieces[1].isBlank() ? pieces[1].trim() : actor.serverLevel().dimension().location().toString();
                        teleport(actor, new SavedPos(dimId, px, py, pz, actor.getYRot(), actor.getXRot()));
                    } catch (NumberFormatException ex) { tell(actor, "Invalid coordinates."); }
                }
                case "TPA" -> requestTeleport(actor, x);
                case "TP_ACCEPT" -> acceptTeleport(actor);
                case "TP_DENY" -> denyTeleport(actor);
                case "SET_RANK" -> {
                    if (!canManageRanks(actor)) { deny(actor, "ranks"); return; }
                    ServerPlayer target = findPlayer(x); String role = key(y);
                    if (target == null) { tell(actor, "Player is not online: " + x); return; }
                    if (!ROLE_PERMISSIONS.containsKey(role)) { tell(actor, "Unknown rank: " + role); return; }
                    if (role.equals("owner") && !canGrantOwner(actor)) { deny(actor, "granting owner"); return; }
                    RANKS.put(target.getUUID(), role); KNOWN_NAMES.put(target.getUUID(), target.getGameProfile().getName()); saveData(); tell(actor, "Rank set: " + target.getGameProfile().getName() + " → " + role);
                }
                case "CREATE_ROLE" -> {
                    if (!canManageRanks(actor)) { deny(actor, "ranks"); return; }
                    String role = key(x);
                    if (!safeKey(role) || role.equals("owner") || role.equals("admin") || ROLE_PERMISSIONS.containsKey(role)) { tell(actor, "Choose a new role name (letters, digits, _ and - only). Reserved or existing name."); return; }
                    ROLE_PERMISSIONS.put(role, new HashSet<>(Arrays.asList("home", "warp", "tpa"))); ROLE_LABELS.put(role, role.substring(0, 1).toUpperCase(Locale.ROOT) + role.substring(1)); saveData();
                    List<String> rows = ROLE_LABELS.keySet().stream().sorted().map(r -> r + "\t" + ROLE_LABELS.get(r) + "\t" + ROLE_PERMISSIONS.getOrDefault(r, Collections.emptySet()).size()).toList();
                    sendUi(actor, "ranks", String.join("\n", rows));
                }
                case "TOGGLE_ROLE_PERMISSION" -> {
                    String role = key(x), permission = key(y);
                    if (!canManageRanks(actor)) { deny(actor, "ranks"); return; }
                    if (role.equals("owner") && !canGrantOwner(actor)) { deny(actor, "editing owner permissions"); return; }
                    if (!ROLE_PERMISSIONS.containsKey(role) || !permissionCatalog().contains(permission)) { tell(actor, "Unknown role or permission."); return; }
                    Set<String> perms = ROLE_PERMISSIONS.get(role);
                    if (perms.contains("*")) { perms.remove("*"); perms.addAll(permissionCatalog()); }
                    if (!perms.remove(permission)) perms.add(permission);
                    saveData();
                    List<String> rows = new ArrayList<>();
                    for (String perm : permissionCatalog()) rows.add(perm + "\t" + (perms.contains("*") || perms.contains(perm)));
                    sendUi(actor, "roleperms", role + "\n" + String.join("\n", rows));
                }
                case "TOGGLE_FREEZE" -> {
                    if (!can(actor, "moderation") && !can(actor, "freeze")) { deny(actor, "moderation"); return; }
                    ServerPlayer target = findPlayer(x); if (target == null) { tell(actor, "Player is not online: " + x); return; }
                    if (FROZEN.containsKey(target.getUUID())) { FROZEN.remove(target.getUUID()); tell(actor, "Unfroze " + target.getGameProfile().getName()); tell(target, "You have been unfrozen."); }
                    else { FROZEN.put(target.getUUID(), currentPos(target)); tell(actor, "Froze " + target.getGameProfile().getName()); tell(target, "You have been frozen by staff."); }
                }
                case "HEAL" -> withTarget(actor, x, "heal", t -> { t.setHealth(t.getMaxHealth()); t.clearFire(); tell(actor, "Healed " + t.getGameProfile().getName() + "."); });
                case "FEED" -> withTarget(actor, x, "feed", t -> { t.getFoodData().eat(20, 20.0F); tell(actor, "Fed " + t.getGameProfile().getName() + "."); });
                case "GAMEMODE" -> {
                    if (!can(actor, "gamemode")) { deny(actor, "gamemode"); return; }
                    ServerPlayer target = findPlayer(x); if (target == null) { tell(actor, "Player is not online: " + x); return; }
                    GameType type = switch (key(y)) { case "creative", "1", "c" -> GameType.CREATIVE; case "adventure", "2", "a" -> GameType.ADVENTURE; case "spectator", "3", "sp" -> GameType.SPECTATOR; default -> GameType.SURVIVAL; };
                    target.setGameMode(type); tell(actor, "Gamemode updated for " + target.getGameProfile().getName() + ".");
                }
                case "TOGGLE_GOD" -> withTarget(actor, x, "god", t -> { t.getAbilities().invulnerable = !t.getAbilities().invulnerable; t.onUpdateAbilities(); tell(actor, "Invulnerability for " + t.getGameProfile().getName() + ": " + t.getAbilities().invulnerable); });
                case "TOGGLE_FLY" -> withTarget(actor, x, "fly", t -> { t.getAbilities().mayfly = !t.getAbilities().mayfly; if (!t.getAbilities().mayfly) t.getAbilities().flying = false; t.onUpdateAbilities(); tell(actor, "Flight permission for " + t.getGameProfile().getName() + ": " + t.getAbilities().mayfly); });
                case "CLEAR_EFFECTS" -> withTarget(actor, x, "moderation", t -> { t.removeAllEffects(); tell(actor, "Cleared effects for " + t.getGameProfile().getName() + "."); });
                case "CLEAR_XP" -> withTarget(actor, x, "moderation", t -> { t.setExperienceLevels(0); t.setExperiencePoints(0); t.totalExperience = 0; tell(actor, "Cleared XP for " + t.getGameProfile().getName() + "."); });
                case "INV_CLEAR" -> withTarget(actor, x, "inventory", t -> { t.getInventory().clearContent(); t.containerMenu.broadcastChanges(); tell(actor, "Cleared inventory of " + t.getGameProfile().getName() + "."); });
                case "ENDER_CLEAR" -> withTarget(actor, x, "inventory", t -> { t.getEnderChestInventory().clearContent(); t.containerMenu.broadcastChanges(); tell(actor, "Cleared ender chest of " + t.getGameProfile().getName() + "."); });
                case "GIVE_ITEM" -> {
                    if (!can(actor, "give") && !can(actor, "inventory")) { deny(actor, "inventory"); return; }
                    ServerPlayer target = findPlayer(x); if (target == null) { tell(actor, "Player is not online: " + x); return; }
                    String[] pieces = y.split("\\s+"); String item = pieces.length > 0 ? pieces[0] : ""; int count = 1;
                    try { if (pieces.length > 1) count = Integer.parseInt(pieces[1]); } catch (NumberFormatException ignored) { count = -1; }
                    if (!SAFE_ITEM.matcher(item).matches() || count < 1 || count > 64) { tell(actor, "Enter an item as minecraft:diamond 1 (count 1-64)."); return; }
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "give " + target.getGameProfile().getName() + " " + item + " " + count);
                    tell(actor, "Give command sent.");
                }
                case "KICK" -> {
                    if (!can(actor, "kick") && !can(actor, "moderation")) { deny(actor, "kick"); return; }
                    ServerPlayer target = findPlayer(x); if (target == null) { tell(actor, "Player is not online: " + x); return; }
                    String reason = y.isBlank() ? "Removed by staff" : y;
                    target.connection.disconnect(Component.literal(reason)); tell(actor, "Kicked " + x + ".");
                }
                case "BAN" -> {
                    if (!can(actor, "ban") && !can(actor, "moderation")) { deny(actor, "ban"); return; }
                    if (!SAFE_NAME.matcher(x).matches()) { tell(actor, "Invalid player name."); return; }
                    String reason = y.isBlank() ? "Banned by staff" : y.replace('\n', ' ').replace('\r', ' ');
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "ban " + x + " " + reason);
                    tell(actor, "Ban command sent for " + x + ".");
                }
                case "PARDON" -> {
                    if (!can(actor, "ban") && !can(actor, "moderation")) { deny(actor, "ban"); return; }
                    if (!SAFE_NAME.matcher(x).matches()) { tell(actor, "Invalid player name."); return; }
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "pardon " + x); tell(actor, "Pardon command sent for " + x + ".");
                }
                case "OP" -> {
                    if (!actor.hasPermissions(4)) { deny(actor, "console-level operator permission"); return; }
                    if (!SAFE_NAME.matcher(x).matches()) { tell(actor, "Invalid player name."); return; }
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "op " + x); tell(actor, "Operator command sent for " + x + ".");
                }
                case "DEOP" -> {
                    if (!actor.hasPermissions(4)) { deny(actor, "console-level operator permission"); return; }
                    if (!SAFE_NAME.matcher(x).matches()) { tell(actor, "Invalid player name."); return; }
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "deop " + x); tell(actor, "Deop command sent for " + x + ".");
                }
                default -> tell(actor, "Unknown UI action: " + a);
            }
        } catch (Exception ex) {
            LOGGER.error("SovereignCore action failed: {}", a, ex);
            tell(actor, "Action failed safely. Check the server log for details.");
        }
    }

    private static void withTarget(ServerPlayer actor, String targetName, String permission, java.util.function.Consumer<ServerPlayer> action) {
        if (!can(actor, permission)) { deny(actor, permission); return; }
        ServerPlayer target = findPlayer(targetName);
        if (target == null) { tell(actor, "Player is not online: " + targetName); return; }
        action.accept(target);
    }

    private static List<String> permissionCatalog() {
        return Arrays.asList("players", "teleport", "homes", "warps", "ranks", "moderation", "freeze", "inventory", "give", "world", "settings", "home", "warp", "tpa", "spawn", "heal", "feed", "gamemode", "fly", "god", "kick", "ban");
    }

    private static void setHome(ServerPlayer player, String name) {
        if (!can(player, "home")) { deny(player, "home"); return; }
        if (!safeKey(name)) { tell(player, "Home name must use 1-32 letters, digits, _ or -."); return; }
        Map<String, SavedPos> homes = HOMES.computeIfAbsent(player.getUUID(), id -> new LinkedHashMap<>());
        String nameKey = key(name);
        if (!homes.containsKey(nameKey) && homes.size() >= homeLimit(player)) { tell(player, "Home limit reached (" + homeLimit(player) + ")."); return; }
        homes.put(nameKey, currentPos(player)); KNOWN_NAMES.put(player.getUUID(), player.getGameProfile().getName()); saveData(); tell(player, "Home saved: " + nameKey);
    }

    private static void deleteHome(ServerPlayer actor, UUID ownerId, String name) {
        if (!ownerId.equals(actor.getUUID()) && !can(actor, "homes")) { deny(actor, "homes"); return; }
        if (!can(actor, "home") && !can(actor, "homes")) { deny(actor, "home"); return; }
        Map<String, SavedPos> homes = HOMES.get(ownerId);
        if (homes == null || homes.remove(key(name)) == null) { tell(actor, "Home not found: " + name); return; }
        if (homes.isEmpty()) HOMES.remove(ownerId);
        saveData(); tell(actor, "Deleted " + displayName(ownerId) + "'s home " + name + ".");
    }

    private static void requestTeleport(ServerPlayer requester, String targetName) {
        if (!can(requester, "tpa")) { deny(requester, "tpa"); return; }
        ServerPlayer target = findPlayer(targetName);
        if (target == null) { tell(requester, "Player is not online: " + targetName); return; }
        if (target.getUUID().equals(requester.getUUID())) { tell(requester, "You cannot request teleport to yourself."); return; }
        TPA_REQUESTS.put(target.getUUID(), new TeleportRequest(requester.getUUID(), System.currentTimeMillis()));
        tell(requester, "Teleport request sent to " + targetName + ".");
        tell(target, requester.getGameProfile().getName() + " requested to teleport to you. Use /tpaccept or the manager UI.");
    }

    private static void acceptTeleport(ServerPlayer target) {
        if (!can(target, "tpa")) { deny(target, "tpa"); return; }
        TeleportRequest request = TPA_REQUESTS.remove(target.getUUID());
        if (request == null || System.currentTimeMillis() - request.time > 120_000L) { tell(target, "No pending request, or it expired."); return; }
        ServerPlayer requester = server.getPlayerList().getPlayer(request.requester);
        if (requester == null) { tell(target, "The requester is no longer online."); return; }
        requester.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        tell(target, "Accepted teleport request from " + requester.getGameProfile().getName() + ".");
        tell(requester, "Your teleport request was accepted.");
    }

    private static void denyTeleport(ServerPlayer target) {
        TeleportRequest request = TPA_REQUESTS.remove(target.getUUID());
        if (request == null) { tell(target, "No pending request."); return; }
        ServerPlayer requester = server.getPlayerList().getPlayer(request.requester);
        tell(target, "Teleport request denied.");
        if (requester != null) tell(requester, target.getGameProfile().getName() + " denied your teleport request.");
    }

    private void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.level().isClientSide) return;
        SavedPos anchor = FROZEN.get(player.getUUID());
        if (anchor == null) return;
        ServerLevel anchorLevel = dimension(server, anchor.dimension);
        if (anchorLevel == null) return;
        if (player.serverLevel() != anchorLevel) player.teleportTo(anchorLevel, anchor.x, anchor.y, anchor.z, anchor.yaw, anchor.pitch);
        else player.teleportTo(anchor.x, anchor.y, anchor.z);
        player.setDeltaMovement(0, 0, 0);
        player.setSprinting(false);
    }

    // -------------------- Commands --------------------

    private void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("sc").executes(c -> openFromCommand(c.getSource())));
        d.register(Commands.literal("manager").executes(c -> openFromCommand(c.getSource())));
        d.register(Commands.literal("sethome").executes(c -> runPlayer(c.getSource(), p -> setHome(p, "home"))).then(Commands.argument("name", StringArgumentType.word()).executes(c -> runPlayer(c.getSource(), p -> setHome(p, StringArgumentType.getString(c, "name"))))));
        d.register(Commands.literal("home").executes(c -> runPlayer(c.getSource(), p -> handleAction(p, "HOME", "home", ""))).then(Commands.argument("name", StringArgumentType.word()).executes(c -> runPlayer(c.getSource(), p -> handleAction(p, "HOME", StringArgumentType.getString(c, "name"), "")))));
        d.register(Commands.literal("delhome").then(Commands.argument("name", StringArgumentType.word()).executes(c -> runPlayer(c.getSource(), p -> deleteHome(p, p.getUUID(), StringArgumentType.getString(c, "name"))))));
        d.register(Commands.literal("homes").executes(c -> runPlayer(c.getSource(), p -> handleAction(p, "REQ_HOMES", "", ""))));
        d.register(Commands.literal("setspawn").executes(c -> runPlayer(c.getSource(), p -> handleAction(p, "SET_SPAWN", "", ""))));
        d.register(Commands.literal("spawn").executes(c -> runPlayer(c.getSource(), p -> handleAction(p, "SPAWN", "", ""))));
        d.register(Commands.literal("setwarp").then(Commands.argument("name", StringArgumentType.word()).executes(c -> runPlayer(c.getSource(), p -> handleAction(p, "SET_WARP", StringArgumentType.getString(c, "name"), "")))));
        d.register(Commands.literal("warp").then(Commands.argument("name", StringArgumentType.word()).executes(c -> runPlayer(c.getSource(), p -> handleAction(p, "WARP", StringArgumentType.getString(c, "name"), "")))));
        d.register(Commands.literal("delwarp").then(Commands.argument("name", StringArgumentType.word()).executes(c -> runPlayer(c.getSource(), p -> handleAction(p, "DEL_WARP", StringArgumentType.getString(c, "name"), "")))));
        d.register(Commands.literal("warps").executes(c -> runPlayer(c.getSource(), p -> handleAction(p, "REQ_WARPS", "", ""))));
        d.register(Commands.literal("tpa").then(Commands.argument("player", StringArgumentType.word()).executes(c -> runPlayer(c.getSource(), p -> handleAction(p, "TPA", StringArgumentType.getString(c, "player"), "")))));
        d.register(Commands.literal("tpaccept").executes(c -> runPlayer(c.getSource(), SovereignCore::acceptTeleport)));
        d.register(Commands.literal("tpdeny").executes(c -> runPlayer(c.getSource(), SovereignCore::denyTeleport)));
        d.register(Commands.literal("sctp").then(Commands.argument("player", StringArgumentType.word()).executes(c -> runPlayer(c.getSource(), p -> handleAction(p, "TP_TO", StringArgumentType.getString(c, "player"), "")))));
    }

    private static int openFromCommand(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) sendUi(player, "main", "");
        else source.sendSuccess(() -> Component.literal("Open SovereignCore in-game using /sc or /manager."), false);
        return 1;
    }

    private static int runPlayer(CommandSourceStack source, java.util.function.Consumer<ServerPlayer> task) {
        if (source.getEntity() instanceof ServerPlayer player) task.accept(player);
        else source.sendFailure(Component.literal("This command must be used by a player."));
        return 1;
    }

    // -------------------- Persistent data structures --------------------

    private static final class SavedPos {
        final String dimension; final double x, y, z; final float yaw, pitch;
        SavedPos(String dimension, double x, double y, double z, float yaw, float pitch) { this.dimension = dimension; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; }
        String encode() { return dimension + "," + x + "," + y + "," + z + "," + yaw + "," + pitch; }
        String shortDisplay() { return dimension + " @ " + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", x, y, z); }
        static SavedPos parse(String s) {
            try { String[] p = s.split(",", 6); if (p.length != 6) return null; return new SavedPos(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5])); }
            catch (RuntimeException ex) { return null; }
        }
    }
    private static final class TeleportRequest {
        final UUID requester; final long time;
        TeleportRequest(UUID requester, long time) { this.requester = requester; this.time = time; }
    }
}
