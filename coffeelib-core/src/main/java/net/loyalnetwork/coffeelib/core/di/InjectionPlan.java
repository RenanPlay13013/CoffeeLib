package net.loyalnetwork.coffeelib.core.di;

import net.loyalnetwork.coffeelib.api.di.annotation.Receive;
import net.loyalnetwork.coffeelib.api.di.exception.MissingProviderException;
import net.loyalnetwork.coffeelib.api.di.exception.ServiceException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** A staged wiring plan: missing providers cannot leave a partly initialized receiver. */
final class InjectionPlan {
    private final Object receiver;
    private final List<Binding> bindings = new ArrayList<>();

    private InjectionPlan(Object receiver) { this.receiver = receiver; }

    static InjectionPlan prepare(Object receiver, ProviderCatalog providers) {
        java.util.Objects.requireNonNull(receiver, "receiverHost");
        InjectionPlan plan = new InjectionPlan(receiver);
        for (Class<?> type = receiver.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Receive annotation = field.getAnnotation(Receive.class);
                if (annotation == null) continue;
                plan.bindings.add(resolveBinding(receiver, providers, field, annotation));
            }
        }
        return plan;
    }

    private static Binding resolveBinding(Object receiver, ProviderCatalog providers, Field field, Receive receive) {
        if (Modifier.isFinal(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
            throw new ServiceException("@Receive field '" + field.getName() + "' on "
                    + receiver.getClass().getName() + " cannot be static or final");
        }
        field.setAccessible(true);
        ProviderEntry entry;
        try {
            entry = providers.resolveEntry(field.getType(), receive.value());
        } catch (MissingProviderException e) {
            throw new MissingProviderException("@Receive field '" + field.getName() + "' on "
                    + receiver.getClass().getName() + ": " + e.getMessage(),
                    e.getType(), e.getRequestedName());
        }
        return new Binding(field, entry.resolve());
    }

    void apply() {
        for (Binding binding : bindings) binding.assign(receiver);
    }

    /** One already-resolved field assignment. A provider is never invoked during apply. */
    private record Binding(Field field, Object value) {
        void assign(Object receiver) {
            try {
                field.set(receiver, value);
            } catch (IllegalAccessException | IllegalArgumentException e) {
                throw new ServiceException("Could not set @Receive field '" + field.getName() + "' on "
                        + receiver.getClass().getName(), e);
            }
        }
    }
}
