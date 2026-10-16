"""Logistics: belt, router, junction, bridge, conduit and the resonance link.

Belt and conduit shapes follow Mindustry's Autotiler blend indices (output always to the right):
0 straight (input from back), 1 input from top only, 2 back + bottom, 3 back + top + bottom, 4 top + bottom.
The game mirrors shapes 1 and 2 vertically for the opposite side.
"""
import math

import parts as K
import raster as R
from families.industry import glow_layer

N = 32
SHAPES = {0: ("back",), 1: ("top",), 2: ("back", "bottom"), 3: ("back", "top", "bottom"), 4: ("top", "bottom")}


def _rails(inputs, thickness=4):
    """Wall mask for a belt/conduit tile given its open input sides."""
    t = thickness
    rails = set()
    closed_top = "top" not in inputs
    closed_bottom = "bottom" not in inputs
    closed_left = "back" not in inputs
    if closed_top:
        rails |= R.rect(0, 0, N, t)
    if closed_bottom:
        rails |= R.rect(0, N - t, N, t)
    if closed_left:
        rails |= R.rect(0, 0, t, N)
    cap = R.rect(0, 0, t, t) - {(t - 1, t - 1), (t - 2, t - 1), (t - 1, t - 2)}
    corners = {
        "tl": (closed_top or closed_left, cap),
        "tr": (closed_top, R.flip_x(cap, N)),
        "bl": (closed_bottom or closed_left, R.flip_y(cap, N)),
        "br": (closed_bottom, R.flip_y(R.flip_x(cap, N), N)),
    }
    for covered, m in corners.values():
        if not covered:
            rails |= m
    return rails


def _round_outer(rails, inputs):
    """Curve tiles (left wall + one side wall) get a rounded outer corner like the vanilla belt."""
    out = set(rails)
    if "back" not in inputs and ("top" not in inputs) != ("bottom" not in inputs):
        corner_y = 0 if "top" not in inputs else N - 1
        for p in list(out):
            if abs(p[1] - corner_y) + p[0] < 4:
                out.discard(p)
    return out


def _lanes(inputs):
    """Flow-coordinate functions for every lane feeding the output on the right."""
    lanes = []
    if "back" in inputs:
        lanes.append(lambda x, y: (x + 0.5, y + 0.5 - N / 2))
    for side, cy in (("top", 0.0), ("bottom", float(N))):
        if side in inputs:
            def lane(x, y, cy=cy):
                dx, dy = x + 0.5 - N, y + 0.5 - cy
                r = math.hypot(dx, dy)
                th = abs(math.atan2(dy, dx))
                s = (math.pi - th) / (math.pi / 2) * N
                return s, r - N / 2
            lanes.append(lane)
    return lanes


def _flat_walls(c, walls, channel, inputs, ramp="steel", base=4, outer=5, lip=2):
    """Wall shading for autotiled tiles. The game mirrors and rotates belt/conduit regions, so a baked light
    direction would flip from tile to tile and make neighbouring rails look offset. Like the vanilla belt, the
    walls carry no light direction: flat body, highlight on the outer tile edge, darker lip against the channel.
    The result is mirror-symmetric, so every flip looks identical and caps merge with straight walls."""
    c.fill(walls, ramp, base)
    left_wall = "back" not in inputs
    for (x, y) in walls:
        if any((x + dx, y + dy) in channel for dx, dy in R.N4):
            c.set((x, y), ramp, lip)
        elif y in (0, N - 1) or (x == 0 and left_wall):
            c.set((x, y), ramp, outer)


def belt_tile(shape, frame):
    inputs = SHAPES[shape]
    c = R.Canvas(N, N)
    rails = _round_outer(_rails(inputs), inputs)
    bed = R.rect(0, 0, N, N) - rails
    c.fill(bed, "dark", 2)
    lanes = _lanes(inputs)
    for p in bed:
        for lane in lanes:
            s, t = lane(*p)
            if abs(t) > 11.6:
                continue
            u = (s + 0.55 * abs(t) - 4 * frame) % 16
            if u < 3:
                c.set(p, "steel", 3 if u < 2 else 2)
            elif abs(abs(t) - 11) < 0.6:
                c.set(p, "dark", 1)
    _flat_walls(c, rails, bed, inputs)
    # rivets on full-length rails, mirrored top/bottom so flipped tiles match
    for y0, y in ((0, 1), (N - 4, N - 2)):
        if R.rect(0, y0, N, 4) <= rails:
            for x in range(5, N, 8):
                c.set((x, y), "steel", 6)
    return c


def transit_belt():
    return {"-%d-%d" % (s, f): belt_tile(s, f) for s in range(5) for f in range(4)}


def conduit_tile(shape):
    """Industrial conduit top: heavy pipe walls with flanges; the channel stays transparent so the game
    can draw the liquid (bottom region) underneath."""
    inputs = SHAPES[shape]
    c = R.Canvas(N, N)
    walls = _round_outer(_rails(inputs, 6), inputs)
    channel = R.rect(0, 0, N, N) - walls
    _flat_walls(c, walls, channel, inputs, base=3, outer=5, lip=1)
    # flanges: lighter collars across full-length walls, mirrored top/bottom
    for y in (0, N - 6):
        if R.rect(0, y, N, 6) <= walls:
            for x in (6, 22):
                flange = R.rect(x, y, 4, 6)
                c.fill(flange, "steel", 5)
                c.fill({q for q in flange if any((q[0] + dx, q[1] + dy) in channel for dx, dy in R.N4)}, "steel", 2)
                c.set((x + 1, y + 2 if y == 0 else y + 3), "amber", 4)
                c.set((x + 2, y + 2 if y == 0 else y + 3), "amber", 4)
    return c


def industrial_conduit():
    out = {"-top-%d" % s: conduit_tile(s) for s in range(5)}
    cap = R.Canvas(N, N)
    flange = R.chamfer(0, 2, 7, 28, 2)
    cap.plate(flange, "steel", 4, bevel=1, hi=2, lo=2, rim=1)
    for y in (6, 15, 24):
        K.stud(cap, 3, y, "steel", 6)
    out["-cap"] = cap
    return out


def cargo_router():
    """Distribuidor: square hub that pushes cargo outward through four gated mouths."""
    c = R.Canvas(N, N)
    K.deck(c, N, 2, "steel", 4, inset=2, layout="x")
    for k in range(4):
        mouth = R.rot90(R.rect(12, 0, 8, 6), N, k)
        c.cavity(mouth, "dark", 2, bevel=1)
        arrow = R.rot90(R.poly([(13, 6), (16, 2.5), (19, 6)]), N, k)
        c.fill(arrow, "amber", 4)
    hub = R.chamfer(10, 10, 12, 12, 3)
    K.raised(c, hub, "dark", 4, bevel=1)
    c.fill(R.rect(14, 14, 4, 4), "dark", 2)
    K.lamp(c, 16, 15, "amber", size=1)
    return {"": c}


def cargo_junction():
    """Cruzamento: two belt lanes cross under a bolted bridge plate."""
    c = R.Canvas(N, N)
    K.frame(c, N, "steel", 3, chamfer=2, bevel=2)
    lanes = R.rect(9, 0, 14, N) | R.rect(0, 9, N, 14)
    c.cavity(lanes, "dark", 2, bevel=1)
    for k in range(4):
        for off in (2, 6):
            chev = R.rot90(R.path([(12, off + 2), (16, off), (20, off + 2)]), N, k)
            c.fill(chev, "steel", 3)
    plate = R.chamfer(11, 11, 10, 10, 2)
    K.raised(c, plate, "steel", 4, bevel=1)
    for p in ((12, 12), (18, 12), (12, 18), (18, 18)):
        K.stud(c, p[0] + 1, p[1] + 1, "steel", 6)
    return {"": c}


def cargo_bridge():
    """Ponte logística: pylon mount, end clamp, deck texture and direction arrow."""
    base = R.Canvas(N, N)
    K.deck(base, N, 3, "steel", 4, inset=2, layout="x")
    pylon = R.circle(16, 16, 9)
    base.drop_shadow(pylon, -1, 1)
    K.plate(base, pylon, "dark", 4, bevel=2)
    base.cavity(R.circle(16, 16, 5), "dark", 2, bevel=1)
    base.dome(R.circle(16, 16, 3), "amber", 2, 5)
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        K.stud(base, int(16 + 7 * math.cos(a)), int(16 + 7 * math.sin(a)), "steel", 5)

    end = R.Canvas(N, N)
    clamp = R.chamfer(7, 22, 18, 10, 3, corners=("tl", "tr"))
    end.plate(clamp, "steel", 4, bevel=1, hi=2, lo=2)
    end.fill(R.rect(10, 26, 12, 2), "dark", 2)
    K.stud(end, 9, 29, "steel", 6)
    K.stud(end, 22, 29, "steel", 6)

    bridge = R.Canvas(N, N)
    deck = R.rect(0, 5, N, 22)
    bridge.fill(deck, "steel", 3)
    bridge.fill(R.rect(0, 5, N, 2), "steel", 5)
    bridge.fill(R.rect(0, 25, N, 2), "steel", 1)
    bridge.fill(R.rect(0, 9, N, 14), "dark", 3)
    for x in range(0, N, 8):
        bridge.fill(R.rect(x, 9, 2, 14), "steel", 2)
    bridge.fill(R.rect(0, 15, N, 2), "amber", 3)

    arrow = R.Canvas(N, N)
    arr = R.poly([(13, 8), (21, 16), (13, 24), (13, 20), (17, 16), (13, 12)])
    arrow.fill(arr, "amber", 4)
    arrow.fill(R.edge(arr) & R.shift(arr, 0, 1) - R.shift(arr, 0, -1), "amber", 2)
    return {"": base, "-end": end, "-bridge": bridge, "-arrow": arrow}


def resonance_link():
    """Conector de ressonância: ancient pylon that relays resonance through a teal beam."""
    c = R.Canvas(N, N)
    plinth = R.chamfer(1, 1, 30, 30, 8)
    c.plate(plinth, "dark", 4, bevel=2, hi=1, lo=2, rim=1)
    K.wear(c, {p for p in plinth if p[0] - p[1] < -10}, "link", 0.15)
    for k in range(4):
        prong = R.rot90(R.poly([(14, 2), (18, 2), (17, 9), (15, 9)]), N, k)
        c.plate(prong, "steel", 4, bevel=1, hi=2, lo=2)
        K.trace(c, [tuple(q) for q in sorted(R.rot90({(16, 10), (16, 12)}, N, k))], node=False, groove=False)
    ring = R.ring(16, 16, 8, 6)
    c.plate(ring, "dark", 5, bevel=1, hi=1, lo=2)
    c.dome(R.circle(16, 16, 5.5), "teal", 2, 5)
    glow = glow_layer(N, R.circle(16, 16, 2.5), "teal", 6, falloff=3, alpha=(110, 60, 30), core_alpha=150)

    laser = R.Canvas(4, 48)
    profile = [(10, 40), (13, 80), (16, 140), (19, 200), (21, 255)]
    for y in range(48):
        d = abs(y - 23.5)
        a = 0
        for limit, alpha in reversed(profile):
            if d <= 24 - limit:
                a = alpha
        if a:
            for x in range(4):
                laser.set((x, y), "pale", 6 if a == 255 else 5, a)
    end = R.Canvas(72, 72)
    for r, idx, a in ((22, 5, 60), (17, 5, 120), (12, 6, 200), (8, 6, 255)):
        end.fill(R.circle(36, 36, r), "pale", idx, a)
    return {"": c, "-glow": glow, "-laser": laser, "-laser-end": end}
