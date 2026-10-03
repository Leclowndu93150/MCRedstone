package com.leclowndu93150.mcredstone.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.netlist.Design;
import com.leclowndu93150.mcredstone.compiler.sim.SequentialProtocol;
import com.leclowndu93150.mcredstone.compiler.sim.SimIo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SequentialSimTest {
    @ParameterizedTest
    @ValueSource(strings = {"counter8", "lfsr16"})
    void runsCycleAccurateInGraphSim(String name) throws Exception {
        Design design = Adder4Test.load(name);
        Layout layout = Compiler.compile(design);
        System.out.println(layout.report());
        assertNotNull(layout.timing());
        SequentialProtocol protocol = SimIo.check(layout, design.vectors().steps().subList(0, 64), 64 * layout.timing().period() * 3 + 1000);
        assertTrue(protocol.done(), "protocol did not finish");
        assertEquals(0, protocol.failed(), String.join("; ", protocol.failures()));
    }
}
