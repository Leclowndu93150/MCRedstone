package com.leclowndu93150.mcredstone.compiler.netlist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record DesignMeta(String name, String clock, List<IoPort> inputs, List<IoPort> outputs) {
    public static DesignMeta read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return read(reader);
        }
    }

    public static DesignMeta read(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonElement clock = root.get("clock");
        return new DesignMeta(root.get("name").getAsString(), clock == null || clock.isJsonNull() ? null : clock.getAsString(), ports(root, "inputs"), ports(root, "outputs"));
    }

    private static List<IoPort> ports(JsonObject root, String key) {
        List<IoPort> ports = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray(key)) {
            JsonObject port = element.getAsJsonObject();
            JsonElement keyBinding = port.get("key");
            JsonElement columns = port.get("columns");
            JsonElement scale = port.get("scale");
            ports.add(new IoPort(
                port.get("name").getAsString(),
                port.get("width").getAsInt(),
                port.get("kind").getAsString(),
                keyBinding == null || keyBinding.isJsonNull() ? null : keyBinding.getAsString(),
                columns == null || columns.isJsonNull() ? 0 : columns.getAsInt(),
                scale == null || scale.isJsonNull() ? 1 : scale.getAsInt()
            ));
        }
        return ports;
    }
}
