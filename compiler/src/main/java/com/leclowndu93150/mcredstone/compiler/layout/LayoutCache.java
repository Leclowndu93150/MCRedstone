package com.leclowndu93150.mcredstone.compiler.layout;

import com.leclowndu93150.mcredstone.compiler.netlist.Design;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Consumer;

public final class LayoutCache {
    private LayoutCache() {
    }

    public static Layout load(Path designs, Path layouts, String name, Consumer<String> progress) throws IOException {
        String hash = hash(designs, name);
        Path file = layouts.resolve(name + ".mcrl.json.gz");
        Path hashFile = layouts.resolve(name + ".hash");
        if (Files.exists(file) && Files.exists(hashFile) && Files.readString(hashFile).trim().equals(hash)) {
            progress.accept("using cached layout");
            return LayoutIO.read(file);
        }
        Layout layout = Compiler.compile(Design.load(designs, name), progress);
        LayoutIO.write(layout, file);
        Files.writeString(hashFile, hash);
        return layout;
    }

    static String hash(Path designs, String name) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Compiler.VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(Files.readAllBytes(designs.resolve(name + ".json")));
            digest.update(Files.readAllBytes(designs.resolve(name + ".mcr.json")));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
