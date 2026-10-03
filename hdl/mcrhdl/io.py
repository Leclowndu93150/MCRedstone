from amaranth.hdl import Signal

INPUT_KINDS = ("lever", "button")
OUTPUT_KINDS = ("lamp", "matrix")
KEYS = ("up", "down", "left", "right", "a", "b")


def Lever(name, width=1, key=None):
    return _port(name, width, "lever", key)


def Button(name, width=1, key=None):
    return _port(name, width, "button", key)


def Led(name, width=1):
    return _port(name, width, "lamp", None)


def LampMatrix(name, width, height, scale=1):
    if scale not in (1, 2):
        raise ValueError("scale must be 1 (2x2 lamps per pixel) or 2 (4x4 lamps per pixel)")
    return Signal(width * height, name=name, attrs={"mcr_io": "matrix", "mcr_columns": width, "mcr_scale": scale})


def pixel(matrix, x, y):
    return matrix[y * matrix.attrs["mcr_columns"] + x]


def _port(name, width, kind, key):
    if key is not None and key not in KEYS:
        raise ValueError(f"unknown key {key}, use one of {KEYS}")
    attrs = {"mcr_io": kind}
    if key is not None:
        attrs["mcr_key"] = key
    return Signal(width, name=name, attrs=attrs)
