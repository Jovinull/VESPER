package vesper.content;

import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import vesper.graphics.*;

public class VUnits{
    public static UnitType scoutDrone, maintenanceDrone, ancientSentinel, rustCrawler;

    public static void load(){
        scoutDrone = new UnitType("scout-drone"){{
            constructor = UnitEntity::create;
            flying = true;
            speed = 3.2f;
            accel = 0.08f;
            drag = 0.04f;
            health = 110f;
            hitSize = 10f;
            engineOffset = 8f;
            engineSize = 2f;
            circleTarget = true;

            weapons.add(new Weapon(){{
                y = 4f;
                x = 0f;
                mirror = false;
                reload = 14f;
                bullet = new BasicBulletType(2.8f, 8f){{
                    width = 6f;
                    height = 8f;
                    lifetime = 45f;
                    frontColor = VPal.industrialLight;
                    backColor = VPal.industrial;
                }};
            }});
        }};

        maintenanceDrone = new UnitType("maintenance-drone"){{
            constructor = UnitEntity::create;
            coreUnitDock = true;
            flying = true;
            lowAltitude = true;
            speed = 2.8f;
            accel = 0.1f;
            drag = 0.05f;
            rotateSpeed = 15f;
            health = 180f;
            hitSize = 11f;
            itemCapacity = 30;
            mineTier = 2;
            mineSpeed = 6.5f;
            buildSpeed = 0.6f;
            engineSize = 0f;
            isEnemy = false;

            weapons.add(new Weapon(){{
                y = 6f;
                x = 0f;
                mirror = false;
                reload = 18f;
                bullet = new BasicBulletType(2.5f, 9f){{
                    width = 6f;
                    height = 7f;
                    lifetime = 50f;
                    buildingDamageMultiplier = 0.01f;
                    frontColor = VPal.industrialLight;
                    backColor = VPal.industrial;
                }};
            }});
        }};

        ancientSentinel = new UnitType("ancient-sentinel"){{
            constructor = MechUnit::create;
            speed = 0.5f;
            hitSize = 13f;
            health = 600f;
            armor = 4f;
            mechFrontSway = 0.55f;
            mechStepParticles = true;

            weapons.add(new Weapon("vesper-ancient-sentinel-weapon"){{
                x = 6f;
                y = 0.5f;
                top = false;
                reload = 26f;
                recoil = 2f;
                ejectEffect = Fx.casing1;
                bullet = new BasicBulletType(3f, 20f){{
                    width = 9f;
                    height = 11f;
                    lifetime = 55f;
                    frontColor = VPal.dangerLight;
                    backColor = VPal.danger;
                }};
            }});
        }};

        rustCrawler = new UnitType("rust-crawler"){{
            constructor = LegsUnit::create;
            speed = 0.6f;
            hitSize = 13f;
            health = 480f;
            armor = 3f;
            legCount = 4;
            legLength = 10f;
            legForwardScl = 0.6f;
            legMoveSpace = 1.4f;
            legBaseOffset = 3f;
            hovering = true;
            shadowElevation = 0.2f;
            groundLayer = Layer.legUnit;

            weapons.add(new Weapon(){{
                x = 0f;
                y = 6f;
                mirror = false;
                reload = 40f;
                bullet = new LaserBoltBulletType(3.5f, 14f){{
                    lifetime = 40f;
                    frontColor = VPal.dangerLight;
                    backColor = VPal.danger;
                }};
            }});
        }};
    }
}
