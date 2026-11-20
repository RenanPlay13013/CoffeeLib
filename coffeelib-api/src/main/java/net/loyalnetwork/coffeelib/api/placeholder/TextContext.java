package net.loyalnetwork.coffeelib.api.placeholder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a text is about, carried with it to every placeholder resolver.
 * <p>
 * A kit's icon, a crate's preview, a warp's message: the text is about
 * <em>something</em>, and placeholders such as {@code %ravenessentials_kit_remaining%}
 * need to know what. Whoever builds the text says so -
 * {@code formatter.parse(text, player, TextContext.of(kit))} - and a
 * {@code @Placeholder} method receives it through a
 * {@link net.loyalnetwork.coffeelib.api.placeholder.annotation.Context @Context}
 * parameter. Nothing is kept in a {@code ThreadLocal}: the context travels
 * with the call, so it holds on any thread and at any later time.
 * <p>
 * One value per class. Values are looked up by type, a subclass satisfying
 * a request for its superclass; a request two values could satisfy (an
 * interface both implement, {@code Object}) has no answer rather than a
 * random one. The context is shared by every namespace in the text, so one
 * mod can read another's value if it knows the class - intentionally.
 * <p>
 * References are kept as given, not copied: put immutable values here.
 */
public final class TextContext {

    /** A text about nothing in particular. */
    public static final TextContext EMPTY = new TextContext(List.of());

    private final List<Object> values;

    private TextContext(List<Object> values) {
        this.values = values;
    }

    /**
     * A context holding {@code values}.
     *
     * @throws IllegalArgumentException when a value is {@code null}, or two
     *                                  values are of the same class
     */
    public static TextContext of(Object... values) {
        Objects.requireNonNull(values, "values");
        List<Object> list = new ArrayList<>(values.length);
        for (Object value : values) {
            requireValue(value);
            for (Object existing : list) {
                if (existing.getClass() == value.getClass()) {
                    throw new IllegalArgumentException("TextContext already holds a " + value.getClass().getName()
                            + "; one value per class");
                }
            }
            list.add(value);
        }
        return list.isEmpty() ? EMPTY : new TextContext(Collections.unmodifiableList(list));
    }

    /** A copy with {@code value} added - or in place of the value of the same class. */
    public TextContext with(Object value) {
        requireValue(value);
        List<Object> list = new ArrayList<>(values.size() + 1);
        for (Object existing : values) {
            if (existing.getClass() != value.getClass()) {
                list.add(existing);
            }
        }
        list.add(value);
        return new TextContext(Collections.unmodifiableList(list));
    }

    /**
     * The value of class {@code type} or a subclass of it. Empty when there is
     * none, and also when more than one value would do (see {@link #isAmbiguous}).
     */
    public <T> Optional<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        T found = null;
        for (Object value : values) {
            if (type.isInstance(value)) {
                if (found != null) {
                    return Optional.empty();
                }
                found = type.cast(value);
            }
        }
        return Optional.ofNullable(found);
    }

    /** Whether more than one value is a {@code type}, so {@link #get} cannot pick one. */
    public boolean isAmbiguous(Class<?> type) {
        Objects.requireNonNull(type, "type");
        int matches = 0;
        for (Object value : values) {
            if (type.isInstance(value) && ++matches > 1) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public String toString() {
        return "TextContext" + values;
    }

    private static void requireValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("A TextContext value cannot be null");
        }
    }
}
