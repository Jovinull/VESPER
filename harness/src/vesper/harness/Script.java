package vesper.harness;

import arc.func.*;
import arc.struct.*;
import arc.util.*;

/**
 * A queue of test steps executed one per frame (or waiting across frames) in game time. Suites append steps;
 * values that only exist at run time are read inside the lambdas. A step that throws fails the running suite
 * and skips to the next suite.
 */
public class Script{
    public interface Step{
        /** @return true when the step is finished. */
        boolean update(float delta);
    }

    final Seq<Step> steps = new Seq<>();
    final Seq<String> suiteOf = new Seq<>();
    String suite = "";
    final VesperHarness h;

    Script(VesperHarness h){
        this.h = h;
    }

    void suite(String name){
        suite = name;
    }

    public void step(Step step){
        steps.add(step);
        suiteOf.add(suite);
    }

    /** Runs once. */
    public void run(Runnable r){
        step(d -> {
            r.run();
            return true;
        });
    }

    /** Waits a number of game seconds (scaled by the harness speed like everything else). */
    public void wait(float seconds){
        float[] t = {0f};
        step(d -> (t[0] += d) >= seconds * 60f);
    }

    /** Waits until the condition holds; records PASS when it does, FAIL on timeout (game seconds). */
    public void until(String check, Boolp cond, float timeout, @Nullable Prov<String> detail){
        float[] t = {0f};
        step(d -> {
            if(cond.get()){
                h.pass(check, detail == null ? "after " + Strings.fixed(t[0] / 60f, 1) + " s" : detail.get() + " (after " + Strings.fixed(t[0] / 60f, 1) + " s)");
                return true;
            }
            if((t[0] += d) >= timeout * 60f){
                h.fail(check, "timed out after " + timeout + " s" + (detail == null ? "" : ": " + detail.get()));
                return true;
            }
            return false;
        });
    }

    /** Waits until the condition holds without recording anything; false on timeout continues silently. */
    public void waitFor(Boolp cond, float timeout){
        float[] t = {0f};
        step(d -> cond.get() || (t[0] += d) >= timeout * 60f);
    }

    /** Records PASS or FAIL now. */
    public void check(String check, Boolp cond, Prov<String> detail){
        run(() -> h.check(check, cond.get(), detail.get()));
    }

    boolean empty(){
        return steps.isEmpty();
    }

    /** Advances the queue; returns the suite name of a step that threw, or null. */
    @Nullable String update(float delta){
        if(steps.isEmpty()) return null;
        String owner = suiteOf.first();
        try{
            if(steps.first().update(delta)){
                steps.remove(0);
                suiteOf.remove(0);
            }
        }catch(Throwable t){
            h.fail(owner + ": step crashed", Strings.getStackTrace(t));
            //drop the rest of this suite
            while(!steps.isEmpty() && suiteOf.first().equals(owner)){
                steps.remove(0);
                suiteOf.remove(0);
            }
            return owner;
        }
        return null;
    }
}
