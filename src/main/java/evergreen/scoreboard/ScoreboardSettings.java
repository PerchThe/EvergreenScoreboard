package evergreen.scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

record ScoreboardSettings(int refreshTicks, SidebarText sidebar, List<Module> modules,
                          PlaceholderOutputReplacements placeholderReplacements,
                          PlaceholderRefreshIntervals placeholderRefreshIntervals) {
    static final int MODULE_ROWS = 12;
    static final String TOP_DIVIDER = "<top-separator>";
    static final String BOTTOM_DIVIDER = "<bottom-separator>";

    record Module(String id, Component name, List<String> lines) {
        int rows() {
            return lines.size();
        }

        boolean startsWithDivider() {
            return isDivider(lines.getFirst());
        }

        boolean endsWithDivider() {
            return isDivider(lines.getLast());
        }

        int insertionIndex(int selectedCount) {
            if (!endsWithDivider() && (selectedCount > 0 || !startsWithDivider())) {
                return selectedCount;
            }
            // Insert before the final module so it still provides the bottom content row.
            if (selectedCount >= 2 || (selectedCount == 1 && !startsWithDivider())) {
                return selectedCount - 1;
            }
            return -1;
        }

        private static boolean isDivider(String line) {
            return TOP_DIVIDER.equals(line) || BOTTOM_DIVIDER.equals(line);
        }
    }

    record SidebarText(String title, String topSeparator, String bottomSeparator, String optionsLine,
                       List<String> emptyLines) {
        private static final List<String> DEFAULT_EMPTY_LINES = List.of(
                "| You can adjust available modules", "| with /sb command.");

        static SidebarText load(YamlConfiguration config) throws InvalidConfigurationException {
            ConfigurationSection sidebar = config.getConfigurationSection("sidebar");
            if (sidebar == null) {
                if (config.contains("sidebar")) {
                    throw new InvalidConfigurationException("sidebar must be a mapping.");
                }
                sidebar = new YamlConfiguration();
            }
            return new SidebarText(
                    readLine(sidebar, "title", "\u2022 Evergreen \u2022"),
                    readLine(sidebar, "top-separator", "---------------"),
                    readLine(sidebar, "bottom-separator", "---------------"),
                    readLine(sidebar, "options-line", "Options: /sb"),
                    readLines(sidebar.get("empty-lines", DEFAULT_EMPTY_LINES), "sidebar.empty-lines", 0, MODULE_ROWS));
        }

        static String readLine(ConfigurationSection config, String key, String fallback)
                throws InvalidConfigurationException {
            Object value = config.get(key, fallback);
            if (!(value instanceof String line) || line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) {
                String path = config.getCurrentPath();
                throw new InvalidConfigurationException((path.isEmpty() ? key : path + "." + key)
                        + " must be a single-line string.");
            }
            return line;
        }
    }

    static ScoreboardSettings load(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        // Placeholder outputs can contain dots, which are literal parts of the YAML keys.
        config.options().pathSeparator('\0');
        config.load(file);

        String refreshKey = "refresh-interval-ticks";
        int refreshTicks = config.getInt(refreshKey, 20);
        if ((config.contains(refreshKey) && !config.isInt(refreshKey)) || refreshTicks < 1) {
            throw new InvalidConfigurationException(refreshKey + " must be a positive integer.");
        }

        ConfigurationSection section = config.getConfigurationSection("modules");
        if (section == null && config.contains("modules")) {
            throw new InvalidConfigurationException("modules must be a mapping.");
        }

        List<Module> modules = new ArrayList<>();
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (!id.matches("[a-z0-9_-]+")) {
                    throw new InvalidConfigurationException("Invalid module ID: " + id);
                }
                ConfigurationSection module = section.getConfigurationSection(id);
                if (module == null || !module.isString("name") || module.getString("name").isBlank()) {
                    throw new InvalidConfigurationException("modules." + id + ".name must be a nonempty string.");
                }
                List<String> lines = readLines(module.get("lines"), "modules." + id + ".lines", 1, MODULE_ROWS);
                Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(module.getString("name"));
                modules.add(new Module(id, name, lines));
            }
        }
        return new ScoreboardSettings(refreshTicks, SidebarText.load(config), List.copyOf(modules),
                PlaceholderOutputReplacements.load(config), PlaceholderRefreshIntervals.load(config));
    }

    static List<String> readLines(Object value, String key, int minRows, int maxRows) throws InvalidConfigurationException {
        if (!(value instanceof List<?> entries) || entries.size() < minRows || entries.size() > maxRows) {
            throw new InvalidConfigurationException(
                    key + " must be a list of " + minRows + " to " + maxRows + " rows.");
        }
        List<String> lines = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof String line) || line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) {
                throw new InvalidConfigurationException("Each entry in " + key + " must be a single-line string.");
            }
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    Map<String, Module> fitSelection(List<String> ids) {
        Map<String, Module> available = new HashMap<>();
        for (Module module : modules) {
            available.put(module.id(), module);
        }
        var selected = new LinkedHashMap<String, Module>();
        int remaining = MODULE_ROWS;
        for (String id : ids) {
            Module module = available.get(id);
            if (module == null || selected.containsKey(id) || (selected.isEmpty() && module.startsWithDivider())) {
                continue;
            }
            int required = module.rows() + (module.endsWithDivider() ? 1 : 0);
            if (required <= remaining) {
                selected.put(id, module);
                remaining -= module.rows();
            }
        }
        while (!selected.isEmpty() && selected.lastEntry().getValue().endsWithDivider()) {
            selected.pollLastEntry();
        }
        return selected;
    }

    static boolean canSwap(List<Module> ordered, int index, int destination) {
        int last = ordered.size() - 1;
        return destination >= 0 && destination <= last
                && fitsAt(ordered.get(index), destination, last)
                && fitsAt(ordered.get(destination), index, last);
    }

    private static boolean fitsAt(Module module, int index, int last) {
        return (index != 0 || !module.startsWithDivider()) && (index != last || !module.endsWithDivider());
    }
}
