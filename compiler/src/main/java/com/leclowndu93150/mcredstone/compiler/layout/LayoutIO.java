package com.leclowndu93150.mcredstone.compiler.layout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import com.leclowndu93150.mcredstone.compiler.voxel.Kind;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelState;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class LayoutIO {
    private LayoutIO() {
    }

    public static void write(Layout layout, Path path) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("name", layout.name());
        root.addProperty("settleTicks", layout.settleTicks());
        root.addProperty("report", layout.report());
        Map<Voxel, Integer> palette = new HashMap<>();
        JsonArray paletteJson = new JsonArray();
        JsonArray voxels = new JsonArray();
        layout.grid().forEach((pos, voxel) -> {
            Integer index = palette.get(voxel);
            if (index == null) {
                index = palette.size();
                palette.put(voxel, index);
                paletteJson.add(voxelJson(voxel));
            }
            JsonArray entry = new JsonArray();
            entry.add(Pos.x(pos));
            entry.add(Pos.y(pos));
            entry.add(Pos.z(pos));
            entry.add(index);
            voxels.add(entry);
        });
        root.add("palette", paletteJson);
        root.add("voxels", voxels);
        root.add("inputs", pinsJson(layout.inputs()));
        root.add("outputs", pinsJson(layout.outputs()));
        JsonArray states = new JsonArray();
        for (Map.Entry<Long, VoxelState> entry : layout.states().entrySet()) {
            JsonArray state = new JsonArray();
            state.add(Pos.x(entry.getKey()));
            state.add(Pos.y(entry.getKey()));
            state.add(Pos.z(entry.getKey()));
            state.add(entry.getValue().on() ? 1 : 0);
            state.add(entry.getValue().power());
            state.add(entry.getValue().locked() ? 1 : 0);
            states.add(state);
        }
        root.add("states", states);
        Map<String, JsonArray> decor = new TreeMap<>();
        for (Map.Entry<Long, String> entry : layout.decor().entrySet()) {
            JsonArray positions = decor.computeIfAbsent(entry.getValue(), k -> new JsonArray());
            positions.add(Pos.x(entry.getKey()));
            positions.add(Pos.y(entry.getKey()));
            positions.add(Pos.z(entry.getKey()));
        }
        JsonObject decorJson = new JsonObject();
        decor.forEach(decorJson::add);
        root.add("decor", decorJson);
        if (layout.timing() != null) {
            JsonObject timing = new JsonObject();
            timing.addProperty("period", layout.timing().period());
            timing.addProperty("inputDelay", layout.timing().inputDelay());
            timing.addProperty("probeX", Pos.x(layout.timing().clockProbe()));
            timing.addProperty("probeY", Pos.y(layout.timing().clockProbe()));
            timing.addProperty("probeZ", Pos.z(layout.timing().clockProbe()));
            timing.addProperty("report", layout.timing().report());
            root.add("timing", timing);
        }
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(path)), StandardCharsets.UTF_8)) {
            writer.write(root.toString());
        }
    }

    public static Layout read(Path path) throws IOException {
        try (Reader reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(path)), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Voxel> palette = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("palette")) {
                palette.add(voxel(element.getAsJsonObject()));
            }
            VoxelGrid grid = new VoxelGrid();
            for (JsonElement element : root.getAsJsonArray("voxels")) {
                JsonArray entry = element.getAsJsonArray();
                grid.set(entry.get(0).getAsInt(), entry.get(1).getAsInt(), entry.get(2).getAsInt(), palette.get(entry.get(3).getAsInt()));
            }
            Map<Long, VoxelState> states = new HashMap<>();
            for (JsonElement element : root.getAsJsonArray("states")) {
                JsonArray state = element.getAsJsonArray();
                states.put(Pos.of(state.get(0).getAsInt(), state.get(1).getAsInt(), state.get(2).getAsInt()),
                    new VoxelState(state.get(3).getAsInt() == 1, state.get(4).getAsInt(), state.get(5).getAsInt() == 1));
            }
            Map<Long, String> decor = new HashMap<>();
            if (root.has("decor")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("decor").entrySet()) {
                    JsonArray positions = entry.getValue().getAsJsonArray();
                    for (int i = 0; i + 2 < positions.size(); i += 3) {
                        decor.put(Pos.of(positions.get(i).getAsInt(), positions.get(i + 1).getAsInt(), positions.get(i + 2).getAsInt()), entry.getKey());
                    }
                }
            }
            Timing timing = null;
            if (root.has("timing")) {
                JsonObject json = root.getAsJsonObject("timing");
                timing = new Timing(
                    json.get("period").getAsInt(),
                    json.get("inputDelay").getAsInt(),
                    Pos.of(json.get("probeX").getAsInt(), json.get("probeY").getAsInt(), json.get("probeZ").getAsInt()),
                    json.get("report").getAsString()
                );
            }
            return new Layout(
                root.get("name").getAsString(),
                grid,
                states,
                pins(root.getAsJsonArray("inputs")),
                pins(root.getAsJsonArray("outputs")),
                root.get("settleTicks").getAsInt(),
                timing,
                root.get("report").getAsString(),
                decor
            );
        }
    }

    private static JsonObject voxelJson(Voxel voxel) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", voxel.kind().name());
        if (voxel.dir() != null) {
            json.addProperty("dir", voxel.dir().name());
        }
        if (voxel.delay() != 0) {
            json.addProperty("delay", voxel.delay());
        }
        if (voxel.subtract()) {
            json.addProperty("subtract", true);
        }
        return json;
    }

    private static Voxel voxel(JsonObject json) {
        return new Voxel(
            Kind.valueOf(json.get("kind").getAsString()),
            json.has("dir") ? Dir.valueOf(json.get("dir").getAsString()) : null,
            json.has("delay") ? json.get("delay").getAsInt() : 0,
            json.has("subtract") && json.get("subtract").getAsBoolean()
        );
    }

    private static JsonArray pinsJson(List<IoPin> pins) {
        JsonArray array = new JsonArray();
        for (IoPin pin : pins) {
            JsonObject json = new JsonObject();
            json.addProperty("port", pin.port());
            json.addProperty("bit", pin.bit());
            json.addProperty("x", Pos.x(pin.pos()));
            json.addProperty("y", Pos.y(pin.pos()));
            json.addProperty("z", Pos.z(pin.pos()));
            json.addProperty("kind", pin.kind());
            if (pin.key() != null) {
                json.addProperty("key", pin.key());
            }
            array.add(json);
        }
        return array;
    }

    private static List<IoPin> pins(JsonArray array) {
        List<IoPin> pins = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            pins.add(new IoPin(
                json.get("port").getAsString(),
                json.get("bit").getAsInt(),
                Pos.of(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt()),
                json.get("kind").getAsString(),
                json.has("key") ? json.get("key").getAsString() : null
            ));
        }
        return pins;
    }
}
