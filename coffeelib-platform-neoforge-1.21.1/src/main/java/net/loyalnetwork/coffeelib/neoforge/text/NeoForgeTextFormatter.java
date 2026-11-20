package net.loyalnetwork.coffeelib.neoforge.text;

import net.loyalnetwork.coffeelib.api.text.TextFormatter;
import net.loyalnetwork.coffeelib.core.placeholder.SharedPlaceholders;
import net.loyalnetwork.coffeelib.core.text.StyledText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Native-component renderer; does not load any client-only classes. */
public final class NeoForgeTextFormatter implements TextFormatter<MutableComponent> {
    @Override
    public MutableComponent parse(String text) {
        MutableComponent result = Component.empty();
        for (StyledText.Run run : StyledText.parse(text).runs()) {
            StyledText.Style source = run.style();
            Style style = Style.EMPTY;
            if (source.rgb() != null) style = style.withColor(TextColor.fromRgb(source.rgb()));
            if (source.bold()) style = style.withBold(true);
            if (source.italic()) style = style.withItalic(true);
            if (source.underline()) style = style.withUnderlined(true);
            if (source.strikethrough()) style = style.withStrikethrough(true);
            if (source.obfuscated()) style = style.withObfuscated(true);
            result.append(Component.literal(run.text()).setStyle(style));
        }
        return result;
    }

    @Override
    public MutableComponent parse(String text, Object player) {
        return parse(SharedPlaceholders.apply(player, text));
    }

    @Override
    public String strip(String text) {
        return StyledText.parse(text).plain();
    }
}
