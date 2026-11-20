package net.loyalnetwork.coffeelib.core.player;

import net.loyalnetwork.coffeelib.api.event.EventSubscription;
import net.loyalnetwork.coffeelib.api.player.PlayerDirectory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedPlayersTest {

    @Test
    void visibleToCombinesNativeVisibilityAndAllOwnerRules() {
        SharedPlayers.clearAll();
        Object viewer = new Object();
        Object visible = new Object();
        Object vanished = new Object();
        Object nativeHidden = new Object();
        PlayerDirectory directory = SharedPlayers.directory();
        SharedPlayers.configureOnlinePlayers(() -> List.of(viewer, visible, vanished, nativeHidden),
                (who, target) -> target != nativeHidden);
        try {
            directory.visibility("vanish-plugin", (who, target) -> target != vanished);
            assertEquals(List.of(viewer, visible), directory.visibleTo(viewer));
            assertEquals(List.of(viewer, visible, vanished, nativeHidden), directory.visibleTo(null));
            assertTrue(directory.canSee(vanished, vanished), "self is always visible");
            assertThrows(UnsupportedOperationException.class,
                    () -> directory.visibleTo(viewer).add(new Object()));
        } finally {
            SharedPlayers.clearAll();
        }
    }

    @Test
    void releaseOwnerRemovesOnlyItsRulesWithoutBreakingSubscriptions() {
        SharedPlayers.clearAll();
        PlayerDirectory directory = SharedPlayers.directory();
        Object viewer = new Object();
        Object target = new Object();
        try {
            EventSubscription first = directory.visibility("alpha", (who, player) -> false);
            EventSubscription second = directory.visibility("beta", (who, player) -> true);
            EventSubscription legacy = directory.visibility((who, player) -> true);
            assertFalse(directory.canSee(viewer, target));
            SharedPlayers.releaseOwner("beta");
            assertTrue(first.isActive());
            assertFalse(second.isActive());
            SharedPlayers.releaseOwner("alpha");
            assertTrue(directory.canSee(viewer, target));
            assertTrue(legacy.isActive());
            first.unsubscribe();
            first.unsubscribe();
            assertFalse(first.isActive());
        } finally {
            SharedPlayers.clearAll();
        }
    }

    @Test
    void brokenVanishRuleFailsClosedAndNeverLeaksPlayerNames() {
        SharedPlayers.clearAll();
        Object viewer = new Object();
        Object hidden = new Object();
        Object visible = new Object();
        SharedPlayers.configureOnlinePlayers(() -> List.of(viewer, hidden, visible), (who, target) -> true);
        PlayerDirectory directory = SharedPlayers.directory();
        try {
            EventSubscription rule = directory.visibility("vanish-plugin", (who, target) -> {
                if (target == hidden) throw new IllegalStateException("visibility cache unavailable");
                return true;
            });
            assertFalse(directory.canSee(viewer, hidden));
            assertEquals(List.of(viewer, visible), directory.visibleTo(viewer));
            assertTrue(directory.canSee(null, hidden), "console retains visibility");
            assertTrue(directory.canSee(hidden, hidden), "self retains visibility");
            rule.unsubscribe();
            assertTrue(directory.canSee(viewer, hidden), "unsubscribing must restore default visibility");
        } finally {
            SharedPlayers.clearAll();
        }
    }

    @Test
    void brokenNativeVisibilityFailsClosedAndRecoversOnReconfiguration() {
        SharedPlayers.clearAll();
        Object viewer = new Object();
        Object hidden = new Object();
        SharedPlayers.configureOnlinePlayers(() -> List.of(hidden), (who, target) -> {
            throw new IllegalStateException("Paper visibility service failed");
        });
        try {
            PlayerDirectory directory = SharedPlayers.directory();
            assertFalse(directory.canSee(viewer, hidden));
            assertEquals(List.of(), directory.visibleTo(viewer));
            assertTrue(directory.canSee(null, hidden));
            SharedPlayers.configureOnlinePlayers(() -> List.of(hidden), (who, target) -> true);
            assertTrue(directory.canSee(viewer, hidden));
        } finally {
            SharedPlayers.clearAll();
        }
    }

    @Test
    void ownerIdIsRequired() {
        SharedPlayers.clearAll();
        PlayerDirectory directory = SharedPlayers.directory();
        assertThrows(IllegalArgumentException.class, () -> directory.visibility(" ", (viewer, target) -> true));
        assertThrows(NullPointerException.class, () -> directory.visibility(null, (viewer, target) -> true));
    }
}
