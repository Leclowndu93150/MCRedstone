package com.leclowndu93150.mcredstone.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.layout.LayoutIO;
import com.leclowndu93150.mcredstone.compiler.netlist.Design;
import com.leclowndu93150.mcredstone.compiler.sim.SequentialProtocol;
import com.leclowndu93150.mcredstone.compiler.sim.SimIo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LayoutIOTest {
    @Test
    void roundTripKeepsAClockedMachineWorking() throws Exception {
        Design design = Adder4Test.load("dot");
        Layout layout = Compiler.compile(design);
        Path file = Files.createTempFile("mcr", ".mcrl.json.gz");
        LayoutIO.write(layout, file);
        Layout read = LayoutIO.read(file);
        assertEquals(layout.grid().size(), read.grid().size());
        layout.grid().forEach((pos, voxel) -> assertEquals(voxel, read.grid().get(pos)));
        assertEquals(layout.states(), read.states());
        assertEquals(layout.inputs(), read.inputs());
        assertEquals(layout.outputs(), read.outputs());
        assertEquals(layout.timing(), read.timing());
        SequentialProtocol protocol = SimIo.check(read, design.vectors().steps().subList(0, 24), 24 * read.timing().period() * 3 + 1000);
        assertTrue(protocol.done());
        assertEquals(0, protocol.failed(), String.join("; ", protocol.failures()));
    }
}
