"""Campaign emblems: the sectors of Chapter One on the planet map and in the tech tree, and the chapter's own badge.

Every emblem sits on the same octagonal badge (ancient hull inside a steel rim, the expedition's frame around the
builders' metal) so the three sectors read as one set; the motif in the middle is the sector's story in one shape.
64x64 with a 6 px margin: the game draws a 5 px outline around sector icons.
"""
import math

import parts as K
import raster as R

N, C = 64, 32


def badge(c, rim_ramp="steel"):
    """The shared badge: a steel octagon frame around a recessed dark hull face."""
    outer = R.regular(C, C, 26, 8, 22.5)
    c.drop_shadow(outer, -1, 1)
    c.plate(outer, rim_ramp, 3, bevel=2, hi=2, lo=1, rim=1, edge_lo=1)
    face = R.regular(C, C, 21, 8, 22.5)
    c.cavity(face, "dark", 3, bevel=1, cast=1)
    for k in range(8):
        a = math.radians(22.5 + k * 45)
        K.rivet(c, round(C + 23.4 * math.cos(a)) - 1, round(C + 23.4 * math.sin(a)) - 1, "steel", 4)
    return face


def sector_first_awakening():
    """The derelict reactor from above: torn rings around a core that has started to burn again."""
    c = R.Canvas(N, N)
    badge(c)
    # three torn rings, each broken in a different place
    for r_out, r_in, gaps, phase in ((18, 15.5, 3, 0.2), (13, 11, 2, 1.3), (9, 7.5, 3, 2.1)):
        ring = R.ring(C, C, r_out, r_in)
        cut = set()
        for k in range(gaps):
            a = phase + k * 2 * math.pi / gaps
            cut |= R.thick(C, C, C + 22 * math.cos(a), C + 22 * math.sin(a), 1.6)
        c.plate(ring - cut, "steel", 4, bevel=1, hi=1, lo=1)
    # the core: amber, ringed in resonance
    c.fill(R.ring(C, C, 6.2, 5.2), "teal", 4)
    c.dome(R.circle(C, C, 5), "amber", 2, 5)
    return {"": c}


def sector_resonance_fault():
    """A fault line glowing teal, with crystal prisms breaking out of it."""
    c = R.Canvas(N, N)
    face = badge(c)
    crack = R.path([(14, 41), (22, 36), (27, 38), (33, 30), (38, 31), (44, 24), (50, 22)])
    glow = R.grow(crack, 1) & face
    c.fill(glow, "teal", 2)
    c.fill(crack & face, "teal", 5)
    #chunky prisms: a pointed top, a lit face and a shaded face
    for cx, cy, h, w in ((23, 35, 10, 4.5), (34, 28, 13, 5.5), (44, 23, 8, 4)):
        top = cy - h
        left = R.poly([(cx - w, cy + 1), (cx - w, top + w * 0.9), (cx, top), (cx, cy + 3)])
        right = R.poly([(cx, top), (cx + w, top + w * 0.9), (cx + w, cy + 1), (cx, cy + 3)])
        c.fill(left, "teal", 3)
        c.fill(right, "teal", 5)
        c.fill(R.line(cx, top + 1, cx, cy + 2), "teal", 6)
        c.fill(R.edge(left | right) - R.line(cx, top + 1, cx, cy + 2), "teal", 1)
    return {"": c}


def sector_buried_foundry():
    """A foundry gear half buried in dust, its furnace still lit."""
    c = R.Canvas(N, N)
    face = badge(c)
    gear = K.gear(C, C + 3, 15, 11, 10, tooth=0.45, angle=0.1)
    c.plate(gear & face, "steel", 4, bevel=1, hi=1, lo=1)
    c.cavity(R.circle(C, C + 3, 7), "dark", 2, bevel=1)
    c.dome(R.circle(C, C + 3, 4), "amber", 2, 5)
    # the dust line: everything under it is buried
    dust = {p for p in face if p[1] >= 38}
    c.fill(dust, "rust", 2)
    c.fill({p for p in dust if p[1] == 38}, "rust", 4)
    K.wear(c, dust, "foundry-dust", amount=0.18, lo=1, cell=3)
    # the archive's glyphs above it
    K.glyph_ring(c, C, C + 3, 19, ramp="teal", base=3, gaps=5, gap_len=0.4, phase=0.6)
    c.erase({p for p in R.ring(C, C + 3, 19.6, 18.4) if p[1] >= 38 or p not in face})
    return {"": c}


def sector_deep_signal():
    """Signal rings rising from below, around a chevron pointing down."""
    c = R.Canvas(N, N)
    face = badge(c, rim_ramp="dark")
    for r, base in ((18, 2), (13.5, 3), (9, 4)):
        c.fill(R.ring(C, C, r, r - 1.4) & face, "teal", base)
    chevron = R.thick(25, 27, 32, 35, 1.8) | R.thick(32, 35, 39, 27, 1.8)
    c.fill(chevron, "teal", 6)
    c.fill(R.circle(C, C - 6, 1.6), "teal", 5)
    return {"": c}


def chapter_one():
    """Chapter One: the basin's three sectors linked in a chain that ends in the signal below."""
    c = R.Canvas(N, N)
    face = badge(c, rim_ramp="steel")
    nodes = ((20, 22, "amber"), (33, 30, "teal"), (44, 22, "steel"))
    chain = R.thick(20, 22, 33, 30, 1.4) | R.thick(33, 30, 44, 22, 1.4)
    c.fill(chain & face, "steel", 5)
    c.fill(R.thick(33, 30, 33, 44, 1.2) & face, "teal", 3)
    for x, y, ramp in nodes:
        c.drop_shadow(R.circle(x, y, 4), -1, 1)
        c.dome(R.circle(x, y, 4), ramp, 2, 5)
    for r, base in ((6, 3), (3.5, 5)):
        c.fill(R.ring(C, 46, r, r - 1.2) & face, "teal", base)
    return {"": c}
