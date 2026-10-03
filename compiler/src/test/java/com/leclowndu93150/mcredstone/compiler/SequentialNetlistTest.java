package com.leclowndu93150.mcredstone.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.netlist.Design;
import com.leclowndu93150.mcredstone.compiler.netlist.NetlistSim;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SequentialNetlistTest {
    @ParameterizedTest
    @ValueSource(strings = {"counter8", "lfsr16"})
    void netlistMatchesVectors(String name) throws Exception {
        Design design = Adder4Test.load(name);
        assertTrue(design.vectors().sequential());
        assertTrue(design.netlist().sequential());
        assertEquals(0, new NetlistSim(design.netlist()).check(design.vectors()));
        assertEquals(0, new NetlistSim(Compiler.optimize(design.netlist())).check(design.vectors()));
    }
}
