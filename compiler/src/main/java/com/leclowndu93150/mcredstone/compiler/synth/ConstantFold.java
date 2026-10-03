package com.leclowndu93150.mcredstone.compiler.synth;

import com.leclowndu93150.mcredstone.compiler.netlist.Gate;
import com.leclowndu93150.mcredstone.compiler.netlist.GateKind;
import com.leclowndu93150.mcredstone.compiler.netlist.Netlist;
import com.leclowndu93150.mcredstone.compiler.netlist.Port;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ConstantFold {
    private ConstantFold() {
    }

    public static Netlist run(Netlist netlist) {
        int[] constant = new int[netlist.netCount()];
        Arrays.fill(constant, -1);
        constant[Netlist.CONST0] = 0;
        constant[Netlist.CONST1] = 1;
        List<Gate> gates = new ArrayList<>(netlist.gates());
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Gate> next = new ArrayList<>();
            for (Gate gate : gates) {
                if (gate.kind() == GateKind.DFF) {
                    next.add(gate);
                    continue;
                }
                boolean forcedLow = false;
                List<Integer> live = new ArrayList<>();
                for (int input : gate.inputs()) {
                    if (constant[input] == 1) {
                        forcedLow = true;
                    } else if (constant[input] != 0) {
                        live.add(input);
                    }
                }
                if (forcedLow || live.isEmpty()) {
                    constant[gate.output()] = forcedLow ? 0 : 1;
                    changed = true;
                } else {
                    next.add(new Gate(gate.kind(), live.stream().mapToInt(Integer::intValue).toArray(), gate.output()));
                    changed |= live.size() != gate.inputs().length;
                }
            }
            gates = next;
        }
        List<Gate> resolved = new ArrayList<>();
        for (Gate gate : gates) {
            int input = gate.inputs()[0];
            if (gate.kind() == GateKind.DFF && constant[input] >= 0) {
                resolved.add(new Gate(gate.kind(), new int[]{constant[input] == 1 ? Netlist.CONST1 : Netlist.CONST0}, gate.output()));
            } else {
                resolved.add(gate);
            }
        }
        gates = resolved;
        List<Port> outputs = new ArrayList<>();
        for (Port port : netlist.outputs()) {
            int[] bits = port.bits().clone();
            for (int i = 0; i < bits.length; i++) {
                if (constant[bits[i]] >= 0) {
                    bits[i] = constant[bits[i]] == 1 ? Netlist.CONST1 : Netlist.CONST0;
                }
            }
            outputs.add(new Port(port.name(), bits));
        }
        return netlist.with(outputs, gates);
    }
}
