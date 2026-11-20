package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.annotation.Command;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CommandPathsTest {
    @Command(value = "HOME Set", aliases = {"HSET", "home write"})
    private void home() { }

    @Test void normalizesPathsAndAliasesWithoutLosingCommands() throws Exception {
        Command value = getClass().getDeclaredMethod("home").getAnnotation(Command.class);
        assertEquals(List.of(List.of("home", "set"), List.of("hset"), List.of("home", "write")),
                CommandPaths.paths(value, "home"));
    }
}
