package vesper.mission;

import arc.*;
import arc.func.*;
import arc.util.*;

/** One step of a mission: done when its condition holds (evaluated from synced world state), with optional progress text. */
public class Objective{
    public final String key;
    public final Boolp done;
    public final @Nullable Prov<String> progress;

    public Objective(String key, Boolp done, @Nullable Prov<String> progress){
        this.key = key;
        this.done = done;
        this.progress = progress;
    }

    public String text(){
        return Core.bundle.get("vesper.objective." + key);
    }
}
