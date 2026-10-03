package com.leclowndu93150.mcredstone.gametest;

import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.netlist.Design;
import com.leclowndu93150.mcredstone.compiler.netlist.DesignMeta;
import com.leclowndu93150.mcredstone.compiler.netlist.Gate;
import com.leclowndu93150.mcredstone.compiler.netlist.GateKind;
import com.leclowndu93150.mcredstone.compiler.netlist.IoPort;
import com.leclowndu93150.mcredstone.compiler.netlist.Netlist;
import com.leclowndu93150.mcredstone.compiler.netlist.NetlistSim;
import com.leclowndu93150.mcredstone.compiler.netlist.Port;
import com.leclowndu93150.mcredstone.compiler.netlist.Vectors;
import com.leclowndu93150.mcredstone.compiler.netlist.YosysJson;
import java.io.InputStream;
import java.math.BigInteger;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class DesignTests {
    private static final String STRUCTURE = "mcredstone-gametest:empty64";

    @GameTest(structure = STRUCTURE, maxTicks = 400)
    public void notGate(GameTestHelper helper) {
        runGate(helper, 1);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 400)
    public void nor2Gate(GameTestHelper helper) {
        runGate(helper, 2);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 600)
    public void nor3Gate(GameTestHelper helper) {
        runGate(helper, 3);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40000)
    public void adder4(GameTestHelper helper) throws Exception {
        Design design = loadDesign("adder4");
        Layout layout = Compiler.compile(design);
        VectorRunner.run(helper, layout, design.vectors().steps());
    }

    private static void runGate(GameTestHelper helper, int inputs) {
        List<Port> ports = new ArrayList<>();
        List<IoPort> metaInputs = new ArrayList<>();
        int[] gateInputs = new int[inputs];
        for (int i = 0; i < inputs; i++) {
            ports.add(new Port("i" + i, new int[]{2 + i}));
            metaInputs.add(new IoPort("i" + i, 1, "lever", null));
            gateInputs[i] = 2 + i;
        }
        int output = 2 + inputs;
        Netlist netlist = new Netlist("nor" + inputs, ports, List.of(new Port("y", new int[]{output})), List.of(new Gate(GateKind.NOR, gateInputs, output)));
        DesignMeta meta = new DesignMeta("nor" + inputs, null, metaInputs, List.of(new IoPort("y", 1, "lamp", null)));
        Vectors vectors = exhaustive(netlist);
        Layout layout = Compiler.compile(new Design(netlist, meta, vectors));
        VectorRunner.run(helper, layout, vectors.steps());
    }

    private static Vectors exhaustive(Netlist netlist) {
        NetlistSim sim = new NetlistSim(netlist);
        List<String> inputNames = new ArrayList<>();
        for (Port port : netlist.inputs()) {
            inputNames.add(port.name());
        }
        List<Vectors.Step> steps = new ArrayList<>();
        int bits = netlist.inputs().size();
        for (int combo = 0; combo < (1 << bits); combo++) {
            Map<String, BigInteger> set = new LinkedHashMap<>();
            for (int i = 0; i < bits; i++) {
                BigInteger value = BigInteger.valueOf((combo >> i) & 1);
                set.put(inputNames.get(i), value);
                sim.set(inputNames.get(i), value);
            }
            sim.evaluate();
            steps.add(new Vectors.Step(set, Map.of("y", sim.get("y"))));
        }
        return new Vectors(false, inputNames, List.of("y"), steps);
    }

    static Design loadDesign(String name) throws Exception {
        DesignMeta meta = DesignMeta.read(resource(name + ".mcr.json"));
        return new Design(
            YosysJson.read(resource(name + ".json"), name, meta.clock()),
            meta,
            Vectors.read(resource(name + ".vectors.json"))
        );
    }

    private static Reader resource(String name) {
        InputStream stream = DesignTests.class.getResourceAsStream("/designs/" + name);
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
