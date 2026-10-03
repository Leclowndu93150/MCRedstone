from amaranth import Elaboratable, Module, Signal

from mcrhdl import Led, Lever


class Adder4(Elaboratable):
    def __init__(self):
        self.a = Lever("a", 4)
        self.b = Lever("b", 4)
        self.cin = Lever("cin")
        self.sum = Led("sum", 4)
        self.cout = Led("cout")

    def elaborate(self, platform):
        m = Module()
        total = Signal(5)
        m.d.comb += total.eq(self.a + self.b + self.cin)
        m.d.comb += [self.sum.eq(total[:4]), self.cout.eq(total[4])]
        return m


def top():
    return Adder4()
