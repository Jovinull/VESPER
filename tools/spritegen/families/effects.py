"""Effect sprites for future animations and lights. All are drawn near-white or in the resonance ramp with
alpha falloff, so code can tint them (Draw.color) or blend them additively."""
import math

import raster as R


def _radial(n, r_max, ramp, idx_core, stops):
    c = R.Canvas(n, n)
    cx = cy = n / 2
    for r, idx, a in stops:
        c.fill(R.circle(cx, cy, r), ramp, idx, a)
    return c


def resonance_glow():
    """Iluminação de ressonância: soft teal light pool (32 px), stepped falloff for pixel-art lights."""
    return {"": _radial(32, 15, "teal", 6, [(15, 3, 30), (12, 4, 60), (9, 4, 100), (6, 5, 150), (3.5, 6, 210)])}


def activity_indicator():
    """Indicador de atividade: 12 px lamp with a lens highlight; tint per state in code."""
    c = R.Canvas(12, 12)
    c.fill(R.circle(6, 6, 5.5), "pale", 4, 70)
    c.fill(R.circle(6, 6, 4), "pale", 5, 160)
    c.fill(R.circle(6, 6, 2.6), "pale", 6, 255)
    c.set((7, 4), "team", 2, 255)
    return {"": c}


def machine_emissive():
    """Elemento emissivo de máquina: 16x8 slotted light strip used over vents and windows."""
    c = R.Canvas(16, 8)
    c.fill(R.rect(0, 1, 16, 6), "amber", 3, 90)
    c.fill(R.rect(1, 2, 14, 4), "amber", 4, 170)
    for x in range(2, 15, 3):
        c.fill(R.rect(x, 3, 2, 2), "amber", 6, 255)
    return {"": c}


def energy_pulse():
    """Pulso energético: 64 px resonance shock ring with an inner echo, for expanding pulse effects."""
    c = R.Canvas(64, 64)
    cx = cy = 32
    c.fill(R.ring(cx, cy, 30, 27), "teal", 4, 120)
    c.fill(R.ring(cx, cy, 29, 28), "teal", 6, 230)
    c.fill(R.ring(cx, cy, 22, 21), "teal", 5, 110)
    for k in range(8):
        a = k * math.pi / 4
        c.fill(R.thick(cx + 24 * math.cos(a), cy + 24 * math.sin(a), cx + 27 * math.cos(a), cy + 27 * math.sin(a), 1.5), "teal", 6, 200)
    return {"": c}
