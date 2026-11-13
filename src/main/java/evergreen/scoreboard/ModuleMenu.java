package evergreen.scoreboard;

import evergreen.scoreboard.GuiSettings.Button;
import evergreen.scoreboard.GuiSettings.Layout;
import evergreen.scoreboard.ScoreboardSettings.Module;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class ModuleMenu implements InventoryHolder {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Component SELECT_COLOR = LEGACY.deserialize("&8» &#7db597Click here &fto select.");
    private static final Component COLOR_SELECTED = LEGACY.deserialize("&8» &#7db597SELECTED");
    private static final Component MOVE_UP = LEGACY.deserialize("&#7db597Left click &fto move up.");
    private static final Component MOVE_DOWN = LEGACY.deserialize("&#7db597Right click &fto move down.");
    private static final Component ADD_MODULE = LEGACY.deserialize("&#7db597Click here &fto &aadd&f.");
    private static final Component REMOVE_MODULE = LEGACY.deserialize("&#7db597Click here &fto &cremove&f.");

    enum View {
        PRESETS, MODULES, REORDER, COLORS
    }

    private final GuiSettings settings;
    private final Inventory inventory;
    private int page;
    private View view;
    private View backView;

    ModuleMenu(GuiSettings settings) {
        this(settings, View.PRESETS, 0, View.PRESETS);
    }

    private ModuleMenu(GuiSettings settings, View view, int page, View backView) {
        this.settings = settings;
        this.view = view;
        this.page = page;
        this.backView = backView;
        Layout layout = layout();
        this.inventory = Bukkit.createInventory(this, layout.size(), layout.title());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void draw(ScoreboardSettings scoreboard, PlayerSidebar sidebar, Function<String, Component> renderLine) {
        inventory.clear();
        layout().decorations().forEach((slot, item) -> inventory.setItem(slot, item.clone()));
        switch (view) {
            case PRESETS -> drawPresets(scoreboard.sidebar(), sidebar.color(), renderLine);
            case MODULES -> drawModules(scoreboard.modules(), sidebar);
            case REORDER -> drawOrder(sidebar);
            case COLORS -> drawColors(sidebar.color());
        }
        drawVisibility(sidebar.visible());
    }

    private void drawPresets(ScoreboardSettings.SidebarText text, SidebarColor color,
                             Function<String, Component> renderLine) {
        Layout layout = layout();
        List<ScoreboardPreset> presets = settings.presets();
        Map<String, Component> renderedLines = new HashMap<>();
        Function<String, Component> cachedRender = line -> renderedLines.computeIfAbsent(line, renderLine);
        for (int index = 0; index < presets.size(); index++) {
            ScoreboardPreset preset = presets.get(index);
            inventory.setItem(layout.entrySlots().get(index),
                    item(preset.material(), preset.name(), preset.tooltip(text, color, cachedRender)));
        }
        drawButton(Button.EDIT, Material.WRITABLE_BOOK,
                Component.text("Edit scoreboard", NamedTextColor.GOLD),
                List.of(Component.text("Add or remove individual modules", NamedTextColor.WHITE)));
        drawCustomizationButtons(color);
    }

    private void drawVisibility(boolean visible) {
        GuiSettings.MenuButton button = layout().buttons().get(Button.VISIBILITY);
        if (button == null) {
            return;
        }
        Component status = Component.text(visible ? "Visible" : "Hidden");
        inventory.setItem(button.slot(), styledItem(button.visibilityStyle(visible),
                visible ? Material.LIME_CUSHION : Material.GRAY_CUSHION,
                Component.text(visible ? "Hide scoreboard" : "Show scoreboard",
                        visible ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                List.of(Component.text("Scoreboard: ", NamedTextColor.WHITE).append(status)), Map.of("status", status), null));
    }

    private void drawModules(List<Module> modules, PlayerSidebar sidebar) {
        Layout layout = layout();
        int perPage = layout.entrySlots().size();
        int lastPage = lastPage(modules.size(), perPage);
        page = Math.clamp(page, 0, lastPage);
        int remaining = ScoreboardSettings.MODULE_ROWS - sidebar.usedRows();
        int start = page * perPage;
        for (int index = 0; index < perPage && start + index < modules.size(); index++) {
            Module module = modules.get(start + index);
            boolean selected = sidebar.selection().containsKey(module.id());
            boolean fits = module.rows() <= remaining;
            boolean canPlace = module.insertionIndex(sidebar.selection().size()) >= 0;
            boolean canAdd = fits && canPlace;
            Material icon = selected ? Material.LIME_CUSHION : canAdd ? Material.GRAY_CUSHION : Material.RED_CUSHION;
            ButtonStyle style = selected ? settings.entries().selected()
                    : canAdd ? settings.entries().available() : settings.entries().noSpace();
            Component action = selected
                    ? REMOVE_MODULE
                    : canAdd ? ADD_MODULE
                    : Component.text(fits ? "A divider needs content on both sides." : "Free up more rows first",
                            NamedTextColor.RED);
            String cost = module.rows() + (module.rows() == 1 ? " row" : " rows");
            Component name = module.name().colorIfAbsent(NamedTextColor.WHITE);
            inventory.setItem(layout.entrySlots().get(index), styledItem(style, icon, name,
                    List.of(Component.text(cost, NamedTextColor.WHITE), action),
                    Map.of("module", module.name(), "rows", Component.text(module.rows())), null));
        }
        drawCustomizationButtons(sidebar.color());
        drawBackButton(View.PRESETS);
        drawButton(Button.INFO, Material.PAPER,
                Component.text(sidebar.usedRows() + "/" + ScoreboardSettings.MODULE_ROWS
                        + " module rows used", NamedTextColor.GOLD),
                List.of(Component.text(remaining + " rows available", NamedTextColor.WHITE),
                        Component.text("Page " + (page + 1) + "/" + (lastPage + 1), NamedTextColor.WHITE)),
                Map.of("used", Component.text(sidebar.usedRows()), "max", Component.text(ScoreboardSettings.MODULE_ROWS),
                        "remaining", Component.text(remaining), "page", Component.text(page + 1),
                        "pages", Component.text(lastPage + 1)));
        if (page > 0) {
            drawButton(Button.PREVIOUS_PAGE, Material.ARROW, Component.text("Previous page"), List.of());
        }
        if (page < lastPage) {
            drawButton(Button.NEXT_PAGE, Material.ARROW, Component.text("Next page"), List.of());
        }
    }

    private void drawCustomizationButtons(SidebarColor color) {
        drawButton(Button.REORDER, Material.COMPARATOR, Component.text("Reorder modules", NamedTextColor.GOLD),
                List.of(Component.text("Change the order of your enabled modules", NamedTextColor.WHITE)));
        drawButton(Button.COLORS, color.material(), Component.text("Sidebar color", NamedTextColor.GOLD),
                List.of(color.label(), Component.text("Click to choose a color", NamedTextColor.WHITE)),
                Map.of("color", color.label()));
    }

    private void drawOrder(PlayerSidebar sidebar) {
        Layout layout = layout();
        List<Module> ordered = List.copyOf(sidebar.selection().values());
        int size = ordered.size();
        for (int index = 0; index < size; index++) {
            Module module = ordered.get(index);
            List<Component> moves = new ArrayList<>(2);
            if (ScoreboardSettings.canSwap(ordered, index, index - 1)) {
                moves.add(MOVE_UP);
            }
            if (ScoreboardSettings.canSwap(ordered, index, index + 1)) {
                moves.add(MOVE_DOWN);
            }
            inventory.setItem(layout.entrySlots().get(index), styledItem(settings.entries().reorder(), Material.LIME_CUSHION,
                    Component.text((index + 1) + ". ", NamedTextColor.WHITE).append(module.name()), moves,
                    Map.of("module", module.name(), "position", Component.text(index + 1)), null));
        }
        drawBackButton(backView);
        String hint = size < 2 ? "Enable at least two modules to change their order"
                : "Modules are shown in sidebar order";
        drawButton(Button.INFO, Material.BOOK, Component.text("Sidebar order", NamedTextColor.GOLD),
                List.of(Component.text(hint, NamedTextColor.WHITE)), Map.of("hint", Component.text(hint)));
    }

    private void drawColors(SidebarColor selected) {
        List<Integer> slots = layout().entrySlots();
        List<SidebarColor> colors = settings.palette().colors();
        for (int index = 0; index < colors.size(); index++) {
            SidebarColor color = colors.get(index);
            boolean active = color.equals(selected);
            Component action = active ? COLOR_SELECTED : SELECT_COLOR;
            inventory.setItem(slots.get(index), item(color.material(), color.label(),
                    color.lore() == null ? List.of(action) : color.lore(), active));
        }
        GuiSettings.CustomColorButton custom = settings.customColor();
        if (custom.slot() >= 0) {
            boolean active = selected.isCustom();
            String mainHex = active ? selected.customMainHex() : "";
            String accentHex = active ? selected.customAccentHex() : "";
            List<Component> lore = active
                    ? List.of(Component.text("Main " + mainHex, selected.textColor()),
                            Component.text("Accent " + accentHex, selected.accentTextColor()),
                            Component.text("Click to edit", NamedTextColor.WHITE))
                    : List.of(Component.text("Click to enter custom colors", NamedTextColor.WHITE));
            inventory.setItem(custom.slot(), styledItem(custom.style(), custom.material(),
                    Component.text("Custom color", active ? selected.textColor() : NamedTextColor.WHITE), lore,
                    Map.of("hex", Component.text(mainHex), "accent-hex", Component.text(accentHex)), active));
        }
        drawBackButton(backView);
    }

    private void drawBackButton(View destination) {
        String target = destination == View.MODULES ? "modules" : "presets";
        drawButton(Button.BACK, Material.ARROW, Component.text("Back to " + target), List.of(),
                Map.of("destination", Component.text(target)));
    }

    View view() {
        return view;
    }

    boolean isVisibilitySlot(int slot) {
        return slot >= 0 && slot == layout().button(Button.VISIBILITY);
    }

    ScoreboardPreset presetAt(int slot) {
        int index = entryIndex(slot);
        List<ScoreboardPreset> presets = settings.presets();
        return index < 0 || index >= presets.size() ? null : presets.get(index);
    }

    SidebarColor colorAt(int slot) {
        int index = entryIndex(slot);
        List<SidebarColor> colors = settings.palette().colors();
        return index < 0 || index >= colors.size() ? null : colors.get(index);
    }

    boolean isCustomColorSlot(int slot) {
        return view == View.COLORS && slot >= 0 && slot == settings.customColor().slot();
    }

    Module moduleAt(List<Module> modules, int slot) {
        int index = entryIndex(slot);
        if (index < 0) {
            return null;
        }
        index += page * layout().entrySlots().size();
        return index < modules.size() ? modules.get(index) : null;
    }

    int entryIndex(int slot) {
        return layout().entrySlots().indexOf(slot);
    }

    View destination(int slot) {
        Layout layout = layout();
        if (view == View.PRESETS || view == View.MODULES) {
            if (slot == layout.button(Button.REORDER)) {
                return View.REORDER;
            }
            if (slot == layout.button(Button.COLORS)) {
                return View.COLORS;
            }
            if (view == View.PRESETS) {
                return slot == layout.button(Button.EDIT) ? View.MODULES : null;
            }
            return slot == layout.button(Button.BACK) ? View.PRESETS : null;
        }
        return slot == layout.button(Button.BACK) ? backView : null;
    }

    boolean changePage(int slot, int moduleCount) {
        if (view != View.MODULES) {
            return false;
        }
        Layout layout = layout();
        if (slot == layout.button(Button.PREVIOUS_PAGE) && page > 0) {
            page--;
            return true;
        }
        if (slot == layout.button(Button.NEXT_PAGE) && page < lastPage(moduleCount, layout.entrySlots().size())) {
            page++;
            return true;
        }
        return false;
    }

    private static int lastPage(int moduleCount, int perPage) {
        return Math.max(0, (moduleCount - 1) / perPage);
    }

    boolean switchInPlace(View destination) {
        Layout target = settings.layout(destination);
        if (inventory.getSize() != target.size() || !layout().title().equals(target.title())) {
            return false;
        }
        backView = backViewFor(destination);
        view = destination;
        return true;
    }

    ModuleMenu newView(View destination) {
        return new ModuleMenu(settings, destination, page, backViewFor(destination));
    }

    private View backViewFor(View destination) {
        return destination == View.REORDER || destination == View.COLORS ? view : View.PRESETS;
    }

    boolean uses(GuiSettings current) {
        return settings.equals(current);
    }

    private Layout layout() {
        return settings.layout(view);
    }

    private void drawButton(Button button, Material material, Component name, List<Component> lore) {
        drawButton(button, material, name, lore, Map.of());
    }

    private void drawButton(Button button, Material material, Component name, List<Component> lore,
                            Map<String, Component> values) {
        Layout layout = layout();
        int slot = layout.button(button);
        if (slot >= 0) {
            inventory.setItem(slot, styledItem(layout.style(button), material, name, lore, values, null));
        }
    }

    private static ItemStack styledItem(ButtonStyle style, Material material, Component name, List<Component> lore,
                                        Map<String, Component> values, Boolean glint) {
        ButtonStyle display = style.withValues(values).orElse(new ButtonStyle(material, name, lore));
        return item(display.material(), display.name(), display.lore(), glint);
    }

    private static ItemStack item(Material icon, Component name, List<Component> lore) {
        return item(icon, name, lore, null);
    }

    private static ItemStack item(Material icon, Component name, List<Component> lore, Boolean glint) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.setEnchantmentGlintOverride(glint);
        item.setItemMeta(meta);
        return item;
    }
}
