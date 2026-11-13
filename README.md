# EvergreenScoreboard

A per-player Minecraft sidebar for Paper and Purpur 26.3.

## Install

Use Java 25 and install PlaceholderAPI on the server. Put
`EvergreenScoreboard-1.4.5.jar` in `plugins/` and restart the server.
When upgrading, replace the old JAR and keep the existing plugin data folder.
Placeholder values come from their respective plugins and PlaceholderAPI
expansions. If a placeholder is still visible, check its expansion with
`/papi parse me %placeholder_name%`. The plugin supplies `%world%` and uses
block coordinates if the Player expansion leaves the coordinate placeholders
unresolved.

PerchShopFinder highlights keep their fixed or rainbow outline colors while
the Evergreen sidebar is shown. The compatibility feature starts automatically
when PerchShopFinder is enabled.

## Commands

| Command | Action |
| --- | --- |
| `/sb` | Open Scoreboard Presets. |
| `/sb reload` | Reload `config.yml`, `gui.yml`, `colors.yml`, `presets.yml`, and `alt.yml`. Requires `evergreen.scoreboard.reload`, granted to operators by default. |

Use `/evergreenscoreboard:sb` if another plugin already owns `/sb`.

Click a preset to replace your enabled modules and their order, then choose a
color. The modules save immediately. Use **Edit scoreboard** on the bottom
row to add or remove individual modules.

Click the visibility toggle in the preset menu to hide or show your sidebar.
The visibility choice
is saved with your player data, and you can still edit the layout and color
while the sidebar is hidden.

To change your sidebar order, open `/sb` and click **Reorder modules**.
Left-click a module to move it up one position or right-click to move it down.
Each move applies and saves immediately. Multi-row modules move together.
Newly enabled content modules normally go at the bottom of the sidebar.

Click **Sidebar color** in `/sb` to pick a color. The choice applies
immediately and is saved with the player's data. The selected item glints.
Choose **Custom color** to enter a main color and an optional accent color.
Each field accepts six hex digits with an optional `#`. A blank accent uses
the main color for both `<color>` and `<color-accent>`. Previously saved
single custom colors use the same fallback.

Players without a saved selection start with the General preset (`general`
in `presets.yml`, or `everyday` in older configs) and Evergreen
(`Ever-Green` in `colors.yml`) if available.
An explicitly cleared selection shows the configured help rows. Each player's
selection is saved with their normal Minecraft player data. The sidebar fits
15 rows beneath the title. Three fixed rows leave 12 rows for modules.
Coordinates uses four rows and each of the other bundled modules uses one.

## Permissions and alt accounts

| Permission | Default | Effect |
| --- | --- | --- |
| `evergreen.scoreboard.use` | Everyone | Shows the standard sidebar and allows `/sb`. |
| `evergreen.scoreboard.alt` | Off | Shows the fixed sidebar from `alt.yml` and blocks scoreboard commands and menus. Takes precedence over `evergreen.scoreboard.use`. |

Players with neither permission have no Evergreen sidebar. Permission changes
take effect on the next sidebar refresh. Removing the alt permission restores
the player's saved standard preferences if they still have access.

The plugin creates `plugins/EvergreenScoreboard/alt.yml` on startup. Edit
`title`, `top-separator`, `bottom-separator`, and `lines` to change what
marked accounts see. Set `color` to a legacy code such as `&a` or a hex
code such as `&#48ab76` to replace `<color>`. Set `accent-color` to give
`<color-accent>` a separate color. It defaults to `color`. A line with just
`<top-separator>` or `<bottom-separator>` inserts the matching divider.
Run `/sb reload` from the console or a player with reload access to apply edits.
The listed commands are informational.

## Configure presets

Edit `plugins/EvergreenScoreboard/presets.yml` to change the names, icons, and
module lists. The file is created on upgrade. Entries appear in file order,
and each list uses the module IDs from `config.yml` in sidebar order.

```yaml
general:
  name: '&aGeneral Scoreboard'
  material: OAK_SIGN
  modules: [online, world, money, channel]
  lore:
    - ''
    - '&aClick here to select this scoreboard!'
    - ''
    - '&fDefault Preview:'
    - '{preview}'
```

The optional `lore` list controls the preset tooltip. Use legacy colors
such as `&a` or hex colors such as `&#48ab76`. Put `{preview}` on its own
line to insert the rendered modules, dividers, and outer separators. The
preview uses the player's values and current sidebar color when the menu
opens or redraws. Add `{title}` or `{options}` on their own lines if you
want to include those parts of the sidebar. Omitting `lore` uses the
example tooltip above. An existing custom tooltip shows the preview
wherever you add the `{preview}` line.

The bundled presets are General, Grinding, and Technical. The
usual 12-row limit applies. Unavailable modules and those that do not fit are
skipped. A preset with no applicable modules is hidden. An explicit
`modules: []` is available for a preset that clears the sidebar selection.

## Configure the chest menus

The plugin creates `plugins/EvergreenScoreboard/gui.yml` on startup.
The `presets`, `modules`, `reorder`, and `colors` sections each have their own
`title`, `auto-size`, `size`, entry slots, named `buttons`, and optional
`decorations`. Titles support legacy color codes.
Sizes are 9, 18, 27, 36, 45, or 54 slots. Slots start at 0 in the top-left
corner and run left to right, then down.

With `auto-size: true`, the menu uses the smallest chest that contains
its highest configured slot, including entry slots, buttons, and decorations.
The manual `size` setting is ignored in that menu. Existing files can add
`title` and `auto-size` inside each menu section as needed.

- `presets.preset-slots` needs one position for each available preset. Older
  `gui.yml` files use the bundled preset layout until you add a `presets` section.
- `modules.module-slots` determines the order and number of modules per page.
  Set `modules.buttons.back` to a free slot to add a Back to presets button.
- `reorder.module-slots` needs at least 12 positions for enabled modules.
- `colors.color-slots` needs at least as many positions as there are colors.
  The colors are placed in the order they appear in `colors.yml`.
- Slot lists accept integers and inclusive ranges such as `[1, 3, '9-13']`.
  Each slot can hold one decoration and one functional item. A visible
  functional item takes priority, so decorations can fill empty slots on
  the first or last page or behind absent module entries.

Under a menu's `decorations`, add named groups with `slots` and a
`material`. You can also set `name` and `lore` with legacy color codes.
The bundled file has an example group.

Run `/sb reload` after editing. An invalid layout leaves the previous
configuration active. Open `/sb` again if the layout changed during reload.

## Customize menu buttons

Replace a button's slot number in `gui.yml` with a mapping. Keep its
existing slot and add any of `material`, `name`, and `lore` to override
the defaults. Names and lore support legacy and hex colors. Use `lore: []`
for an item with no lore. Configured dye icons display as cushions of the
same color.

```yaml
presets:
  buttons:
    edit:
      slot: 22
      material: BOOK
      name: '&aEdit my sidebar'
      lore: ['&7Add or remove modules']
    visibility:
      slot: 24
      visible:
        material: LIME_CUSHION
        name: '&aHide scoreboard'
      hidden:
        material: GRAY_CUSHION
        name: '&7Show scoreboard'
```

Merge these settings into the existing menu section. Add `visibility: 24`
under `modules.buttons` to show the toggle in the module editor, or add it
under `reorder.buttons` or `colors.buttons` with a free slot in that menu.
Use a mapping as shown above for per-menu styles. The `visible` and
`hidden` mappings style the button for the current sidebar state. Set
shared styles directly under `visibility`.

All menus toggle the same saved player setting. Set `visibility: false`
under a menu's `buttons` to hide its toggle. If the presets layout omits
`visibility`, the plugin chooses a free slot automatically.

Templates can use these values in names and lore:

| Setting | Values |
| --- | --- |
| Visibility button | `{status}` |
| Sidebar color button | `{color}` |
| Module editor info | `{used}`, `{max}`, `{remaining}`, `{page}`, `{pages}` |
| Reorder info | `{hint}` |
| Back buttons | `{destination}` |
| Custom color | `{hex}` for the main color, `{accent-hex}` for the effective accent |
| `modules.entries.available`, `modules.entries.selected`, `modules.entries.no-space` | `{module}`, `{rows}` |
| `reorder.entry` | `{module}`, `{position}` |

Customize the row counter under `modules.buttons.info`. Replace the old
slot number with a mapping and keep the slot used by your layout.

```yaml
info:
  slot: 40
  material: PAPER
  name: '&6{used}/{max} module rows used'
  lore:
    - '&f{remaining} rows available'
    - '&fPage {page}/{pages}'
```

The module and reorder entry styles take `material`, `name`, and `lore`
without a slot. Preset names and materials are set in `presets.yml`.
Color option materials are set in `colors.yml`, where an optional `name`
overrides the label while preserving the saved ID. Both files accept
optional `lore` on each entry.

## Configure the color picker

`plugins/EvergreenScoreboard/colors.yml` starts with the 16 Minecraft colors
and Ever Green. Add, remove, or reorder entries, then run `/sb reload`.

```yaml
Dark-Blue:
  color: '&1'
  material: BLUE_WOOL
Ever-Green:
  color: '&#48ab76'
  accent-color: '&#7db597'
  material: SPRUCE_LEAVES
```

The keys identify saved colors and become the menu labels, with hyphens and
underscores shown as spaces. Keep the key when changing a color or its icon
to preserve selections. Both color fields accept the same code formats.
Omit `accent-color` to use `color` for both sidebar placeholders.
If a player's saved color is unavailable, White is
the fallback when defined, or the first entry. At least one color is required.

To customize the hex color button, edit the existing `colors` section of
`gui.yml`:

```yaml
colors:
  buttons:
    custom-color:
      slot: 49
      material: GLASS
      name: '&fCustom color'
```

Keep the other entries in that section. Existing `custom-color-material`
settings are supported. If the custom button is omitted,
the plugin chooses a free slot. A completely filled color picker needs one
more free slot to show the button.

## Configure sidebar text

Edit the `sidebar` section in `plugins/EvergreenScoreboard/config.yml` and
run `/sb reload`. If you are upgrading, add the section to your existing
config when you want to customize it.

```yaml
sidebar:
  title: '<color>• Evergreen •'
  top-separator: '&8---------------'
  bottom-separator: '&8---------------'
  options-line: '&7Options: &f/sb'
  empty-lines:
    - '&7Use <color>/sb &7to choose modules.'
    - '&7Your selections are saved automatically.'
```

Sidebar text accepts legacy colors, `<color>`, `<color-accent>`, and
PlaceholderAPI placeholders. Preset sidebar previews use the same colors.
Each missing field uses its original text. Use `''` for a blank row.

The separator patterns repeat or trim to fit the wider of the title and the
widest enabled text row after placeholders are resolved, then add the next
mark in the pattern. With no modules enabled, the help message takes the place
of the modules, or the options line if there is no visible help text. Sizing
uses vanilla font widths for common characters and estimates other glyphs.

`empty-lines` appears when the player has no modules enabled. Add up to 12
rows or set `empty-lines: []` to hide the help message. Omit the setting to
use the original two help rows.

## Configure modules

Edit `plugins/EvergreenScoreboard/config.yml`. Every entry under `modules`
has a stable lowercase ID, a menu name and a list of scoreboard rows.
The menu name accepts legacy colors such as `&a` and hex colors such as
`&#48ab76`. Add, remove, or reorder entries as needed. Keep the ID when changing a module's
name or text to preserve selections. For example:

```yaml
  playtime:
    name: '&#48ab76Playtime'
    lines:
      - '| <color>Playtime&f: %statistic_time_played%'
```

The `<color>` and `<color-accent>` tags apply the player's main and accent
colors from that point in the line. In the example, `&f` switches to white
before the value.

The bundled Divider uses one row and needs content above and below it.
Enable at least two content modules first. The editor inserts the Divider
before the last module. Reordering preserves the content at both edges.
If removing content strands a divider at either edge, the plugin removes
that divider from the selection. The same rule applies to presets and saved
layouts.

Add this under `modules` if you are keeping an older `config.yml`:

```yaml
  divider:
    name: 'Divider'
    lines: ['<top-separator>']
```

Use `<bottom-separator>` to copy the bottom separator instead. Each tag must
occupy the entire row. Divider rows inherit the rendered separator's width
and formatting and are excluded from the width calculation.

The add/remove menu follows the order in the file. If a configuration
change makes an existing selection too large, modules are kept in the player's order
as space allows. Removed modules and modules that no longer fit are dropped
from player selections on reload or the player's next join. Legacy colors
such as `&9` are supported in scoreboard rows.

Placeholders refresh every 20 ticks by default. Adjust
`refresh-interval-ticks` to change that interval. The plugin uses a private
Bukkit scoreboard for each player, so other sidebar plugins, scoreboard
teams and objectives can compete with its display.

## Replace placeholder outputs

Add `placeholderapi-refresh-intervals` to `config.yml` to cache PlaceholderAPI
results per player. The default applies to any token without an exact override.

```yaml
placeholderapi-refresh-intervals:
  default-refresh-interval: 1000
  "%griefprevention_remainingclaims%": 2000
```

Intervals are in milliseconds. The next render after a value expires resolves
it again. Use `0` to resolve a token on every render, or omit the section to
resolve all tokens on every render. Built-in values follow the sidebar's
render interval. Apply config changes with `/sb reload`.

Set `placeholder-output-replacements` in `config.yml` to rename specific
placeholder results on the sidebar and in preset previews.

```yaml
placeholder-output-replacements:
  '%world%':
    Nest: Overworld
    Nest_nether: Nether
    Nest_the_end: The End
```

Each key under a placeholder is an exact, case-sensitive output. Outputs
without a matching entry display as resolved. Replacement text can use
legacy colors. Add the section to an existing config and run `/sb reload`
after editing.

## Build

Open this directory as a Gradle project with JDK 25. Run
`./gradlew build` or `gradlew.bat build` on Windows. The built JAR goes to
`build/libs/EvergreenScoreboard-1.4.5.jar`.
