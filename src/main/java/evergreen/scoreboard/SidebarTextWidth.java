package evergreen.scoreboard;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

final class SidebarTextWidth {
    private SidebarTextWidth() {}

    static int width(Component component) {
        return width(component, false);
    }

    static Component fitSeparator(Component pattern, int targetWidth) {
        int patternWidth = width(pattern);
        if (patternWidth == 0) {
            return pattern;
        }

        TextComponent.Builder result = Component.text();
        int repeats = targetWidth / patternWidth;
        for (int i = 0; i < repeats; i++) {
            result.append(pattern);
        }
        result.append(prefixWithExtraMark(pattern, targetWidth % patternWidth, false));
        return result.build().compact();
    }

    private static int width(Component component, boolean inheritedBold) {
        boolean bold = isBold(component, inheritedBold);
        int width = 0;
        if (component instanceof TextComponent text) {
            String content = text.content();
            for (int offset = 0; offset < content.length();) {
                int codePoint = content.codePointAt(offset);
                width += advance(codePoint, bold);
                offset += Character.charCount(codePoint);
            }
        }
        for (Component child : component.children()) {
            width += width(child, bold);
        }
        return width;
    }

    private static Component prefixWithExtraMark(Component component, int remaining, boolean inheritedBold) {
        boolean bold = isBold(component, inheritedBold);
        if (component instanceof TextComponent text) {
            String content = text.content();
            for (int offset = 0; offset < content.length();) {
                int codePoint = content.codePointAt(offset);
                int advance = advance(codePoint, bold);
                offset += Character.charCount(codePoint);
                if (advance > remaining) {
                    return text.content(content.substring(0, offset)).children(List.of());
                }
                remaining -= advance;
            }
        }
        List<Component> children = new ArrayList<>();
        for (Component child : component.children()) {
            int childWidth = width(child, bold);
            if (childWidth > remaining) {
                children.add(prefixWithExtraMark(child, remaining, bold));
                break;
            }
            children.add(child);
            remaining -= childWidth;
        }
        return component.children(children);
    }

    private static boolean isBold(Component component, boolean inheritedBold) {
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        return state == TextDecoration.State.NOT_SET ? inheritedBold : state == TextDecoration.State.TRUE;
    }

    // Advances from the vanilla 26.3 bitmap font for ASCII and common separator glyphs.
    private static int advance(int codePoint, boolean bold) {
        int regular = switch (codePoint) {
            case '\u200c' -> 0;
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case '`', 'l', '\u2022' -> 3;
            case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~', '\u2013' -> 7;
            case '\u2591' -> 8;
            case '\u2014', '\u2500', '\u2550', '\u2580', '\u2588' -> 9;
            default -> 6;
        };
        return regular + (bold ? 1 : 0);
    }
}
