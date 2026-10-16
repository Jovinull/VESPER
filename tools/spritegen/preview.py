"""Preview helpers: nearest-neighbour upscaling and simulations of what Mindustry adds at runtime.

Nothing here is baked into mod sprites. Outlines, ore shadows and block shadows are simulated only so the
review sheets and the factory mock-up show sprites the way the game will.
"""
import math

from PIL import Image, ImageDraw, ImageFont

import palette
import raster as R

BG_NEUTRAL = (58, 58, 66, 255)
BG_DARK = (18, 19, 22, 255)
BG_LIGHT = (150, 150, 156, 255)
INK = (214, 222, 220, 255)
INK_DIM = (140, 150, 150, 255)

_font_cache = {}


def font(size=12, bold=False):
    key = (size, bold)
    if key not in _font_cache:
        names = ["consolab.ttf", "DejaVuSansMono-Bold.ttf"] if bold else ["consola.ttf", "DejaVuSansMono.ttf"]
        f = None
        for n in names:
            try:
                f = ImageFont.truetype(n, size)
                break
            except OSError:
                continue
        _font_cache[key] = f or ImageFont.load_default()
    return _font_cache[key]


def up(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def outline(img, color_hex, radius):
    """Approximation of arc.graphics.Pixmaps.outline: fills empty pixels within `radius` of opaque ones."""
    w, h = img.size
    pad = radius
    src = img
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    col = palette.hex_to_rgba(color_hex)
    sp = src.load()
    op = out.load()
    opaque = [(x, y) for y in range(h) for x in range(w) if sp[x, y][3] > 10]
    opaque_set = set(opaque)
    offs = [(dx, dy) for dx in range(-pad, pad + 1) for dy in range(-pad, pad + 1) if dx * dx + dy * dy <= radius * radius]
    for x, y in opaque:
        for dx, dy in offs:
            q = (x + dx, y + dy)
            if 0 <= q[0] < w and 0 <= q[1] < h and q not in opaque_set:
                op[q] = col
    out.alpha_composite(src)
    return out


def ore_shadow(img):
    """Replicates OreBlock.createIcons: 30% black shadow (width/8 - 1) pixels below each ore pixel."""
    w, h = img.size
    off = w // 8 - 1
    out = img.copy()
    sp, op = img.load(), out.load()
    for x in range(w):
        for y in range(off, h):
            if sp[x, y][3] == 0 and sp[x, y - off][3] != 0:
                op[x, y] = (0, 0, 0, int(255 * palette.ORE_SHADOW_ALPHA))
    return out


def checker(w, h, a=(52, 52, 60, 255), b=(64, 64, 72, 255), cell=8):
    img = Image.new("RGBA", (w, h), a)
    d = ImageDraw.Draw(img)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            if (x // cell + y // cell) % 2:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=b)
    return img


def floor_field(tiles_w, tiles_h, pick, seed=1):
    """Lay out 32px floor tiles. `pick(tx, ty, rnd)` returns a PIL image for that tile."""
    rnd = R.rng("field", seed)
    img = Image.new("RGBA", (tiles_w * 32, tiles_h * 32), (0, 0, 0, 255))
    for ty in range(tiles_h):
        for tx in range(tiles_w):
            img.alpha_composite(pick(tx, ty, rnd), (tx * 32, ty * 32))
    return img


def block_shadows(size_px, footprints, offset_tiles=0.5):
    """Mindustry draws building shadows from a 1 px-per-tile buffer with linear filtering, sampled half a
    tile toward the top right, so the shadow appears softly offset to the bottom left."""
    tw, th = size_px[0] // 32, size_px[1] // 32
    grid = [[0.0] * tw for _ in range(th)]
    for (tx, ty, s) in footprints:
        for j in range(ty, ty + s):
            for i in range(tx, tx + s):
                if 0 <= i < tw and 0 <= j < th:
                    grid[j][i] = 1.0
    shadow = Image.new("RGBA", size_px, (0, 0, 0, 0))
    sp = shadow.load()
    alpha = palette.BLOCK_SHADOW_ALPHA
    off = offset_tiles * 32
    for y in range(size_px[1]):
        gy = (y - off) / 32.0 - 0.5
        for x in range(size_px[0]):
            gx = (x + off) / 32.0 - 0.5
            x0, y0 = int(math.floor(gx)), int(math.floor(gy))
            fx, fy = gx - x0, gy - y0

            def g(i, j):
                return grid[j][i] if 0 <= i < tw and 0 <= j < th else 0.0

            v = (g(x0, y0) * (1 - fx) * (1 - fy) + g(x0 + 1, y0) * fx * (1 - fy) +
                 g(x0, y0 + 1) * (1 - fx) * fy + g(x0 + 1, y0 + 1) * fx * fy)
            if v > 0:
                sp[x, y] = (0, 0, 0, int(255 * alpha * v))
    return shadow


def label(draw, xy, text, size=12, fill=INK, bold=False):
    draw.text(xy, text, font=font(size, bold), fill=fill)


def contact_sheet(entries, scale, bg=BG_NEUTRAL, cols=8, cell=None, pad=10, title=None, caption=True, label_size=11):
    """entries: list of (name, PIL image). Grid with labels under each sprite."""
    if cell is None:
        cell = max(max(e[1].width, e[1].height) for e in entries) * scale
    lab_h = label_size + 6 if caption else 0
    top = 28 if title else 0
    rows = (len(entries) + cols - 1) // cols
    w = pad + cols * (cell + pad)
    h = top + pad + rows * (cell + lab_h + pad)
    sheet = Image.new("RGBA", (w, h), bg)
    d = ImageDraw.Draw(sheet)
    if title:
        label(d, (pad, 7), title, 14, bold=True)
    for k, (name, img) in enumerate(entries):
        cx = pad + (k % cols) * (cell + pad)
        cy = top + pad + (k // cols) * (cell + lab_h + pad)
        big = up(img, scale)
        sheet.alpha_composite(big, (cx + (cell - big.width) // 2, cy + (cell - big.height) // 2))
        if caption:
            label(d, (cx, cy + cell + 2), name[: max(6, cell // 7)], label_size, INK_DIM)
    return sheet


def add_glow(img, glow):
    """Additive blend, like DrawGlowRegion (Blending.additive)."""
    out = img.copy()
    op, gp = out.load(), glow.load()
    for y in range(min(img.height, glow.height)):
        for x in range(min(img.width, glow.width)):
            r, g, b, a = gp[x, y]
            if a:
                o = op[x, y]
                k = a / 255.0
                op[x, y] = (min(255, o[0] + int(r * k)), min(255, o[1] + int(g * k)), min(255, o[2] + int(b * k)), max(o[3], a))
    return out


def compose(layers, kind="block", outline_heads=True):
    """Flatten generated layers the way the game draws them. layers: {suffix: PIL image}."""
    if kind == "turret":
        base = layers["-base"].copy()
        head = layers[""]
        if outline_heads:
            head = outline(head, palette.TURRET_OUTLINE, 4)
        base.alpha_composite(head, ((base.width - head.width) // 2, (base.height - head.height) // 2))
        if "-heat" in layers:
            pass
        return base
    out = layers[""].copy()
    for k in ("-rotator", "-mid", "-top", "-team"):
        if k in layers:
            out.alpha_composite(layers[k], ((out.width - layers[k].width) // 2, (out.height - layers[k].height) // 2))
    if "-glow" in layers:
        out = add_glow(out, layers["-glow"])
    return out


# ----------------------------------------------------------------------------------------------- belts

D4 = ((1, 0), (0, 1), (-1, 0), (0, -1))  # Mindustry directions, y-up


def belt_blend(belts, x, y, sources=()):
    """Replicates Autotiler/Conveyor blending. belts: {(x, y): rotation}, y-up grid. `sources` are tiles of
    non-rotating blocks that output items (drills, crafters, routers): like in Conveyor.blends, a belt bends
    toward them as if they were feeding it. Returns (region index, yscale)."""
    rot = belts[(x, y)]

    def blends(direction):
        real = (rot - direction) % 4
        nx, ny = x + D4[real][0], y + D4[real][1]
        if (nx, ny) in sources:
            return True
        other = belts.get((nx, ny))
        return other is not None and (nx + D4[other][0], ny + D4[other][1]) == (x, y)

    b1, b2, b3 = blends(1), blends(2), blends(3)
    if b2 and b1 and b3:
        return 3, 1
    if b1 and b3:
        return 4, 1
    if b1 and b2:
        return 2, 1
    if b3 and b2:
        return 2, -1
    if b1:
        return 1, -1
    if b3:
        return 1, 1
    return 0, 1


def place_belt(img, regions, belts, x, y, frame, origin_h, sources=()):
    """Draw one belt at grid (x, y) (y-up) onto img whose height in tiles is origin_h."""
    idx, ys = belt_blend(belts, x, y, sources)
    tile = regions[(idx, frame)]
    if ys < 0:
        tile = tile.transpose(Image.FLIP_TOP_BOTTOM)
    tile = tile.rotate(90 * belts[(x, y)])
    img.alpha_composite(tile, (x * 32, (origin_h - 1 - y) * 32))


# ----------------------------------------------------------------------------------------------- units

SHARDED = (255, 211, 127)   # Team.sharded color, used to tint cells in previews
CRUX = (243, 69, 88)        # Team.crux (enemy) color


def tint(img, rgb):
    """Multiply tint, like Draw.color(teamColor) applied to a cell region."""
    out = img.copy()
    p = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = p[x, y]
            if a:
                p[x, y] = (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a)
    return out


def _paste_rot(dst, img, cx, cy, angle):
    r = img.rotate(angle, resample=Image.NEAREST, expand=True)
    dst.alpha_composite(r, (int(cx - r.width / 2), int(cy - r.height / 2)))


def compose_unit(layers, team=SHARDED, rotation=0, outline_on=True):
    """Approximate in-game look of a unit: legs, outlined body and team-tinted cell. rotation in degrees
    (0 = facing up)."""
    body = layers[""]
    pad = 40
    size = max(body.width, body.height) + pad * 2
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    cx = cy = size / 2

    def ol(img):
        return outline(img, palette.UNIT_OUTLINE, 3) if outline_on else img

    if "-leg" in layers and "-foot" in layers:
        # legged unit: four legs splayed diagonally
        for k, ang in enumerate((40, 140, 220, 320)):
            a = math.radians(ang + rotation)
            base_len, leg_len = 16, 18
            jx, jy = cx + math.cos(a) * base_len, cy - math.sin(a) * base_len
            fx, fy = jx + math.cos(a - 0.5 * (1 if k % 3 == 0 else -1)) * leg_len, jy - math.sin(a - 0.5 * (1 if k % 3 == 0 else -1)) * leg_len
            lb = layers.get("-leg-base", layers["-leg"])
            _paste_rot(out, ol(lb), (cx + jx) / 2, (cy + jy) / 2, math.degrees(a))
            _paste_rot(out, ol(layers["-leg"]), (jx + fx) / 2, (jy + fy) / 2, math.degrees(math.atan2(-(fy - jy), fx - jx)))
            _paste_rot(out, ol(layers["-foot"]), fx, fy, 0)
            if "-joint" in layers:
                _paste_rot(out, ol(layers["-joint"]), jx, jy, 0)
    elif "-leg" in layers:
        if "-base" in layers:
            _paste_rot(out, layers["-base"], cx, cy, rotation)
        for side, off in ((1, -5), (-1, 5)):
            leg = layers["-leg"] if side > 0 else layers["-leg"].transpose(Image.FLIP_LEFT_RIGHT)
            _paste_rot(out, ol(leg), cx, cy + off, rotation)
    _paste_rot(out, ol(body), cx, cy, rotation)
    if "-weapon" in layers:
        for side in (-1, 1):
            g = ol(layers["-weapon"])
            if side < 0:
                g = g.transpose(Image.FLIP_LEFT_RIGHT)
            _paste_rot(out, g, cx + side * body.width * 0.36, cy - 4, rotation)
    if "-cell" in layers:
        _paste_rot(out, tint(layers["-cell"], team), cx, cy, rotation)
    return out
