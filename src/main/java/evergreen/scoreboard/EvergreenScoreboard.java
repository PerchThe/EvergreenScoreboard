package evergreen.scoreboard;

import evergreen.scoreboard.ScoreboardSettings.Module;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class EvergreenScoreboard extends JavaPlugin implements Listener {
    private enum Access { NONE, STANDARD, ALT }

    private static final String USE_PERMISSION = "evergreen.scoreboard.use";
    private static final String ALT_PERMISSION = "evergreen.scoreboard.alt";
    private static final String DEFAULT_PRESET = "general";
    private static final String LEGACY_DEFAULT_PRESET = "everyday";
    private static final String DEFAULT_COLOR = "EVER_GREEN";
    private final Map<UUID, PlayerSidebar> sidebars = new HashMap<>();
    private final Set<String> warnedTemplates = new HashSet<>();
    private NamespacedKey selectionKey;
    private NamespacedKey colorKey;
    private NamespacedKey visibilityKey;
    private ScoreboardSettings settings;
    private AltSidebarSettings altSettings;
    private GuiSettings guiSettings;
    private BukkitTask refreshTask;
    private ShopHighlightTeams shopHighlightTeams;
    private BukkitTask shopHighlightTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String resource : List.of("gui.yml", "colors.yml", "presets.yml", "alt.yml")) {
            if (!new File(getDataFolder(), resource).exists()) {
                saveResource(resource, false);
            }
        }
        selectionKey = new NamespacedKey(this, "modules");
        colorKey = new NamespacedKey(this, "color");
        visibilityKey = new NamespacedKey(this, "visible");
        try {
            loadSettings();
        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().log(Level.SEVERE, "Could not load scoreboard configuration.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        if (getServer().getPluginManager().isPluginEnabled(ShopHighlightTeams.PLUGIN_NAME)) {
            startShopHighlightSync();
        }
        getServer().getOnlinePlayers().forEach(this::synchronizeSidebar);
        startRefreshTask();
    }

    @Override
    public void onDisable() {
        stopShopHighlightSync();
        for (Player player : getServer().getOnlinePlayers()) {
            PlayerSidebar sidebar = sidebars.get(player.getUniqueId());
            if (sidebar != null) {
                sidebar.restore(player);
            }
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ModuleMenu) {
                player.closeInventory();
            }
        }
        sidebars.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        synchronizeSidebar(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sidebars.remove(playerId);
        settings.placeholderRefreshIntervals().removePlayer(playerId);
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (ShopHighlightTeams.PLUGIN_NAME.equals(event.getPlugin().getName())) {
            startShopHighlightSync();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (ShopHighlightTeams.PLUGIN_NAME.equals(event.getPlugin().getName())) {
            stopShopHighlightSync();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && access(player) != Access.STANDARD) {
            synchronizeSidebar(player);
            player.sendMessage(Component.text("Scoreboard customization is unavailable for this account.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("evergreen.scoreboard.reload")) {
                reloadSettings(sender);
            } else {
                sender.sendMessage(Component.text("You don't have permission to reload the scoreboard.", NamedTextColor.RED));
            }
            return true;
        }
        if (args.length != 0) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Run /sb in game to customize your scoreboard, or use sb reload.");
            return true;
        }
        PlayerSidebar sidebar = synchronizeSidebar(player);
        if (sidebar != null && !sidebar.alt()) {
            ModuleMenu menu = new ModuleMenu(guiSettings);
            drawMenu(player, sidebar, menu);
            player.openInventory(menu.getInventory());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("evergreen.scoreboard.reload")
                && (!(sender instanceof Player player) || access(player) == Access.STANDARD)
                && "reload".regionMatches(true, 0, args[0], 0, args[0].length())) {
            return List.of("reload");
        }
        return List.of();
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ModuleMenu menu)) {
            return;
        }
        event.setCancelled(true);
        ClickType click = event.getClick();
        if ((click != ClickType.LEFT && !(menu.view() == ModuleMenu.View.REORDER && click == ClickType.RIGHT))
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        PlayerSidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null || sidebar.alt() || access(player) != Access.STANDARD) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0) {
            return;
        }
        if (click == ClickType.LEFT) {
            if (menu.isVisibilitySlot(slot)) {
                sidebar.setVisible(!sidebar.visible());
                player.getPersistentDataContainer().set(visibilityKey, PersistentDataType.BOOLEAN, sidebar.visible());
                applyVisibility(player, sidebar);
                drawMenu(player, sidebar, menu);
                return;
            }
            ModuleMenu.View destination = menu.destination(slot);
            if (destination != null) {
                changeMenuView(player, sidebar, menu, destination);
                return;
            }
            if (menu.changePage(slot, settings.modules().size())) {
                drawMenu(player, sidebar, menu);
                return;
            }
        }
        if (menu.view() == ModuleMenu.View.PRESETS) {
            ScoreboardPreset preset = menu.presetAt(slot);
            if (preset != null) {
                updateSelection(player, sidebar, menu, preset.modules().stream().map(Module::id).toList());
                changeMenuView(player, sidebar, menu, ModuleMenu.View.COLORS);
            }
            return;
        }
        if (menu.view() == ModuleMenu.View.REORDER) {
            moveModule(player, sidebar, menu, menu.entryIndex(slot), click == ClickType.LEFT ? -1 : 1);
            return;
        }
        if (menu.view() == ModuleMenu.View.COLORS) {
            if (menu.isCustomColorSlot(slot)) {
                getServer().getScheduler().runTask(this, () -> {
                    if (isCurrentMenu(player, sidebar, menu) && menu.view() == ModuleMenu.View.COLORS) {
                        SidebarColor current = sidebar.color();
                        String main = current.isCustom() ? current.customMainHex() : "";
                        String accent = current.hasCustomAccent() ? current.customAccentHex() : "";
                        promptCustomColor(player, sidebar, menu, main, accent, false);
                    }
                });
            } else {
                SidebarColor color = menu.colorAt(slot);
                if (color != null) {
                    applyColor(player, sidebar, menu, color);
                }
            }
            return;
        }
        Module module = menu.moduleAt(settings.modules(), slot);
        if (module == null) {
            return;
        }

        List<String> selected = new ArrayList<>(sidebar.selection().keySet());
        if (!selected.remove(module.id())) {
            if (sidebar.usedRows() + module.rows() > ScoreboardSettings.MODULE_ROWS) {
                player.sendMessage(Component.text().color(NamedTextColor.RED).append(module.name())
                        .append(Component.text(" needs " + module.rows() + " "
                                + (module.rows() == 1 ? "row" : "rows") + ". Free up space before adding it."))
                        .build());
                return;
            }
            int index = module.insertionIndex(selected.size());
            if (index < 0) {
                sendDividerPlacementMessage(player);
                return;
            }
            selected.add(index, module.id());
        }
        updateSelection(player, sidebar, menu, selected);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ModuleMenu) {
            event.setCancelled(true);
        }
    }

    private void changeMenuView(Player player, PlayerSidebar sidebar, ModuleMenu menu, ModuleMenu.View destination) {
        if (menu.switchInPlace(destination)) {
            drawMenu(player, sidebar, menu);
            return;
        }

        getServer().getScheduler().runTask(this, () -> {
            if (!isCurrentMenu(player, sidebar, menu)) {
                return;
            }
            ModuleMenu next = menu.newView(destination);
            drawMenu(player, sidebar, next);
            player.openInventory(next.getInventory());
        });
    }

    private boolean isCurrentMenu(Player player, PlayerSidebar sidebar, ModuleMenu menu) {
        return access(player) == Access.STANDARD && sidebars.get(player.getUniqueId()) == sidebar && menu.uses(guiSettings)
                && menu.getInventory().equals(player.getOpenInventory().getTopInventory());
    }

    private void promptCustomColor(Player player, PlayerSidebar sidebar, ModuleMenu menu,
                                   String main, String accent, boolean invalid) {
        CustomColorDialog.show(player, main, accent, invalid, (mainInput, accentInput) -> {
            if (!isCurrentMenu(player, sidebar, menu) || menu.view() != ModuleMenu.View.COLORS) {
                return;
            }
            SidebarColor color = SidebarColor.custom(mainInput, accentInput, guiSettings.customColor().material());
            if (color == null) {
                String retryMain = mainInput != null && mainInput.length() <= 7 ? mainInput : main;
                String retryAccent = accentInput != null && accentInput.length() <= 7 ? accentInput : accent;
                promptCustomColor(player, sidebar, menu, retryMain, retryAccent, true);
                return;
            }
            applyColor(player, sidebar, menu, color);
        });
    }

    private void applyColor(Player player, PlayerSidebar sidebar, ModuleMenu menu, SidebarColor color) {
        player.getPersistentDataContainer().set(colorKey, PersistentDataType.STRING, color.id());
        if (!color.equals(sidebar.color())) {
            sidebar.setColor(color);
            refreshSidebar(player, sidebar);
            drawMenu(player, sidebar, menu);
        }
    }

    private static Access access(Player player) {
        if (player.hasPermission(ALT_PERMISSION)) {
            return Access.ALT;
        }
        return player.hasPermission(USE_PERMISSION) ? Access.STANDARD : Access.NONE;
    }

    private PlayerSidebar synchronizeSidebar(Player player) {
        Access access = access(player);
        UUID id = player.getUniqueId();
        PlayerSidebar sidebar = sidebars.get(id);
        if (sidebar != null && access != Access.NONE && sidebar.alt() == (access == Access.ALT)) {
            return sidebar;
        }
        if (sidebar != null) {
            sidebar.restore(player);
            if (!sidebar.alt() && player.getOpenInventory().getTopInventory().getHolder() instanceof ModuleMenu) {
                player.closeInventory();
            }
        }
        if (access == Access.NONE) {
            sidebars.remove(id);
            return null;
        }

        var scoreboard = getServer().getScoreboardManager().getNewScoreboard();
        if (shopHighlightTeams != null) {
            shopHighlightTeams.copyTo(scoreboard);
        }
        if (access == Access.ALT) {
            sidebar = new PlayerSidebar(scoreboard, altSettings.selection(), altSettings.color(), true, true);
        } else {
            initializePreferences(player);
            boolean visible = player.getPersistentDataContainer().getOrDefault(visibilityKey, PersistentDataType.BOOLEAN, true);
            sidebar = new PlayerSidebar(scoreboard, loadSelection(player), loadColor(player), visible, false);
        }
        sidebars.put(id, sidebar);
        applyVisibility(player, sidebar);
        return sidebar;
    }

    private void applyVisibility(Player player, PlayerSidebar sidebar) {
        if (sidebar.visible()) {
            renderSidebar(player, sidebar);
            sidebar.show(player);
        } else {
            sidebar.restore(player);
        }
    }

    private void initializePreferences(Player player) {
        var data = player.getPersistentDataContainer();
        var selectionType = PersistentDataType.LIST.strings();
        if (data.has(selectionKey, selectionType)) {
            return;
        }
        List<ScoreboardPreset> presets = guiSettings.presets();
        presets.stream().filter(preset -> preset.id().equals(DEFAULT_PRESET)).findFirst()
                .or(() -> presets.stream().filter(preset -> preset.id().equals(LEGACY_DEFAULT_PRESET)).findFirst())
                .ifPresent(preset -> data.set(selectionKey, selectionType, preset.modules().stream().map(Module::id).toList()));
        if (!data.has(colorKey, PersistentDataType.STRING)) {
            SidebarColor color = guiSettings.palette().fromSaved(DEFAULT_COLOR);
            if (color.id().equals(DEFAULT_COLOR)) {
                data.set(colorKey, PersistentDataType.STRING, color.id());
            }
        }
    }

    private Map<String, Module> loadSelection(Player player) {
        List<String> saved = player.getPersistentDataContainer().getOrDefault(
                selectionKey, PersistentDataType.LIST.strings(), List.of());
        Map<String, Module> selection = settings.fitSelection(saved);
        if (selection.size() != saved.size()) {
            saveSelection(player, selection);
        }
        return selection;
    }

    private SidebarColor loadColor(Player player) {
        String saved = player.getPersistentDataContainer().get(colorKey, PersistentDataType.STRING);
        if (saved != null && saved.startsWith("#")) {
            SidebarColor custom = SidebarColor.savedCustom(saved, guiSettings.customColor().material());
            if (custom != null) {
                return custom;
            }
        }
        return guiSettings.palette().fromSaved(saved);
    }

    private void saveSelection(Player player, Map<String, Module> selection) {
        player.getPersistentDataContainer().set(
                selectionKey, PersistentDataType.LIST.strings(), new ArrayList<>(selection.keySet()));
    }

    private void moveModule(Player player, PlayerSidebar sidebar, ModuleMenu menu, int slot, int direction) {
        int size = sidebar.selection().size();
        int destination = slot + direction;
        if (slot < 0 || slot >= size || destination < 0 || destination >= size) {
            return;
        }
        if (!ScoreboardSettings.canSwap(List.copyOf(sidebar.selection().values()), slot, destination)) {
            sendDividerPlacementMessage(player);
            return;
        }
        List<String> selected = new ArrayList<>(sidebar.selection().keySet());
        Collections.swap(selected, slot, destination);
        updateSelection(player, sidebar, menu, selected);
    }

    private void sendDividerPlacementMessage(Player player) {
        player.sendMessage(Component.text("A divider needs content on both sides.", NamedTextColor.RED));
    }

    private void updateSelection(Player player, PlayerSidebar sidebar, ModuleMenu menu, List<String> selected) {
        sidebar.select(settings.fitSelection(selected));
        saveSelection(player, sidebar.selection());
        refreshSidebar(player, sidebar);
        drawMenu(player, sidebar, menu);
    }

    private void drawMenu(Player player, PlayerSidebar sidebar, ModuleMenu menu) {
        menu.draw(settings, sidebar, line -> renderLine(player, line));
    }

    private void loadSettings() throws IOException, InvalidConfigurationException {
        ScoreboardSettings nextSettings = ScoreboardSettings.load(new File(getDataFolder(), "config.yml"));
        AltSidebarSettings nextAlt = AltSidebarSettings.load(new File(getDataFolder(), "alt.yml"), nextSettings.sidebar());
        ColorPalette palette = ColorPalette.load(new File(getDataFolder(), "colors.yml"));
        List<ScoreboardPreset> presets = ScoreboardPreset.load(new File(getDataFolder(), "presets.yml"), nextSettings);
        GuiSettings nextGui;
        try (var bundledGui = new InputStreamReader(getResource("gui.yml"), StandardCharsets.UTF_8)) {
            nextGui = GuiSettings.load(new File(getDataFolder(), "gui.yml"), bundledGui, palette, presets);
        }
        settings = nextSettings;
        altSettings = nextAlt;
        guiSettings = nextGui;
        if (nextGui.customColor().slot() < 0) {
            getLogger().warning("No free color picker slot for Custom color. Free a slot in gui.yml.");
        }
        if (nextGui.presetVisibilityEnabled() && nextGui.presetLayout().button(GuiSettings.Button.VISIBILITY) < 0) {
            getLogger().warning("No free preset menu slot for scoreboard visibility. Free a slot in gui.yml.");
        }
    }

    private void reloadSettings(CommandSender sender) {
        try {
            loadSettings();
        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().log(Level.WARNING, "Could not reload scoreboard configuration.", exception);
            sender.sendMessage(Component.text("Scoreboard reload failed. See the server log.", NamedTextColor.RED));
            return;
        }
        warnedTemplates.clear();
        for (Player player : getServer().getOnlinePlayers()) {
            PlayerSidebar sidebar = synchronizeSidebar(player);
            if (sidebar != null) {
                if (!sidebar.alt()) {
                    initializePreferences(player);
                }
                sidebar.select(sidebar.alt() ? altSettings.selection() : loadSelection(player));
                sidebar.setColor(sidebar.alt() ? altSettings.color() : loadColor(player));
                refreshSidebar(player, sidebar);
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof ModuleMenu menu) {
                    if (menu.uses(guiSettings)) {
                        drawMenu(player, sidebar, menu);
                    } else {
                        player.closeInventory();
                    }
                }
            }
        }
        startRefreshTask();
        sender.sendMessage(Component.text("Scoreboard configuration reloaded.", NamedTextColor.GREEN));
    }

    private void startShopHighlightSync() {
        if (shopHighlightTask == null) {
            var teams = new ShopHighlightTeams(getServer().getScoreboardManager().getMainScoreboard());
            teams.refresh(sidebars.values());
            shopHighlightTeams = teams;
            // ShopFinder's rainbow glow changes every ten ticks.
            shopHighlightTask = getServer().getScheduler().runTaskTimer(this, () -> teams.refresh(sidebars.values()), 1, 1);
        }
    }

    private void stopShopHighlightSync() {
        if (shopHighlightTask != null) {
            shopHighlightTask.cancel();
            shopHighlightTask = null;
            shopHighlightTeams.clear(sidebars.values());
            shopHighlightTeams = null;
        }
    }

    private void startRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        int interval = settings.refreshTicks();
        refreshTask = getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                PlayerSidebar sidebar = synchronizeSidebar(player);
                if (sidebar != null) {
                    refreshSidebar(player, sidebar);
                }
            }
        }, interval, interval);
    }

    private void refreshSidebar(Player player, PlayerSidebar sidebar) {
        if (sidebar.visible() && sidebar.isShowing(player)) {
            renderSidebar(player, sidebar);
        }
    }

    private void renderSidebar(Player player, PlayerSidebar sidebar) {
        sidebar.refresh(sidebar.alt() ? altSettings.text() : settings.sidebar(), line -> renderLine(player, line));
    }

    private Component renderLine(Player player, String template) {
        String line = settings.placeholderReplacements().apply(template, part -> resolvePlaceholders(player, part, template));
        line = line.replace('\r', ' ').replace('\n', ' ');
        return LegacyComponentSerializer.legacySection().deserialize(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
    }

    private String resolvePlaceholders(Player player, String part, String template) {
        if (part.indexOf('%') < 0) {
            return part;
        }
        String line = part.replace("%world%", player.getWorld().getName());
        if (line.indexOf('%') >= 0) {
            try {
                line = settings.placeholderRefreshIntervals().resolve(player, line);
            } catch (RuntimeException exception) {
                if (warnedTemplates.add(template)) {
                    getLogger().log(Level.WARNING, "Could not resolve placeholders in " + template, exception);
                }
            }
        }
        if (line.contains("%player_x%") || line.contains("%player_y%") || line.contains("%player_z%")) {
            Location location = player.getLocation();
            line = line.replace("%player_x%", Integer.toString(location.getBlockX()))
                    .replace("%player_y%", Integer.toString(location.getBlockY()))
                    .replace("%player_z%", Integer.toString(location.getBlockZ()));
        }
        return line;
    }
}
