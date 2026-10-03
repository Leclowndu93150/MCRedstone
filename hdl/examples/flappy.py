from amaranth import Cat, Elaboratable, Module, Signal

from mcrhdl import LampMatrix, Led, Lever, pixel

WIDTH = 8
HEIGHT = 10
GAP = 3
BIRD_COLUMN = 1


class Flappy(Elaboratable):
    def __init__(self):
        self.flap = Lever("flap", key="up")
        self.restart = Lever("restart", key="a")
        self.screen = LampMatrix("screen", WIDTH, HEIGHT)
        self.score = Led("score", 4)

    def elaborate(self, platform):
        m = Module()
        bird = Signal(4, init=4)
        pipe = Signal(3, init=WIDTH - 1)
        gap = Signal(3, init=3)
        score = Signal(4)
        dead = Signal()
        noise = Signal(8, init=0x5A)

        m.d.sync += noise.eq(Cat(noise[1:], noise[0] ^ noise[2] ^ noise[3] ^ noise[4]))

        in_gap = Signal()
        m.d.comb += in_gap.eq((bird >= gap) & (bird < gap + GAP))
        hit_pipe = Signal()
        m.d.comb += hit_pipe.eq((pipe == BIRD_COLUMN) & ~in_gap)
        hit_ground = Signal()
        m.d.comb += hit_ground.eq(bird == HEIGHT - 1)

        with m.If(dead):
            with m.If(self.restart):
                m.d.sync += [dead.eq(0), bird.eq(4), pipe.eq(WIDTH - 1), score.eq(0)]
        with m.Elif(hit_pipe | hit_ground):
            m.d.sync += dead.eq(1)
        with m.Else():
            with m.If(self.flap):
                with m.If(bird != 0):
                    m.d.sync += bird.eq(bird - 1)
            with m.Else():
                m.d.sync += bird.eq(bird + 1)
            with m.If(pipe == 0):
                m.d.sync += [pipe.eq(WIDTH - 1), gap.eq(noise[:3] % (HEIGHT - GAP)), score.eq(score + 1)]
            with m.Else():
                m.d.sync += pipe.eq(pipe - 1)

        for row in range(HEIGHT):
            row_in_gap = (row >= gap) & (row < gap + GAP)
            for column in range(WIDTH):
                lit = (pipe == column) & ~row_in_gap
                if column == BIRD_COLUMN:
                    lit = lit | (bird == row)
                m.d.comb += pixel(self.screen, column, row).eq(lit)
        m.d.comb += self.score.eq(score)
        return m


def top():
    return Flappy()
