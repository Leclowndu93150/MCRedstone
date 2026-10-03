package com.leclowndu93150.mcredstone.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.netlist.Design;
import com.leclowndu93150.mcredstone.compiler.netlist.Netlist;
import com.leclowndu93150.mcredstone.compiler.netlist.NetlistSim;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Adder4Test {
    static Design load(String name) throws Exception {
        return Design.load(designs(), name);
    }

    static Path designs() throws URISyntaxException {
        return Path.of(Adder4Test.class.getResource("/designs").toURI());
    }

    @Test
    void netlistMatchesVectors() throws Exception {
        Design design = load("adder4");
        assertEquals(512, design.vectors().steps().size());
        assertEquals(0, new NetlistSim(design.netlist()).check(design.vectors()));
    }

    @Test
    void optimizedNetlistMatchesVectors() throws Exception {
        Design design = load("adder4");
        Netlist optimized = Compiler.optimize(design.netlist());
        assertTrue(optimized.gates().size() <= design.netlist().gates().size());
        assertEquals(0, new NetlistSim(optimized).check(design.vectors()));
        System.out.println("gates " + design.netlist().gates().size() + " -> " + optimized.gates().size());
    }

    @Test
    void compilesLvsClean() throws Exception {
        Layout layout = Compiler.compile(load("adder4"));
        System.out.println(layout.report());
        assertEquals(9, layout.inputs().size());
        assertEquals(5, layout.outputs().size());
        assertTrue(layout.settleTicks() > 0);
    }
}
