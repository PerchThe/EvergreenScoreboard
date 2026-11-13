package evergreen.scoreboard;

import static evergreen.scoreboard.ScoreboardSettings.BOTTOM_DIVIDER;
import static evergreen.scoreboard.ScoreboardSettings.TOP_DIVIDER;

import evergreen.scoreboard.ScoreboardSettings.Module;
import evergreen.scoreboard.ScoreboardSettings.SidebarText;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

final class PlayerSidebar {
    static final int SIDEBAR_ROWS = 15;

    record Rendered(Component title, List<Component> rows) {}

    private Scoreboard previous;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final boolean alt;
    private final Component[] displayed = new Component[SIDEBAR_ROWS];
    private Component displayedTitle;
    private Map<String, Module> selection;
    private SidebarColor color;
    private boolean visible;
    private int usedRows;

    PlayerSidebar(Scoreboard scoreboard, Map<String, Module> selection, SidebarColor color, boolean visible, boolean alt) {
        this.scoreboard = scoreboard;
        this.alt = alt;
        this.color = color;
        this.visible = visible;
        this.objective = scoreboard.registerNewObjective("evergreen_sb", Criteria.DUMMY, Component.empty());
        objective.numberFormat(NumberFormat.blank());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        select(selection);
    }

    void select(Map<String, Module> selection) {
        this.selection = selection;
        this.usedRows = selection.values().stream().mapToInt(Module::rows).sum();
    }

    Map<String, Module> selection() {
        return selection;
    }

    int usedRows() {
        return usedRows;
    }

    SidebarColor color() {
        return color;
    }

    void setColor(SidebarColor color) {
        this.color = color;
    }

    boolean visible() {
        return visible;
    }

    boolean alt() {
        return alt;
    }

    void setVisible(boolean visible) {
        this.visible = visible;
    }

    boolean isShowing(Player player) {
        return player.getScoreboard().equals(scoreboard);
    }

    Scoreboard scoreboard() {
        return scoreboard;
    }

    void show(Player player) {
        if (!isShowing(player)) {
            previous = player.getScoreboard();
            player.setScoreboard(scoreboard);
        }
    }

    void restore(Player player) {
        if (isShowing(player)) {
            player.setScoreboard(previous);
        }
    }

    void refresh(SidebarText text, Function<String, Component> render) {
        Rendered sidebar = render(text, selection.values(), color, render);
        if (!sidebar.title().equals(displayedTitle)) {
            objective.displayName(sidebar.title());
            displayedTitle = sidebar.title();
        }

        List<Component> rows = sidebar.rows();
        for (int i = 0; i < rows.size(); i++) {
            Component row = rows.get(i);
            if (!row.equals(displayed[i])) {
                Score score = objective.getScore("evergreen_row_" + i);
                score.customName(row);
                if (displayed[i] == null) {
                    score.setScore(SIDEBAR_ROWS - i);
                }
                displayed[i] = row;
            }
        }
        for (int i = rows.size(); i < displayed.length && displayed[i] != null; i++) {
            objective.getScore("evergreen_row_" + i).resetScore();
            displayed[i] = null;
        }
    }

    static Rendered render(SidebarText text, Collection<Module> selection, SidebarColor color,
                           Function<String, Component> render) {
        Function<String, Component> renderColored = line ->
                render.apply(line.replace("<color>", color.legacy()).replace("<color-accent>", color.accentLegacy()));
        Component title = renderColored.apply(text.title());
        List<Component> rows = new ArrayList<>(SIDEBAR_ROWS);
        List<Integer> topDividers = new ArrayList<>();
        List<Integer> bottomDividers = new ArrayList<>();
        rows.add(Component.empty());
        if (selection.isEmpty()) {
            for (String line : text.emptyLines()) {
                rows.add(renderColored.apply(line));
            }
        } else {
            for (Module module : selection) {
                for (String line : module.lines()) {
                    switch (line) {
                        case TOP_DIVIDER -> {
                            topDividers.add(rows.size());
                            rows.add(Component.empty());
                        }
                        case BOTTOM_DIVIDER -> {
                            bottomDividers.add(rows.size());
                            rows.add(Component.empty());
                        }
                        default -> rows.add(renderColored.apply(line));
                    }
                }
            }
        }
        int contentWidth = 0;
        for (int i = 1; i < rows.size(); i++) {
            contentWidth = Math.max(contentWidth, SidebarTextWidth.width(rows.get(i)));
        }
        Component options = text.optionsLine() == null ? null : renderColored.apply(text.optionsLine());
        if (options != null && contentWidth == 0 && selection.isEmpty()) {
            contentWidth = SidebarTextWidth.width(options);
        }
        int separatorWidth = Math.max(contentWidth, SidebarTextWidth.width(title));
        Component topSeparator = SidebarTextWidth.fitSeparator(renderColored.apply(text.topSeparator()), separatorWidth);
        Component bottomSeparator = SidebarTextWidth.fitSeparator(renderColored.apply(text.bottomSeparator()), separatorWidth);
        rows.set(0, topSeparator);
        topDividers.forEach(index -> rows.set(index, topSeparator));
        bottomDividers.forEach(index -> rows.set(index, bottomSeparator));
        rows.add(bottomSeparator);
        if (options != null) {
            rows.add(options);
        }
        return new Rendered(title, rows);
    }
}
