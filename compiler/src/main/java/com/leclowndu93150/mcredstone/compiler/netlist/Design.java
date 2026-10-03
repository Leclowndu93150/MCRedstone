package com.leclowndu93150.mcredstone.compiler.netlist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record Design(Netlist netlist, DesignMeta meta, Vectors vectors) {
    public static Design load(Path directory, String name) throws IOException {
        Path vectorsPath = directory.resolve(name + ".vectors.json");
        DesignMeta meta = DesignMeta.read(directory.resolve(name + ".mcr.json"));
        return new Design(
            YosysJson.read(directory.resolve(name + ".json"), name, meta.clock()),
            meta,
            Files.exists(vectorsPath) ? Vectors.read(vectorsPath) : null
        );
    }

    public Design withNetlist(Netlist replacement) {
        return new Design(replacement, meta, vectors);
    }
}
