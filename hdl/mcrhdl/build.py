import importlib
import itertools
import json
import os
import random
import shutil
from pathlib import Path

import yowasp_yosys
from amaranth.back import rtlil
from amaranth.hdl import ClockDomain, Elaboratable, Module, Signal
from amaranth.sim import Simulator

from .io import INPUT_KINDS, OUTPUT_KINDS

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "hdl" / "build"
DESIGNS = ROOT / "run" / "mcredstone" / "designs"
SCRIPT = Path(__file__).with_name("mcr.ys")
MAX_EXHAUSTIVE_BITS = 16
SEQUENTIAL_CYCLES = 256


class Board(Elaboratable):
    def __init__(self, inner):
        self.inner = inner
        self.sync = ClockDomain("sync", reset_less=True)

    def elaborate(self, platform):
        m = Module()
        m.domains.sync = self.sync
        m.submodules.inner = self.inner
        return m


def build(top, name, testbench=None, cycles=SEQUENTIAL_CYCLES):
    inputs, outputs = collect_ports(top)
    board = Board(top)
    netlist = synthesize(board, name, inputs, outputs)
    sequential = has_flip_flops(netlist)
    if testbench:
        steps = testbench(board, inputs, outputs)
    elif sequential:
        steps = sequential_vectors(board, inputs, outputs, cycles)
    else:
        steps = exhaustive_vectors(board, inputs, outputs)
    DESIGNS.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(netlist, DESIGNS / f"{name}.json")
    write_json(DESIGNS / f"{name}.mcr.json", {
        "name": name,
        "clock": "clk",
        "inputs": [port_meta(signal) for signal in inputs],
        "outputs": [port_meta(signal) for signal in outputs],
    })
    write_json(DESIGNS / f"{name}.vectors.json", {
        "sequential": sequential,
        "inputs": [signal.name for signal in inputs],
        "outputs": [signal.name for signal in outputs],
        "steps": steps,
    })
    print(f"{name}: {'sequential' if sequential else 'combinational'}, {len(steps)} vectors -> {DESIGNS}")


def collect_ports(top):
    inputs, outputs = [], []
    for value in vars(top).values():
        if isinstance(value, Signal):
            kind = value.attrs.get("mcr_io")
            if kind in INPUT_KINDS:
                inputs.append(value)
            elif kind in OUTPUT_KINDS:
                outputs.append(value)
    return inputs, outputs


def synthesize(board, name, inputs, outputs):
    work = WORK / name
    work.mkdir(parents=True, exist_ok=True)
    ports = inputs + outputs + [board.sync.clk]
    (work / f"{name}.il").write_text(rtlil.convert(board, name="top", ports=ports, emit_src=False), newline="\n")
    (work / f"{name}.ys").write_text(SCRIPT.read_text().format(name=name), newline="\n")
    cwd = os.getcwd()
    os.chdir(work)
    try:
        code = yowasp_yosys.run_yosys(["-q", "-s", f"{name}.ys"])
    finally:
        os.chdir(cwd)
    if code != 0:
        raise SystemExit(f"yosys exited with code {code}")
    return work / f"{name}.json"


def has_flip_flops(netlist):
    modules = json.loads(Path(netlist).read_text())["modules"]
    return any(cell["type"] == "$_DFF_P_" for module in modules.values() for cell in module["cells"].values())


def exhaustive_vectors(board, inputs, outputs):
    if sum(len(signal) for signal in inputs) > MAX_EXHAUSTIVE_BITS:
        raise SystemExit("too many input bits for exhaustive vectors, pass a testbench")
    steps = []

    async def bench(ctx):
        for combo in itertools.product(*(range(1 << len(signal)) for signal in inputs)):
            for signal, value in zip(inputs, combo):
                ctx.set(signal, value)
            await ctx.delay(1e-6)
            steps.append({
                "set": {signal.name: value for signal, value in zip(inputs, combo)},
                "expect": {signal.name: ctx.get(signal) for signal in outputs},
            })

    sim = Simulator(board)
    sim.add_testbench(bench)
    sim.run()
    return steps


def sequential_vectors(board, inputs, outputs, cycles, seed=1):
    rng = random.Random(seed)
    steps = []

    async def bench(ctx):
        for _ in range(cycles):
            values = {signal.name: rng.randrange(1 << len(signal)) for signal in inputs}
            for signal in inputs:
                ctx.set(signal, values[signal.name])
            steps.append({
                "set": values,
                "expect": {signal.name: ctx.get(signal) for signal in outputs},
            })
            await ctx.tick()

    sim = Simulator(board)
    sim.add_clock(1e-6)
    sim.add_testbench(bench)
    sim.run()
    return steps


def port_meta(signal):
    return {
        "name": signal.name,
        "width": len(signal),
        "kind": signal.attrs["mcr_io"],
        "key": signal.attrs.get("mcr_key"),
        "columns": signal.attrs.get("mcr_columns", 0),
        "scale": signal.attrs.get("mcr_scale", 1),
    }


def write_json(path, data):
    path.write_text(json.dumps(data, indent=1))


def main(argv):
    module = importlib.import_module(argv[1])
    name = getattr(module, "NAME", argv[1].rsplit(".", 1)[-1])
    build(module.top(), name, getattr(module, "testbench", None))
