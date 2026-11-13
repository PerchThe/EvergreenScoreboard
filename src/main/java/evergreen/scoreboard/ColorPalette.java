package evergreen.scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

record ColorPalette(List<SidebarColor> colors) {
    static ColorPalette load(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        List<SidebarColor> colors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (String key : config.getKeys(false)) {
            ConfigurationSection entry = config.getConfigurationSection(key);
            if (entry == null || !key.matches("[A-Za-z0-9_-]+")) {
                throw new InvalidConfigurationException("colors.yml needs a mapping for color " + key
                        + " with a name made of letters, numbers, hyphens, or underscores.");
            }
            String id = key.toUpperCase(Locale.ROOT).replace('-', '_');
            if (!ids.add(id)) {
                throw new InvalidConfigurationException("Duplicate color name in colors.yml: " + key);
            }

            String legacy = SidebarColor.readLegacyCode(entry.get("color"), "colors.yml " + key + ".color");
            Object accent = entry.get("accent-color");
            String accentLegacy = accent == null ? legacy
                    : SidebarColor.readLegacyCode(accent, "colors.yml " + key + ".accent-color");
            TextColor textColor = SidebarColor.decode(legacy);
            if (textColor == null) {
                throw new InvalidConfigurationException("Could not parse colors.yml " + key + ".color.");
            }

            ButtonStyle style = ButtonStyle.read(entry, "colors.yml " + key);
            Material material = style.material();
            if (material == null) {
                throw new InvalidConfigurationException("colors.yml " + key + ".material must name a valid item.");
            }
            Component name = style.name() == null ? Component.text(key.replace('-', ' ').replace('_', ' '), textColor)
                    : Component.text().color(textColor).append(style.name()).build();
            colors.add(new SidebarColor(id, name, legacy, accentLegacy, textColor, material, style.lore()));
        }
        if (colors.isEmpty()) {
            throw new InvalidConfigurationException("colors.yml must define at least one color.");
        }
        return new ColorPalette(List.copyOf(colors));
    }

    SidebarColor fromSaved(String id) {
        SidebarColor fallback = colors.getFirst();
        for (SidebarColor color : colors) {
            if (color.id().equals(id)) {
                return color;
            }
            if (color.id().equals("WHITE")) {
                fallback = color;
            }
        }
        return fallback;
    }
}
