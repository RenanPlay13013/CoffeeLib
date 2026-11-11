package net.loyalnetwork.coffeelib.api.config.annotation;

import net.loyalnetwork.coffeelib.api.config.ConfigFormat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a config model. {@link #value()} is the file name (without
 * extension) — the backend for {@link #format()} appends its own extension
 * (.yml, .json, .conf, or whatever the platform uses natively).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigFile {

    String value();

    /**
     * On-disk format for this config. Defaults to {@link ConfigFormat#NATIVE},
     * i.e. the platform's own config system — on Forge and NeoForge that
     * means a registered {@code ModConfig} whose loading the loader owns.
     * <p>
     * Any other value makes CoffeeLib own the file end to end: it is read and
     * validated synchronously inside {@link
     * net.loyalnetwork.coffeelib.api.config.ConfigManager#load}, independently
     * of the mod-loading lifecycle, and it can be reloaded on demand or
     * hot-reloaded through {@link
     * net.loyalnetwork.coffeelib.api.config.ConfigReloadService}.
     */
    ConfigFormat format() default ConfigFormat.NATIVE;
}
