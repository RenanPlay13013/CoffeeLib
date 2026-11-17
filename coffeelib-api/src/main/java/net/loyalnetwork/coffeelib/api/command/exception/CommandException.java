package net.loyalnetwork.coffeelib.api.command.exception;

import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

/**
 * Something wrong with what the sender did, as opposed to a bug: the message
 * is shown to the sender as-is and nothing is logged.
 * <p>
 * Throw it from an executor for "you have no crate called X". CoffeeLib uses
 * it too, for a bad number, a missing argument or a missing permission. Any
 * other exception from an executor is treated as a bug: logged with its stack
 * trace, and the sender is only told something went wrong.
 */
public class CommandException extends CoffeeLibException {

    public CommandException(String message) {
        super(message);
    }
}
