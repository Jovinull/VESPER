"""Single source of truth for every Vesper sprite.

Each entry names one piece of content, the atlas folder it ships in, its kind (which drives validation), its
generator, the design alternatives that were explored and the one that was approved. Generators return
{suffix: Canvas}; the shipped files are <folder>/<name><suffix>.png under assets/sprites.
"""
from dataclasses import dataclass, field

from families import defense, drills, effects, industry, logistics, materials, special, terrain, units


@dataclass
class Entry:
    name: str
    family: str
    folder: str
    kind: str                 # floor | wall | ore | item | block | turret | unit | effect
    gen: object               # callable(alt) or callable() returning {suffix: Canvas}
    size: int = 1             # footprint in tiles (blocks and turrets)
    label: str = ""           # English display name
    label_pt: str = ""        # Portuguese display name
    alts: tuple = ()          # explored alternatives, e.g. ("a", "b")
    approved: str = ""        # chosen alternative
    note: str = ""            # why the approved alternative won
    free_layers: tuple = ()   # block layers allowed to differ from the footprint size (never used in icons)

    def layers(self, alt=None):
        alt = alt or self.approved
        return self.gen(alt) if self.alts else self.gen()


def _variants(fn, count):
    def gen():
        return {str(v): fn(v) for v in range(1, count + 1)}
    return gen


def _single(fn):
    def gen():
        return {"": fn()}
    return gen


ENTRIES = []


def add(*args, **kwargs):
    ENTRIES.append(Entry(*args, **kwargs))


# --- terrain: 4 families x 3 variants
for key, label, label_pt in (("machine-basalt", "Machine Basalt", "Basalto Mecânico"),
                             ("scrap-soil", "Compacted Scrap Soil", "Solo de Sucata Compactada"),
                             ("rusted-plating", "Rusted Plating", "Chapas Metálicas Oxidadas"),
                             ("vitrified-dust", "Vitrified Dust", "Poeira Mineral Vitrificada")):
    add(key, "terrain", "blocks/environment", "floor", _variants(terrain.FAMILIES[key], 3), 1, label, label_pt)

# --- static wall: the basalt ridges that shape the map (2 variants + a 2x2 large variant)
add("machine-basalt-wall", "terrain", "blocks/environment", "wall", terrain.machine_basalt_wall, 1, "Basalt Ridge", "Crista de Basalto")

# --- ores: 4 resources x 2 variants
for key, label, label_pt in (("ore-ferroprism", "Ferroprism Ore", "Minério de Ferroprisma"),
                             ("ore-conductive-scrap", "Conductive Scrap Deposit", "Depósito de Sucata Condutora"),
                             ("ore-resonance-crystal", "Resonance Crystal Vein", "Veio de Cristal de Ressonância"),
                             ("ore-vitreous-carbon", "Vitreous Carbon Deposit", "Depósito de Carbono Vítreo")):
    add(key, "ores", "blocks/environment", "ore", _variants(materials.ORES[key], 2), 1, label, label_pt)

# --- items
for key, label, label_pt in (("ferroprism", "Ferroprism", "Ferroprisma"),
                             ("conductive-scrap", "Conductive Scrap", "Sucata Condutora"),
                             ("resonance-crystal", "Resonance Crystal", "Cristal de Ressonância"),
                             ("vitreous-carbon", "Vitreous Carbon", "Carbono Vítreo"),
                             ("structural-alloy", "Structural Alloy", "Liga Estrutural"),
                             ("recovered-circuit", "Recovered Circuit", "Circuito Recuperado"),
                             ("resonance-core", "Resonance Core", "Núcleo de Ressonância"),
                             ("vitrified-compound", "Vitrified Compound", "Composto Vitrificado")):
    add(key, "items", "items", "item", _single(materials.ITEMS[key]), 1, label, label_pt)

# --- logistics
add("transit-belt", "logistics", "blocks/distribution", "block", logistics.transit_belt, 1, "Transit Belt", "Esteira de Transporte")
add("cargo-router", "logistics", "blocks/distribution", "block", logistics.cargo_router, 1, "Cargo Router", "Distribuidor")
add("cargo-junction", "logistics", "blocks/distribution", "block", logistics.cargo_junction, 1, "Cargo Junction", "Cruzamento")
add("cargo-bridge", "logistics", "blocks/distribution", "block", logistics.cargo_bridge, 1, "Cargo Bridge", "Ponte Logística")
add("industrial-conduit", "logistics", "blocks/liquid", "block", logistics.industrial_conduit, 1, "Industrial Conduit", "Conduto Industrial")
add("resonance-link", "logistics", "blocks/power", "block", logistics.resonance_link, 1, "Resonance Link", "Conector de Ressonância")

# --- extraction
add("survey-drill", "extraction", "blocks/drills", "block", drills.survey_drill, 2, "Survey Drill", "Perfuradora Básica",
    alts=("a", "b"), approved="b", note="b: the octagonal well and hazard clamps read at native scale; a's gantry hid the spinning head.")
add("reinforced-drill", "extraction", "blocks/drills", "block", drills.reinforced_drill, 3, "Reinforced Drill", "Perfuradora Reforçada")
add("scrap-collector", "extraction", "blocks/drills", "block", drills.scrap_collector, 2, "Scrap Collector", "Coletor de Sucata")
add("crystal-extractor", "extraction", "blocks/drills", "block", drills.crystal_extractor, 2, "Crystal Extractor", "Extrator de Cristal")

# --- industry and power
add("structural-forge", "industry", "blocks/production", "block", industry.structural_forge, 2, "Structural Forge", "Fornalha Estrutural")
add("scrap-recycler", "industry", "blocks/production", "block", industry.scrap_recycler, 2, "Scrap Recycler", "Recicladora Industrial",
    alts=("a", "b"), approved="a", note="a: the flared hopper and twin shredders say 'recycling' instantly and give a unique silhouette; b's carousel read as a generic sorter.",
    free_layers=("-rotator",))
add("alloy-refinery", "industry", "blocks/production", "block", industry.alloy_refinery, 3, "Alloy Refinery", "Refinaria de Liga",
    alts=("a", "b"), approved="a", note="a: asymmetric crucible, stack, hopper and fins give a clear process story; b read as two eyes.")
add("mineral-compressor", "industry", "blocks/production", "block", industry.mineral_compressor, 2, "Mineral Compressor", "Compressor Mineral")
add("combustion-engine", "industry", "blocks/power", "block", industry.combustion_engine, 2, "Combustion Engine", "Gerador de Combustão")
add("power-accumulator", "industry", "blocks/power", "block", industry.power_accumulator, 2, "Power Accumulator", "Acumulador de Energia")
add("experimental-resonator", "industry", "blocks/production", "block", industry.experimental_resonator, 3, "Experimental Resonator", "Ressonador Experimental",
    alts=("a", "b"), approved="a", note="a: the ancient octagonal plinth clamped by player brackets tells the recovered-technology story; b's spire was cleaner but generic.")
add("component-assembler", "industry", "blocks/production", "block", industry.component_assembler, 3, "Component Assembler", "Montadora de Componentes")

# --- defense
add("reinforced-wall", "defense", "blocks/walls", "block", defense.reinforced_wall, 1, "Reinforced Wall", "Muralha Reforçada")
add("heavy-wall", "defense", "blocks/walls", "block", defense.heavy_wall, 2, "Heavy Wall", "Muralha Pesada")
add("kinetic-turret", "defense", "blocks/turrets", "turret", defense.kinetic_turret, 2, "Kinetic Turret", "Torre Cinética",
    alts=("a", "b"), approved="b", note="b: one heavy barrel gives a stronger silhouette; a's drum read as a ring.")
add("pulse-turret", "defense", "blocks/turrets", "turret", defense.pulse_turret, 2, "Pulse Turret", "Torre de Pulso")
add("ancient-platform", "defense", "blocks/turrets", "turret", defense.ancient_platform, 3, "Ancient Platform", "Plataforma Defensiva Antiga")

# --- units
add("scout-drone", "units", "units", "unit", units.scout_drone, 0, "Scout Drone", "Drone de Reconhecimento")
add("maintenance-drone", "units", "units", "unit", units.maintenance_drone, 0, "Maintenance Drone", "Drone de Manutenção")
add("ancient-sentinel", "units", "units", "unit", units.ancient_sentinel, 0, "Ancient Sentinel", "Sentinela Antiga")
add("rust-crawler", "units", "units", "unit", units.rust_crawler, 0, "Rust Crawler", "Aranha Mecânica")

# --- special structures
add("core-foothold", "special", "blocks/storage", "block", special.core_foothold, 3, "Core: Foothold", "Núcleo: Posto Avançado",
    alts=("a", "b"), approved="a", note="a: flank cargo pods and the comms mast give the core a front and a personality; b was a generic symmetric pad.")
add("derelict-reactor", "special", "blocks/ruins", "block", special.derelict_reactor, 4, "Derelict Reactor", "Reator Antigo Abandonado")
add("stabilizer-pylon", "special", "blocks/ruins", "block", special.stabilizer_pylon, 2, "Stabilizer Pylon", "Torre de Estabilização")
add("data-terminal", "special", "blocks/ruins", "block", special.data_terminal, 2, "Data Terminal", "Terminal de Dados")
add("recoverable-ruin", "special", "blocks/ruins", "block", special.recoverable_ruin, 3, "Industrial Ruin", "Ruína Industrial Recuperável")

# --- effects
add("resonance-glow", "effects", "effects", "effect", effects.resonance_glow, 0, "Resonance Glow", "Iluminação de Ressonância")
add("activity-indicator", "effects", "effects", "effect", effects.activity_indicator, 0, "Activity Indicator", "Indicador de Atividade")
add("machine-emissive", "effects", "effects", "effect", effects.machine_emissive, 0, "Machine Emissive", "Elemento Emissivo")
add("energy-pulse", "effects", "effects", "effect", effects.energy_pulse, 0, "Energy Pulse", "Pulso Energético")

BY_NAME = {e.name: e for e in ENTRIES}

# suffixes that are not separate atlas regions of the block itself but helper regions with their own rules
TEAM_SUFFIXES = ("-cell", "-team")
GLOW_SUFFIXES = ("-glow", "-rim", "-heat", "-laser", "-laser-end", "-active")
