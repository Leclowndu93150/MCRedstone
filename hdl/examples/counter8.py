from amaranth import Elaboratable, Module, Signal

from mcrhdl import Led


class Counter8(Elaboratable):
    def __init__(self):
        self.count = Led("count", 8)

    def elaborate(self, platform):
        m = Module()
        value = Signal(8)
        m.d.sync += value.eq(value + 1)
        m.d.comb += self.count.eq(value)
        return m


def top():
    return Counter8()
