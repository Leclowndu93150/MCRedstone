package com.leclowndu93150.mcredstone.compiler.netlist;

public record Gate(GateKind kind, int[] inputs, int output) {
}
