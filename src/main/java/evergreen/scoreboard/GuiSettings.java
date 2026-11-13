package evergreen.scoreboard;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

record GuiSettings(Layout presetLayout, Layout modules, Layout reorder, Layout colors, ColorPalette palette,
                   CustomColorButton customColor, List<ScoreboardPreset> presets, EntryStyles entries,
                   boolean presetVisibilityEnabled) {
    enum Button {
        PREVIOUS_PAGE, NEXT_PAGE, REORDER, INFO, COLORS, BACK, EDIT, VISIBILITY;

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    record MenuButton(int slot, ButtonStyle style, ButtonStyle visibleStyle, ButtonStyle hiddenStyle) {
        MenuButton(int slot, ButtonStyle style) {
            this(slot, style, ButtonStyle.DEFAULT, ButtonStyle.DEFAULT);
        }

        ButtonStyle visibilityStyle(boolean visible) {
            return (visible ? visibleStyle : hiddenStyle).orElse(style);
        }
    }

    record CustomColorButton(int slot, Material material, ButtonStyle style) {}

    record EntryStyles(ButtonStyle available, ButtonStyle selected, ButtonStyle noSpace, ButtonStyle reorder) {}

    record Layout(int size, Component title, List<Integer> entrySlots, Map<Button, MenuButton> buttons,
                  Map<Integer, ItemStack> decorations) {
        int button(Button button) {
            MenuButton control = buttons.get(button);
            return control == null ? -1 : control.slot();
        }

        ButtonStyle style(Button button) {
            MenuButton control = buttons.get(button);
            return control == null ? ButtonStyle.DEFAULT : control.style();
        }

        Set<Integer> usedSlots(List<Integer> entries) {
            Set<Integer> used = new HashSet<>(entries);
            buttons.values().forEach(button -> used.add(button.slot()));
            return used;
        }
    }

    static GuiSettings load(File file, Reader bundledGui, ColorPalette palette, List<ScoreboardPreset> presets)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        YamlConfiguration presetConfig = config;
        boolean useBundledPresets = !config.contains("presets");
        if (useBundledPresets) {
            presetConfig = new YamlConfiguration();
            presetConfig.load(bundledGui);
        }
        Layout presetLayout = readLayout(presetConfig, "presets", "preset-slots",
                Button.REORDER, Button.EDIT, Button.COLORS);
        boolean presetVisibilityEnabled = !Boolean.FALSE.equals(presetConfig.get("presets.buttons.visibility"));
        if (presetVisibilityEnabled && presetLayout.button(Button.VISIBILITY) < 0) {
            presetLayout = addVisibilityButton(presetConfig, presetLayout);
        }
        if (useBundledPresets && presetLayout.entrySlots().size() < presets.size()) {
            presetLayout = expandPresetSlots(presetLayout, presets.size());
        }
        if (presetLayout.entrySlots().size() < presets.size()) {
            throw new InvalidConfigurationException("gui.yml presets.preset-slots needs at least "
                    + presets.size() + " slots for the available presets.");
        }
        Layout modules = readLayout(config, "modules", "module-slots",
                Button.PREVIOUS_PAGE, Button.NEXT_PAGE, Button.REORDER, Button.INFO, Button.COLORS, Button.BACK);
        Layout reorder = readLayout(config, "reorder", "module-slots", Button.BACK, Button.INFO);
        Layout colors = readLayout(config, "colors", "color-slots", Button.BACK);
        if (reorder.entrySlots().size() < ScoreboardSettings.MODULE_ROWS) {
            throw new InvalidConfigurationException("reorder.module-slots needs at least "
                    + ScoreboardSettings.MODULE_ROWS + " slots.");
        }
        if (colors.entrySlots().size() < palette.colors().size()) {
            throw new InvalidConfigurationException("gui.yml colors.color-slots needs at least "
                    + palette.colors().size() + " slots for the colors defined in colors.yml.");
        }
        CustomColorButton custom = readCustomColor(config, colors, palette.colors().size());
        if (custom.slot() >= colors.size()) {
            int size = (custom.slot() / 9 + 1) * 9;
            colors = new Layout(size, colors.title(), colors.entrySlots(), colors.buttons(), colors.decorations());
        }
        EntryStyles entries = new EntryStyles(readStyle(config, "modules.entries.available"),
                readStyle(config, "modules.entries.selected"), readStyle(config, "modules.entries.no-space"),
                readStyle(config, "reorder.entry"));
        return new GuiSettings(presetLayout, modules, reorder, colors, palette, custom, presets, entries,
                presetVisibilityEnabled);
    }

    private static Layout expandPresetSlots(Layout layout, int presetCount) {
        List<Integer> entries = new ArrayList<>(layout.entrySlots());
        Set<Integer> used = layout.usedSlots(entries);
        used.addAll(layout.decorations().keySet());
        for (int slot = 0; slot < layout.size() && entries.size() < presetCount; slot++) {
            if (used.add(slot)) {
                entries.add(slot);
            }
        }
        return new Layout(layout.size(), layout.title(), List.copyOf(entries), layout.buttons(), layout.decorations());
    }

    private static Layout addVisibilityButton(YamlConfiguration config, Layout layout) {
        Set<Integer> used = layout.usedSlots(layout.entrySlots());
        int limit = config.getBoolean("presets.auto-size") ? 54 : layout.size();
        int slot = layout.size() == 27 && !used.contains(24) ? 24 : findButtonSlot(layout, used, limit);
        if (slot < 0) {
            return layout;
        }
        Map<Button, MenuButton> buttons = new EnumMap<>(Button.class);
        buttons.putAll(layout.buttons());
        buttons.put(Button.VISIBILITY, new MenuButton(slot, ButtonStyle.DEFAULT));
        int size = Math.max(layout.size(), (slot / 9 + 1) * 9);
        return new Layout(size, layout.title(), layout.entrySlots(), Map.copyOf(buttons), layout.decorations());
    }

    private static CustomColorButton readCustomColor(YamlConfiguration config, Layout layout, int paletteSize)
            throws InvalidConfigurationException {
        String materialKey = "colors.custom-color-material";
        Set<Integer> used = layout.usedSlots(layout.entrySlots().subList(0, paletteSize));
        int limit = config.getBoolean("colors.auto-size") ? 54 : layout.size();
        String key = "colors.buttons.custom-color";
        MenuButton button = config.contains(key) ? readButton(config, key, limit, used)
                : new MenuButton(findButtonSlot(layout, used, limit), ButtonStyle.DEFAULT);
        Material material = button.style().material() != null ? button.style().material()
                : ButtonStyle.readMaterial(config.get(materialKey, "GLASS"), materialKey);
        return new CustomColorButton(button.slot(), material, button.style());
    }

    private static int findButtonSlot(Layout layout, Set<Integer> used, int limit) {
        int decorated = -1;
        for (int slot = layout.size() - 1; slot >= 0; slot--) {
            if (used.contains(slot)) {
                continue;
            }
            if (!layout.decorations().containsKey(slot)) {
                return slot;
            }
            if (decorated < 0) {
                decorated = slot;
            }
        }
        if (decorated >= 0) {
            return decorated;
        }
        return layout.size() < limit ? layout.size() : -1;
    }

    Layout layout(ModuleMenu.View view) {
        return switch (view) {
            case PRESETS -> presetLayout;
            case MODULES -> modules;
            case REORDER -> reorder;
            case COLORS -> colors;
        };
    }

    private static Layout readLayout(YamlConfiguration config, String menu, String entryKey, Button... controls)
            throws InvalidConfigurationException {
        String autoKey = menu + ".auto-size";
        if (config.contains(autoKey) && !config.isBoolean(autoKey)) {
            throw new InvalidConfigurationException(autoKey + " must be true or false.");
        }
        boolean autoSize = config.getBoolean(autoKey);
        String sizeKey = menu + ".size";
        int slotLimit = autoSize ? 54 : config.getInt(sizeKey);
        if (!autoSize && (!config.isInt(sizeKey) || slotLimit < 9 || slotLimit > 54 || slotLimit % 9 != 0)) {
            throw new InvalidConfigurationException(sizeKey + " must be 9, 18, 27, 36, 45, or 54.");
        }
        String titleKey = menu + ".title";
        Object titleValue = config.get(titleKey, "Scoreboard Modules");
        if (!(titleValue instanceof String title) || title.indexOf('\n') >= 0 || title.indexOf('\r') >= 0) {
            throw new InvalidConfigurationException(titleKey + " must be a single-line string.");
        }

        Set<Integer> functionalSlots = new HashSet<>();
        String entriesKey = menu + "." + entryKey;
        List<Integer> entries = readSlots(config.get(entriesKey), entriesKey, slotLimit, functionalSlots);
        Map<Button, MenuButton> buttons = new EnumMap<>(Button.class);
        for (Button button : controls) {
            String key = menu + ".buttons." + button.key();
            if (menu.equals("modules") && button == Button.BACK && !config.contains(key)) {
                continue;
            }
            buttons.put(button, readButton(config, key, slotLimit, functionalSlots));
        }
        String visibilityKey = menu + ".buttons.visibility";
        if (config.contains(visibilityKey) && !Boolean.FALSE.equals(config.get(visibilityKey))) {
            MenuButton button = readButton(config, visibilityKey, slotLimit, functionalSlots);
            buttons.put(Button.VISIBILITY, new MenuButton(button.slot(), button.style(),
                    readStyle(config, visibilityKey + ".visible"), readStyle(config, visibilityKey + ".hidden")));
        }

        String decorationsKey = menu + ".decorations";
        ConfigurationSection groups = config.getConfigurationSection(decorationsKey);
        if (groups == null && config.contains(decorationsKey)) {
            throw new InvalidConfigurationException(decorationsKey + " must be a mapping.");
        }
        Map<Integer, ItemStack> decorations = new HashMap<>();
        Set<Integer> decorationSlots = new HashSet<>();
        if (groups != null) {
            for (String group : groups.getKeys(false)) {
                String key = decorationsKey + "." + group;
                List<Integer> slots = readSlots(config.get(key + ".slots"), key + ".slots", slotLimit, decorationSlots);
                ItemStack item = readDecoration(config, key);
                for (int slot : slots) {
                    decorations.put(slot, item);
                }
            }
        }
        functionalSlots.addAll(decorationSlots);
        int size = autoSize ? (Collections.max(functionalSlots) / 9 + 1) * 9 : slotLimit;
        return new Layout(size, LegacyComponentSerializer.legacyAmpersand().deserialize(title),
                List.copyOf(entries), Map.copyOf(buttons), Map.copyOf(decorations));
    }

    private static MenuButton readButton(YamlConfiguration config, String key, int size, Set<Integer> used)
            throws InvalidConfigurationException {
        ConfigurationSection section = config.getConfigurationSection(key);
        String slotKey = section == null ? key : key + ".slot";
        if (!config.isInt(slotKey)) {
            throw new InvalidConfigurationException(slotKey + " must be a slot number.");
        }
        int slot = config.getInt(slotKey);
        reserve(slot, slotKey, size, used);
        return new MenuButton(slot, ButtonStyle.read(section, key));
    }

    private static ButtonStyle readStyle(YamlConfiguration config, String key) throws InvalidConfigurationException {
        ConfigurationSection section = config.getConfigurationSection(key);
        if (section == null && config.contains(key)) {
            throw new InvalidConfigurationException(key + " must be a mapping.");
        }
        return ButtonStyle.read(section, key);
    }

    private static List<Integer> readSlots(Object value, String key, int size, Set<Integer> used)
            throws InvalidConfigurationException {
        if (!(value instanceof List<?> slots) || slots.isEmpty()) {
            throw new InvalidConfigurationException(key + " must be a nonempty list of slots or ranges.");
        }
        List<Integer> result = new ArrayList<>();
        for (Object entry : slots) {
            int first;
            int last;
            if (entry instanceof Integer slot) {
                first = last = slot;
            } else if (entry instanceof String range && range.matches("[0-9]{1,2}-[0-9]{1,2}")) {
                String[] bounds = range.split("-", 2);
                first = Integer.parseInt(bounds[0]);
                last = Integer.parseInt(bounds[1]);
            } else {
                throw new InvalidConfigurationException(key + " contains an invalid slot or range: " + entry);
            }
            if (first > last || first < 0 || last >= size) {
                throw new InvalidConfigurationException(key + " must use slots from 0 to " + (size - 1) + ".");
            }
            for (int slot = first; slot <= last; slot++) {
                reserve(slot, key, size, used);
                result.add(slot);
            }
        }
        return result;
    }

    private static void reserve(int slot, String key, int size, Set<Integer> used) throws InvalidConfigurationException {
        if (slot < 0 || slot >= size) {
            throw new InvalidConfigurationException(key + " must use slots from 0 to " + (size - 1) + ".");
        }
        if (!used.add(slot)) {
            throw new InvalidConfigurationException(key + " overlaps another item at slot " + slot + ".");
        }
    }

    private static ItemStack readDecoration(YamlConfiguration config, String key) throws InvalidConfigurationException {
        Material material = ButtonStyle.readMaterial(config.get(key + ".material"), key + ".material");
        Object name = config.get(key + ".name", "");
        Object lore = config.get(key + ".lore", List.of());
        if (!(name instanceof String displayName) || !(lore instanceof List<?> lines)
                || lines.stream().anyMatch(line -> !(line instanceof String))) {
            throw new InvalidConfigurationException(key + " needs a string name and a list of string lore lines.");
        }

        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(serializer.deserialize(displayName).decoration(TextDecoration.ITALIC, false));
        meta.lore(lines.stream().map(line -> serializer.deserialize((String) line)
                .decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
