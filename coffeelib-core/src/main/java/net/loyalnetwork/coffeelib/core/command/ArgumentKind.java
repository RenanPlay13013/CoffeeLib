package net.loyalnetwork.coffeelib.core.command;

/**
 * How an argument is read from the input. Platforms map these onto their own
 * parsers - Brigadier argument types on Forge-like platforms - so the client
 * can validate and highlight a number as a number while the player types.
 */
public enum ArgumentKind {

    /** One word, up to the next space. */
    WORD,

    /** Everything that is left, spaces included. Only ever the last argument. */
    GREEDY,

    INTEGER,
    LONG,
    DOUBLE,
    FLOAT,
    BOOLEAN,

    /** One word, matched case-insensitively against an enum's constants. */
    ENUM
}
