package net.loyalnetwork.coffeelib.core.command;

import net.loyalnetwork.coffeelib.api.command.CommandSource;
import net.loyalnetwork.coffeelib.api.command.SuggestContext;
import net.loyalnetwork.coffeelib.api.command.annotation.Suggest;
import net.loyalnetwork.coffeelib.api.exception.CoffeeLibException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Inspects completion providers at registration and leaves runtime invocation to the callback. */
final class CommandSuggestions {
    private CommandSuggestions() { }

    @SuppressWarnings("unchecked")
    static ParameterDefinition.Suggester from(Object handler, Parameter parameter, String context) {
        Suggest suggest = parameter.getAnnotation(Suggest.class);
        if (suggest == null) {
            return null;
        }
        if (suggest.provider().isEmpty()) {
            List<String> values = Arrays.asList(suggest.value());
            return new ParameterDefinition.Suggester() {
                @Override
                public Collection<String> suggest(SuggestContext suggestContext) {
                    return values;
                }

                @Override
                public boolean byToken() {
                    return false;
                }
            };
        }

        Method provider = findProvider(handler.getClass(), suggest.provider(), context);
        Type declared = provider.getGenericReturnType();
        if (declared instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length == 1 && arguments[0] instanceof Class<?> element && element != String.class) {
                throw new CoffeeLibException("@Suggest provider '" + suggest.provider() + "' for " + context
                        + " returns Collection<" + element.getTypeName() + "> instead of Collection<String>");
            }
        }
        provider.setAccessible(true);
        Class<?> takes = provider.getParameterCount() == 1 ? provider.getParameterTypes()[0] : null;
        // CommandSource first, so a provider taking Object keeps the old meaning.
        boolean takesSource = takes != null && takes.isAssignableFrom(CommandSource.class);
        boolean takesContext = takes != null && !takesSource;
        return new ParameterDefinition.Suggester() {
            @Override
            public Collection<String> suggest(SuggestContext suggestContext) {
                try {
                    Object result = takesContext ? provider.invoke(handler, suggestContext)
                            : takesSource ? provider.invoke(handler, suggestContext.source())
                            : provider.invoke(handler);
                    return (Collection<String>) result;
                } catch (IllegalAccessException | InvocationTargetException e) {
                    // A broken provider costs the sender suggestions, not the command.
                    return List.of();
                }
            }

            @Override
            public boolean byToken() {
                return takesContext;
            }
        };
    }

    private static Method findProvider(Class<?> type, String name, String context) {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name) || !Collection.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (method.getParameterCount() == 0) {
                return method;
            }
            if (method.getParameterCount() == 1) {
                Class<?> takes = method.getParameterTypes()[0];
                if (takes.isAssignableFrom(CommandSource.class) || takes.isAssignableFrom(SuggestContext.class)) {
                    return method;
                }
            }
        }
        throw new CoffeeLibException("@Suggest(provider = \"" + name + "\") on " + context + " names no method "
                + "returning a Collection<String> that takes nothing, a CommandSource or a SuggestContext");
    }

}
