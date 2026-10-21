package com.sovereigncore;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ManagerScreen extends Screen {
    private final String page;
    private final String data;
    private EditBox inputA;
    private EditBox inputB;
    private int panelX, panelY, panelW, panelH;
    private String currentTarget = "";
    private String currentRole = "";

    public ManagerScreen(String page, String data) {
        super(Component.literal("SovereignCore Manager"));
        this.page = page == null ? "main" : page;
        this.data = data == null ? "" : data;
        if (this.page.equals("player")) currentTarget = this.data.trim();
        if (this.page.equals("roleperms")) currentRole = firstLine(this.data).trim();
        if (this.page.equals("inspect")) currentTarget = firstLine(this.data).trim();
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(540, Math.max(360, width - 24));
        panelH = Math.min(410, Math.max(290, height - 24));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int left = panelX + 16;
        int top = panelY + 42;
        int gap = 7;
        int colW = (panelW - 39) / 2;
        int rowH = 21;
        switch (page) {
            case "main" -> {
                button("Player management", left, top, colW, rowH, () -> request("REQ_PLAYERS", "", ""));
                button("Teleport tools", left + colW + gap, top, colW, rowH, () -> setPage("teleport", ""));
                button("Homes", left, top + 29, colW, rowH, () -> request("REQ_HOMES", "", ""));
                button("Warps", left + colW + gap, top + 29, colW, rowH, () -> request("REQ_WARPS", "", ""));
                button("Ranks & permissions", left, top + 58, colW, rowH, () -> request("REQ_RANKS", "", ""));
                button("Personal tools", left + colW + gap, top + 58, colW, rowH, () -> setPage("personal", ""));
                button("Server settings", left, top + 87, colW, rowH, () -> setPage("settings", ""));
                button("Help / commands", left + colW + gap, top + 87, colW, rowH, () -> setPage("help", ""));
                button("Close", panelX + panelW / 2 - 58, panelY + panelH - 32, 116, 20, this::onClose);
            }
            case "players" -> buildPlayers(left, top, colW, gap, rowH);
            case "player" -> buildPlayerPanel(left, top, colW, gap, rowH);
            case "teleport" -> buildTeleport(left, top, colW, gap, rowH);
            case "homes" -> buildHomes(left, top, colW, gap, rowH);
            case "warps" -> buildWarps(left, top, colW, gap, rowH);
            case "ranks" -> buildRanks(left, top, colW, gap, rowH);
            case "roleperms" -> buildRolePerms(left, top, colW, gap, rowH);
            case "personal" -> buildPersonal(left, top, colW, gap, rowH);
            case "settings" -> buildSettings(left, top, colW, gap, rowH);
            case "inspect" -> buildInspect(left, top, colW, gap, rowH);
            case "help" -> buildHelp(left, top, colW, gap, rowH);
            case "notice" -> button("Back to manager", panelX + panelW / 2 - 75, panelY + panelH - 34, 150, 20, () -> setPage("main", ""));
            default -> button("Back", panelX + panelW / 2 - 50, panelY + panelH - 32, 100, 20, () -> setPage("main", ""));
        }
    }

    private void buildPlayers(int left, int top, int colW, int gap, int rowH) {
        List<String> rows = lines(data);
        int shown = Math.min(rows.size(), 10);
        for (int i = 0; i < shown; i++) {
            String[] p = rows.get(i).split("\\t", -1);
            if (p.length < 2) continue;
            String name = p[1];
            String rank = p.length > 2 ? p[2] : "member";
            int col = i % 2, row = i / 2;
            button(name + "  §7[" + rank + "]", left + col * (colW + gap), top + row * 25, colW, rowH, () -> setPage("player", name));
        }
        if (rows.isEmpty()) label("No players online.", left, top + 4);
        if (rows.size() > 10) label("Showing first 10 online players.", left, top + 130);
        button("Refresh", panelX + 14, panelY + panelH - 32, 90, 20, () -> request("REQ_PLAYERS", "", ""));
        button("Back", panelX + panelW - 104, panelY + panelH - 32, 90, 20, () -> setPage("main", ""));
    }

    private void buildPlayerPanel(int left, int top, int colW, int gap, int rowH) {
        label("Selected player: " + currentTarget, left, panelY + 26);
        inputA = new EditBox(font, left, panelY + 48, panelW - 32, 18, Component.literal("Reason / rank / item ID and count"));
        inputA.setMaxLength(180);
        inputA.setHint(Component.literal("Rank, kick reason, or item id count"));
        addRenderableWidget(inputA);
        String[] labels = {
                "Teleport to player", "Bring player here",
                "View homes", "Set rank from input",
                "Freeze / unfreeze", "Heal player",
                "Feed player", "Gamemode: Creative",
                "Toggle invulnerability", "Toggle flight",
                "View inventory", "Clear inventory",
                "Clear ender chest", "Clear potion effects",
                "Clear XP", "Kick with reason",
                "Ban with reason", "Pardon player",
                "Give item from input", "Set Operator (OP)",
                "Remove Operator", "Back to player list"
        };
        String[] actions = {
                "TP_TO", "TP_HERE", "REQ_HOMES", "SET_RANK", "TOGGLE_FREEZE", "HEAL", "FEED", "GAMEMODE",
                "TOGGLE_GOD", "TOGGLE_FLY", "REQ_INSPECT", "INV_CLEAR", "ENDER_CLEAR", "CLEAR_EFFECTS",
                "CLEAR_XP", "KICK", "BAN", "PARDON", "GIVE_ITEM", "OP", "DEOP", "BACK_PLAYERS"
        };
        int startY = panelY + 74;
        int compactW = (panelW - 39) / 2;
        for (int i = 0; i < labels.length; i++) {
            final String action = actions[i];
            int col = i % 2, row = i / 2;
            button(labels[i], panelX + 16 + col * (compactW + 7), startY + row * 23, compactW, 19, () -> {
                if (action.equals("BACK_PLAYERS")) { request("REQ_PLAYERS", "", ""); return; }
                if (action.equals("REQ_HOMES")) { request(action, currentTarget, ""); return; }
                if (action.equals("REQ_INSPECT")) { request(action, currentTarget, ""); return; }
                String extra = inputA == null ? "" : inputA.getValue().trim();
                if (action.equals("SET_RANK")) { act(action, currentTarget, extra); return; }
                if (action.equals("GAMEMODE")) { act(action, currentTarget, "creative"); return; }
                if (action.equals("GIVE_ITEM")) { act(action, currentTarget, extra); return; }
                if (action.equals("KICK") || action.equals("BAN")) { act(action, currentTarget, extra); return; }
                act(action, currentTarget, extra);
            });
        }
        button("Back", panelX + panelW / 2 - 45, panelY + panelH - 31, 90, 19, () -> request("REQ_PLAYERS", "", ""));
    }

    private void buildTeleport(int left, int top, int colW, int gap, int rowH) {
        inputA = new EditBox(font, left, top, panelW - 32, 20, Component.literal("Player name"));
        inputA.setMaxLength(32); inputA.setHint(Component.literal("Target player name")); addRenderableWidget(inputA);
        inputB = new EditBox(font, left, top + 32, panelW - 32, 20, Component.literal("Coordinates"));
        inputB.setMaxLength(180); inputB.setHint(Component.literal("x,y,z OR x,y,z|minecraft:the_nether")); addRenderableWidget(inputB);
        button("Teleport to player", left, top + 63, colW, rowH, () -> act("TP_TO", val(inputA), ""));
        button("Bring player here", left + colW + gap, top + 63, colW, rowH, () -> act("TP_HERE", val(inputA), ""));
        button("Teleport to coordinates", left, top + 92, panelW - 32, rowH, () -> act("TP_COORDS", val(inputB), ""));
        button("Request teleport to player", left, top + 121, panelW - 32, rowH, () -> act("TPA", val(inputA), ""));
        button("Accept pending request", left, top + 150, colW, rowH, () -> act("TP_ACCEPT", "", ""));
        button("Deny pending request", left + colW + gap, top + 150, colW, rowH, () -> act("TP_DENY", "", ""));
        button("Back", panelX + panelW / 2 - 45, panelY + panelH - 31, 90, 19, () -> setPage("main", ""));
    }

    private void buildHomes(int left, int top, int colW, int gap, int rowH) {
        List<String> rows = lines(data);
        label("Click a home to teleport. Use X to delete.", left, panelY + 25);
        int shown = Math.min(rows.size(), 10);
        for (int i = 0; i < shown; i++) {
            String[] f = rows.get(i).split("\\t", -1);
            if (f.length < 3) continue;
            String owner = f[0], ownerName = f[1], homeName = f[2];
            String display = ownerName + ": " + homeName;
            int y = top + i * 23;
            button(display, left, y, panelW - 86, 19, () -> act("HOME", owner + "|" + homeName, ""));
            button("X", left + panelW - 72, y, 40, 19, () -> act("DEL_HOME_OTHER", owner + "|" + homeName, ""));
        }
        if (rows.isEmpty()) label("No saved homes found.", left, top + 8);
        inputA = new EditBox(font, left, panelY + panelH - 62, panelW - 120, 18, Component.literal("Home name"));
        inputA.setMaxLength(32); inputA.setHint(Component.literal("New / delete home name")); addRenderableWidget(inputA);
        button("Set my home", left + panelW - 109, panelY + panelH - 62, 93, 18, () -> act("SET_HOME", val(inputA), ""));
        button("Delete my home", left, panelY + panelH - 37, 115, 19, () -> act("DEL_HOME", val(inputA), ""));
        button("My homes", left + 121, panelY + panelH - 37, 90, 19, () -> request("REQ_HOMES", "", ""));
        button("Back", left + panelW - 96, panelY + panelH - 37, 80, 19, () -> setPage("main", ""));
    }

    private void buildWarps(int left, int top, int colW, int gap, int rowH) {
        List<String> rows = lines(data);
        label("Click a warp to teleport.", left, panelY + 25);
        int shown = Math.min(rows.size(), 10);
        for (int i = 0; i < shown; i++) {
            String[] f = rows.get(i).split("\\t", -1);
            if (f.length < 1) continue;
            String name = f[0]; String detail = f.length > 1 ? f[1] : "";
            int y = top + i * 23;
            button(name + "  §7" + detail, left, y, panelW - 90, 19, () -> act("WARP", name, ""));
            button("Del", left + panelW - 82, y, 66, 19, () -> act("DEL_WARP", name, ""));
        }
        if (rows.isEmpty()) label("No warps saved yet.", left, top + 8);
        inputA = new EditBox(font, left, panelY + panelH - 62, panelW - 130, 18, Component.literal("Warp name"));
        inputA.setMaxLength(32); inputA.setHint(Component.literal("Set a warp at your current position")); addRenderableWidget(inputA);
        button("Set warp", left + panelW - 118, panelY + panelH - 62, 102, 18, () -> act("SET_WARP", val(inputA), ""));
        button("Refresh", left, panelY + panelH - 37, 90, 19, () -> request("REQ_WARPS", "", ""));
        button("Back", left + panelW - 96, panelY + panelH - 37, 80, 19, () -> setPage("main", ""));
    }

    private void buildRanks(int left, int top, int colW, int gap, int rowH) {
        List<String> rows = lines(data);
        label("Select a rank to edit its permission toggles.", left, panelY + 25);
        int shown = Math.min(rows.size(), 12);
        for (int i = 0; i < shown; i++) {
            String[] f = rows.get(i).split("\\t", -1);
            if (f.length < 1) continue;
            String role = f[0], label = f.length > 1 ? f[1] : role;
            int col = i % 2, row = i / 2;
            button(label + " (" + role + ")", left + col * (colW + gap), top + row * 25, colW, rowH, () -> request("REQ_ROLE_PERMS", role, ""));
        }
        inputA = new EditBox(font, left, panelY + panelH - 59, panelW - 124, 18, Component.literal("New rank id"));
        inputA.setMaxLength(32); inputA.setHint(Component.literal("Create a new role id")); addRenderableWidget(inputA);
        button("Create rank", left + panelW - 114, panelY + panelH - 59, 98, 18, () -> act("CREATE_ROLE", val(inputA), ""));
        button("Refresh", left, panelY + panelH - 32, 90, 19, () -> request("REQ_RANKS", "", ""));
        button("Back", left + panelW - 96, panelY + panelH - 32, 80, 19, () -> setPage("main", ""));
    }

    private void buildRolePerms(int left, int top, int colW, int gap, int rowH) {
        label("Permissions for rank: " + currentRole, left, panelY + 25);
        List<String> rows = linesAfterFirst(data);
        int shown = Math.min(rows.size(), 24);
        int compactW = (panelW - 39) / 2;
        for (int i = 0; i < shown; i++) {
            String[] f = rows.get(i).split("\\t", -1);
            if (f.length < 2) continue;
            String perm = f[0]; boolean on = Boolean.parseBoolean(f[1]);
            int col = i % 2, row = i / 2;
            button((on ? "✓ " : "○ ") + perm, left + col * (compactW + gap), top + row * 23, compactW, 19,
                    () -> request("TOGGLE_ROLE_PERMISSION", currentRole, perm));
        }
        button("Back to ranks", panelX + panelW / 2 - 60, panelY + panelH - 31, 120, 19, () -> request("REQ_RANKS", "", ""));
    }

    private void buildPersonal(int left, int top, int colW, int gap, int rowH) {
        inputA = new EditBox(font, left, top, panelW - 32, 20, Component.literal("Home / target name"));
        inputA.setMaxLength(32); inputA.setHint(Component.literal("Home name or player name")); addRenderableWidget(inputA);
        button("Set home", left, top + 32, colW, rowH, () -> act("SET_HOME", val(inputA), ""));
        button("Teleport to home", left + colW + gap, top + 32, colW, rowH, () -> act("HOME", val(inputA).isBlank() ? "home" : val(inputA), ""));
        button("Browse my homes", left, top + 61, colW, rowH, () -> request("REQ_HOMES", "", ""));
        button("Request teleport", left + colW + gap, top + 61, colW, rowH, () -> act("TPA", val(inputA), ""));
        button("Accept request", left, top + 90, colW, rowH, () -> act("TP_ACCEPT", "", ""));
        button("Deny request", left + colW + gap, top + 90, colW, rowH, () -> act("TP_DENY", "", ""));
        button("Teleport to spawn", left, top + 119, colW, rowH, () -> act("SPAWN", "", ""));
        button("View public warps", left + colW + gap, top + 119, colW, rowH, () -> request("REQ_WARPS", "", ""));
        button("Back", panelX + panelW / 2 - 45, panelY + panelH - 31, 90, 19, () -> setPage("main", ""));
    }

    private void buildSettings(int left, int top, int colW, int gap, int rowH) {
        label("Server setup actions require world/settings permission.", left, panelY + 25);
        button("Set server spawn here", left, top, colW, rowH, () -> act("SET_SPAWN", "", ""));
        button("Teleport to spawn", left + colW + gap, top, colW, rowH, () -> act("SPAWN", "", ""));
        button("Manage warps", left, top + 29, colW, rowH, () -> request("REQ_WARPS", "", ""));
        button("Manage ranks", left + colW + gap, top + 29, colW, rowH, () -> request("REQ_RANKS", "", ""));
        button("Refresh online list", left, top + 58, colW, rowH, () -> request("REQ_PLAYERS", "", ""));
        button("Back", left + colW + gap, top + 58, colW, rowH, () -> setPage("main", ""));
    }

    private void buildInspect(int left, int top, int colW, int gap, int rowH) {
        List<String> rows = linesAfterFirst(data);
        int shown = Math.min(rows.size(), 13);
        for (int i = 0; i < shown; i++) label(rows.get(i), left, top + i * 16);
        button("Clear inventory", left, panelY + panelH - 58, colW, rowH, () -> act("INV_CLEAR", currentTarget, ""));
        button("Clear ender chest", left + colW + gap, panelY + panelH - 58, colW, rowH, () -> act("ENDER_CLEAR", currentTarget, ""));
        button("Back to player", panelX + panelW / 2 - 60, panelY + panelH - 31, 120, 19, () -> setPage("player", currentTarget));
    }

    private void buildHelp(int left, int top, int colW, int gap, int rowH) {
        String[] help = {"/sc or /manager — open management UI", "/sethome [name], /home [name], /delhome <name>", "/homes — open saved homes", "/setwarp <name>, /warp <name>, /delwarp <name>", "/warps — browse warps", "/setspawn and /spawn", "/tpa <player>, /tpaccept, /tpdeny", "/sctp <player> — staff teleport (permission-gated)", "Install this mod on the server AND every client."};
        for (int i = 0; i < help.length; i++) label(help[i], left, top + i * 20);
        button("Back", panelX + panelW / 2 - 45, panelY + panelH - 31, 90, 19, () -> setPage("main", ""));
    }

    private void button(String text, int x, int y, int w, int h, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> action.run()).bounds(x, y, w, h).build());
    }

    private void label(String text, int x, int y) {
        // Labels are drawn from render so they do not capture mouse input.
        staticLabels.add(new Label(text, x, y));
    }

    private final List<Label> staticLabels = new ArrayList<>();

    private void act(String action, String arg1, String arg2) {
        NetworkHandler.sendAction(action, arg1, arg2);
        onClose();
    }

    private void request(String action, String arg1, String arg2) {
        NetworkHandler.sendAction(action, arg1, arg2);
    }

    private void setPage(String next, String text) {
        minecraft.setScreen(new ManagerScreen(next, text));
    }

    private String val(EditBox box) { return box == null ? "" : box.getValue().trim(); }
    private static String firstLine(String s) { int i = s.indexOf('\n'); return i < 0 ? s : s.substring(0, i); }
    private static List<String> lines(String s) {
        List<String> result = new ArrayList<>();
        if (s == null || s.isBlank()) return result;
        for (String line : s.split("\\n")) if (!line.isBlank()) result.add(line);
        return result;
    }
    private static List<String> linesAfterFirst(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? new ArrayList<>() : lines(s.substring(i + 1));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        staticLabels.clear();
        renderBackground(graphics);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0181928);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 3, 0xFFB05BFF);
        graphics.fill(panelX, panelY + 3, panelX + panelW, panelY + 4, 0xFF44315B);
        String title = switch (page) {
            case "main" -> "SOVEREIGNCORE  •  SERVER CONTROL";
            case "players" -> "ONLINE PLAYERS";
            case "player" -> "PLAYER CONTROL";
            case "teleport" -> "TELEPORT CENTER";
            case "homes" -> "HOME MANAGEMENT";
            case "warps" -> "WARP DIRECTORY";
            case "ranks" -> "RANKS & PERMISSIONS";
            case "roleperms" -> "PERMISSION EDITOR";
            case "personal" -> "PERSONAL TELEPORTS";
            case "settings" -> "SERVER SETTINGS";
            case "inspect" -> "INVENTORY INSPECTOR";
            case "help" -> "COMMAND REFERENCE";
            case "notice" -> "SOVEREIGNCORE NOTICE";
            default -> "SOVEREIGNCORE";
        };
        graphics.drawCenteredString(font, Component.literal(title), width / 2, panelY + 14, 0xFFE8D9FF);
        if (page.equals("notice")) {
            graphics.drawCenteredString(font, Component.literal(data), width / 2, panelY + panelH / 2, 0xFFFFC2A8);
        }
        // Render the fixed text labels for this frame.
        staticLabels.clear();
        collectLabelsForRender();
        for (Label label : staticLabels) graphics.drawString(font, Component.literal(label.text), label.x, label.y, 0xFFD8D4E8, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void collectLabelsForRender() {
        int left = panelX + 16;
        int top = panelY + 42;
        switch (page) {
            case "player" -> label("Selected player: " + currentTarget, left, panelY + 26);
            case "homes" -> label("Click a home to teleport. Use X to delete.", left, panelY + 25);
            case "warps" -> label("Click a warp to teleport.", left, panelY + 25);
            case "ranks" -> label("Select a rank to edit its permission toggles.", left, panelY + 25);
            case "roleperms" -> label("Permissions for rank: " + currentRole, left, panelY + 25);
            case "settings" -> label("Server setup actions require world/settings permission.", left, panelY + 25);
            case "inspect" -> {
                List<String> rows = linesAfterFirst(data);
                for (int i = 0; i < Math.min(rows.size(), 13); i++) label(rows.get(i), left, top + i * 16);
            }
            case "help" -> {
                String[] help = {"/sc or /manager — open management UI", "/sethome [name], /home [name], /delhome <name>", "/homes — open saved homes", "/setwarp <name>, /warp <name>, /delwarp <name>", "/warps — browse warps", "/setspawn and /spawn", "/tpa <player>, /tpaccept, /tpdeny", "/sctp <player> — staff teleport (permission-gated)", "Install this mod on the server AND every client."};
                for (int i = 0; i < help.length; i++) label(help[i], left, top + i * 20);
            }
            case "players" -> {
                List<String> rows = lines(data);
                if (rows.isEmpty()) label("No players online.", left, top + 4);
                if (rows.size() > 10) label("Showing first 10 online players.", left, top + 130);
            }
            default -> { }
        }
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private record Label(String text, int x, int y) {}
}
