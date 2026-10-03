from amaranth import Elaboratable, Module, Signal

from mcrhdl import LampMatrix, Lever, pixel


class Dot(Elaboratable):
    def __init__(self):
        self.left = Lever("left", key="left")
        self.right = Lever("right", key="right")
        self.up = Lever("up", key="up")
        self.down = Lever("down", key="down")
        self.screen = LampMatrix("screen", 4, 4)

    def elaborate(self, platform):
        m = Module()
        x = Signal(2, init=1)
        y = Signal(2, init=1)
        with m.If(self.left & (x != 0)):
            m.d.sync += x.eq(x - 1)
        with m.Elif(self.right & (x != 3)):
            m.d.sync += x.eq(x + 1)
        with m.If(self.up & (y != 0)):
            m.d.sync += y.eq(y - 1)
        with m.Elif(self.down & (y != 3)):
            m.d.sync += y.eq(y + 1)
        for row in range(4):
            for column in range(4):
                m.d.comb += pixel(self.screen, column, row).eq((x == column) & (y == row))
        return m


def top():
    return Dot()
