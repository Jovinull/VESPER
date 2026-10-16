"""Vesper palette.

Every color used by generated sprites comes from one of these ramps. Ramps run dark -> light and are
hue-shifted (cooler shadows, warmer or greener highlights) so shading never needs off-palette blending.
The anchor colors from the art direction are marked with a trailing comment.
"""

RAMPS = {
    # player-built structural steel; frames, plates and housings
    "steel": ["#171b20", "#232930", "#323a42", "#4b5760", "#63737b", "#7f9396", "#91a6a7", "#bccbc6"],  # 3=#4B5760, 6=#91A6A7
    # ancient hull metal; the vanished civilisation's machinery
    "dark": ["#0d0f12", "#14171b", "#1c2025", "#252a30", "#30363e", "#3c434c", "#4c545d"],  # 3=#252A30
    # oxidation and wear
    "rust": ["#241b17", "#3a2c24", "#553f32", "#6d5443", "#846a53", "#a1876a", "#c2a988"],  # 4=#846A53
    # resonance energy
    "teal": ["#0b2c30", "#114a4e", "#167272", "#1f9c97", "#36c8be", "#7ce8d8", "#d4fff4"],  # 4=#36C8BE
    # industrial energy and player-tech indicators
    "amber": ["#3d2513", "#6e4520", "#a5692c", "#d0923f", "#e9b55b", "#f6d58e", "#fff2cc"],  # 4=#E9B55B
    # danger and instability; hostile ancient units
    "red": ["#33121a", "#5e1f24", "#8f3434", "#b84a47", "#d66561", "#ee9a89", "#ffd6c8"],  # 4=#D66561
    # team-color cells; Mindustry swaps these exact values for team shades
    "team": ["#9d7f7f", "#dcc6c6", "#ffffff"],

    # --- The Silent Basin terrain ---
    "basalt": ["#111316", "#171a1e", "#1e2227", "#262b31", "#30363d", "#3b424a", "#4a525b"],
    "scrapsoil": ["#191513", "#211c19", "#2a2420", "#342d27", "#40372f", "#4d4239", "#5d5045"],
    "rustplate": ["#1b1817", "#24201e", "#2e2926", "#39322e", "#453c36", "#534840", "#64574c"],
    "vitdust": ["#272529", "#302d32", "#3a363b", "#454045", "#514b50", "#5f585c", "#716869"],

    # --- ores and raw materials ---
    "ferro": ["#2a1716", "#4a2422", "#6e3530", "#934a40", "#b56553", "#d18b70", "#ecb898"],
    "copper": ["#2c1a12", "#51301b", "#7c4a24", "#a8682f", "#cc8a3f", "#e6ad62", "#f8d595"],
    "glass": ["#0c0c12", "#15141d", "#201e2b", "#2d2a3c", "#433e57", "#6b6588", "#b4b0d6", "#f0eeff"],
    "pale": ["#3c3f3f", "#555a58", "#717773", "#8e948d", "#acb1a7", "#cbcfc3", "#eceee4"],
}

# Mindustry runtime constants used to simulate the in-game look in previews (not baked into sprites).
TURRET_OUTLINE = "#404049"   # Block.outlineColor, radius 4
UNIT_OUTLINE = "#565666"     # Pal.darkerMetal, UnitType.outlineRadius 3
ORE_SHADOW_ALPHA = 0.3       # OreBlock.createIcons
BLOCK_SHADOW_ALPHA = 0.71    # BlockRenderer.shadowColor


def hex_to_rgba(value, alpha=255):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


RGBA = {name: [hex_to_rgba(c) for c in ramp] for name, ramp in RAMPS.items()}


def color(ramp, index, alpha=255):
    shades = RGBA[ramp]
    r, g, b, _ = shades[max(0, min(len(shades) - 1, index))]
    return (r, g, b, alpha)


def size(ramp):
    return len(RAMPS[ramp])
