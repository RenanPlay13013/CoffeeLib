package net.loyalnetwork.coffeelib.core.text;

import net.loyalnetwork.coffeelib.api.placeholder.TextContext;
import net.loyalnetwork.coffeelib.api.text.Messages;
import net.loyalnetwork.coffeelib.api.config.ConfigReloadService;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Application service: catalog lifetime and delivery, delegating template rules to value objects. */
public final class DefaultMessages<T> implements Messages<T> {
    private final T catalog;
    private final ConfigReloadService reloadService;
    private final MessageDelivery delivery;

    /** A delivery that has no use for a text context: it is dropped. */
    public DefaultMessages(T catalog, ConfigReloadService reloadService, BiConsumer<Object, String> delivery) {
        this(catalog, reloadService, withoutContext(delivery));
    }

    public DefaultMessages(T catalog, ConfigReloadService reloadService, MessageDelivery delivery) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.reloadService = Objects.requireNonNull(reloadService, "reloadService");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
    }

    private static MessageDelivery withoutContext(BiConsumer<Object, String> delivery) {
        Objects.requireNonNull(delivery, "delivery");
        return (recipient, text, context) -> delivery.accept(recipient, text);
    }

    @Override
    public String render(Function<T, String> template, Object... namesAndValues) {
        Objects.requireNonNull(template, "template");
        PlaceholderBindings bindings = PlaceholderBindings.of(namesAndValues);
        String text = Objects.requireNonNull(template.apply(catalog), "message template");
        return MessageTemplate.render(text, bindings.values());
    }

    @Override
    public boolean reload() { return reloadService.reloadAll() > 0; }

    @Override
    public void send(Object recipient, Function<T, String> template, Object... namesAndValues) {
        send(recipient, TextContext.EMPTY, template, namesAndValues);
    }

    @Override
    public void send(Object recipient, TextContext context, Function<T, String> template, Object... namesAndValues) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(context, "context");
        delivery.deliver(recipient, render(template, namesAndValues), context);
    }
}
