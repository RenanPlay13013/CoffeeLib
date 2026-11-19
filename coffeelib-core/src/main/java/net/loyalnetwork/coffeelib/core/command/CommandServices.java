package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.annotation.Command;
import net.loyalnetwork.coffeelib.api.di.ServiceContainer;
import net.loyalnetwork.coffeelib.api.di.exception.MissingProviderException;
import net.loyalnetwork.coffeelib.api.di.exception.AmbiguousProviderException;
import net.loyalnetwork.coffeelib.core.di.SharedServices;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Performs handler injection before the command is published on any platform. */
public final class CommandServices {
    private CommandServices() { }

    public static void wire(String ownerId, Object handler) {
        ServiceContainer container = SharedServices.existing(ownerId);
        if (container == null) return; // Legacy directly constructed managers retain their old behavior.
        try {
            container.wire(handler);
        } catch (MissingProviderException e) {
            throw new MissingProviderException(context(handler) + e.getMessage(), e.getType(), e.getRequestedName());
        } catch (AmbiguousProviderException e) {
            throw new AmbiguousProviderException(context(handler) + e.getMessage(), e.getType(), e.getCandidateNames());
        }
    }

    private static String context(Object handler) {
        String methods = Arrays.stream(handler.getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Command.class))
                .map(Method::getName).sorted().collect(Collectors.joining(", "));
        return "During @Command register " + handler.getClass().getName()
                + (methods.isEmpty() ? "" : "#" + methods) + ": ";
    }
}
