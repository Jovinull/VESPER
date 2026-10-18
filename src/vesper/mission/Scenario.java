package vesper.mission;

import arc.struct.*;
import arc.util.*;

/** A Vesper map's mission as the HUD shows it: objectives in order and the outcome. */
public interface Scenario{
    boolean active();

    /** Bundle key prefix: the HUD reads {@code <key>.title} and announces {@code <key>.victory}. */
    String key();

    Seq<Objective> objectives();

    /** Number of completed objectives; the current one is {@code objectives().get(completed())}. */
    int completed();

    boolean victory();

    /** Whether the final waves are held right now, waiting for the mission's trigger (the HUD explains it with {@code <key>.hold}). */
    default boolean holding(){
        return false;
    }

    /** Whether the final waves have been released (the HUD announces it with {@code <key>.final}). */
    default boolean released(){
        return false;
    }

    default @Nullable Objective current(){
        return completed() < objectives().size ? objectives().get(completed()) : null;
    }
}
