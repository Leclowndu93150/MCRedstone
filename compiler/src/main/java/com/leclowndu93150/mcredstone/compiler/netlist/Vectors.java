package com.leclowndu93150.mcredstone.compiler.netlist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigInteger;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Vectors(boolean sequential, List<String> inputs, List<String> outputs, List<Step> steps) {
    public record Step(Map<String, BigInteger> set, Map<String, BigInteger> expect) {
    }

    public static Vectors read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return read(reader);
        }
    }

    public static Vectors read(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        List<Step> steps = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("steps")) {
            JsonObject step = element.getAsJsonObject();
            steps.add(new Step(values(step.getAsJsonObject("set")), values(step.getAsJsonObject("expect"))));
        }
        boolean sequential = root.has("sequential") && root.get("sequential").getAsBoolean();
        return new Vectors(sequential, names(root, "inputs"), names(root, "outputs"), steps);
    }

    private static List<String> names(JsonObject root, String key) {
        List<String> names = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray(key)) {
            names.add(element.getAsString());
        }
        return names;
    }

    private static Map<String, BigInteger> values(JsonObject object) {
        Map<String, BigInteger> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            values.put(entry.getKey(), entry.getValue().getAsBigInteger());
        }
        return values;
    }
}
