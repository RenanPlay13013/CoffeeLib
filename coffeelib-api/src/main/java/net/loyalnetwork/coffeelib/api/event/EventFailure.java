package net.loyalnetwork.coffeelib.api.event;

/**
 * One handler that threw while an event was being delivered.
 *
 * @param ownerId          the mod or plugin that registered the handler
 * @param handlerEventType the event type the handler was registered for, which
 *                         can be a supertype of the event that was posted
 * @param cause            what the handler threw, exactly as thrown - not
 *                         wrapped, whichever way the handler was registered
 */
public record EventFailure(String ownerId, Class<?> handlerEventType, Throwable cause) {
}
