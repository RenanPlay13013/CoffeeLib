package net.loyalnetwork.coffeelib.paper.commands;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperLocationArgumentTest {

    @Test
    void relativeCoordinatesUseThePlayersWorldAndRetainOrientation() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        CommandSource source = mock(CommandSource.class);
        when(source.player()).thenReturn(player);
        when(player.getLocation()).thenReturn(new Location(world, 10, 65, -5, 90, 20));

        PaperArguments.LocationArgument argument = new PaperArguments.LocationArgument();
        assertEquals(3, argument.words());
        Location result = argument.parse(source, "~ ~2 4");
        assertEquals(world, result.getWorld());
        assertEquals(10.0, result.getX());
        assertEquals(67.0, result.getY());
        assertEquals(4.0, result.getZ());
        assertEquals(90.0f, result.getYaw());
        assertEquals(20.0f, result.getPitch());
    }

    @Test
    void consoleAndInvalidCoordinatesFailAsCommandErrors() {
        PaperArguments.LocationArgument argument = new PaperArguments.LocationArgument();
        CommandSource console = mock(CommandSource.class);
        assertThrows(CommandException.class, () -> argument.parse(console, "~ ~ ~"));

        Player player = mock(Player.class);
        World world = mock(World.class);
        CommandSource source = mock(CommandSource.class);
        when(source.player()).thenReturn(player);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        assertThrows(CommandException.class, () -> argument.parse(source, "NaN 0 0"));
        assertThrows(CommandException.class, () -> argument.parse(source, "1 2"));
        assertThrows(CommandException.class, () -> argument.parse(source, "~bad 0 0"));
    }
}
