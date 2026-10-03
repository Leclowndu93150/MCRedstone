package com.leclowndu93150.mcredstone.compiler;

import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfSystemProperty(named = "mcr.bench", matches = "true")
class CompileBenchmark {
    private static final Path LOG = Path.of(System.getProperty("java.io.tmpdir"), "mcr-progress.log");

    @ParameterizedTest
    @ValueSource(strings = {"adder4", "counter8", "lfsr16", "dot", "flappy"})
    void compiles(String name) throws Exception {
        long start = System.nanoTime();
        String report = Compiler.compile(Adder4Test.load(name), message -> log(name + " " + (System.nanoTime() - start) / 1_000_000 + " ms " + message)).report();
        log("BENCH " + report + " total " + (System.nanoTime() - start) / 1_000_000 + " ms");
    }

    private static void log(String line) {
        try {
            Files.writeString(LOG, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
