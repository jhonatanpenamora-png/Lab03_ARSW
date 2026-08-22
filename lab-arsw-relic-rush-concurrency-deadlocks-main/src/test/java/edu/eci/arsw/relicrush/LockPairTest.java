package edu.eci.arsw.relicrush;

import edu.eci.arsw.relicrush.concurrency.LockPair;
import edu.eci.arsw.relicrush.model.ForgeStation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LockPairTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void deadlockReproduction_oppositeOrder_shouldNotDeadlock() throws InterruptedException {
        ForgeStation anvil = new ForgeStation(1, "Arcane Anvil");
        ForgeStation furnace = new ForgeStation(2, "Dragon Furnace");

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch bothAcquired = new CountDownLatch(2);
        AtomicInteger failures = new AtomicInteger(0);

        Thread t1 = new Thread(() -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.incrementAndGet();
                return;
            }
            try {
                LockPair.withBoth(anvil, furnace, () -> {
                    bothAcquired.countDown();
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception e) {
                failures.incrementAndGet();
            }
        }, "test-A-anvil-then-furnace");
        t1.setDaemon(true);

        Thread t2 = new Thread(() -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.incrementAndGet();
                return;
            }
            try {
                LockPair.withBoth(furnace, anvil, () -> {
                    bothAcquired.countDown();
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception e) {
                failures.incrementAndGet();
            }
        }, "test-B-furnace-then-anvil");
        t2.setDaemon(true);

        t1.start();
        t2.start();
        start.countDown();

        assertTrue(bothAcquired.await(3, TimeUnit.SECONDS),
                "Ambos threads deberían adquirir los locks dentro del timeout (no deadlock)");
        assertEquals(0, failures.get(), "No debería haber fallos por interrupción");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void sameStationTwice_shouldAcquireOnce() throws InterruptedException {
        ForgeStation station = new ForgeStation(5, "Echo Forge");
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger(0);

        Thread t = new Thread(() -> {
            try {
                LockPair.withBoth(station, station, () -> {
                    // no-op, solo adquirir una vez
                });
                done.countDown();
            } catch (Exception e) {
                failures.incrementAndGet();
            }
        }, "test-same-station");
        t.setDaemon(true);

        t.start();
        assertTrue(done.await(3, TimeUnit.SECONDS), "Debe completar sin deadlock al pedir la misma estación dos veces");
        assertEquals(0, failures.get());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void disjointPairs_shouldRunConcurrently() throws InterruptedException {
        ForgeStation s1 = new ForgeStation(1, "Station 1");
        ForgeStation s2 = new ForgeStation(2, "Station 2");
        ForgeStation s3 = new ForgeStation(3, "Station 3");
        ForgeStation s4 = new ForgeStation(4, "Station 4");

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch bothDone = new CountDownLatch(2);
        AtomicInteger failures = new AtomicInteger(0);

        Thread t1 = new Thread(() -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.incrementAndGet();
                return;
            }
            try {
                LockPair.withBoth(s1, s2, () -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
                bothDone.countDown();
            } catch (Exception e) {
                failures.incrementAndGet();
            }
        }, "test-pair-1-2");
        t1.setDaemon(true);

        Thread t2 = new Thread(() -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.incrementAndGet();
                return;
            }
            try {
                LockPair.withBoth(s3, s4, () -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
                bothDone.countDown();
            } catch (Exception e) {
                failures.incrementAndGet();
            }
        }, "test-pair-3-4");
        t2.setDaemon(true);

        t1.start();
        t2.start();
        start.countDown();

        assertTrue(bothDone.await(3, TimeUnit.SECONDS),
                "Pares disjuntos {1,2} y {3,4} deben ejecutarse concurrentemente sin bloquearse");
        assertEquals(0, failures.get());
    }
}