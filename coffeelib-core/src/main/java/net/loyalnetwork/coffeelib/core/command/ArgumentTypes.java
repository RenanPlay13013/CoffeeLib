package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandArgument;

import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The parameter types one command manager reads beyond the built-in ones
 * (strings, numbers, booleans, enums): the platform's (players, dimensions...),
 * the core's ({@code Duration}, {@code UUID}), and the owner's own.
 * <p>
 * A type maps to a factory taking the parameter, so a type can read its
 * annotations - {@code Holder<T>} needs {@code @Registry} to know which
 * registry to look in. Matching is by the parameter's exact class.
 */
public final class ArgumentTypes {

    private final Map<Class<?>, Function<Parameter, CommandArgument<?>>> factories = new ConcurrentHashMap<>();
    private final Set<Class<?>> playerTypes = ConcurrentHashMap.newKeySet();

    /** Only the core's own types: {@code Duration} and {@code UUID}. */
    public static ArgumentTypes withDefaults() {
        ArgumentTypes types = new ArgumentTypes();
        CoreArguments.registerInto(types);
        return types;
    }

    public <T> void register(Class<T> type, CommandArgument<T> argument) {
        Objects.requireNonNull(argument, "argument");
        factory(type, parameter -> argument);
    }

    /** A type whose reader depends on the parameter, such as on an annotation of it. */
    public void factory(Class<?> type, Function<Parameter, CommandArgument<?>> factory) {
        factories.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(factory, "factory"));
    }

    /** Marks {@code type} as the platform's player type, the one {@code @OrSelf} takes. */
    public void markPlayer(Class<?> type) {
        playerTypes.add(type);
    }

    public boolean isPlayer(Class<?> type) {
        return playerTypes.contains(type);
    }

    /** The reader for {@code parameter}, or {@code null} when its type is not known here. */
    CommandArgument<?> find(Parameter parameter) {
        Function<Parameter, CommandArgument<?>> factory = factories.get(parameter.getType());
        return factory == null ? null : factory.apply(parameter);
    }

    /** The known types' simple names, for an error message. */
    String names() {
        Set<String> names = new TreeSet<>();
        for (Class<?> type : factories.keySet()) {
            names.add(type.getSimpleName());
        }
        return String.join(", ", names);
    }
}
