package evergreen.scoreboard;

import evergreen.scoreboard.ScoreboardSettings.Module;
import evergreen.scoreboard.ScoreboardSettings.SidebarText;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

record AltSidebarSettings(SidebarText text, Map<String, Module> selection, SidebarColor color) {
    static AltSidebarSettings load(File file, SidebarText regular) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        String legacy = SidebarColor.readLegacyCode(config.get("color", "&#48ab76"), "alt.yml color");
        Object accent = config.get("accent-color");
        String accentLegacy = accent == null ? legacy : SidebarColor.readLegacyCode(accent, "alt.yml accent-color");
        TextColor textColor = SidebarColor.decode(legacy);
        SidebarColor color = new SidebarColor("ALT", Component.text("Alt", textColor), legacy, accentLegacy,
                textColor, Material.PAPER, null);
        SidebarText text = new SidebarText(
                SidebarText.readLine(config, "title", regular.title()),
                SidebarText.readLine(config, "top-separator", regular.topSeparator()),
                SidebarText.readLine(config, "bottom-separator", regular.bottomSeparator()),
                null, List.of());
        List<String> lines = ScoreboardSettings.readLines(config.get("lines"), "alt.yml lines", 1,
                PlayerSidebar.SIDEBAR_ROWS - 2);
        Module layout = new Module("alt", Component.text("Alt"), lines);
        if (layout.startsWithDivider() || layout.endsWithDivider()) {
            throw new InvalidConfigurationException("alt.yml lines must have content at the top and bottom.");
        }
        return new AltSidebarSettings(text, Map.of(layout.id(), layout), color);
    }
}
