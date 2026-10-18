package vesper.ui;

import arc.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.dialogs.*;
import vesper.campaign.*;
import vesper.campaign.Chapter.*;
import vesper.content.*;
import vesper.graphics.*;

import static mindustry.Vars.*;

/**
 * What the player sees of Chapter One outside the HUD: a short dialog when a sector is secured (what was found,
 * what is available now, where to go next), the chapter's end, the VESPER Archive with every record found, and on
 * the planet map an Archive button and a one-time pointer to the new planet. Only the host or the single player
 * gets the dialogs: a multiplayer client has no planet map and its own profile is not the one that progresses.
 */
public class ChapterUi{
    static final String introKey = "vesper-campaign-intro";
    /** Text column: 560 units, narrower on a small screen. */
    static float textWidth(){
        return Math.min(560f, Core.graphics.getWidth() / Scl.scl(1f) - 80f);
    }

    /** The dialog's content in a scroll pane, so a long text never pushes the buttons off a small screen. */
    static Table scrolling(BaseDialog dialog){
        Table c = new Table();
        c.defaults().width(textWidth()).left();
        dialog.cont.pane(c).maxHeight(Core.graphics.getHeight() / Scl.scl(1f) - 200f).scrollX(false);
        return c;
    }

    final BasinHud hud;

    public ChapterUi(BasinHud hud){
        this.hud = hud;
        //once the HUD has said its part (the victory, the records found with it), so nothing is drawn over the dialog
        Events.on(SecuredEvent.class, e -> {
            if(!net.client()) Time.run(3f * 60f, () -> afterAnnouncements(e, 0));
        });
        Events.on(mindustry.game.EventType.ClientLoadEvent.class, e -> ui.planet.shown(this::planetShown));
    }

    void afterAnnouncements(SecuredEvent e, int tries){
        if(!state.isGame() || state.rules.sector == null || state.rules.sector.preset != e.preset) return;
        if(!hud.quiet() && tries < 80){
            Time.run(30f, () -> afterAnnouncements(e, tries + 1));
            return;
        }
        if(e.chapterComplete) showChapterComplete();
        else showSecured(e.preset);
    }

    void planetShown(){
        //the dialog rebuilds its buttons on every show
        ui.planet.buttons.button("@vesper.archive", Icon.book, this::showArchive).size(200f, 54f).pad(2).bottom()
            .visible(() -> ui.planet.state.planet == VPlanets.vesper && ui.planet.mode == PlanetDialog.Mode.look);
        if(!Core.settings.getBool(introKey, false) && !VPlanets.vesper.sectors.contains(Sector::hasBase)) waitForPlanetMap();
    }

    /** The intro waits for the planet map to be on top (a first campaign start asks for Serpulo or Erekir before). */
    void waitForPlanetMap(){
        Time.run(30f, () -> {
            if(!ui.planet.isShown() || Core.settings.getBool(introKey, false)) return;
            if(Core.scene.getDialog() != ui.planet){
                waitForPlanetMap();
                return;
            }
            Core.settings.put(introKey, true);
            BaseDialog dialog = new BaseDialog("@vesper.intro.title");
            dialog.cont.add("@vesper.intro.text").width(textWidth()).wrap().left();
            dialog.buttons.defaults().size(240f, 60f);
            dialog.buttons.button("@vesper.intro.later", Icon.left, dialog::hide);
            dialog.buttons.button("@vesper.intro.go", Icon.planet, () -> {
                dialog.hide();
                ui.planet.viewPlanet(VPlanets.vesper, false);
                ui.planet.lookAt(VSectors.firstAwakening.sector);
                ui.planet.selectSector(VSectors.firstAwakening.sector);
            });
            dialog.show();
        });
    }

    public void showSecured(SectorPreset preset){
        BaseDialog dialog = new BaseDialog(Core.bundle.format("vesper.secured.title", preset.localizedName));
        Table c = scrolling(dialog);
        records(c, Chapter.sectors.indexOf(preset));

        var unlocked = Chapter.unlockedBy(preset).select(UnlockableContent::unlocked);
        if(unlocked.any()){
            c.add("@vesper.secured.available").color(Pal.accent).padTop(12f).row();
            c.table(t -> {
                t.left().defaults().left().padTop(3f);
                for(var content : unlocked){
                    t.image(content.uiIcon).size(iconMed).padRight(8f);
                    t.add(content.localizedName).row();
                }
            }).row();
        }

        int index = Chapter.sectors.indexOf(preset);
        SectorPreset next = index + 1 < Chapter.sectors.size ? Chapter.sectors.get(index + 1) : null;
        if(next != null) c.add(Core.bundle.format("vesper.secured.next", next.localizedName)).wrap().color(Pal.lightishGray).padTop(12f).row();

        dialog.buttons.defaults().size(240f, 60f);
        dialog.buttons.button("@continue", Icon.ok, dialog::hide);
        dialog.buttons.button("@planetmap", Icon.planet, () -> {
            dialog.hide();
            showPlanet(next);
        });
        dialog.show();
    }

    public void showChapterComplete(){
        BaseDialog dialog = new BaseDialog("@vesper.chapter.title");
        Table c = scrolling(dialog);
        c.image(Core.atlas.find("vesper-chapter-one")).size(128f).center().padBottom(8f).row();
        c.add("@vesper.chapter.subtitle").color(VPal.resonance).center().row();
        c.add("@vesper.chapter.text").wrap().padTop(10f).row();
        c.add(Chapter.Record.deepSignal.title()).color(Pal.accent).padTop(12f).row();
        c.add(Chapter.Record.deepSignal.text()).wrap().row();
        c.add("@vesper.chapter.next").wrap().color(Pal.lightishGray).padTop(12f).row();

        dialog.buttons.defaults().size(220f, 60f);
        dialog.buttons.button("@continue", Icon.ok, dialog::hide);
        dialog.buttons.button("@vesper.archive", Icon.book, this::showArchive);
        dialog.buttons.button("@planetmap", Icon.planet, () -> {
            dialog.hide();
            showPlanet(VSectors.deepSignal);
        });
        dialog.show();
    }

    public void showArchive(){
        BaseDialog dialog = new BaseDialog("@vesper.archive.title");
        dialog.cont.pane(p -> {
            p.defaults().width(textWidth()).left();
            for(int i = 0; i < Chapter.sectors.size; i++){
                p.add(Chapter.sectors.get(i).localizedName).color(VPal.resonance).padTop(i == 0 ? 0f : 16f).row();
                p.image().color(Pal.gray).height(3f).growX().padBottom(4f).row();
                records(p, i);
            }
            if(Chapter.complete()) p.add("@vesper.chapter.title").color(Pal.accent).padTop(16f).row();
        }).growY().maxHeight(Core.graphics.getHeight() * 0.7f / Scl.scl());
        dialog.addCloseButton();
        dialog.show();
    }

    /** The records of one sector: the ones found with their text, the others as unknown. */
    static void records(Table c, int sector){
        for(Chapter.Record record : Chapter.Record.all){
            if(record.sector != sector) continue;
            if(record.found()){
                c.add(record.title()).color(Pal.accent).padTop(6f).row();
                c.add(record.text()).wrap().row();
            }else{
                c.add("@vesper.archive.unknown").color(Pal.gray).padTop(6f).row();
            }
        }
    }

    /** Opens the planet map on a sector (the next one to play, or the deep region). */
    static void showPlanet(@Nullable SectorPreset focus){
        ui.planet.show();
        ui.planet.viewPlanet(VPlanets.vesper, false);
        if(focus != null){
            ui.planet.lookAt(focus.sector);
            if(!focus.locked()) ui.planet.selectSector(focus.sector);
        }
    }
}
