"""Central Control (0.8): the complex's controller, the locks over its vents, the sites its recovery protocol brings
back, the conduits of its two buses and the sector's emblem.

Same language as the Underworks: ancient dark hull with steel fittings and wear; the two protocols keep their own
colours everywhere, cyan (teal) for containment and amber for recovery, so a player reads which bus feeds what.
"""
import math

import parts as K
import raster as R
from families.industry import glow_layer
from families.terrain import N as TILE, _panels


def central_controller(alt="a"):
    """Controlador central 5x5: a heavy octagonal frame around the routing core. The containment bus leaves on the
    left and top (cyan ducts), the recovery bus on the right and bottom (amber ducts); glyph panels in the corners;
    the core's split ring is the -rotator (turning with the recovery share) under a cap (-top); panels, ducts and
    the ring light up once it is surveyed (-glow)."""
    n, cx, cy = 160, 80, 80
    base = R.Canvas(n, n)
    frame = R.regular(cx, cy, 80, 8, 22.5) & R.rect(0, 0, n, n)
    base.plate(frame, "dark", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(base, frame, "central-controller", 0.08)
    K.cracks(base, "central-controller-a", (26, 132), 14)
    K.cracks(base, "central-controller-b", (128, 30), 10)
    lit = set()
    # the bus ducts: containment (teal) west and north, recovery (amber) east and south
    for k, ramp in ((0, "teal"), (1, "amber"), (2, "amber"), (3, "teal")):
        duct = R.rot90(R.rect(70, 2, 20, 40), n, k)
        K.raised(base, duct, "dark", 4, bevel=1, shadow=2)
        line = R.rot90(R.rect(77, 4, 6, 36), n, k)
        base.fill(line, ramp, 2)
        lit |= line
        for t in (12, 26):
            K.stud(base, *sorted(R.rot90({(73, t)}, n, k))[0], "steel", 5)
            K.stud(base, *sorted(R.rot90({(86, t)}, n, k))[0], "steel", 5)
    # glyph panels in the four corners
    for k in range(4):
        pad = R.rot90(R.chamfer(22, 22, 30, 30, 5), n, k)
        K.raised(base, pad, "dark", 5, bevel=1, shadow=2)
        screen = R.rot90(R.rect(26, 26, 22, 22), n, k)
        base.cavity(screen, "dark", 1, bevel=1)
        for gy in range(29, 46, 5):
            glyph = R.rot90(R.rect(29, gy, 6 + (gy * 7) % 9, 2), n, k)
            base.fill(glyph, "teal" if k in (0, 3) else "amber", 3)
            lit |= glyph
    for (x, y) in ((14, 80), (146, 80), (80, 14), (80, 146)):
        K.lamp(base, x, y, "amber", size=2)
    # the routing core: a deep well ringed by steel, the split ring turns inside it
    K.raised(base, R.circle(cx, cy, 40), "dark", 4, bevel=2, shadow=3)
    base.fill(R.ring(cx, cy, 37, 35.5), "steel", 4)
    K.well(base, R.circle(cx, cy, 33), "dark", 1, bevel=1, cast=2)
    for k in range(12):
        a = 2 * math.pi * k / 12
        K.rivet(base, int(cx + 38 * math.cos(a)), int(cy + 38 * math.sin(a)), ramp="steel", base=5)

    rot = R.Canvas(n, n)
    left = R.rect(0, 0, cx, n)
    ring = R.ring(cx, cy, 30, 24)
    K.plate(rot, ring, "steel", 3, bevel=1)
    rot.fill(R.ring(cx, cy, 28.5, 25.5) & left, "teal", 3)
    rot.fill(R.ring(cx, cy, 28.5, 25.5) - left, "amber", 3)
    for k in range(6):
        a = 2 * math.pi * k / 6 + 0.26
        spoke = R.thick(cx + 12 * math.cos(a), cy + 12 * math.sin(a), cx + 25 * math.cos(a), cy + 25 * math.sin(a), 4)
        K.plate(rot, spoke, "dark", 5, bevel=1)
    top = R.Canvas(n, n)
    cap = R.regular(cx, cy, 13, 8, 22.5)
    K.raised(top, cap, "dark", 5, bevel=2, shadow=2)
    top.fill(R.circle(cx, cy, 5), "dark", 1)
    top.dome(R.circle(cx, cy, 4), "teal", 2, 5)
    glow = glow_layer(n, lit | R.circle(cx, cy, 3), "teal", 5, falloff=3, alpha=(110, 60, 30), core_alpha=150)
    return {"": base, "-rotator": rot, "-top": top, "-glow": glow}


def containment_lock(alt="a"):
    """Trava de contenção 3x3: an ancient collar bolted around a vent shaft that drops into the dark, its throat
    lit from below (-glow, tinted by the game: amber open, red while the planet answers, cyan shut); the -shutter is
    one half of the ribbed closure, drawn from both sides as it closes."""
    n, cx, cy = 96, 48, 48
    base = R.Canvas(n, n)
    collar = R.chamfer(2, 2, 92, 92, 14)
    base.plate(collar, "dark", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(base, collar, "containment-lock", 0.12)
    for k in range(4):
        clamp = R.rot90(R.chamfer(38, 2, 20, 12, 3), n, k)
        K.raised(base, clamp, "steel", 3, bevel=1, shadow=2)
        K.rivet(base, *sorted(R.rot90({(43, 7)}, n, k))[0], ramp="steel", base=5)
        K.rivet(base, *sorted(R.rot90({(52, 7)}, n, k))[0], ramp="steel", base=5)
    K.stripes(base, R.rect(10, 84, 76, 4), ("amber", 3), ("dark", 1), period=8, width=4)
    shaft = R.chamfer(16, 16, 64, 64, 8)
    base.cavity(shaft, "dark", 0, bevel=1, cast=3)
    throat = R.chamfer(24, 24, 48, 48, 6)
    base.fill(throat, "dark", 0)
    for r in (20, 14, 8):
        base.fill(R.ring(cx, cy, r, r - 1.2) & throat, "rust", 1)
    shutter = R.Canvas(n // 2, n)
    plate = R.rect(0, 14, n // 2, 68)
    K.plate(shutter, plate, "steel", 3, bevel=1)
    for y in range(18, 80, 8):
        shutter.fill(R.rect(2, y, n // 2 - 4, 2), "steel", 1)
    shutter.fill(R.rect(n // 2 - 3, 14, 3, 68), "teal", 3)
    glow = glow_layer(n, R.ring(cx, cy, 20, 18) | R.ring(cx, cy, 9, 7), "pale", 5, falloff=3, alpha=(110, 60, 30), core_alpha=140)
    return {"": base, "-shutter": shutter, "-glow": glow}


def _site_pad(c, n, seed, cracked=0.12):
    pad = R.chamfer(2, 2, n - 4, n - 4, 10)
    c.plate(pad, "dark", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(c, pad, seed, cracked)
    return pad


def distribution_junction(alt="a"):
    """Junção de distribuição 3x3: the hub where the buses split. Found: a cracked hub, its four conduit stubs dead
    and rusted. Restored: the hub sealed again, two cyan and two amber conduits lit (-restored, -restored-glow)."""
    n, cx, cy = 96, 48, 48
    found = R.Canvas(n, n)
    _site_pad(found, n, "junction-found", 0.2)
    stubs = set()
    for k in range(4):
        stub = R.rot90(R.rect(42, 2, 12, 26), n, k)
        K.raised(found, stub, "dark", 4, bevel=1, shadow=2)
        found.fill(R.rot90(R.rect(46, 4, 4, 20), n, k), "rust", 2)
        stubs |= R.rot90(R.rect(46, 4, 4, 20), n, k)
    K.raised(found, R.circle(cx, cy, 20), "dark", 4, bevel=2, shadow=2)
    found.cavity(R.circle(cx, cy, 12), "dark", 1, bevel=1, cast=2)
    K.cracks(found, "junction-c", (30, 70), 16)
    fglow = glow_layer(n, R.circle(cx, cy, 2), "amber", 4, falloff=2, alpha=(60, 30), core_alpha=90)

    rest = R.Canvas(n, n)
    _site_pad(rest, n, "junction-restored", 0.015)
    lit = set()
    for k, ramp in ((0, "teal"), (1, "amber"), (2, "amber"), (3, "teal")):
        stub = R.rot90(R.rect(42, 2, 12, 26), n, k)
        K.raised(rest, stub, "dark", 4, bevel=1, shadow=2)
        line = R.rot90(R.rect(46, 4, 4, 20), n, k)
        rest.fill(line, ramp, 3)
        lit |= line
    K.raised(rest, R.circle(cx, cy, 20), "dark", 5, bevel=2, shadow=2)
    rest.fill(R.ring(cx, cy, 17, 15.5), "steel", 4)
    K.well(rest, R.circle(cx, cy, 12), "dark", 1, bevel=1, cast=1)
    K.glyph_ring(rest, cx, cy, 9, "teal", 3, gaps=4, gap_len=0.3)
    rest.dome(R.circle(cx, cy, 4), "teal", 2, 5)
    rglow = glow_layer(n, lit | R.circle(cx, cy, 3) | R.ring(cx, cy, 9.5, 8.5), "teal", 5, falloff=3, alpha=(110, 60, 30), core_alpha=150)
    return {"": found, "-glow": fglow, "-restored": rest, "-restored-glow": rglow}


def conduction_array(alt="a"):
    """Rede de condução 4x4: a bank of four conduction coils around a field ring. Found: the coils dark and cracked,
    one torn open. Restored: the coils whole and lit, the field ring (-rotator) turning while it feeds the grid."""
    n, cx, cy = 128, 64, 64
    found = R.Canvas(n, n)
    _site_pad(found, n, "array-found", 0.18)
    coils = [(30, 30), (98, 30), (30, 98), (98, 98)]
    for i, (x, y) in enumerate(coils):
        K.raised(found, R.circle(x, y, 20), "dark", 4, bevel=2, shadow=2)
        found.cavity(R.circle(x, y, 13), "dark", 1, bevel=1, cast=2)
        if i != 2:
            for r in (11, 8, 5):
                found.fill(R.ring(x, y, r, r - 1.2), "rust", 2)
    K.cracks(found, "array-c1", (20, 100), 22)
    K.cracks(found, "array-c2", (104, 64), 14)
    K.raised(found, R.circle(cx, cy, 16), "dark", 4, bevel=1, shadow=2)
    fglow = glow_layer(n, R.circle(cx, cy, 2), "teal", 4, falloff=2, alpha=(60, 30), core_alpha=80)

    rest = R.Canvas(n, n)
    _site_pad(rest, n, "array-restored", 0.015)
    lit = set()
    for x, y in coils:
        K.raised(rest, R.circle(x, y, 20), "dark", 5, bevel=2, shadow=2)
        rest.fill(R.ring(x, y, 17, 15.5), "steel", 4)
        K.well(rest, R.circle(x, y, 13), "dark", 1, bevel=1, cast=1)
        for r in (11, 8, 5):
            ring = R.ring(x, y, r, r - 1.2)
            rest.fill(ring, "teal", 3)
            lit |= ring
        K.pipe(rest, x, y, cx, cy, width=4, ramp="steel")
    K.well(rest, R.circle(cx, cy, 18), "dark", 1, bevel=1, cast=1)
    rot = R.Canvas(n, n)
    K.plate(rot, R.ring(cx, cy, 16, 12), "steel", 3, bevel=1)
    for k in range(4):
        a = 2 * math.pi * k / 4 + 0.4
        node = R.circle(cx + 14 * math.cos(a), cy + 14 * math.sin(a), 3)
        rot.fill(node, "teal", 5)
    rot.fill(R.circle(cx, cy, 5), "teal", 4)
    rglow = glow_layer(n, lit | R.circle(cx, cy, 4), "teal", 5, falloff=3, alpha=(110, 60, 30), core_alpha=150)
    return {"": found, "-glow": fglow, "-restored": rest, "-restored-glow": rglow, "-rotator": rot}


def processing_line(alt="a"):
    """Linha de processamento 4x4: a press line between an intake hopper and an output chute. Found: the press
    jammed, rollers seized with rust. Restored: the line clean, the rollers (-rotator) turning, the press lit amber
    and the output chute lit cyan (resonance cores)."""
    n, cx, cy = 128, 64, 64
    found = R.Canvas(n, n)
    _site_pad(found, n, "line-found", 0.18)
    hopper = R.chamfer(10, 12, 34, 34, 5)
    K.raised(found, hopper, "dark", 4, bevel=2, shadow=2)
    found.cavity(R.shrink(hopper, 6), "dark", 1, bevel=1, cast=2)
    chute = R.chamfer(84, 82, 34, 34, 5)
    K.raised(found, chute, "dark", 4, bevel=2, shadow=2)
    found.cavity(R.shrink(chute, 6), "dark", 1, bevel=1, cast=2)
    bed = R.chamfer(20, 50, 88, 28, 4)
    K.raised(found, bed, "dark", 4, bevel=1, shadow=2)
    for x in range(30, 104, 14):
        found.fill(R.circle(x, 64, 5), "rust", 2)
    K.cracks(found, "line-c", (100, 30), 18)
    fglow = glow_layer(n, R.rect(30, 62, 4, 4), "amber", 4, falloff=2, alpha=(60, 30), core_alpha=80)

    rest = R.Canvas(n, n)
    _site_pad(rest, n, "line-restored", 0.015)
    K.raised(rest, hopper, "steel", 4, bevel=2, shadow=2)
    rest.cavity(R.shrink(hopper, 6), "dark", 1, bevel=1, cast=2)
    K.raised(rest, chute, "steel", 4, bevel=2, shadow=2)
    rest.cavity(R.shrink(chute, 6), "dark", 1, bevel=1, cast=2)
    out = R.rect(92, 90, 18, 18)
    rest.fill(out, "teal", 3)
    K.raised(rest, bed, "dark", 5, bevel=1, shadow=2)
    press = R.rect(56, 46, 16, 36)
    K.raised(rest, press, "steel", 3, bevel=2, shadow=2)
    heat = R.rect(60, 52, 8, 24)
    rest.fill(heat, "amber", 4)
    K.stripes(rest, R.rect(20, 82, 60, 3), ("amber", 4), ("dark", 1), period=6, width=3)
    rot = R.Canvas(n, n)
    for x in range(30, 104, 14):
        if 52 < x < 76:
            continue
        K.plate(rot, R.circle(x, 64, 5), "steel", 4, bevel=1)
        rot.fill(R.rect(x - 1, 60, 2, 8), "dark", 2)
    rglow = glow_layer(n, heat | out, "amber", 5, falloff=3, alpha=(110, 60, 30), core_alpha=150)
    return {"": found, "-glow": fglow, "-restored": rest, "-restored-glow": rglow, "-rotator": rot}


def _conduit(variant, ramp, seed):
    """A conduit floor: hull panels with the bus's line along the middle of the tile both ways, so a run of conduit
    tiles reads as one line in any direction; the line is the bus's colour."""
    c = R.Canvas(TILE, TILE)
    _panels(c, "dark", 2, 3, seed)
    for t in range(TILE):
        for q in (14, 17):
            c.set((t, q), "dark", 1)
            c.set((q, t), "dark", 1)
    lit = 3 if variant != 2 else 2
    for t in range(TILE):
        for q in (15, 16):
            c.set((t, q), ramp, lit)
            c.set((q, t), ramp, lit)
    for q in (15, 16):
        for r in (15, 16):
            c.set((q, r), ramp, 5)
    if variant == 3:
        # a cracked sleeve, the line dimmer for a span
        for t in range(4, 10):
            c.set((t, 15), "dark", 2)
            c.set((t, 16), "dark", 2)
    return c


def containment_conduit(variant):
    return _conduit(variant, "teal", 61)


def recovery_conduit(variant):
    return _conduit(variant, "amber", 67)


def sector_central_control():
    """Two arcs, cyan and amber, around a central square: two protocols sharing one controller."""
    from families.campaign import badge, C, N
    c = R.Canvas(N, N)
    face = badge(c, rim_ramp="dark")
    left = R.rect(0, 0, C, N)
    ring = R.ring(C, C, 17, 14.6) & face
    c.fill(ring & left, "teal", 4)
    c.fill(ring - left, "amber", 4)
    c.fill(R.regular(C, C, 7.5, 4, 45), "dark", 5)
    c.fill(R.regular(C, C, 5, 4, 45), "teal", 5)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        c.fill(R.circle(C + 11 * math.cos(a), C + 11 * math.sin(a), 1.3), "teal" if k in (1, 2) else "amber", 5)
    return {"": c}
