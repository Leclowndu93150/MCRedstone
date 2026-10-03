package com.leclowndu93150.mcredstone.compiler.netlist;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class NetlistSim {
    private final Netlist netlist;
    private final List<Integer> order;
    private final List<Gate> flipFlops = new ArrayList<>();
    private final boolean[] values;

    public NetlistSim(Netlist netlist) {
        this.netlist = netlist;
        this.order = netlist.topologicalOrder();
        this.values = new boolean[netlist.netCount()];
        values[Netlist.CONST1] = true;
        for (int net : netlist.initialHigh()) {
            values[net] = true;
        }
        for (Gate gate : netlist.gates()) {
            if (gate.kind() == GateKind.DFF) {
                flipFlops.add(gate);
            }
        }
    }

    public void set(String port, long value) {
        set(port, BigInteger.valueOf(value));
    }

    public void set(String port, BigInteger value) {
        int[] bits = netlist.input(port).bits();
        for (int i = 0; i < bits.length; i++) {
            if (bits[i] > Netlist.CONST1) {
                values[bits[i]] = value.testBit(i);
            }
        }
    }

    public BigInteger get(String port) {
        int[] bits = netlist.output(port).bits();
        BigInteger value = BigInteger.ZERO;
        for (int i = 0; i < bits.length; i++) {
            if (values[bits[i]]) {
                value = value.setBit(i);
            }
        }
        return value;
    }

    public boolean value(int net) {
        return values[net];
    }

    public void evaluate() {
        for (int index : order) {
            Gate gate = netlist.gates().get(index);
            boolean any = false;
            for (int input : gate.inputs()) {
                any |= values[input];
            }
            values[gate.output()] = !any;
        }
    }

    public void tick() {
        boolean[] next = new boolean[flipFlops.size()];
        for (int i = 0; i < next.length; i++) {
            next[i] = values[flipFlops.get(i).inputs()[0]];
        }
        for (int i = 0; i < next.length; i++) {
            values[flipFlops.get(i).output()] = next[i];
        }
    }

    public int check(Vectors vectors) {
        int failures = 0;
        for (Vectors.Step step : vectors.steps()) {
            for (Map.Entry<String, BigInteger> entry : step.set().entrySet()) {
                set(entry.getKey(), entry.getValue());
            }
            evaluate();
            for (Map.Entry<String, BigInteger> entry : step.expect().entrySet()) {
                if (!get(entry.getKey()).equals(entry.getValue())) {
                    failures++;
                    break;
                }
            }
            if (vectors.sequential()) {
                tick();
            }
        }
        return failures;
    }
}
