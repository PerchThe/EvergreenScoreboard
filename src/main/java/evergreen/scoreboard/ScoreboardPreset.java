package evergreen.scoreboard;

import evergreen.scoreboard.ScoreboardSettings.Module;
import evergreen.scoreboard.ScoreboardSettings.SidebarText;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

record ScoreboardPreset(String id, Component name, Material material, List<Module> modules, List<Component> lore) {
    private static final List<Component> DEFAULT_LORE = List.of(
            Component.empty(),
            Component.text("Click here to select this scoreboard!", NamedTextColor.GREEN),
            Component.empty(),
            Component.text("Default Preview:", NamedTextColor.WHITE),
            Component.text("{preview}"));

    List<Component> tooltip(SidebarText text, SidebarColor color, Function<String, Component> renderLine) {
        List<Component> tooltip = new ArrayList<>();
        PlayerSidebar.Rendered preview = null;
        for (Component line : lore) {
            String token = PlainTextComponentSerializer.plainText().serialize(line);
            if (!token.equals("{preview}") && !token.equals("{title}") && !token.equals("{options}")) {
                tooltip.add(line);
                continue;
            }
            if (preview == null) {
                preview = PlayerSidebar.render(text, modules, color, renderLine);
            }
            switch (token) {
                case "{preview}" -> tooltip.addAll(preview.rows().subList(0, preview.rows().size() - 1));
                case "{title}" -> tooltip.add(preview.title());
                case "{options}" -> tooltip.add(preview.rows().getLast());
            }
        }
        return tooltip;
    }

    static List<ScoreboardPreset> load(File file, ScoreboardSettings settings)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        List<ScoreboardPreset> presets = new ArrayList<>();
        for (String id : config.getKeys(false)) {
            ConfigurationSection entry = config.getConfigurationSection(id);
            if (entry == null) {
                throw new InvalidConfigurationException("presets.yml " + id + " must be a mapping.");
            }
            String name = entry.getString("name");
            if (!entry.isString("name") || name.isBlank() || name.contains("\n") || name.contains("\r")) {
                throw new InvalidConfigurationException("presets.yml " + id + ".name must be a nonempty single-line string.");
            }
            ButtonStyle style = ButtonStyle.read(entry, "presets.yml " + id);
            if (style.material() == null) {
                throw new InvalidConfigurationException("presets.yml " + id + ".material must name a valid item.");
            }
            List<?> entries = entry.getList("modules");
            if (entries == null || entries.stream().anyMatch(value ->
                    !(value instanceof String moduleId) || !moduleId.matches("[a-z0-9_-]+"))) {
                throw new InvalidConfigurationException("presets.yml " + id + ".modules must be a list of module IDs.");
            }
            List<Module> modules = List.copyOf(settings.fitSelection(entry.getStringList("modules")).values());
            if (entries.isEmpty() || !modules.isEmpty()) {
                List<Component> lore = style.lore() == null ? DEFAULT_LORE : style.lore();
                presets.add(new ScoreboardPreset(id, style.name(), style.material(), modules, lore));
            }
        }
        return List.copyOf(presets);
    }
}
