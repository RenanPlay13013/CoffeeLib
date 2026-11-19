package net.loyalnetwork.coffeelib.core.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StyledTextTest {
    @Test void recognizesLegacyColorsAndResets() {
        StyledText text = StyledText.parse("&aGreen &lBold&r normal");
        assertEquals("Green Bold normal", text.plain());
        assertEquals(3, text.runs().size());
        assertTrue(text.runs().get(1).style().bold());
        assertFalse(text.runs().get(2).style().bold());
        assertNull(text.runs().get(2).style().rgb());
    }
    @Test void recognizesHexAndKeepsFormatting() {
        StyledText text = StyledText.parse("&#123AbC&lHex");
        assertEquals("Hex", text.plain());
        assertEquals(0x123ABC, text.runs().get(0).style().rgb());
        assertTrue(text.runs().get(0).style().bold());
    }
    @Test void doesNotStripUnknownCodesOrEscapedAmpersands() {
        assertEquals("&unknown &a literal &q §?", StyledText.parse("&unknown \\&a literal &q §?").plain());
    }
    @Test void legacyDowngradesHexToNearestColor() {
        assertEquals("§cred", StyledText.parse("&#FF5555red").legacy());
    }
    @Test void formatResetsOnNewColor() {
        StyledText text = StyledText.parse("&lbold&cnow normal");
        assertTrue(text.runs().get(0).style().bold());
        assertFalse(text.runs().get(1).style().bold());
    }
    @Test void emptyInputAndTrailingFormattingAreFine() {
        assertEquals("", StyledText.parse("").plain());
        assertEquals("abc", StyledText.parse("abc&l").plain());
    }
    @Test void malformedRgbIsNotAnInstruction() {
        assertEquals("&#12345Xtext", StyledText.parse("&#12345Xtext").plain());
    }
}
