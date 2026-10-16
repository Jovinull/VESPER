package vesper.content;

import arc.graphics.*;
import mindustry.type.*;

public class VItems{
    public static Item
    ferroprism, conductiveScrap, resonanceCrystal, vitreousCarbon,
    structuralAlloy, recoveredCircuit, resonanceCore, vitrifiedCompound;

    public static void load(){
        ferroprism = new Item("ferroprism", Color.valueOf("b56553")){{
            hardness = 2;
            cost = 0.8f;
        }};

        conductiveScrap = new Item("conductive-scrap", Color.valueOf("cc8a3f")){{
            hardness = 1;
            cost = 0.6f;
        }};

        resonanceCrystal = new Item("resonance-crystal", Color.valueOf("36c8be")){{
            hardness = 3;
            cost = 1.2f;
            charge = 0.4f;
        }};

        vitreousCarbon = new Item("vitreous-carbon", Color.valueOf("433e57")){{
            hardness = 2;
            flammability = 1.1f;
            cost = 0.9f;
        }};

        structuralAlloy = new Item("structural-alloy", Color.valueOf("91a6a7")){{
            cost = 1.3f;
        }};

        recoveredCircuit = new Item("recovered-circuit", Color.valueOf("1f9c97")){{
            cost = 1.6f;
        }};

        resonanceCore = new Item("resonance-core", Color.valueOf("7ce8d8")){{
            cost = 2f;
            charge = 1f;
        }};

        vitrifiedCompound = new Item("vitrified-compound", Color.valueOf("cbcfc3")){{
            cost = 1.1f;
        }};
    }
}
