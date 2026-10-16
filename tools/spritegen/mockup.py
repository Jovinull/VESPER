"""Factory mock-up composed only from Vesper sprites, simulating how the game layers a scene.

    python tools/spritegen/mockup.py OUT.png [--stencil EDGE_STENCIL] [--crop OUT_CROP.png]

This is a composition, not a game capture. Coordinates are tiles, y-up like Mindustry, origin bottom left.
"""
import argparse
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from PIL import Image  # noqa: E402

import catalog  # noqa: E402
import palette  # noqa: E402
import preview as P  # noqa: E402
import raster as R  # noqa: E402
import sheets  # noqa: E402

W, H = 46, 26


def floor_at(tx, ty):
    n = R.value_noise(tx * 6, ty * 6, 36, 360, 17)
    if tx >= 30 and ty >= 14 and tx <= 44:
        return "rusted-plating"
    if tx + n * 4 < 13 and ty + n * 3 < 11:
        return "scrap-soil"
    if tx + n * 3 > 27 and ty < 11 + n * 2 and tx < 41:
        return "vitrified-dust"
    return "machine-basalt"


ORES = {
    "ore-ferroprism": [(x, y) for x in range(4, 11) for y in range(16, 21) if (x * 7 + y * 3) % 5 != 0],
    "ore-conductive-scrap": [(x, y) for x in range(1, 7) for y in range(3, 8) if (x + y * 2) % 4 != 0],
    "ore-vitreous-carbon": [(x, y) for x in range(8, 13) for y in range(0, 5) if (x * 3 + y) % 5 != 1],
    "ore-resonance-crystal": [(x, y) for x in range(30, 35) for y in range(10, 14) if (x + y) % 3 != 0],
}

BLOCKS = [
    ("core-foothold", 18, 13, 0), ("survey-drill", 5, 17, 0), ("survey-drill", 7, 17, 0),
    ("structural-forge", 14, 15, 0), ("scrap-collector", 3, 5, 0), ("scrap-recycler", 12, 5, 0),
    ("reinforced-drill", 9, 1, 0), ("alloy-refinery", 16, 6, 0), ("crystal-extractor", 31, 11, 0),
    ("component-assembler", 23, 11, 0), ("mineral-compressor", 23, 8, 0),
    ("combustion-engine", 23, 16, 0), ("combustion-engine", 25, 16, 0), ("power-accumulator", 27, 16, 0),
    ("resonance-link", 22, 19, 0), ("resonance-link", 29, 19, 0), ("experimental-resonator", 30, 20, 0),
    ("derelict-reactor", 36, 19, 0), ("stabilizer-pylon", 34, 23, 0), ("stabilizer-pylon", 41, 16, 0),
    ("data-terminal", 31, 15, 0), ("recoverable-ruin", 35, 14, 0),
    ("ancient-platform", 33, 4, 200), ("kinetic-turret", 37, 8, -30), ("kinetic-turret", 37, 1, -10),
    ("pulse-turret", 37, 11, -60), ("cargo-router", 13, 2, 0), ("cargo-junction", 21, 9, 0),
    ("cargo-bridge", 26, 13, 0),
]
WALLS = [(41, y, 2) for y in range(0, 14, 2)] + [(40, y, 1) for y in range(0, 14)]

BELTS = {}


def belt_line(points, rotation):
    for p in points:
        BELTS[p] = rotation


belt_line([(x, 16) for x in range(5, 14)], 0)          # ferroprism drills -> forge
belt_line([(16, 15), (17, 15)], 0)                    # forge -> core
belt_line([(5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5)], 0)   # scrap -> recycler
belt_line([(14, 6), (15, 6)], 0)                      # recycler -> refinery
belt_line([(12, 2)], 0)                               # carbon drill -> router
belt_line([(14, 2), (15, 2)], 0)
belt_line([(16, 2), (16, 3), (16, 4), (16, 5)], 1)    # up into the refinery
belt_line([(19, 7), (20, 7)], 0)                      # refinery output
belt_line([(21, 7), (21, 8)], 1)
belt_line([(21, 10), (21, 11), (21, 12)], 1)
belt_line([(21, 13)], 2)                              # into the core
belt_line([(30, 12), (29, 12), (28, 12), (27, 12), (26, 12)], 2)   # crystals -> assembler
BELT_ITEMS = {"ferroprism": [(6, 16), (9, 16), (12, 16), (17, 15)], "conductive-scrap": [(6, 5), (9, 5)],
              "vitreous-carbon": [(14, 2), (16, 4)], "structural-alloy": [(20, 7), (21, 11)],
              "resonance-crystal": [(29, 12), (27, 12)]}
LINKS = [((22, 19), (29, 19)), ((29, 19), (31, 21)), ((22, 19), (24, 17))]
UNITS = [("maintenance-drone", 21.5, 17.5, 30, True), ("scout-drone", 8, 22, 70, True),
         ("ancient-sentinel", 44.2, 6.5, 90, False), ("rust-crawler", 44.5, 11.5, 110, False),
         ("rust-crawler", 43.8, 2.8, 80, False)]


def img_xy(tx, ty, size, img):
    """Top-left image position for a block whose bottom-left tile is (tx, ty)."""
    return tx * 32 + (size * 32 - img.width) // 2, (H - ty - size) * 32 + (size * 32 - img.height) // 2


def compose(stencil):
    grid = {(tx, H - 1 - ty): (floor_at(tx, ty), 1 + (tx * 5 + ty * 3) % 3) for tx in range(W) for ty in range(H)}
    scene = sheets.render_floors(grid, W, H, stencil)
    # ores (with the shadow the game adds)
    for name, tiles in ORES.items():
        layers = catalog.BY_NAME[name].layers()
        for k, (tx, ty) in enumerate(tiles):
            scene.alpha_composite(P.ore_shadow(layers[str(1 + k % 2)].image()), (tx * 32, (H - 1 - ty) * 32))
    # building shadows (soft, offset like BlockRenderer)
    feet = []
    for name, tx, ty, _ in BLOCKS:
        s = catalog.BY_NAME[name].size
        feet.append((tx, H - ty - s, s))
    for tx, ty, s in WALLS:
        feet.append((tx, H - ty - s, s))
    scene.alpha_composite(P.block_shadows(scene.size, feet))
    # belts
    belt = catalog.BY_NAME["transit-belt"].layers()
    regions = {(s, f): belt["-%d-%d" % (s, f)].image() for s in range(5) for f in range(4)}
    sources = set()
    for name, tx, ty, _ in BLOCKS:
        e = catalog.BY_NAME[name]
        if e.family == "extraction" or (e.family == "industry" and name not in ("combustion-engine", "power-accumulator"))                 or name in ("cargo-router", "cargo-junction", "cargo-bridge"):
            sources |= {(x, y) for x in range(tx, tx + e.size) for y in range(ty, ty + e.size)}
    for (tx, ty) in BELTS:
        P.place_belt(scene, regions, BELTS, tx, ty, (tx + ty) % 4, H, sources)
    for item, spots in BELT_ITEMS.items():
        icon = catalog.BY_NAME[item].layers()[""].image().resize((20, 20), Image.BILINEAR)
        for (tx, ty) in spots:
            scene.alpha_composite(icon, (tx * 32 + 6, (H - 1 - ty) * 32 + 6))
    # walls
    wall1 = catalog.BY_NAME["reinforced-wall"].layers()[""].image()
    wall2 = catalog.BY_NAME["heavy-wall"].layers()[""].image()
    for tx, ty, s in WALLS:
        scene.alpha_composite(wall2 if s == 2 else wall1, (tx * 32, (H - ty - s) * 32))
    # blocks and turrets
    for name, tx, ty, angle in BLOCKS:
        e = catalog.BY_NAME[name]
        if e.kind == "turret":
            imgs = sheets.images(e.layers())
            base = imgs["-base"]
            scene.alpha_composite(base, img_xy(tx, ty, e.size, base))
            head = P.outline(imgs[""], palette.TURRET_OUTLINE, 4).rotate(angle, Image.NEAREST, expand=True)
            scene.alpha_composite(head, img_xy(tx, ty, e.size, head))
        else:
            look = sheets.game_look(e)
            scene.alpha_composite(look, img_xy(tx, ty, e.size, look))
    # resonance beams between links
    lp = catalog.BY_NAME["resonance-link"].layers()
    beam_tex = P.tint(lp["-laser"].image(), palette.hex_to_rgba(palette.RAMPS["teal"][4])[:3])
    end_tex = P.tint(lp["-laser-end"].image(), palette.hex_to_rgba(palette.RAMPS["teal"][5])[:3]).resize((18, 18), Image.BILINEAR)
    for (a, b) in LINKS:
        ax, ay = a[0] * 32 + 16, (H - 1 - a[1]) * 32 + 16
        bx, by = b[0] * 32 + 16, (H - 1 - b[1]) * 32 + 16
        length = int(math.hypot(bx - ax, by - ay))
        strip = beam_tex.resize((max(1, length), 12), Image.BILINEAR)
        ang = math.degrees(math.atan2(-(by - ay), bx - ax))
        r = strip.rotate(ang, Image.BILINEAR, expand=True)
        scene.alpha_composite(r, (int((ax + bx) / 2 - r.width / 2), int((ay + by) / 2 - r.height / 2)))
        for x, y in ((ax, ay), (bx, by)):
            scene.alpha_composite(end_tex, (x - 9, y - 9))
    # units (flying ones cast an offset shadow)
    for name, ux, uy, rot, ally in UNITS:
        e = catalog.BY_NAME[name]
        imgs = sheets.images(e.layers())
        u = P.compose_unit(imgs, P.SHARDED if ally else P.CRUX, rot - 90)
        px, py = int(ux * 32 - u.width / 2), int((H - uy) * 32 - u.height / 2)
        if name.endswith("drone"):
            shadow = Image.new("RGBA", u.size, (0, 0, 0, 0))
            shadow.putalpha(u.split()[3].point(lambda a: 56 if a > 40 else 0))
            scene.alpha_composite(shadow, (px - 10, py + 12))
        scene.alpha_composite(u, (px, py))
    return scene


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--stencil")
    args = ap.parse_args()
    stencil = Image.open(args.stencil).convert("RGBA") if args.stencil and os.path.isfile(args.stencil) else None
    scene = compose(stencil)
    note_h = 30
    sheet = Image.new("RGBA", (scene.width, scene.height + note_h + 2 * 448 + 20), P.BG_DARK)
    from PIL import ImageDraw
    d = ImageDraw.Draw(sheet)
    P.label(d, (10, 8), "Mockup de fábrica — composição com sprites de Vesper (NÃO é captura do jogo). Escala nativa; abaixo, recortes em 2x.", 14, bold=True)
    sheet.alpha_composite(scene, (0, note_h))
    crop1 = scene.crop((2 * 32, 9 * 32, 2 * 32 + 23 * 32 // 1 - 32, 9 * 32 + 7 * 32))
    sheet.alpha_composite(P.up(scene.crop((96, 288, 96 + 352, 288 + 224)), 2), (0, scene.height + note_h + 10))
    sheet.alpha_composite(P.up(scene.crop((960, 64, 960 + 352, 64 + 224)), 2), (720, scene.height + note_h + 10))
    sheet.save(args.out)
    print(args.out, sheet.size)


if __name__ == "__main__":
    main()
