package vesper.systems;

/** A building that disturbs the planet by itself (ancient technology), independent of industrial work. */
public interface ActivitySource{
    /** Activity this building adds to the basin, in points of the 0-100 index. */
    float activity();
}
