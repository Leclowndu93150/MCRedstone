package com.leclowndu93150.mcredstone;

import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.layout.LayoutCache;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

public final class Designs {
    private static final String SIDECAR = ".mcr.json";

    private Designs() {
    }

    public static Path root() {
        return FabricLoader.getInstance().getGameDir().resolve("mcredstone");
    }

    public static Path directory() {
        return root().resolve("designs");
    }

    public static Layout layout(String name, Consumer<String> progress) {
        try {
            return LayoutCache.load(directory(), root().resolve("layouts"), name, progress);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<String> autobuild() {
        Path file = root().resolve("autobuild.txt");
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file).stream().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<String> names() {
        Path directory = directory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(SIDECAR))
                .map(name -> name.substring(0, name.length() - SIDECAR.length()))
                .sorted()
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
