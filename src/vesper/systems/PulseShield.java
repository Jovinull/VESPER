package vesper.systems;

import mindustry.gen.*;

/**
 * A building that keeps resonance pulses from interfering with the machines around it (see
 * {@link PlanetaryActivity}): it does not lower the activity, it only spares the industry within its reach.
 */
public interface PulseShield{
    /** How far it reaches, in world units. */
    float shieldRadius();

    /**
     * A pulse lands within reach (server only): pay for absorbing it and return whether it is absorbed. A shield
     * that cannot pay is overloaded and protects nothing until the pulse ends.
     */
    boolean absorbPulse(int pulse);

    /** Whether it protects its reach from the pulse running now (on every side, from synced state). */
    boolean shielding();
}
