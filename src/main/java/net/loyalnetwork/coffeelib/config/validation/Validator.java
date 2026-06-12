package net.loyalnetwork.coffeelib.config.validation;

public interface Validator<T> {

    void validate(T value, String path, String file) throws ValidationException;
}
