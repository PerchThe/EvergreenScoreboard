package evergreen.scoreboard;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

final class ShopHighlightTeams {
    static final String PLUGIN_NAME = "PerchShopFinder";
    private static final String TEAM_PREFIX = "shop_highlight_";

    private record Highlight(ChatColor color, Set<String> entries) {}

    private final Scoreboard main;
    private Map<String, Highlight> highlights = Map.of();

    ShopHighlightTeams(Scoreboard main) {
        this.main = main;
    }

    void refresh(Collection<PlayerSidebar> sidebars) {
        Map<String, Highlight> next = new HashMap<>();
        for (Team team : main.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX) && team.getSize() > 0) {
                next.put(team.getName(), new Highlight(team.getColor(), Set.copyOf(team.getEntries())));
            }
        }
        updateSidebars(sidebars, next);
    }

    void copyTo(Scoreboard scoreboard) {
        update(scoreboard, Map.of(), highlights);
    }

    void clear(Collection<PlayerSidebar> sidebars) {
        updateSidebars(sidebars, Map.of());
    }

    private void updateSidebars(Collection<PlayerSidebar> sidebars, Map<String, Highlight> next) {
        if (!highlights.equals(next)) {
            for (PlayerSidebar sidebar : sidebars) {
                update(sidebar.scoreboard(), highlights, next);
            }
            highlights = next;
        }
    }

    private static void update(Scoreboard scoreboard, Map<String, Highlight> previous, Map<String, Highlight> next) {
        for (String name : previous.keySet()) {
            if (!next.containsKey(name)) {
                Team team = scoreboard.getTeam(name);
                if (team != null) {
                    team.unregister();
                }
            }
        }
        for (var entry : next.entrySet()) {
            String name = entry.getKey();
            Highlight highlight = entry.getValue();
            if (highlight.equals(previous.get(name))) {
                continue;
            }
            Team team = scoreboard.getTeam(name);
            if (team == null) {
                team = scoreboard.registerNewTeam(name);
            }
            if (team.getColor() != highlight.color()) {
                team.setColor(highlight.color());
            }
            for (String member : team.getEntries()) {
                if (!highlight.entries().contains(member)) {
                    team.removeEntry(member);
                }
            }
            for (String member : highlight.entries()) {
                if (!team.hasEntry(member)) {
                    team.addEntry(member);
                }
            }
        }
    }
}
