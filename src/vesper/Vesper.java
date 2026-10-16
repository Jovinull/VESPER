package vesper;

import mindustry.mod.*;
import vesper.content.*;

public class Vesper extends Mod{

    @Override
    public void loadContent(){
        VItems.load();
        VUnits.load();
    }
}
