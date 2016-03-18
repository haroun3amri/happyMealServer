package fr.inria.corese.kgtool.workflow;

import fr.inria.acacia.corese.exceptions.EngineException;
import fr.inria.acacia.corese.triple.parser.NSManager;
import fr.inria.edelweiss.kgraph.api.Loader;
import fr.inria.edelweiss.kgraph.core.Graph;
import fr.inria.edelweiss.kgraph.rule.Rule;
import fr.inria.edelweiss.kgraph.rule.RuleEngine;
import fr.inria.edelweiss.kgtool.load.Load;
import fr.inria.edelweiss.kgtool.load.LoadException;
import fr.inria.edelweiss.kgtool.transform.Transformer;

/**
 *
 * @author Olivier Corby, Wimmics INRIA I3S, 2016
 *
 */
public class RuleProcess extends  SemanticProcess {

    private String path;
    private RuleEngine engine;
    int profile = -1;
    
    public RuleProcess(String p){
        path = p;
        if (path.equals(Transformer.OWLRL) || path.equals(NSManager.OWLRL)){
            profile = RuleEngine.OWL_RL;
        }
    }
    
    public RuleProcess(int p){
        profile = p;
    }
    
    @Override
    public Data process(Data data) throws EngineException {
        try {
            if (isDebug()){
                System.out.println("RuleBase: " + path);
            }
            RuleEngine re = create(data.getGraph()); 
            re.setContext(getContext());
            setEngine(re);
            re.process();
            Data res = new Data(data.getGraph());
            res.setProcess(this);
            setData(res);
            if (isDebug()){
                System.out.println(res.getGraph());
            }
            return res;
        } catch (LoadException ex) {
            throw new EngineException(ex);
        }
    }
    
    @Override
    public String stringValue(Data data){
        return data.getGraph().toString();
    }
    
    
    RuleEngine create(Graph g) throws LoadException {
        RuleEngine re;
        if (profile == -1) {
            re = init(g, getPath());
        } else {
            re = RuleEngine.create(g);
            re.setProfile(profile);
        }
        init(re);
        return re;
    }
    
    void init(RuleEngine re){
        if (getContext() != null){
            for (Rule r : re.getRules()){
                r.getQuery().setContext(getContext());
            }
        }
    }
    
    RuleEngine init(Graph g, String p) throws LoadException{
        Load ld = Load.create(g);
        ld.parse(p, Loader.RULE_FORMAT);
        return ld.getRuleEngine();
    }

    /**
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * @param path the path to set
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * @return the engine
     */
    public RuleEngine getEngine() {
        return engine;
    }

    /**
     * @param engine the engine to set
     */
    public void setEngine(RuleEngine engine) {
        this.engine = engine;
    }

  
    
}