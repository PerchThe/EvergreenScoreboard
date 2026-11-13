package evergreen.scoreboard;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

final class CustomColorDialog {
    private static final String HEX_INPUT = "hex";
    private static final String ACCENT_INPUT = "accent";
    private static final ClickCallback.Options CALLBACK = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(10)).build();

    private CustomColorDialog() {}

    static void show(Player player, String main, String accent, boolean invalid, BiConsumer<String, String> submit) {
        Component hint = invalid
                ? Component.text("Use six hex digits for each color you enter, such as #48ab76.", NamedTextColor.RED)
                : Component.text("Enter six hex digits, with or without #. Leave the accent blank to use the main color.");
        var mainInput = DialogInput.text(HEX_INPUT, Component.text("Main color"))
                .initial(main).maxLength(7).build();
        var accentInput = DialogInput.text(ACCENT_INPUT, Component.text("Accent color (optional)"))
                .initial(accent).maxLength(7).build();
        DialogBase base = DialogBase.builder(Component.text("Custom scoreboard color"))
                .afterAction(DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(hint))).inputs(List.of(mainInput, accentInput)).build();
        ActionButton apply = ActionButton.create(Component.text("Apply"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player actor && actor.getUniqueId().equals(player.getUniqueId())) {
                        submit.accept(response.getText(HEX_INPUT), response.getText(ACCENT_INPUT));
                    }
                }, CALLBACK));
        ActionButton cancel = ActionButton.builder(Component.text("Cancel")).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base)
                .type(DialogType.confirmation(apply, cancel))));
    }
}
