from amaranth import Cat, Elaboratable, Module, Signal

from mcrhdl import Led, Lever


class Lfsr16(Elaboratable):
    def __init__(self):
        self.enable = Lever("enable")
        self.state = Led("state", 16)

    def elaborate(self, platform):
        m = Module()
        value = Signal(16, init=0xACE1)
        feedback = value[0] ^ value[2] ^ value[3] ^ value[5]
        with m.If(self.enable):
            m.d.sync += value.eq(Cat(value[1:], feedback))
        m.d.comb += self.state.eq(value)
        return m


def top():
    return Lfsr16()
