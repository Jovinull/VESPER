"""Review sheets for art direction (written outside the mod, e.g. into Vesper-Docs/previews).

    python tools/spritegen/sheets.py OUT_DIR [--stencil PATH_TO_VANILLA_EDGE_STENCIL]

All upscaling is nearest-neighbour. Runtime effects the game adds (turret/unit outlines, ore shadows, block
shadows, team tint, additive glow, battery charge fill) are simulated so sprites read as they will in game.
"""
import argparse
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from PIL import Image, ImageDraw  # noqa: E402

import catalog  # noqa: E402
import palette  # noqa: E402
import preview as P  # noqa: E402

FAMILY_TITLES = [
    ("terrain", "Terreno — The Silent Basin"), ("ores", "Minérios"), ("items", "Itens"),
    ("logistics", "Logística"), ("extraction", "Extração"), ("industry", "Indústria e energia"),
    ("defense", "Defesa"), ("units", "Unidades"), ("special", "Estruturas especiais"), ("effects", "Efeitos"),
]
CHARGE = (248, 194, 102)   # DrawPower empty light color, used to show the accumulator windows
LIQUID = (90, 150, 200)


def images(layers):
    return {k: v.image() for k, v in layers.items()}


def game_look(e, alt=None, team=None):
    """One flattened image that shows an entry the way the game draws it."""
    return look_from_images(e, images(e.layers(alt)), team)


def look_from_images(e, imgs, team=None):
    if e.kind == "floor":
        return imgs["1"]
    if e.kind == "ore":
        return P.ore_shadow(imgs["1"])
    if e.kind in ("item", "effect"):
        return imgs[""]
    if e.kind == "unit":
        hostile = e.name in ("ancient-sentinel", "rust-crawler")
        return P.compose_unit(imgs, team or (P.CRUX if hostile else P.SHARDED))
    if e.kind == "turret":
        return P.compose(imgs, "turret")
    if e.name == "transit-belt":
        return imgs["-0-0"]
    if e.name == "industrial-conduit":
        out = Image.new("RGBA", (32, 32), LIQUID + (255,))
        out.alpha_composite(imgs["-top-0"])
        return out
    if e.name == "power-accumulator":
        out = imgs[""].copy()
        if "-power" in imgs:
            out.alpha_composite(P.tint(imgs["-power"], CHARGE))
        else:
            fill = Image.new("RGBA", out.size, CHARGE + (255,))
            out.paste(fill, (0, 0), imgs[""].split()[3])
        out.alpha_composite(imgs["-top"])
        return out
    if e.name == "scrap-recycler" and "-top" not in imgs:
        out = imgs[""].copy()
        rot = imgs["-rotator"]
        y = 24 if imgs["-rotator"].width == 24 and imgs[""].getpixel((1, 4))[3] else 27
        for cx, cy, ang in ((20, y, 15), (44, y, -15)):
            r = rot.rotate(ang, Image.NEAREST)
            out.alpha_composite(r, (cx - r.width // 2, cy - r.height // 2))
        return out
    if e.name == "core-foothold":
        out = imgs[""].copy()
        out.alpha_composite(P.tint(imgs["-team"], team or P.SHARDED))
        return out
    if e.name == "cargo-bridge":
        return imgs[""]
    return P.compose(imgs, "block")


def caption(e):
    return e.label_pt, e.name


def flow_sheet(groups, scale, width=1900, bg=P.BG_NEUTRAL, title=None, subtitle=None, show_native=True):
    """groups: [(header, [(entry, image)])]. Flow layout with captions, each sprite at `scale` plus a
    native-size copy beside it."""
    pad, lab = 14, 30
    layout, x, y = [], pad, (56 if title else pad)
    for header, items in groups:
        if x != pad:
            x, y = pad, y + row_h + pad
        layout.append(("header", header, pad, y))
        y += 26
        x, row_h = pad, 0
        for e, img in items:
            w = img.width * scale + (img.width + 8 if show_native else 0)
            h = img.height * scale + lab
            cw = max(w, 150)
            if x + cw > width - pad:
                x, y = pad, y + row_h + pad
                row_h = 0
            layout.append(("item", (e, img), x, y))
            x += cw + pad
            row_h = max(row_h, h)
        y += row_h + pad
        x = pad
        row_h = 0
    sheet = Image.new("RGBA", (width, y + pad), bg)
    d = ImageDraw.Draw(sheet)
    if title:
        P.label(d, (pad, 10), title, 20, bold=True)
        if subtitle:
            P.label(d, (pad, 34), subtitle, 12, P.INK_DIM)
    for kind, payload, px, py in layout:
        if kind == "header":
            P.label(d, (px, py), payload, 15, bold=True)
            d.line([(px, py + 20), (width - pad, py + 20)], fill=(90, 96, 104, 255))
            continue
        e, img = payload
        big = P.up(img, scale)
        sheet.alpha_composite(big, (px, py))
        if show_native:
            sheet.alpha_composite(img, (px + big.width + 8, py + big.height - img.height))
        name_pt, name = caption(e)
        P.label(d, (px, py + big.height + 3), name_pt, 11)
        P.label(d, (px, py + big.height + 16), name, 10, P.INK_DIM)
    return sheet


def by_family(fam):
    return [e for e in catalog.ENTRIES if e.family == fam]


# ------------------------------------------------------------------------------------------ sheets

def sheet_catalog(out):
    groups = [(t, [(e, game_look(e)) for e in by_family(f)]) for f, t in FAMILY_TITLES]
    s = flow_sheet(groups, 2, title="VESPER — catálogo completo (0.1.0-art-preview)",
                   subtitle="Cada sprite em 2x (nearest-neighbour) com a cópia em escala nativa ao lado. Contornos, sombras de minério, "
                            "tinta de equipe e glow aditivo simulados como o jogo aplica.")
    s.save(os.path.join(out, "01-full-catalog.png"))


def floor_tile(fam, v):
    return catalog.BY_NAME[fam].layers()[str(v)].image()


def sheet_terrain(out, stencil):
    fams = by_family("terrain")
    W = 1900
    sheet = Image.new("RGBA", (W, 1500), P.BG_DARK)
    d = ImageDraw.Draw(sheet)
    P.label(d, (14, 10), "Terreno — variações lado a lado, campo nativo, ampliação 2x e transições", 18, bold=True)
    y = 46
    for e in fams:
        vs = [floor_tile(e.name, v) for v in (1, 2, 3)]
        P.label(d, (14, y), "%s  (%s1..3)" % (e.label_pt, e.name), 13)
        x = 14
        for v in vs:
            sheet.alpha_composite(P.up(v, 4), (x, y + 20))
            x += 136
        field = P.floor_field(10, 4, lambda tx, ty, r: vs[r.randint(0, 2)], seed=len(e.name))
        sheet.alpha_composite(field, (x + 10, y + 20))
        sheet.alpha_composite(P.up(field.crop((0, 0, 192, 64)), 2), (x + 10 + 330, y + 20))
        y += 160
    # transitions with the game's edge blending, plus ores on every floor
    P.label(d, (14, y), "Transições entre famílias (mescla de bordas como no jogo) e minérios sobre cada terreno — nativo e 2x", 13)
    scene = terrain_scene(stencil, 24, 10)
    sheet.alpha_composite(scene, (14, y + 22))
    sheet.alpha_composite(P.up(scene.crop((160, 64, 160 + 352, 64 + 160)), 2), (14 + scene.width + 16, y + 22))
    sheet = sheet.crop((0, 0, W, y + 22 + max(scene.height, 320) + 14))
    sheet.save(os.path.join(out, "02-terrain-comparison.png"))


def floor_grid(tw, th, region_fn, seed=7):
    rnd_vars = {}
    grid = {}
    import raster as R
    rnd = R.rng("grid", seed)
    for ty in range(th):
        for tx in range(tw):
            grid[(tx, ty)] = (region_fn(tx, ty), rnd.randint(1, 3))
    return grid


FLOOR_ORDER = ["machine-basalt", "scrap-soil", "vitrified-dust", "rusted-plating"]


def render_floors(grid, tw, th, stencil):
    """Draw floors with Mindustry-style soft edges: higher-order floors bleed into lower neighbours."""
    tiles = {f: [floor_tile(f, v) for v in (1, 2, 3)] for f in FLOOR_ORDER}
    img = Image.new("RGBA", (tw * 32, th * 32), (0, 0, 0, 255))
    for (tx, ty), (f, v) in grid.items():
        img.alpha_composite(tiles[f][v - 1], (tx * 32, ty * 32))
    if stencil is None:
        return img
    edges = {}
    for f in FLOOR_ORDER:
        tex = tiles[f][0]
        e = Image.new("RGBA", stencil.size, (0, 0, 0, 0))
        sp, tp, ep = stencil.load(), tex.load(), e.load()
        for y in range(stencil.height):
            for x in range(stencil.width):
                a = sp[x, y][3]
                if a:
                    c = tp[x % 32, y % 32]
                    ep[x, y] = (c[0], c[1], c[2], a)
        edges[f] = e
    rank = {f: i for i, f in enumerate(FLOOR_ORDER)}
    for (tx, ty), (f, _) in grid.items():
        blend = []
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if dx == 0 and dy == 0:
                    continue
                o = grid.get((tx + dx, ty + dy))
                if o and rank[o[0]] > rank[f]:
                    blend.append((rank[o[0]], o[0], dx, dy))
        for _, of, dx, dy in sorted(blend):
            # image coords: neighbour at (dx, dy) uses stencil cell (1 - dx, 1 - dy)
            cell = edges[of].crop(((1 - dx) * 32, (1 - dy) * 32, (2 - dx) * 32, (2 - dy) * 32))
            img.alpha_composite(cell, (tx * 32, ty * 32))
    return img


def terrain_scene(stencil, tw, th):
    import raster as R

    def region(tx, ty):
        n = R.value_noise(tx * 8, ty * 8, 48, 384, 99)
        if tx < tw * 0.25 + n * 3:
            return "machine-basalt"
        if tx < tw * 0.5 + n * 3:
            return "scrap-soil"
        if tx < tw * 0.75 + n * 3:
            return "vitrified-dust"
        return "rusted-plating"
    grid = floor_grid(tw, th, region)
    img = render_floors(grid, tw, th, stencil)
    ores = [e for e in by_family("ores")]
    for k, e in enumerate(ores):
        for (tx, ty) in grid:
            if ty in (1 + (k % 2) * 4, 2 + (k % 2) * 4) and (tx * 3 + k * 7) % 12 < 5 and (tx // 3) % 4 == k:
                ore = P.ore_shadow(e.layers()[str(1 + (tx + ty) % 2)].image())
                img.alpha_composite(ore, (tx * 32, ty * 32))
    for k, e in enumerate(ores):
        for i in range(tw // 4):
            tx, ty = i * 4 + k, 8 + (k % 2)
            if ty < th:
                img.alpha_composite(P.ore_shadow(e.layers()[str(1 + i % 2)].image()), (tx * 32, ty * 32))
    return img


def sheet_items(out):
    items = by_family("items")
    W = 1500
    sheet = Image.new("RGBA", (W, 760), P.BG_NEUTRAL)
    d = ImageDraw.Draw(sheet)
    P.label(d, (14, 10), "Itens — matérias-primas (4) e processados (4) em fundos escuro, neutro e claro; tamanhos de interface e de esteira", 16, bold=True)
    bgs = [(P.BG_DARK, "escuro"), (P.BG_NEUTRAL, "neutro"), (P.BG_LIGHT, "claro")]
    y = 44
    for bg, name in bgs:
        strip = Image.new("RGBA", (W - 28, 150), bg)
        sd = ImageDraw.Draw(strip)
        x = 10
        for e in items:
            img = e.layers()[""].image()
            strip.alpha_composite(P.up(img, 3), (x, 10))
            strip.alpha_composite(img, (x + 100, 74))
            small = img.resize((16, 16), Image.BILINEAR)
            strip.alpha_composite(small, (x + 100, 46))
            P.label(sd, (x, 112), e.label_pt[:22], 11, P.INK if bg != P.BG_LIGHT else (20, 22, 26, 255))
            x += 178
        P.label(d, (14, y), "fundo " + name + ": 3x, 32 px (ícone) e 16 px (UI compacta)", 11, P.INK_DIM)
        sheet.alpha_composite(strip, (14, y + 16))
        y += 172
    # items riding a belt at the size the game draws them on conveyors (itemSize ~5 world units = 20 px)
    belt = catalog.BY_NAME["transit-belt"].layers()
    row = Image.new("RGBA", (16 * 32, 32), (0, 0, 0, 0))
    for i in range(16):
        row.alpha_composite(belt["-0-%d" % (i % 4 if False else 0)].image(), (i * 32, 0))
    for i, e in enumerate(items * 2):
        icon = e.layers()[""].image().resize((20, 20), Image.BILINEAR)
        row.alpha_composite(icon, (i * 32 + 6, 6))
    P.label(d, (14, y), "na esteira, no tamanho real desenhado pelo jogo (≈20 px, filtragem linear) — nativo e 2x", 11, P.INK_DIM)
    sheet.alpha_composite(row, (14, y + 16))
    sheet.alpha_composite(P.up(row.crop((0, 0, 256, 32)), 2), (14 + row.width + 20, y + 16))
    sheet = sheet.crop((0, 0, W, y + 16 + 64 + 14))
    sheet.save(os.path.join(out, "03-items-comparison.png"))


def showroom(entries, cols, ground, spacing=1, scale=1, team=None):
    """Place entries on a terrain field with block shadows, one tile of spacing, like a build grid."""
    sizes = [max(1, e.size) for e in entries]
    cell = max(sizes) + spacing
    rows = (len(entries) + cols - 1) // cols
    tw, th = cols * cell + spacing, rows * cell + spacing
    field = P.floor_field(tw, th, lambda tx, ty, r: ground[r.randint(0, 2)], seed=11)
    feet = []
    pos = []
    for i, e in enumerate(entries):
        cx, cy = spacing + (i % cols) * cell, spacing + (i // cols) * cell
        s = max(1, e.size)
        ox, oy = cx + (cell - spacing - s) // 2, cy + (cell - spacing - s) // 2
        feet.append((ox, oy, s))
        pos.append((ox, oy, s))
    field.alpha_composite(P.block_shadows(field.size, feet))
    for e, (ox, oy, s) in zip(entries, pos):
        img = game_look(e, team=team)
        field.alpha_composite(img, (ox * 32 + (s * 32 - img.width) // 2, oy * 32 + (s * 32 - img.height) // 2))
    return field if scale == 1 else P.up(field, scale)


def ground(fam):
    return [floor_tile(fam, v) for v in (1, 2, 3)]


def sheet_machines(out):
    ents = by_family("extraction") + by_family("industry") + [e for e in by_family("logistics") if e.name != "transit-belt"]
    W = 1900
    groups = [("Extração", [(e, game_look(e)) for e in by_family("extraction")]),
              ("Indústria e energia", [(e, game_look(e)) for e in by_family("industry")]),
              ("Logística", [(e, game_look(e)) for e in by_family("logistics")])]
    top = flow_sheet(groups, 2, width=W, title="Máquinas industriais — 2x com cópia nativa; abaixo, em escala nativa sobre três terrenos com sombra de bloco")
    rooms = [showroom(ents, 7, ground(g)) for g in ("machine-basalt", "rusted-plating", "vitrified-dust")]
    h = top.height + max(r.height for r in rooms) + 30
    sheet = Image.new("RGBA", (W, h), P.BG_NEUTRAL)
    sheet.alpha_composite(top, (0, 0))
    x = 14
    for r in rooms:
        if x + r.width > W:
            break
        sheet.alpha_composite(r, (x, top.height + 10))
        x += r.width + 14
    sheet.save(os.path.join(out, "04-industrial-machines.png"))


def sheet_turrets_units(out):
    W = 1900
    sheet = Image.new("RGBA", (W, 3600), P.BG_DARK)
    d = ImageDraw.Draw(sheet)
    P.label(d, (14, 10), "Torres e unidades — contorno do jogo simulado (torres #404049 r4, unidades #565666 r3); cabeças giradas; equipe aliada (amarelo) e hostil (vermelho)", 15, bold=True)
    y = 44
    turrets = by_family("defense")
    g = ground("vitrified-dust")
    x = 14
    for e in turrets:
        imgs = images(e.layers())
        n = max(1, e.size) * 32
        strip = P.floor_field(max(1, e.size) * 3 + 2, max(1, e.size) + 2, lambda tx, ty, r: g[r.randint(0, 2)], seed=3)
        for k, ang in enumerate((0, -35, 120)):
            ox, oy = 32 + k * n, 32
            strip.alpha_composite(P.block_shadows(strip.size, [(1 + k * max(1, e.size), 1, max(1, e.size))]))
            if e.kind == "turret":
                base = imgs["-base"]
                head = P.outline(imgs[""], palette.TURRET_OUTLINE, 4).rotate(ang, Image.NEAREST, expand=True)
                strip.alpha_composite(base, (ox, oy))
                strip.alpha_composite(head, (ox + (n - head.width) // 2, oy + (n - head.height) // 2))
            else:
                strip.alpha_composite(imgs[""], (ox, oy))
        sheet.alpha_composite(P.up(strip, 2) if strip.width < 400 else strip, (x, y))
        P.label(d, (x, y - 2), e.label_pt, 11, P.INK_DIM)
        x += (strip.width * 2 if strip.width < 400 else strip.width) + 16
        if x > W - 300:
            x, y = 14, y + 420
    y += 440
    P.label(d, (14, y), "Unidades em escala nativa e 3x, sobre basalto e poeira vitrificada, em três orientações", 13)
    y += 22
    x = 14
    for e in by_family("units"):
        imgs = images(e.layers())
        hostile = e.name in ("ancient-sentinel", "rust-crawler")
        team = P.CRUX if hostile else P.SHARDED
        for gi, gname in enumerate(("machine-basalt", "vitrified-dust")):
            gg = ground(gname)
            f = P.floor_field(9, 4, lambda tx, ty, r: gg[r.randint(0, 2)], seed=gi + 1)
            for k, rot in enumerate((0, 50, 180)):
                u = P.compose_unit(imgs, team, rot)
                f.alpha_composite(u, (48 + k * 96 - u.width // 2 + 24, 64 - u.height // 2))
            sheet.alpha_composite(f, (x, y + gi * 140))
        big = P.compose_unit(imgs, team, 0)
        sheet.alpha_composite(P.up(big, 3), (x + 300, y))
        P.label(d, (x, y + 284), e.label_pt + ("  [hostil]" if hostile else "  [aliado]"), 12)
        x += 300 + big.width * 3 + 20
        if x > W - 500:
            x, y = 14, y + 420
    sheet = sheet.crop((0, 0, W, y + 330))
    sheet.save(os.path.join(out, "05-turrets-and-units.png"))


def sheet_alternatives(out):
    alts = [e for e in catalog.ENTRIES if e.alts]
    W = 1900
    rows = []
    for e in alts:
        rows.append(e)
    sheet = Image.new("RGBA", (W, 60 + len(rows) * 300), P.BG_NEUTRAL)
    d = ImageDraw.Draw(sheet)
    P.label(d, (14, 10), "Propostas alternativas — comparação e escolha (2x + nativo sobre terreno)", 18, bold=True)
    y = 50
    g = ground("machine-basalt")
    for e in rows:
        P.label(d, (14, y), "%s (%s)" % (e.label_pt, e.name), 14, bold=True)
        P.label(d, (14, y + 18), "Escolha: %s — %s" % (e.approved, e.note), 11, P.INK_DIM)
        x = 14
        for alt in e.alts:
            img = game_look(e, alt)
            s = 2 if img.width >= 64 else 4
            big = P.up(img, s)
            sheet.alpha_composite(big, (x, y + 40))
            n = max(1, e.size)
            f = P.floor_field(n + 2, n + 2, lambda tx, ty, r: g[r.randint(0, 2)], seed=5)
            f.alpha_composite(P.block_shadows(f.size, [(1, 1, n)]))
            f.alpha_composite(img, (32 + (n * 32 - img.width) // 2, 32 + (n * 32 - img.height) // 2))
            sheet.alpha_composite(f, (x + big.width + 10, y + 40))
            tag = "APROVADA" if alt == e.approved else "rejeitada"
            col = (120, 220, 170, 255) if alt == e.approved else (230, 130, 120, 255)
            P.label(d, (x, y + 40 + big.height + 4), "proposta %s — %s" % (alt, tag), 12, col, bold=True)
            x += big.width + f.width + 40
        y += 300
    sheet.save(os.path.join(out, "06-alternative-designs.png"))


def sheet_native(out, stencil):
    ents = [e for e in catalog.ENTRIES if e.kind in ("block", "turret")]
    W = 1900
    room = showroom(ents, 12, ground("machine-basalt"), spacing=1)
    small = room.resize((room.width // 2, room.height // 2), Image.BILINEAR)
    units = [e for e in by_family("units")]
    sheet = Image.new("RGBA", (W, room.height + small.height + 140), P.BG_DARK)
    d = ImageDraw.Draw(sheet)
    P.label(d, (14, 10), "Revisão em escala nativa — 1 pixel do sprite = 1 pixel de tela (zoom padrão do jogo). Abaixo: zoom afastado 0,5x com filtragem linear.", 15, bold=True)
    sheet.alpha_composite(room, (14, 40))
    sheet.alpha_composite(small, (14, 60 + room.height))
    x = 14 + small.width + 30
    yy = 60 + room.height
    for e in units + by_family("items"):
        img = game_look(e)
        sheet.alpha_composite(img, (x, yy))
        x += img.width + 10
    sheet.save(os.path.join(out, "08-native-scale-review.png"))


def sheet_silhouette(out):
    ents = [e for e in catalog.ENTRIES if e.kind in ("block", "turret", "unit", "item", "ore")]
    looks = [(e, game_look(e)) for e in ents]
    W = 1900
    pad = 10
    x, y, rowh = pad, 40, 0
    placed = []
    for e, img in looks:
        if x + img.width * 2 > W - pad:
            x, y, rowh = pad, y + rowh + pad, 0
        placed.append((e, img, x, y))
        x += img.width * 2 + pad
        rowh = max(rowh, img.height * 2)
    h = y + rowh + pad
    sheet = Image.new("RGBA", (W, h * 2 + 40), (205, 205, 200, 255))
    d = ImageDraw.Draw(sheet)
    P.label(d, (14, 10), "Teste de silhueta (preenchimento sólido) e de valor (escala de cinza) — legibilidade sem cor", 15, (20, 20, 24, 255), bold=True)
    for e, img, px, py in placed:
        sil = Image.new("RGBA", img.size, (28, 30, 34, 255))
        sil.putalpha(img.split()[3].point(lambda a: 255 if a > 40 else 0))
        sheet.alpha_composite(P.up(sil, 2), (px, py))
        gray = img.convert("LA").convert("RGBA")
        bg = Image.new("RGBA", img.size, (58, 58, 66, 255))
        bg.alpha_composite(gray)
        sheet.alpha_composite(P.up(bg, 2), (px, py + h))
    sheet.save(os.path.join(out, "09-silhouette-and-value.png"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--stencil")
    ap.add_argument("--only", nargs="*")
    ap.add_argument("--before", help="snapshot folder of a previous pass: write a before/after sheet instead")
    ap.add_argument("--before-title", default="Refinamento — antes e depois")
    ap.add_argument("--before-file", default="10-refinement.png")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    stencil = Image.open(args.stencil).convert("RGBA") if args.stencil and os.path.isfile(args.stencil) else None
    jobs = {
        "catalog": lambda: sheet_catalog(args.out),
        "terrain": lambda: sheet_terrain(args.out, stencil),
        "items": lambda: sheet_items(args.out),
        "machines": lambda: sheet_machines(args.out),
        "turrets": lambda: sheet_turrets_units(args.out),
        "alternatives": lambda: sheet_alternatives(args.out),
        "native": lambda: sheet_native(args.out, stencil),
        "silhouette": lambda: sheet_silhouette(args.out),
    }
    if args.before:
        changed = sheet_before_after(args.out, args.before, args.before_title, args.before_file)
        print("changed since snapshot:", ", ".join(changed))
        return
    for name, job in jobs.items():
        if args.only and name not in args.only:
            continue
        job()
        print("sheet", name, "done")



def load_layers_from(folder, e):
    """Read an entry's layers from a sprite folder (e.g. a snapshot of a previous pass)."""
    base = os.path.join(folder, *e.folder.split("/"))
    out = {}
    if not os.path.isdir(base):
        return out
    for f in os.listdir(base):
        if f.startswith(e.name) and f.endswith(".png"):
            suffix = f[len(e.name):-4]
            if suffix == "" or suffix.startswith("-") or suffix.isdigit():
                if e.kind not in ("floor", "ore") and suffix.isdigit():
                    continue
                out[suffix] = Image.open(os.path.join(base, f)).convert("RGBA")
    return out


def sheet_before_after(out, old_dir, title, filename):
    """Side-by-side comparison of every entry whose files changed since `old_dir`."""
    rows = []
    for e in catalog.ENTRIES:
        old = load_layers_from(old_dir, e)
        new = load_layers_from(build_dir(), e)
        if not old or set(old) != set(new) or any(list(old[k].getdata()) != list(new[k].getdata()) for k in old):
            if old:
                rows.append((e, look_from_images(e, old), look_from_images(e, new)))
    W = 1900
    pad = 16
    x, y, rowh = pad, 50, 0
    placed = []
    for e, a, b in rows:
        s = 2 if max(a.width, b.width) >= 48 else 4
        w = (a.width + b.width) * s + 40
        if x + w > W:
            x, y, rowh = pad, y + rowh + 40, 0
        placed.append((e, a, b, s, x, y))
        x += w + 30
        rowh = max(rowh, max(a.height, b.height) * s)
    sheet = Image.new("RGBA", (W, y + rowh + 50), P.BG_NEUTRAL)
    d = ImageDraw.Draw(sheet)
    P.label(d, (pad, 12), title, 18, bold=True)
    for e, a, b, s, px, py in placed:
        sheet.alpha_composite(P.up(a, s), (px, py))
        sheet.alpha_composite(P.up(b, s), (px + a.width * s + 24, py))
        P.label(d, (px + a.width * s + 4, py + a.height * s // 2 - 8), ">", 16, bold=True)
        P.label(d, (px, py + max(a.height, b.height) * s + 4), e.label_pt + " — antes | depois", 11)
    sheet.save(os.path.join(out, filename))
    return [r[0].name for r in rows]


def build_dir():
    return os.path.join(os.path.dirname(os.path.dirname(HERE)), "assets", "sprites")


if __name__ == "__main__":
    main()
