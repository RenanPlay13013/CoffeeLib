package net.loyalnetwork.coffeelib.paper.text;

import net.loyalnetwork.coffeelib.api.placeholder.PlaceholderManager;
import net.loyalnetwork.coffeelib.api.text.TextFormatter;
import net.loyalnetwork.coffeelib.core.text.StyledText;

import java.util.Objects;

/** Legacy Bukkit-compatible renderer; RGB is reduced to the closest legacy color. */
public final class PaperTextFormatter implements TextFormatter<String> {
    private final PlaceholderManager placeholders;

    public PaperTextFormatter(PlaceholderManager placeholders) {
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
    }

    @Override
    public String parse(String text) {
        return StyledText.parse(text).legacy();
    }

    @Override
    public String parse(String text, Object player) {
        return parse(placeholders.apply(player, text));
    }

    @Override
    public String parse(String text, Object player, net.loyalnetwork.coffeelib.api.placeholder.TextContext context) {
        return parse(placeholders.apply(player, text, context));
    }

    @Override
    public String strip(String text) {
        return StyledText.parse(text).plain();
    }
}
