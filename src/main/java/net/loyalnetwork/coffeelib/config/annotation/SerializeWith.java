package net.loyalnetwork.coffeelib.config.annotation;

import net.loyalnetwork.coffeelib.config.serializer.ConfigSerializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SerializeWith {

    Class<? extends ConfigSerializer<?>> value();
}
