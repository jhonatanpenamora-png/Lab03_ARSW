package edu.eci.arsw.relicrush;

import edu.eci.arsw.relicrush.concurrency.ForgeLedger;
import edu.eci.arsw.relicrush.model.ForgeEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForgeLedgerConcurrencyTest {

    @Test
    void recordsEveryConcurrentEventExactlyOnce() throws InterruptedException {
        int workers = 32;
        int writesPerWorker = 2_000;
        int expected = workers * writesPerWorker;
        ForgeLedger ledger = new ForgeLedger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int workerId = 0; workerId < workers; workerId++) {
            int id = workerId;
            Thread thread = Thread.ofPlatform().name("test-ledger-writer-" + id).unstarted(() -> {
                try {
                    start.await();
                    for (int write = 1; write <= writesPerWorker; write++) {
                        ledger.record(new ForgeEvent(write, "writer-" + id, "A", "B", write));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }

        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(expected, ledger.totalCrafted());
        assertEquals(expected, ledger.eventCount());
        assertEquals(expected, ledger.snapshot().size());
    }
}