package edu.eci.arsw.relicrush;

import edu.eci.arsw.relicrush.game.GameConfig;
import edu.eci.arsw.relicrush.game.GameEngine;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class GameEngineInvariantTest {

    @Test
    void everyCompletedRoundPreservesTheScoreLedgerInvariant() {
        assertTimeout(Duration.ofSeconds(10), () -> {
            int rounds = 25;
            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try (PrintStream replacement = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
                System.setOut(replacement);
                new GameEngine(new GameConfig(16, 6, rounds)).run();
            } finally {
                System.setOut(originalOut);
            }

            String output = captured.toString(StandardCharsets.UTF_8);
            assertFalse(output.contains("invariant=BROKEN"), output);
            long validRounds = output.lines().filter(line -> line.contains("invariant=OK")).count();
            assertEquals(rounds, validRounds, output);
        });
    }
}
