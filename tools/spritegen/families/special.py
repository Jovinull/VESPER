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
    # restored state: the glyph ring and the whole core light up (drawn additively once the reactor runs)
    lit = R.ring(cx, cy, 28.5, 26.5) - R.poly([(cx, cy), (cx - 60, cy + 4), (cx - 20, cy - 60)])
    active = glow_layer(n, lit | core | leaks, "teal", 5, falloff=3, alpha=(110, 55, 22), core_alpha=150)
    return {"": base, "-glow": glow, "-active": active}


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


# --- industrial archaeology (0.4): installations that come back, or come apart, and the rig that finds them out

def ancient_recoverer(alt="a"):
    """Recuperador antigo 3x3, as found, restored and stripped. The found state is the approved industrial ruin
    (same generator); restored, the shell closes over a glass-domed rotor fed by an intake and lit conduits; dismantled,
    only the scarred pad and empty sockets remain."""
    found = recoverable_ruin(alt)
    n, cx, cy = 96, 48, 48
    pad = R.chamfer(2, 2, 92, 92, 10)

    restored = R.Canvas(n, n)
    restored.plate(pad, "dark", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(restored, pad, "recoverer-pad", 0.05, cell=7)
    shell = R.chamfer(10, 10, 76, 76, 12)
    K.raised(restored, shell, "dark", 4, bevel=2, shadow=3)
    K.panels(restored, R.shrink(shell, 3), R.line(10, 48, 86, 48) | R.line(48, 10, 48, 86), "dark", 4)
    # intake hopper (top-left) and output port (bottom-right), the conduits between them lit again
    hopper = R.chamfer(14, 14, 22, 22, 4)
    K.raised(restored, hopper, "steel", 4, bevel=2, shadow=2)
    restored.cavity(R.shrink(hopper, 5), "dark", 1, bevel=1, cast=2)
    port = R.chamfer(60, 60, 22, 22, 4)
    K.raised(restored, port, "steel", 4, bevel=2, shadow=2)
    restored.fill(R.rect(66, 66, 10, 10), "teal", 3)
    for path in ([(36, 25), (44, 25), (44, 36)], [(60, 71), (52, 71), (52, 60)], [(25, 36), (25, 70), (36, 70)]):
        K.trace(restored, path, node=True, groove=True)
    # the rotor chamber: a dome window, the rotor itself is the -rotator layer
    restored.cavity(R.circle(cx, cy, 15), "dark", 1, bevel=1, cast=2)
    restored.fill(R.ring(cx, cy, 15.5, 14.5), "steel", 5)
    restored.fill(R.ring(cx, cy, 12.5, 11.8), "teal", 2)
    for k in range(8):
        a = 2 * math.pi * k / 8
        K.rivet(restored, int(cx + 18 * math.cos(a)), int(cy + 18 * math.sin(a)), ramp="steel", base=5)
    K.lamp(restored, 80, 18, "teal", size=1)
    rotor = R.Canvas(n, n)
    K.plate(rotor, K.gear(cx, cy, 11, 8, 8, 0.5), "steel", 4, bevel=1)
    rotor.fill(R.circle(cx, cy, 4), "teal", 4)
    rotor.fill(R.circle(cx, cy, 1.5), "teal", 6)
    rglow = glow_layer(n, R.circle(cx, cy, 3) | R.rect(66, 66, 10, 10) | R.ring(cx, cy, 12.5, 11.8), "teal", 5, falloff=2,
                       alpha=(90, 40), core_alpha=130)

    husk = R.Canvas(n, n)
    husk.plate(pad, "dark", 2, bevel=2, hi=1, lo=2, rim=1)
    K.wear(husk, pad, "husk-pad", 0.22, cell=6)
    # empty sockets where the shell was bolted, and the torn-out rotor bed
    for (x, y) in ((14, 14), (60, 14), (14, 60), (60, 60)):
        husk.cavity(R.chamfer(x, y, 22, 22, 3), "dark", 1, bevel=1, cast=1)
    husk.cavity(R.circle(cx, cy, 13), "dark", 0, bevel=1, cast=2)
    husk.fill(R.ring(cx, cy, 13.5, 12.5), "rust", 2)
    for j in range(9):
        rnd = R.rng("husk-debris", j)
        x, y = 6 + rnd.randint(0, 80), 6 + rnd.randint(0, 80)
        chunk = R.chamfer(x, y, rnd.randint(3, 6), rnd.randint(2, 5), 1) & R.shrink(pad, 2)
        K.raised(husk, chunk, "rust" if j % 3 == 0 else "dark", 3, bevel=1)
    K.cracks(husk, "husk-c1", (30, 80), 18)
    K.cracks(husk, "husk-c2", (78, 40), 14)
    return {"": found[""], "-glow": found["-glow"], "-restored": restored, "-restored-glow": rglow, "-rotator": rotor, "-dismantled": husk}


def archive_terminal(alt="a"):
    """Terminal de arquivo 2x2: the approved data terminal design (same generator), now an installation to survey."""
    return data_terminal(alt)


def survey_rig(alt="a"):
    """Sonda de investigação 2x2: a frontier tripod rig: hazard-marked base with circuit hopper and cable spool, a
    sensor dish on a slewing ring (the -dish layer turns) and the scan lens (-glow)."""
    n, cx, cy = 64, 32, 32
    base = R.Canvas(n, n)
    slab = R.chamfer(3, 3, 58, 58, 8)
    base.plate(slab, "steel", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(base, slab, "rig", 0.08)
    # three outrigger feet
    for k in range(3):
        a = math.radians(210 + 120 * k)
        leg = R.thick(cx, cy, cx + 26 * math.cos(a), cy - 26 * math.sin(a), 5)
        K.raised(base, leg & slab, "dark", 4, bevel=1)
        K.rivet(base, int(cx + 23 * math.cos(a)), int(cy - 23 * math.sin(a)), ramp="steel", base=5)
    K.stripes(base, R.rect(8, 54, 48, 3), ("amber", 4), ("dark", 1), period=6, width=3)
    hopper = R.chamfer(6, 6, 14, 14, 3)
    K.raised(base, hopper, "dark", 4, bevel=1, shadow=1)
    base.fill(R.rect(9, 9, 8, 8), "teal", 2)
    spool = R.circle(51, 13, 6)
    K.raised(base, spool, "rust", 3, bevel=1, shadow=1)
    base.fill(R.ring(51, 13, 3.5, 2.5), "dark", 2)
    ring = R.circle(cx, cy, 13)
    K.raised(base, ring, "dark", 4, bevel=2, shadow=2)
    base.fill(R.ring(cx, cy, 11.5, 10.5), "steel", 5)
    K.lamp(base, 12, 46, "amber", size=1)
    dish = R.Canvas(n, n)
    bowl = R.circle(cx, cy - 3, 9) - R.circle(cx, cy - 7, 7)
    K.plate(dish, R.circle(cx, cy, 6), "steel", 4, bevel=1)
    K.plate(dish, bowl, "steel", 5, bevel=1)
    dish.fill(R.thick(cx, cy, cx, cy - 14, 2), "dark", 3)
    dish.fill(R.circle(cx, cy - 14, 2), "teal", 4)
    lens = glow_layer(n, R.circle(cx, cy - 14, 1.5) | R.rect(9, 9, 8, 8), "teal", 5, falloff=2, alpha=(90, 40), core_alpha=140)
    return {"": base, "-dish": dish, "-glow": lens}


def containment_field(alt="a"):
    """Campo de contenção 2x2: a player-built emitter: braced foot with a core cartridge slot, four field prongs
    on the diagonals and a stacked emitter column with its lens; the lens and prong tips light (-glow) while the
    field holds, the status lamp sits on the upper deck (drawn by the game)."""
    n, cx, cy = 64, 32, 32
    base = R.Canvas(n, n)
    K.foundation(base, n)
    foot = R.regular(cx, cy, 27, 8, 22.5)
    K.raised(base, foot, "steel", 3, bevel=2, shadow=2)
    K.wear(base, foot, "field", 0.08)
    tips = set()
    for k in range(4):
        a = math.radians(45 + 90 * k)
        prong = R.thick(cx + 9 * math.cos(a), cy - 9 * math.sin(a), cx + 25 * math.cos(a), cy - 25 * math.sin(a), 5)
        K.raised(base, prong, "dark", 4, bevel=1)
        tx, ty = int(round(cx + 24 * math.cos(a))), int(round(cy - 24 * math.sin(a)))
        tip = R.circle(tx + 0.5, ty + 0.5, 2.2)
        base.fill(tip, "teal", 3)
        tips |= tip
    # core cartridge slot (bottom) under a hazard band
    slot = R.chamfer(24, 47, 16, 9, 2)
    K.raised(base, slot, "dark", 4, bevel=1)
    base.fill(R.rect(27, 49, 10, 4), "teal", 2)
    K.stripes(base, R.rect(22, 57, 20, 2), ("amber", 4), ("dark", 1), period=6, width=3)
    # lamp pad on the upper deck (the status lamp is drawn there)
    K.raised(base, R.chamfer(28, 7, 8, 6, 1), "dark", 3, bevel=1)
    # emitter column: stacked rings narrowing to the lens
    for r, ramp, idx in ((15, "dark", 4), (11, "steel", 4), (7.5, "dark", 5)):
        K.raised(base, R.circle(cx, cy, r), ramp, idx, bevel=2, shadow=2)
    base.fill(R.ring(cx, cy, 13.2, 12.4), "teal", 3)
    base.dome(R.circle(cx, cy, 5), "teal", 2, 5)
    glow = glow_layer(n, R.circle(cx, cy, 3.5) | R.ring(cx, cy, 13.2, 12.4) | tips, "teal", 5, falloff=2, alpha=(100, 45),
                      core_alpha=150)
    return {"": base, "-glow": glow}


def works_relay(alt="a"):
    """Relé das obras 3x3: the builders' power relay, still wired below: a worn octagonal hull, four conductor
    posts on the diagonals tied by resonance traces to a deep bus well, and a capacitor crown (-rotator) that turns
    while the relay is fed; the bus and the posts light up (-glow)."""
    n, cx, cy = 96, 48, 48
    base = R.Canvas(n, n)
    hull = R.regular(cx, cy, 46, 8, 22.5) & R.rect(0, 0, n, n)
    base.plate(hull, "dark", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(base, hull, "relay", 0.12)
    K.cracks(base, "relay", (14, 70), 9)
    posts = set()
    for k in range(4):
        a = math.radians(45 + 90 * k)
        px, py = cx + 31 * math.cos(a), cy - 31 * math.sin(a)
        post = R.regular(px, py, 7, 6, 0)
        K.raised(base, post, "dark", 5, bevel=1, shadow=2)
        base.dome(R.circle(px, py, 3), "teal", 2, 5)
        posts |= R.circle(px, py, 2)
        K.trace(base, [(int(px - 7 * math.cos(a)), int(py + 7 * math.sin(a))), (int(cx + 21 * math.cos(a)), int(cy - 21 * math.sin(a)))], node=False)
    for k in range(4):
        tab = R.rot90(R.chamfer(42, 4, 12, 8, 2), n, k)
        K.raised(base, tab, "steel", 3, bevel=1)
        base.fill(R.rot90(R.rect(45, 6, 6, 3), n, k), "teal", 2)
    K.raised(base, R.circle(cx, cy, 22), "dark", 4, bevel=2, shadow=2)
    K.well(base, R.circle(cx, cy, 16), "dark", 1, bevel=1, cast=1)
    base.fill(R.ring(cx, cy, 11, 9.5), "teal", 2)
    base.fill(R.circle(cx, cy, 5), "teal", 1)

    rot = R.Canvas(n, n)
    crown = R.ring(cx, cy, 15, 12)
    K.plate(rot, crown, "steel", 3, bevel=1)
    for k in range(6):
        a = 2 * math.pi * k / 6
        cell = R.chamfer(int(cx + 13.5 * math.cos(a)) - 2, int(cy + 13.5 * math.sin(a)) - 2, 5, 5, 1)
        K.plate(rot, cell, "copper", 3, bevel=1)
    rot.fill(R.circle(cx, cy, 3), "teal", 4)

    glow = glow_layer(n, R.circle(cx, cy, 4) | R.ring(cx, cy, 11, 9.5) | posts, "teal", 5, falloff=3, alpha=(110, 60, 30), core_alpha=150)
    return {"": base, "-rotator": rot, "-glow": glow}


def signal_node(alt="a"):
    """Nó de sinal 3x3 (0.7): a relay of the deep network: a worn octagonal plinth, four conduit shoes on the axes
    (where the signal leaves), a crystal emitter in a deep socket; the emitter crown (-rotator) turns while it works
    and the conduits and the crystal light up (-glow)."""
    n, cx, cy = 96, 48, 48
    base = R.Canvas(n, n)
    plinth = R.regular(cx, cy, 46, 8, 22.5) & R.rect(0, 0, n, n)
    base.plate(plinth, "dark", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(base, plinth, "signal-node", 0.1)
    lines = set()
    for k in range(4):
        shoe = R.rot90(R.chamfer(40, 3, 16, 12, 3), n, k)
        K.raised(base, shoe, "dark", 5, bevel=1, shadow=2)
        slot = R.rot90(R.rect(46, 5, 4, 8), n, k)
        base.fill(slot, "teal", 2)
        lines |= slot
        K.trace(base, [tuple(q) for q in sorted(R.rot90({(48, 15), (48, 24)}, n, k))], node=False)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        px, py = cx + 30 * math.cos(a), cy - 30 * math.sin(a)
        K.raised(base, R.regular(px, py, 5, 6, 0), "steel", 3, bevel=1)
        K.stud(base, int(px), int(py), "steel", 6)
    K.raised(base, R.circle(cx, cy, 21), "dark", 4, bevel=2, shadow=2)
    K.well(base, R.circle(cx, cy, 15), "dark", 1, bevel=1, cast=1)
    core = R.regular(cx, cy, 8, 6, 30)
    left, upper = R.rect(0, 0, cx, n), R.rect(0, 0, n, cy)
    base.facets([(core & left - upper, (-1, -0.6)), (core & left & upper, (-0.6, 1)),
                 (core & upper - left, (0.7, 1)), (core - left - upper, (1, -0.5))], "teal", 2, 1)

    rot = R.Canvas(n, n)
    crown = R.ring(cx, cy, 19, 15.5)
    K.plate(rot, crown, "steel", 3, bevel=1)
    for k in range(3):
        a = 2 * math.pi * k / 3
        fin = R.thick(cx + 12 * math.cos(a), cy + 12 * math.sin(a), cx + 21 * math.cos(a), cy + 21 * math.sin(a), 4)
        K.plate(rot, fin, "dark", 5, bevel=1)
        rot.set((int(cx + 21 * math.cos(a)), int(cy + 21 * math.sin(a))), "teal", 5)

    glow = glow_layer(n, R.circle(cx, cy, 4) | lines, "teal", 5, falloff=3, alpha=(110, 60, 30), core_alpha=160)
    return {"": base, "-rotator": rot, "-glow": glow}


def signal_terminal(alt="a"):
    """Terminal de controle 4x4 (0.7): the deep network's control console: a heavy ancient frame, four conduits
    running out to its edges (towards the relays), glyph panels on three sides and a round display well in the
    middle; the panels and the display light up (-glow) once it is surveyed and while it synchronizes."""
    n, cx, cy = 128, 64, 64
    base = R.Canvas(n, n)
    frame = R.chamfer(2, 2, n - 4, n - 4, 14)
    base.plate(frame, "dark", 3, bevel=2, hi=1, lo=2, rim=1)
    K.wear(base, frame, "signal-terminal", 0.1)
    K.cracks(base, "signal-terminal", (18, 100), 10)
    conduits = set()
    for k in range(4):
        duct = R.rot90(R.rect(58, 4, 12, 30), n, k)
        K.raised(base, duct, "dark", 4, bevel=1, shadow=2)
        line = R.rot90(R.rect(62, 6, 4, 26), n, k)
        base.fill(line, "teal", 2)
        conduits |= line
    panels = set()
    for k in (1, 2, 3):
        pad = R.rot90(R.chamfer(14, 14, 30, 22, 3), n, k)
        K.raised(base, pad, "dark", 5, bevel=1, shadow=2)
        screen = R.rot90(R.rect(17, 17, 24, 16), n, k)
        base.cavity(screen, "dark", 1, bevel=1)
        for gx in range(19, 39, 5):
            glyph = R.rot90(R.rect(gx, 21, 3, 2) | R.rect(gx + 1, 25, 2, 3), n, k)
            base.fill(glyph, "teal", 3)
            panels |= glyph
    for (x, y) in ((16, 110), (110, 110), (110, 16), (16, 16)):
        K.lamp(base, x, y, "amber", size=2)
    K.raised(base, R.circle(cx, cy, 26), "dark", 4, bevel=2, shadow=2)
    base.fill(R.ring(cx, cy, 23, 21.5), "steel", 4)
    K.well(base, R.circle(cx, cy, 19), "dark", 1, bevel=1, cast=1)
    K.glyph_ring(base, cx, cy, 15, "teal", 3, gaps=8, gap_len=0.3)
    base.dome(R.circle(cx, cy, 7), "teal", 2, 5)
    glow = glow_layer(n, R.circle(cx, cy, 5) | R.ring(cx, cy, 15.5, 14.5) | conduits | panels, "teal", 5, falloff=3, alpha=(110, 60, 30), core_alpha=150)
    return {"": base, "-glow": glow}
