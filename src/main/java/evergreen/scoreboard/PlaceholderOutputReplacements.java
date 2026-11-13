package evergreen.scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;

record PlaceholderOutputReplacements(Map<String, Map<String, String>> rules) {
    private static final String KEY = "placeholder-output-replacements";
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:world|[^%_\\s]+_[^%]*)%");

    static PlaceholderOutputReplacements load(ConfigurationSection config) throws InvalidConfigurationException {
        ConfigurationSection section = config.getConfigurationSection(KEY);
        if (section == null) {
            if (config.contains(KEY)) {
                throw new InvalidConfigurationException(KEY + " must be a mapping.");
            }
            return new PlaceholderOutputReplacements(Map.of());
        }

        Map<String, Map<String, String>> rules = new HashMap<>();
        for (var entry : section.getValues(false).entrySet()) {
            String placeholder = entry.getKey();
            if (!PLACEHOLDER.matcher(placeholder).matches() || !(entry.getValue() instanceof ConfigurationSection outputs)) {
                throw new InvalidConfigurationException(KEY + " entries must map a placeholder such as '%world%' to its outputs.");
            }
            Map<String, String> replacements = new HashMap<>();
            for (var output : outputs.getValues(false).entrySet()) {
                Object value = output.getValue();
                if (!(value instanceof String || value instanceof Number || value instanceof Boolean)
                        || value.toString().contains("\n") || value.toString().contains("\r")) {
                    throw new InvalidConfigurationException(KEY + " for " + placeholder + " must use single-line output values.");
                }
                replacements.put(output.getKey(), value.toString());
            }
            if (!replacements.isEmpty()) {
                rules.put(placeholder, Map.copyOf(replacements));
            }
        }
        return new PlaceholderOutputReplacements(Map.copyOf(rules));
    }

    String apply(String template, Function<String, String> resolve) {
        if (rules.isEmpty() || template.indexOf('%') < 0) {
            return resolve.apply(template);
        }

        var matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = null;
        int end = 0;
        while (matcher.find()) {
            String placeholder = matcher.group();
            Map<String, String> replacements = rules.get(placeholder);
            if (replacements == null) {
                continue;
            }
            if (result == null) {
                result = new StringBuilder(template.length());
            }
            result.append(resolve.apply(template.substring(end, matcher.start())));
            String output = resolve.apply(placeholder);
            result.append(replacements.getOrDefault(output, output));
            end = matcher.end();
        }
        return result == null ? resolve.apply(template) : result.append(resolve.apply(template.substring(end))).toString();
    }
}
