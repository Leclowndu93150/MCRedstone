package com.leclowndu93150.mcredstone.compiler.netlist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class YosysJson {
    private YosysJson() {
    }

    public static Netlist read(Path path, String name, String clock) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return read(reader, name, clock);
        }
    }

    public static Netlist read(Reader reader, String name, String clock) {
        JsonObject modules = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("modules");
        JsonObject top = modules.has("top") ? modules.getAsJsonObject("top") : modules.entrySet().iterator().next().getValue().getAsJsonObject();
        List<Port> inputs = new ArrayList<>();
        List<Port> outputs = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : top.getAsJsonObject("ports").entrySet()) {
            JsonObject port = entry.getValue().getAsJsonObject();
            Port parsed = new Port(entry.getKey(), bits(port.getAsJsonArray("bits")));
            if (entry.getKey().equals(clock)) {
                continue;
            }
            switch (port.get("direction").getAsString()) {
                case "input" -> inputs.add(parsed);
                case "output" -> outputs.add(parsed);
                default -> throw new IllegalArgumentException("unsupported port direction on " + entry.getKey());
            }
        }
        List<Gate> gates = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : top.getAsJsonObject("cells").entrySet()) {
            JsonObject cell = entry.getValue().getAsJsonObject();
            JsonObject connections = cell.getAsJsonObject("connections");
            String type = cell.get("type").getAsString();
            switch (type) {
                case "$_NOT_" -> gates.add(new Gate(GateKind.NOR, new int[]{pin(connections, "A")}, pin(connections, "Y")));
                case "$_NOR_" -> gates.add(new Gate(GateKind.NOR, new int[]{pin(connections, "A"), pin(connections, "B")}, pin(connections, "Y")));
                case "$_DFF_P_" -> gates.add(new Gate(GateKind.DFF, new int[]{pin(connections, "D")}, pin(connections, "Q")));
                default -> throw new IllegalArgumentException("unsupported cell type " + type + " (" + entry.getKey() + ")");
            }
        }
        Set<Integer> initialHigh = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : top.getAsJsonObject("netnames").entrySet()) {
            JsonObject net = entry.getValue().getAsJsonObject();
            JsonObject attributes = net.getAsJsonObject("attributes");
            if (attributes == null || !attributes.has("init")) {
                continue;
            }
            String init = attributes.get("init").getAsString();
            JsonArray netBits = net.getAsJsonArray("bits");
            for (int i = 0; i < netBits.size() && i < init.length(); i++) {
                JsonElement bit = netBits.get(i);
                if (bit.getAsJsonPrimitive().isNumber() && init.charAt(init.length() - 1 - i) == '1') {
                    initialHigh.add(bit.getAsInt());
                }
            }
        }
        return new Netlist(name, inputs, outputs, gates, initialHigh);
    }

    private static int pin(JsonObject connections, String pin) {
        return bit(connections.getAsJsonArray(pin).get(0));
    }

    private static int[] bits(JsonArray array) {
        int[] bits = new int[array.size()];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = bit(array.get(i));
        }
        return bits;
    }

    private static int bit(JsonElement element) {
        if (element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        return "1".equals(element.getAsString()) ? Netlist.CONST1 : Netlist.CONST0;
    }
}
