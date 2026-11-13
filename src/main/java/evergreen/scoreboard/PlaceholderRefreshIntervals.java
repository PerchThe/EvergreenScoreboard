package evergreen.scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;

final class PlaceholderRefreshIntervals {
    private static final String KEY = "placeholderapi-refresh-intervals";
    private static final String DEFAULT_KEY = "default-refresh-interval";
    private static final int DEFAULT_MILLIS = 1000;
    // PAPI accepts spaces in parameters, but a space before the first underscore ends a candidate.
    private static final Pattern TOKEN = Pattern.compile("%[^%_ ]*(?:_[^%]*)?%");

    private record CachedValue(String text, long expiresAt) {}

    private final long defaultNanos;
    private final Map<String, Long> overrides;
    private final Map<UUID, Map<String, CachedValue>> playerValues = new HashMap<>();

    private PlaceholderRefreshIntervals(long defaultNanos, Map<String, Long> overrides) {
        this.defaultNanos = defaultNanos;
        this.overrides = overrides;
    }

    static PlaceholderRefreshIntervals load(ConfigurationSection config) throws InvalidConfigurationException {
        ConfigurationSection section = config.getConfigurationSection(KEY);
        if (section == null) {
            if (config.contains(KEY)) {
                throw new InvalidConfigurationException(KEY + " must be a mapping.");
            }
            return new PlaceholderRefreshIntervals(0, Map.of());
        }
        long defaultNanos = readInterval(section, DEFAULT_KEY, DEFAULT_MILLIS);
        Map<String, Long> overrides = new HashMap<>();
        for (String key : section.getKeys(false)) {
            if (DEFAULT_KEY.equals(key)) {
                continue;
            }
            if (!TOKEN.matcher(key).matches() || key.indexOf('_') <= 1) {
                throw new InvalidConfigurationException(KEY + " entries must use PlaceholderAPI tokens such as '%player_name%'.");
            }
            overrides.put(key, readInterval(section, key, 0));
        }
        return new PlaceholderRefreshIntervals(defaultNanos, Map.copyOf(overrides));
    }

    private static long readInterval(ConfigurationSection section, String key, int fallback)
            throws InvalidConfigurationException {
        int millis = section.getInt(key, fallback);
        if ((section.contains(key) && !section.isInt(key)) || millis < 0) {
            throw new InvalidConfigurationException(KEY + "." + key + " must be a nonnegative integer in milliseconds.");
        }
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    String resolve(OfflinePlayer player, String text) {
        if (defaultNanos == 0 && overrides.isEmpty()) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return TOKEN.matcher(text).replaceAll(match -> {
            String token = match.group();
            String value = token.indexOf('_') < 0 ? token : resolveToken(player, token);
            return Matcher.quoteReplacement(value);
        });
    }

    private String resolveToken(OfflinePlayer player, String token) {
        long interval = overrides.getOrDefault(token, defaultNanos);
        if (interval == 0) {
            return PlaceholderAPI.setPlaceholders(player, token);
        }
        Map<String, CachedValue> values = playerValues.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        CachedValue cached = values.get(token);
        if (cached != null && System.nanoTime() - cached.expiresAt() < 0) {
            return cached.text();
        }
        String value = PlaceholderAPI.setPlaceholders(player, token);
        values.put(token, new CachedValue(value, System.nanoTime() + interval));
        return value;
    }

    void removePlayer(UUID player) {
        playerValues.remove(player);
    }
}
