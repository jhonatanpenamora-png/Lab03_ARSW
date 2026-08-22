package edu.eci.arsw.relicrush.concurrency;

import edu.eci.arsw.relicrush.model.ForgeStation;

import java.util.Objects;

/**
 * Adquiere dos monitores de ForgeStation en orden global por ID para prevenir deadlock.
 * Mantiene concurrencia entre pares de estaciones disjuntas.
 */
public final class LockPair {

    private LockPair() {
    }

    public static void withBoth(ForgeStation first, ForgeStation second, Runnable action) {
        Objects.requireNonNull(first, "first station must not be null");
        Objects.requireNonNull(second, "second station must not be null");
        Objects.requireNonNull(action, "action must not be null");

        if (first == second) {
            synchronized (first) {
                action.run();
            }
            return;
        }

        if (first.id() == second.id()) {
            throw new IllegalArgumentException("Dos estaciones distintas no pueden compartir el mismo ID: " + first.id());
        }

        ForgeStation lower = first.id() < second.id() ? first : second;
        ForgeStation higher = first.id() < second.id() ? second : first;

        synchronized (lower) {
            synchronized (higher) {
                action.run();
            }
        }
    }
}