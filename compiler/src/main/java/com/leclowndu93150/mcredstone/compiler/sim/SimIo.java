package com.leclowndu93150.mcredstone.compiler.sim;

import com.leclowndu93150.mcredstone.compiler.layout.IoPin;
import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.netlist.Vectors;
import com.leclowndu93150.mcredstone.compiler.synth.ClockInsert;
import java.math.BigInteger;
import java.util.List;

public final class SimIo implements SequentialProtocol.Io {
    private final GraphSim sim;
    private final Layout layout;

    public SimIo(GraphSim sim, Layout layout) {
        this.sim = sim;
        this.layout = layout;
    }

    @Override
    public boolean clock() {
        return sim.node(layout.timing().clockProbe()).on();
    }

    @Override
    public void drive(String port, BigInteger value) {
        for (IoPin pin : layout.inputs()) {
            if (pin.port().equals(port)) {
                sim.setLever(pin.pos(), value.testBit(pin.bit()));
            }
        }
    }

    @Override
    public BigInteger read(String port) {
        BigInteger value = BigInteger.ZERO;
        for (IoPin pin : layout.outputs()) {
            if (pin.port().equals(port) && sim.node(pin.pos()).on()) {
                value = value.setBit(pin.bit());
            }
        }
        return value;
    }

    @Override
    public void run(boolean on) {
        drive(ClockInsert.RUN, on ? BigInteger.ONE : BigInteger.ZERO);
    }

    public static SequentialProtocol check(Layout layout, List<Vectors.Step> steps, int maxTicks) {
        GraphSim sim = new GraphSim(layout.grid());
        sim.load(layout.states());
        SequentialProtocol protocol = new SequentialProtocol(new SimIo(sim, layout), steps, layout.timing().inputDelay(), 2 * layout.timing().period());
        for (int tick = 0; tick < maxTicks && !protocol.done(); tick++) {
            protocol.tick();
            sim.step();
        }
        return protocol;
    }
}
