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

    default @Nullable Objective current(){
        return completed() < objectives().size ? objectives().get(completed()) : null;
    }
}
