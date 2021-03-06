package fr.inria.corese.compiler.federate;

import fr.inria.corese.sparql.triple.parser.Atom;
import fr.inria.corese.sparql.triple.parser.BasicGraphPattern;
import fr.inria.corese.sparql.triple.parser.Exist;
import fr.inria.corese.sparql.triple.parser.Exp;
import fr.inria.corese.sparql.triple.parser.Expression;
import fr.inria.corese.sparql.triple.parser.Service;
import fr.inria.corese.sparql.triple.parser.Triple;
import fr.inria.corese.sparql.triple.parser.Variable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;




/**
 * Group triple patterns that share a unique service URI
 * into one service URI
 * 
 * @author Olivier Corby, Wimmics INRIA I3S, 2018
 *
 */
public class RewriteBGP {
   
    FederateVisitor visitor;
    private boolean debug = false;
    
    RewriteBGP(FederateVisitor vis) {
        visitor = vis;
    }
    
    /**
     * service -> List of connected BGP
     */
     class ServiceBGP {
        HashMap<String, List<BasicGraphPattern>> map = new HashMap<>();
        
        List<BasicGraphPattern> get(String label){
            List<BasicGraphPattern> bgpList = map.get(label);
            if (bgpList == null) {
                bgpList = new ArrayList<>();
                map.put(label, bgpList);
            }
            return bgpList;
        }
        
        void add(String label, BasicGraphPattern bgp) {
            List<BasicGraphPattern> bgpList = map.get(label);
            if (bgpList == null) {
                bgpList = new ArrayList<>();
                map.put(label, bgpList);
            }
            bgpList.add(bgp);
        }
        
        HashMap<String, List<BasicGraphPattern>> getMap() {
            return map;
        }      
       
    }
    
    
    
    /**
     * group triple patterns that share the same service URI
     * into one service URI
     * modify the body (replace triples by BGPs)
     * filterList: list to be filled with filters that are copied into service
     */
    void prepare(Atom name, Exp body, List<Exp> filterList) {
        ServiceBGP map = new ServiceBGP();
        boolean tripleFilter = true;
        // create BGPs for triples that share same service URI
        // create a table service -> BGP
        for (int i = 0; i<body.size(); i++) {
            Exp exp = body.get(i);
            if (exp.isFilter()) {
                // do nothing
            }
            else if (exp.isTriple()) {
                Triple triple = exp.getTriple();
                List<Atom> list = visitor.getServiceList(triple);
                if (list.size() == 1) {
                   process(map, triple, list.get(0));
                }
            }
            else {
                tripleFilter = false;
            }
        }
               
        HashMap<Triple, BasicGraphPattern>  table = new HashMap<>();
        HashMap<BasicGraphPattern, Service> done  = new HashMap<>();
        
        // record triples that are member of created BGPs
        for (List<BasicGraphPattern> list : map.getMap().values()) {
            for (BasicGraphPattern bgp : list) {
                for (Exp exp : bgp) {
                    table.put(exp.getTriple(), bgp);
                }
            }
        }
        
        // for each triple member of created BGP
        // replace first triple by service s { BGP }
        // move simple filters into BGP
        for (int i = 0; i<body.size(); i++) {
            if (body.get(i).isTriple()) { 
                Triple t = body.get(i).getTriple();
                if (table.containsKey(t)){
                    BasicGraphPattern bgp = table.get(t);
                    if (done.get(bgp) == null ) {                       
                        // service s { bgp }
                        Service serv = visitor.getRewriteTriple().rewrite(name, bgp, visitor.getServiceList(t));
                        // do it once for first triple of this BGP
                        done.put(bgp, serv);
                        // replace first triple of BGP by service BGP
                        body.set(i, serv);
                    } 
                }
            }
        }
        
        // remove triples member of a created service 
        for (Triple t : table.keySet()) {
            body.getBody().remove(t);
        }
        
        filter(name, body, done, filterList, tripleFilter);
    }
    
    /**
     * Add triple in appropriate serv -> (BGP)
     */
    void process2(ServiceBGP map, Triple triple, Atom serv) {
        List<BasicGraphPattern> bgpList = map.get(serv.getLabel());
        if (bgpList.isEmpty()) {
            bgpList.add(BasicGraphPattern.create());
        }
        bgpList.get(0).add(triple);
    }

    /**
     * Add triple in appropriate BGP in serv -> (BGP1 , BGPn)
     * where triple is connected to BGP
     * merge BGPs that are connected with triple.
     */
    void process(ServiceBGP map, Triple triple, Atom serv) {
        List<BasicGraphPattern> bgpList = map.get(serv.getLabel());
        boolean isConnected = false;
        int i = 0;
        
        // find a connected BGP
        for (BasicGraphPattern bgp : bgpList) {
            if (bgp.isConnected(triple)) {
                bgp.add(triple);
                isConnected = true;
                break;
            }
            i++;
        }
        
        if (isConnected) {
            ArrayList<BasicGraphPattern> toRemove = new ArrayList<>();
            
            // if  another BGP is connected to triple
            // include it into the first BGP
            for (int j = i+1; j < bgpList.size(); j++) {
                if (bgpList.get(j).isConnected(triple)) {
                    bgpList.get(i).include(bgpList.get(j));
                    toRemove.add(bgpList.get(j));
                }
            }
            
            // remove BGPs that have been included
            for (BasicGraphPattern bgp : toRemove) {
                bgpList.remove(bgp);
            }
        }
        else {
            // new BGP because triple is connected to nobody
            bgpList.add(BasicGraphPattern.create(triple));
        }
    }
    
    
    void filter(Atom name, Exp body, HashMap<BasicGraphPattern, Service> bgpList, List<Exp> filterList, boolean tripleFilter) {
        // copy filters from body into BGP who bind all filter variables
        for (Exp exp : body) {
            if (exp.isFilter()) {
                if (visitor.isRecExist(exp)) {
                    if (visitor.isExist() && tripleFilter &&
                            (visitor.isExist(exp) || visitor.isNotExist(exp))) {
                        // draft for testing
                        filterExist(name, body, bgpList, filterList, exp);                       
                    }
                }
                else {
                    for (BasicGraphPattern bgp : bgpList.keySet()) {
                        List<Variable> varList = bgp.getVariables();
                        if (exp.getFilter().isBound(varList)) {
                            bgp.add(exp);
                            if (!filterList.contains(exp)) {
                                filterList.add(exp);
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Draft for testing
     * TODO: test conditions to move exists clause into service
     * 
     * body = 
     * service URI { BGP }  filter exists { EXP } 
     * ->
     * service URI { BGP }  filter exists { service URI { EXP } }
     * ->
     * service URI { BGP  filter exists { EXP } } 
     * 
     * TODO: 
     * does not work if there were existing service before rewrite
     * because they are not in bgpMap
     * In this case, this function is not called
     */
    void filterExist(Atom name, Exp body, HashMap<BasicGraphPattern, Service> bgpMap, List<Exp> filterList, Exp filterExist) {
        boolean isNotExist = visitor.isNotExist(filterExist);
        Expression filter = filterExist.getFilter();
        visitor.rewriteFilter(name, filter);
        Exist exist;
        if (isNotExist) {
            exist = filter.getTerm().getArg(0).getTerm().getExistPattern();
        }
        else {
            exist = filter.getTerm().getExistPattern();            
        }
        Exp bgpExist = exist.get(0);
        
        if (bgpExist.size() == 1 && bgpExist.get(0).isService()) {
            Service servExist = bgpExist.get(0).getService();
            if (servExist.getServiceList().size() == 1) {

               List<BasicGraphPattern> bgpList = new ArrayList<>();
               List<Variable> existVarList = bgpExist.getVariables();
               List<Variable> previousIntersection = null;
               
                // select relevant BGP with same URI as exists
                // If other BGP bind some variables of exists, 
                // they must bind the same variables as the relevant one
                for (BasicGraphPattern bgp : bgpMap.keySet()) {

                    Service servBGP = bgpMap.get(bgp);
                    List<Variable> bgpVarList = bgp.getVariables();                  
                    List<Variable> currentIntersection = intersection(existVarList, bgpVarList);
                    
                    if (isDebug()) {
                        System.out.println("R: " + existVarList + " " + bgpVarList);
                        System.out.println("Intersection: " + currentIntersection);
                    }
                    
                    if (servExist.getServiceName().equals(servBGP.getServiceName())) {
                        if (previousIntersection == null) {
                             previousIntersection = currentIntersection;
                        }                        
                        if (equal(previousIntersection, currentIntersection)) {
                            bgpList.add(bgp);
                        } else {
                            // two BGP intersect differently the exists clause
                            return;
                        }
                    }
                    // service with another URI
                    else if (!currentIntersection.isEmpty()) {
                        if (previousIntersection == null) {
                            previousIntersection = currentIntersection;
                        } else if (!equal(previousIntersection, currentIntersection)) {
                            // two BGP intersect differently the exists clause
                            return;
                        }
                    }                   
                }

                if (bgpList.size() == 1) {
                    // remove service from exists
                    exist.set(0, servExist.get(0));
                    // move exist into relevant service 
                    bgpList.get(0).add(filterExist);
                    if (!filterList.contains(filterExist)) {
                        filterList.add(filterExist);
                    }
                }
                
            }
        }
    }
    
    boolean equal(List<Variable> list1, List<Variable> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }
        for (Variable var : list1) {
            if (! list2.contains(var)) {
                return false;
            }
        }
        return true;
    }
    
    boolean hasIntersection(List<Variable> list1, List<Variable> list2) {
       return ! intersection(list1, list2).isEmpty();              
    }
    
    List<Variable> intersection(List<Variable> list1, List<Variable> list2) {
        ArrayList<Variable> list = new ArrayList<>();
         for (Variable var : list1) {
            if (list2.contains(var) && ! list.contains(var)) {
                list.add(var);
            }
        }
        return list;
    }
    
    
    boolean includedIn(List<Variable> list1, List<Variable> list2) {
        for (Variable var : list1) {
            if (! list2.contains(var)) {
                return false;
            }
        }
        return true;
    }     
    
 /**
     * @return the debug
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * @param debug the debug to set
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }    
    
}
