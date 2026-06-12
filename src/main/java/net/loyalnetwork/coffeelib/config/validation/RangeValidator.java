package net.loyalnetwork.coffeelib.config.validation;

public final class RangeValidator implements Validator<Number> {

    private final double min;
    private final double max;

    public RangeValidator(double min, double max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public void validate(Number value, String path, String file) throws ValidationException {
        double doubleValue = value.doubleValue();
        if (doubleValue < min || doubleValue > max) {
            throw new ValidationException(
                    "Value " + doubleValue + " for '" + path + "' in " + file
                            + " is out of range [" + min + ", " + max + "]"
            );
        }
    }
}
