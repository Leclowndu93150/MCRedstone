package com.leclowndu93150.mcredstone.compiler.synth;

import com.leclowndu93150.mcredstone.compiler.netlist.Gate;
import com.leclowndu93150.mcredstone.compiler.netlist.GateKind;
import com.leclowndu93150.mcredstone.compiler.netlist.Netlist;
import com.leclowndu93150.mcredstone.compiler.netlist.Port;
import java.util.ArrayList;
import java.util.List;

public final class ClockInsert {
    public static final String RUN = "run";

    public record Result(Netlist netlist, int run, int hold, int k1, int k2) {
    }

    private ClockInsert() {
    }

    public static Result run(Netlist netlist) {
        int run = netlist.netCount();
        int hold = run + 1;
        List<Port> inputs = new ArrayList<>(netlist.inputs());
        inputs.add(new Port(RUN, new int[]{run}));
        List<Gate> gates = new ArrayList<>(netlist.gates());
        gates.add(new Gate(GateKind.NOR, new int[]{run}, hold));
        Netlist inserted = new Netlist(netlist.name(), inputs, netlist.outputs(), gates, netlist.initialHigh());
        return new Result(inserted, run, hold, hold + 1, hold + 2);
    }
}
