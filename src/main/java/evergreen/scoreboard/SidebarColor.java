package evergreen.scoreboard;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;

record SidebarColor(String id, Component label, String legacy, String accentLegacy, TextColor textColor,
                    Material material, List<Component> lore) {
    static String readLegacyCode(Object value, String key) throws InvalidConfigurationException {
        if (!(value instanceof String code) || !code.matches("(?i)&(?:[0-9a-f]|#[0-9a-f]{6})")) {
            throw new InvalidConfigurationException(key + " must be a color code like &1 or &#48ab76.");
        }
        return "\u00a7" + code.substring(1).toLowerCase(Locale.ROOT);
    }

    static TextColor decode(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy + "x").color();
    }

    static SidebarColor custom(String mainInput, String accentInput, Material material) {
        String main = normalizeHex(mainInput);
        if (main == null) {
            return null;
        }
        String accent = accentInput == null || accentInput.isBlank() ? "" : normalizeHex(accentInput);
        if (accent == null) {
            return null;
        }
        String id = accent.isEmpty() ? main : main + "/" + accent;
        TextColor color = TextColor.fromHexString(main);
        return new SidebarColor(id, Component.text("Custom color", color), "\u00a7" + main,
                "\u00a7" + (accent.isEmpty() ? main : accent), color, material, null);
    }

    static SidebarColor savedCustom(String id, Material material) {
        String[] codes = id.split("/", 2);
        return custom(codes[0], codes.length == 2 ? codes[1] : "", material);
    }

    private static String normalizeHex(String input) {
        if (input == null) {
            return null;
        }
        String hex = input.strip();
        if (!hex.matches("(?i)#?[0-9a-f]{6}")) {
            return null;
        }
        return "#" + hex.substring(hex.length() - 6).toLowerCase(Locale.ROOT);
    }

    boolean isCustom() {
        return id.startsWith("#");
    }

    boolean hasCustomAccent() {
        return isCustom() && id.contains("/");
    }

    String customMainHex() {
        return legacy.substring(1);
    }

    String customAccentHex() {
        return accentLegacy.substring(1);
    }

    TextColor accentTextColor() {
        return decode(accentLegacy);
    }
}
