package net.loyalnetwork.coffeelib.core.text;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageTemplateTest {
    @Test void cannotInjectOtherPlaceholdersThroughReplacementValues() {
        PlaceholderBindings bindings = PlaceholderBindings.of("player", "{coins}", "coins", "999");
        assertEquals("Hello {coins} 999", MessageTemplate.render("Hello {player} {coins}", bindings.values()));
    }

    @Test void invalidPlaceholderNamesAreRejectedAtTheBoundary() {
        assertThrows(IllegalArgumentException.class, () -> PlaceholderBindings.of("a b", 1));
        assertThrows(IllegalArgumentException.class, () -> PlaceholderBindings.of("a", "one", "a", "two"));
        assertThrows(IllegalArgumentException.class, () -> PlaceholderBindings.of("orphan"));
    }

    @Test void escapedOpeningBraceDoesNotInterpolate() {
        PlaceholderBindings bindings = PlaceholderBindings.of("name", "Ana");
        assertEquals("{name} Ana", MessageTemplate.render("\\{name} {name}", bindings.values()));
    }
}
