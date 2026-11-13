package evergreen.scoreboard;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;

record ButtonStyle(Material material, Component name, List<Component> lore) {
    static final ButtonStyle DEFAULT = new ButtonStyle(null, null, null);

    static ButtonStyle read(ConfigurationSection section, String key) throws InvalidConfigurationException {
        if (section == null) {
            return DEFAULT;
        }
        Material material = section.contains("material") ? readMaterial(section.get("material"), key + ".material") : null;
        Object name = section.get("name");
        if (name != null && !isSingleLine(name)) {
            throw new InvalidConfigurationException(key + ".name must be a single-line string.");
        }
        Object lore = section.get("lore");
        if (lore != null && (!(lore instanceof List<?> lines) || lines.stream().anyMatch(line -> !isSingleLine(line)))) {
            throw new InvalidConfigurationException(key + ".lore must be a list of single-line strings.");
        }
        var serializer = LegacyComponentSerializer.legacyAmpersand();
        return new ButtonStyle(material, name == null ? null : serializer.deserialize((String) name),
                lore == null ? null : section.getStringList("lore").stream().<Component>map(serializer::deserialize).toList());
    }

    static Material readMaterial(Object value, String key) throws InvalidConfigurationException {
        Material material = value instanceof String name ? Material.matchMaterial(name) : null;
        if (material == null || material.isAir() || !material.isItem()) {
            throw new InvalidConfigurationException(key + " must name a valid item.");
        }
        String name = material.name();
        if (name.endsWith("_DYE")) {
            Material cushion = Material.getMaterial(name.replace("_DYE", "_CUSHION"));
            if (cushion != null) {
                return cushion;
            }
        }
        return material;
    }

    private static boolean isSingleLine(Object value) {
        return value instanceof String text && !text.contains("\n") && !text.contains("\r");
    }

    ButtonStyle orElse(ButtonStyle fallback) {
        return new ButtonStyle(material == null ? fallback.material : material,
                name == null ? fallback.name : name, lore == null ? fallback.lore : lore);
    }

    ButtonStyle withValues(Map<String, Component> values) {
        if (values.isEmpty() || (name == null && lore == null)) {
            return this;
        }
        return new ButtonStyle(material, name == null ? null : replace(name, values),
                lore == null ? null : lore.stream().map(line -> replace(line, values)).toList());
    }

    private static Component replace(Component text, Map<String, Component> values) {
        for (var entry : values.entrySet()) {
            text = text.replaceText(builder -> builder.matchLiteral("{" + entry.getKey() + "}")
                    .replacement(entry.getValue()));
        }
        return text;
    }
}
