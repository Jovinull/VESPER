package vesper.campaign;

import arc.*;
import arc.func.*;
import mindustry.game.Objectives.*;

/** A tech tree requirement kept by the chapter (the native research dialog lists it like the others). */
public class ChapterObjective implements Objective{
    final String key;
    final Boolp complete;

    public ChapterObjective(String key, Boolp complete){
        this.key = key;
        this.complete = complete;
    }

    @Override
    public boolean complete(){
        return complete.get();
    }

    @Override
    public String display(){
        return Core.bundle.get("vesper.requirement." + key);
    }
}
