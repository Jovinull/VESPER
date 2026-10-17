package vesper.systems;

/** A building that holds the basin's resonance down (see {@link PlanetaryActivity#stabilization}). */
public interface ActivityStabilizer{
    /** How well it works right now, 0-1: switched on, powered and not overloaded by a pulse. */
    float stabilizerOperation();

    /** The points it actually takes off the activity after diminishing returns; set by the server every sample. */
    void stabilizerContribution(float points);
}
