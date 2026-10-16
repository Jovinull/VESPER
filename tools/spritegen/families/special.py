"""Special structures: the player's core and the ancient installations of The Silent Basin."""
import math

import parts as K
import raster as R
from families.industry import glow_layer


def core_foothold(alt="a"):
    """Núcleo inicial 3x3: the player's landing foothold. An octagonal landing deck on four hazard-marked
    struts, cargo pods on both flanks, a comms mast, and the central spawn iris ringed in team color."""
    n, cx, cy = 96, 48, 48
    base = R.Canvas(n, n)
    team = R.Canvas(n, n)
    K.deck(base, n, 9, "steel", 3, inset=3, seed="core" + alt, rust=0.05, layout="x")
    # landing struts in the corners
    for k in range(4):
        strut = R.rot90(R.chamfer(4, 4, 18, 18, 6), n, k)
        K.raised(base, strut, "steel", 4, bevel=2)
        K.stripes(base, R.rot90(R.chamfer(7, 7, 12, 12, 4), n, k), ("amber", 4), ("dark", 2), period=6, width=3)
        K.rivet(base, *sorted(R.rot90({(12, 12)}, n, k))[0], ramp="steel", base=5)
    if alt == "a":
        # cargo pods on the flanks
        for x in (6, n - 18):
            pod = R.chamfer(x, 28, 12, 40, 3)
            K.raised(base, pod, "steel", 4, bevel=2)
            K.panels(base, R.shrink(pod, 2), R.rect(0, 47, n, 2), "steel", 4)
            base.fill(R.rect(x + 2, 32, 8, 2), "amber", 4)
            base.fill(R.rect(x + 2, 62, 8, 2), "amber", 4)
        # comms mast, top edge
        mast = R.chamfer(40, 4, 16, 10, 3)
        K.raised(base, mast, "dark", 4, bevel=1)
        K.lamp(base, 44, 8, "amber", size=1)
        K.lamp(base, 51, 8, "teal", size=1)
        K.vent(base, 38, 84, 20, 7, horizontal=False)
    else:
        for k in range(4):
            fin = R.rot90(R.chamfer(42, 4, 12, 16, 3), n, k)
            K.raised(base, fin, "steel", 4, bevel=1)
            K.lamp(base, *sorted(R.rot90({(47, 8)}, n, k))[0], ramp="amber", size=1)
    # octagonal landing deck
    deck = R.regular(cx, cy, 30, 8, 22.5)
    base.drop_shadow(deck, -1, 2)
    K.plate(base, deck, "steel", 5, bevel=2, rim=1)
    K.panels(base, R.shrink(deck, 3), R.line(18, 18, 77, 77) | R.line(77, 18, 18, 77), "steel", 5, gap=("steel", 2))
    ring = R.circle(cx, cy, 19)
    K.plate(base, ring, "dark", 4, bevel=2, rim=1)
    # spawn iris: six blades around a dark well
    base.cavity(R.circle(cx, cy, 13), "dark", 1, bevel=1)
    iris = R.circle(cx, cy, 12) - R.circle(cx, cy, 4)
    seams = set()
    for k in range(6):
        a = k * math.pi / 3 + 0.2
        seams |= R.thick(cx + 4 * math.cos(a), cy + 4 * math.sin(a), cx + 12 * math.cos(a + 0.45), cy + 12 * math.sin(a + 0.45), 1.2)
    K.panels(base, iris, seams, "steel", 3, gap=("dark", 1))
    base.dome(R.circle(cx, cy, 3.5), "amber", 3, 5)
    # team-colored ring and deck markers (white / #dcc6c6 / #9d7f7f are swapped for team shades)
    team.fill(R.ring(cx, cy, 17.5, 15.5), "team", 2)
    team.fill(R.ring(cx, cy, 15.5, 14.8) - R.ring(cx, cy, 17.5, 15.5), "team", 0)
    for k in range(4):
        mark = R.rot90(R.poly([(cx - 3, 21), (cx + 3, 21), (cx, 25)]), n, k)
        team.fill(mark, "team", 1)
    return {"": base, "-team": team}


def derelict_reactor(alt="a"):
    """Reator antigo abandonado 4x4: a vast containment vessel, torn open. Its shattered rings are displaced,
    one anchor pylon has collapsed, and the exposed crystalline core still bleeds resonance through cracks."""
    n, cx, cy = 128, 64, 64
    base = R.Canvas(n, n)
    found = R.regular(cx, cy, 70, 8, 22.5) & R.rect(0, 0, n, n)
    base.plate(found, "dark", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(base, {p for p in found if p[0] - p[1] < 10}, "reactor-found", 0.07, cell=7)
    # anchor pylons; the top-left one has collapsed into rubble
    for k, (px, py) in enumerate(((20, 20), (108, 20), (20, 108), (108, 108))):
        if k == 0:
            for j in range(7):
                rnd = R.rng("rubble", j)
                rx, ry = 10 + rnd.randint(0, 22), 10 + rnd.randint(0, 22)
                chunk = R.chamfer(rx, ry, rnd.randint(4, 8), rnd.randint(3, 7), 1)
                K.raised(base, chunk, "dark", 4 if j % 2 else 3, bevel=1)
                K.wear(base, chunk, "rubble%d" % j, 0.3)
            continue
        pyl = R.regular(px, py, 12, 8, 22.5)
        K.raised(base, pyl, "dark", 5, bevel=2, rim=1)
        K.wear(base, pyl, "pyl%d" % k, 0.15)
        base.fill(R.circle(px, py, 3), "red", 1)
        base.set((px, py - 1), "red", 2)
    # outer containment ring, torn open on the top-left: jagged ends show bright fractured metal
    breach = R.poly([(cx, cy), (cx - 70, cy + 6), (cx - 70, cy - 70), (cx + 4, cy - 70)])
    jag = set()
    for k in range(14):
        rnd = R.rng("jag", k)
        a = math.radians(176 + k * 6.6)
        r0 = 40 + rnd.randint(0, 9)
        jag |= R.thick(cx + r0 * math.cos(a), cy + r0 * math.sin(a), cx + 52 * math.cos(a), cy + 52 * math.sin(a), 2)
    outer = R.ring(cx, cy, 50, 40) - breach - jag
    base.drop_shadow(outer, -1, 3)
    spokes = set()
    for k in range(12):
        a = k * math.pi / 6
        spokes |= R.thick(cx + 40 * math.cos(a), cy + 40 * math.sin(a), cx + 50 * math.cos(a), cy + 50 * math.sin(a), 1.4)
    K.panels(base, outer, spokes, "dark", 5, gap=("dark", 1))
    K.wear(base, outer, "reactor-outer", 0.08)
    broken_ends = R.halo(breach | jag) & outer
    base.fill(broken_ends, "steel", 5)
    for k in range(12):
        a = (k + 0.5) * math.pi / 6
        q = (int(cx + 45 * math.cos(a)), int(cy + 45 * math.sin(a)))
        if q in outer:
            K.stud(base, q[0], q[1], "steel", 4)
    # the breach itself: a scorched void with ring fragments thrown outward
    void = (breach | jag) & R.circle(cx, cy, 52) - R.circle(cx, cy, 39)
    base.cavity(void, "dark", 1, bevel=1, cast=2)
    base.fill(R.shrink(void, 2), "dark", 0)
    for j, (ang, r, fw, fh) in enumerate(((203, 59, 9, 6), (224, 62, 8, 7), (243, 58, 9, 5), (214, 49, 5, 4), (256, 61, 6, 4))):
        a = math.radians(ang)
        fx, fy = int(cx + r * math.cos(a) - fw / 2), int(cy + r * math.sin(a) - fh / 2)
        chunk = R.chamfer(fx, fy, fw, fh, 2)
        K.raised(base, chunk, "dark", 5, bevel=1, shadow=2)
        base.fill(R.edge(chunk) & R.shift(chunk, 1, 1) - chunk, "steel", 5)
    # inner chamber with the exposed core
    chamber = R.circle(cx, cy, 39)
    base.cavity(chamber - outer, "dark", 1, bevel=2, cast=3)
    inner = R.ring(cx, cy, 30, 25) - R.poly([(cx, cy), (cx - 60, cy + 4), (cx - 20, cy - 60)])
    K.plate(base, inner, "dark", 4, bevel=1)
    K.glyph_ring(base, cx, cy, 27.5, "teal", 2, gaps=9, gap_len=0.45, phase=0.1)
    core = R.regular(cx, cy, 15, 6, 0)
    upper, left = R.rect(0, 0, n, cy), R.rect(0, 0, cx, n)
    base.facets([(core & upper & left, (-0.5, 1)), (core & upper - left, (0.8, 0.8)),
                 (core - upper - left, (0.6, -0.8)), (core - upper & left, (-0.9, -0.4))], "teal", 3, 2)
    # fractures leaking resonance
    leaks = set()
    for pts in ([(cx - 3, cy - 2), (cx - 12, cy - 9), (cx - 22, cy - 12), (cx - 34, cy - 22)],
                [(cx + 4, cy + 3), (cx + 9, cy + 12), (cx + 7, cy + 20)],
                [(cx - 2, cy + 5), (cx - 10, cy + 14), (cx - 19, cy + 17)]):
        line = R.path(pts)
        leaks |= line
    base.fill(leaks, "teal", 5)
    base.fill(R.path([(cx - 2, cy - 4), (cx + 3, cy - 9)]) | R.path([(cx + 2, cy + 1), (cx + 6, cy - 3)]), "teal", 6)
    base.fill(R.circle(cx, cy, 2.5), "teal", 6)
    K.cracks(base, "reactor-c1", (96, 60), 18)
    K.cracks(base, "reactor-c2", (70, 100), 14)
    glow = glow_layer(n, leaks | R.circle(cx, cy, 6), "teal", 5, falloff=3, alpha=(120, 60, 25), core_alpha=170)
    return {"": base, "-glow": glow}


def stabilizer_pylon(alt="a"):
    """Torre de estabilização 2x2: stacked ancient rings narrowing to an emitter, braced by three fins."""
    n, cx, cy = 64, 32, 32
    base = R.Canvas(n, n)
    foot = R.regular(cx, cy, 34, 8, 22.5) & R.rect(0, 0, n, n)
    base.plate(foot, "dark", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(base, foot, "pylon", 0.12)
    for k in range(3):
        a = math.radians(90 + 120 * k)
        fin = R.thick(cx + 10 * math.cos(a), cy - 10 * math.sin(a), cx + 27 * math.cos(a), cy - 27 * math.sin(a), 5)
        K.raised(base, fin, "steel", 4, bevel=1)
        K.trace(base, [(int(cx + 12 * math.cos(a)), int(cy - 12 * math.sin(a))), (int(cx + 24 * math.cos(a)), int(cy - 24 * math.sin(a)))], node=False, groove=False)
    for r, ramp, idx in ((19, "dark", 4), (14, "dark", 5), (9.5, "steel", 4)):
        ringm = R.circle(cx, cy, r)
        K.raised(base, ringm, ramp, idx, bevel=2, shadow=2)
    base.fill(R.ring(cx, cy, 16.5, 15.5), "teal", 3)
    base.dome(R.circle(cx, cy, 6), "teal", 3, 5)
    glow = glow_layer(n, R.circle(cx, cy, 3) | R.ring(cx, cy, 16.5, 15.5), "teal", 5, falloff=2, alpha=(100, 45), core_alpha=140)
    return {"": base, "-glow": glow}


def data_terminal(alt="a"):
    """Terminal de dados 2x2: ancient console; slanted glyph screen, input slab and trailing data cables."""
    n = 64
    base = R.Canvas(n, n)
    slab = R.chamfer(2, 2, 60, 60, 6)
    base.plate(slab, "dark", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(base, slab, "terminal", 0.12)
    for y in (46, 52):
        K.pipe(base, 4, y, 20, y, width=3, ramp="dark", lo=1, hi=5)
    housing = R.chamfer(10, 8, 44, 34, 5)
    K.raised(base, housing, "dark", 5, bevel=2, shadow=2)
    screen = R.chamfer(14, 12, 36, 22, 3)
    base.fill(screen, "teal", 0)
    for i, y in enumerate(range(15, 32, 3)):
        lw = (30, 22, 26, 14, 28, 18)[i]
        for x in range(17, 17 + lw, 2 + (i % 2)):
            base.set((x, y), "teal", 3 + (1 if (x + y) % 5 == 0 else 0))
    base.fill(R.rect(17, 15, 4, 1), "teal", 5)
    keys = R.chamfer(20, 44, 30, 14, 3)
    K.raised(base, keys, "steel", 3, bevel=1)
    for x in range(23, 47, 4):
        for y in (47, 52):
            K.plate(base, R.rect(x, y, 3, 3), "dark", 4, bevel=1)
    K.lamp(base, 52, 48, "teal", size=1)
    glow = glow_layer(n, R.shrink(screen, 1), "teal", 3, falloff=1, alpha=(60,), core_alpha=70)
    return {"": base, "-glow": glow}


def recoverable_ruin(alt="a"):
    """Ruína industrial recuperável 3x3: a collapsed ancient machine with exposed gearing and conduits,
    flagged by a player salvage beacon and hazard tape."""
    n = 96
    base = R.Canvas(n, n)
    pad = R.chamfer(2, 2, 92, 92, 10)
    base.plate(pad, "dark", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(base, pad, "ruin-pad", 0.1, cell=7)
    shell = R.chamfer(10, 10, 76, 76, 12) - R.poly([(50, 10), (96, 10), (96, 56)])
    K.raised(base, shell, "dark", 4, bevel=2, shadow=3)
    K.panels(base, R.shrink(shell, 3), R.line(10, 48, 86, 48) | R.line(48, 10, 48, 86), "dark", 4)
    K.wear(base, shell, "ruin-shell", 0.16, cell=6)
    # exposed interior through the collapsed corner
    hole = R.poly([(52, 14), (82, 14), (82, 44)]) | R.circle(66, 30, 12)
    base.cavity(hole & R.shrink(pad, 2), "dark", 1, bevel=1, cast=2)
    g = K.gear(66, 30, 9, 7, 10, 0.5, 4)
    K.plate(base, g & hole, "rust", 3, bevel=1)
    base.fill(R.circle(66, 30, 2.5), "dark", 2)
    K.pipe(base, 54, 40, 80, 40, width=3, ramp="rust", lo=1, hi=5)
    base.fill(R.path([(57, 22), (60, 19)]) | R.path([(73, 41), (76, 44)]), "teal", 3)
    # debris
    for j in range(6):
        rnd = R.rng("ruin-debris", j)
        x, y = 54 + rnd.randint(0, 34), 52 + rnd.randint(0, 34) if j % 2 else 4 + rnd.randint(0, 8)
        chunk = R.chamfer(x, y, rnd.randint(3, 6), rnd.randint(3, 5), 1) & pad
        K.raised(base, chunk, "dark", 4, bevel=1)
    # salvage beacon and hazard tape (player marking)
    K.stripes(base, R.rect(14, 80, 44, 3), ("amber", 4), ("dark", 1), period=6, width=3)
    beacon = R.circle(22, 22, 6)
    K.raised(base, beacon, "steel", 4, bevel=1, shadow=2)
    base.dome(R.circle(22, 22, 3), "amber", 3, 5)
    K.cracks(base, "ruin-c", (20, 56), 16)
    glow = glow_layer(96, R.circle(22, 22, 2), "amber", 5, falloff=2, alpha=(90, 40), core_alpha=120)
    return {"": base, "-glow": glow}


def emblem():
    """Vesper emblem for the mod icon: a recovered ancient core clamped by frontier brackets."""
    n, cx, cy = 64, 32, 32
    c = R.Canvas(n, n)
    plate = R.regular(cx, cy, 31, 8, 22.5)
    c.plate(plate, "dark", 4, bevel=2, hi=1, lo=2, rim=1)
    K.glyph_ring(c, cx, cy, 24, "teal", 3, gaps=8, gap_len=0.3)
    ring = R.ring(cx, cy, 19, 15)
    K.plate(c, ring, "steel", 4, bevel=1)
    for k in range(4):
        clamp = R.rot90(R.chamfer(28, 4, 8, 9, 2), n, k)
        K.plate(c, clamp, "amber", 3, bevel=1)
    c.cavity(R.circle(cx, cy, 14.5), "dark", 1, bevel=1)
    core = R.regular(cx, cy, 11, 6, 30)
    left, upper = R.rect(0, 0, cx, n), R.rect(0, 0, n, cy)
    c.facets([(core & left - upper, (-1, -0.6)), (core & left & upper, (-0.6, 1)),
              (core & upper - left, (0.7, 1)), (core - left - upper, (1, -0.5))], "teal", 4, 1)
    c.fill(R.rect(cx - 1, cy - 1, 3, 3), "teal", 6)
    return c
