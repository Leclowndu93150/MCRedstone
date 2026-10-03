package com.leclowndu93150.mcredstone.compiler.netlist;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Set;

public record Netlist(String name, List<Port> inputs, List<Port> outputs, List<Gate> gates, Set<Integer> initialHigh) {
    public static final int CONST0 = 0;
    public static final int CONST1 = 1;

    public Netlist(String name, List<Port> inputs, List<Port> outputs, List<Gate> gates) {
        this(name, inputs, outputs, gates, Set.of());
    }

    public Netlist with(List<Port> newOutputs, List<Gate> newGates) {
        return new Netlist(name, inputs, newOutputs, newGates, initialHigh);
    }

    public Netlist withGates(List<Gate> newGates) {
        return new Netlist(name, inputs, outputs, newGates, initialHigh);
    }

    public boolean sequential() {
        for (Gate gate : gates) {
            if (gate.kind() == GateKind.DFF) {
                return true;
            }
        }
        return false;
    }

    public int netCount() {
        int max = CONST1;
        for (Port port : inputs) {
            for (int bit : port.bits()) {
                max = Math.max(max, bit);
            }
        }
        for (Port port : outputs) {
            for (int bit : port.bits()) {
                max = Math.max(max, bit);
            }
        }
        for (Gate gate : gates) {
            max = Math.max(max, gate.output());
            for (int input : gate.inputs()) {
                max = Math.max(max, input);
            }
        }
        return max + 1;
    }

    public int[] driverGates() {
        int[] drivers = new int[netCount()];
        Arrays.fill(drivers, -1);
        for (int i = 0; i < gates.size(); i++) {
            drivers[gates.get(i).output()] = i;
        }
        return drivers;
    }

    public int[] fanouts() {
        int[] fanouts = new int[netCount()];
        for (Gate gate : gates) {
            for (int input : gate.inputs()) {
                fanouts[input]++;
            }
        }
        for (Port port : outputs) {
            for (int bit : port.bits()) {
                fanouts[bit]++;
            }
        }
        return fanouts;
    }

    public List<Integer> topologicalOrder() {
        int[] drivers = driverGates();
        int[] pending = new int[gates.size()];
        List<List<Integer>> readers = new ArrayList<>();
        for (int i = 0; i < netCount(); i++) {
            readers.add(new ArrayList<>());
        }
        Deque<Integer> ready = new ArrayDeque<>();
        int combinational = 0;
        for (int i = 0; i < gates.size(); i++) {
            if (gates.get(i).kind() == GateKind.DFF) {
                continue;
            }
            combinational++;
            for (int input : gates.get(i).inputs()) {
                if (drivers[input] >= 0 && gates.get(drivers[input]).kind() != GateKind.DFF) {
                    pending[i]++;
                    readers.get(input).add(i);
                }
            }
            if (pending[i] == 0) {
                ready.add(i);
            }
        }
        List<Integer> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            int gate = ready.poll();
            order.add(gate);
            for (int reader : readers.get(gates.get(gate).output())) {
                if (--pending[reader] == 0) {
                    ready.add(reader);
                }
            }
        }
        if (order.size() != combinational) {
            throw new IllegalStateException("netlist has a combinational loop");
        }
        return order;
    }

    public Port input(String portName) {
        return find(inputs, portName);
    }

    public Port output(String portName) {
        return find(outputs, portName);
    }

    private static Port find(List<Port> ports, String portName) {
        for (Port port : ports) {
            if (port.name().equals(portName)) {
                return port;
            }
        }
        throw new IllegalArgumentException("unknown port " + portName);
    }
}
