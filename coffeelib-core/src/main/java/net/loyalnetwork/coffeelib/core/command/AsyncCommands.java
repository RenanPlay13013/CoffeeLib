package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.exception.CommandException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The rest of an executor that returned a future: once it completes, its
 * failure reaches the sender the way a synchronous one would - a
 * {@link CommandException}'s message, or the generic message with the cause
 * logged - on the main thread, and only while the sender is still there.
 * <p>
 * Shared by every platform, which only supply their main-thread executor and
 * how a failure is shown (Brigadier's red {@code sendFailure}, or a plain
 * message).
 */
public final class AsyncCommands {

    private AsyncCommands() {
    }

    /**
     * @param mainThread  where the reply is sent from
     * @param sendFailure shows a failure message to {@code source}
     */
    public static void watch(CommandDefinition definition, CommandSource source, CompletionStage<?> pending,
                             Executor mainThread, Consumer<String> sendFailure) {
        pending.whenComplete((value, error) -> {
            if (error == null) {
                return;
            }
            mainThread.execute(() -> {
                Throwable cause = unwrap(error);
                if (!(cause instanceof CommandException)) {
                    // Logged even when the sender is gone: it is still a bug.
                    definition.logFailure(source, cause);
                }
                if (source.isAvailable()) {
                    sendFailure.accept(cause instanceof CommandException userError ? userError.getMessage()
                            : CommandDefinition.INTERNAL_ERROR);
                }
            });
        });
    }

    /** The exception thrown inside the future, out of the wrappers {@code CompletableFuture} adds. */
    static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
