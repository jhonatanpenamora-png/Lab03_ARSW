package edu.eci.arsw.relicrush.concurrency;

import edu.eci.arsw.relicrush.model.ForgeEvent;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global match ledger.
 *
 * Thread-safe global ledger. Writers do not acquire a game-wide monitor:
 * the counter uses an atomic read-modify-write operation and events are stored
 * in a concurrent queue.
 */
public final class ForgeLedger {
    private final AtomicInteger totalCrafted = new AtomicInteger();
    private final Queue<ForgeEvent> events = new ConcurrentLinkedQueue<>();

    public void record(ForgeEvent event) {
        events.add(Objects.requireNonNull(event, "event must not be null"));
        totalCrafted.incrementAndGet();
    }

    public int totalCrafted() {
        return totalCrafted.get();
    }

    public int eventCount() {
        return events.size();
    }

    public List<ForgeEvent> snapshot() {
        return List.copyOf(events);
    }
}
