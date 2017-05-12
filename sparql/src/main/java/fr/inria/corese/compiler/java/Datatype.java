package fr.inria.corese.compiler.java;

import fr.inria.acacia.corese.api.IDatatype;
import fr.inria.acacia.corese.cg.datatype.RDF;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

/**
 *
 * @author Olivier Corby, Wimmics INRIA I3S, 2017
 *
 */
public class Datatype {
    static final String NL = System.getProperty("line.separator");
    static final String VAR = "_cst_";
    int count = 0;

    StringBuilder sb;
    TreeData cache;
    HashMap<String, String> strCache;

    Datatype() {
        sb = new StringBuilder();
        cache = new TreeData();
        strCache = new HashMap<String, String>();
    }
    
    StringBuilder getStringBuilder(){
        return sb;
    }

    String toJava(IDatatype dt) {
        switch (dt.getCode()) {
            case IDatatype.URI:
                return resource(dt);
            case IDatatype.INTEGER:
                return genericInteger(dt);
            case IDatatype.LONG:
                return newInstance(dt.longValue());
            case IDatatype.DOUBLE:
                return newInstance(dt.doubleValue());
            case IDatatype.DECIMAL:
                return newDecimal(dt.doubleValue());
            case IDatatype.FLOAT:
                return newInstance(dt.floatValue());
            case IDatatype.BOOLEAN:
                return newInstance(dt.booleanValue());
            case IDatatype.STRING:
                return string(dt);
            case IDatatype.LITERAL:
            case IDatatype.DATE:
            case IDatatype.DATETIME:
                return newLiteral(dt);
            case IDatatype.UNDEF:
                if (dt.isList()) {
                    return list(dt);
                }
        }

        return newInstance(dt.stringValue());
    }
    
    String genericInteger(IDatatype dt){
        if (dt.getDatatypeURI().equals(RDF.xsdinteger)){
            return newInteger(dt.longValue());
        }
        else {
            return newLiteral(dt);
        }
    }

    String list(IDatatype dt) {
        return list(dt.getValues());
    }

    String list(List<IDatatype> list) {
        StringBuilder sb = new StringBuilder();
        boolean rst = false;
        for (IDatatype dt : list) {
            if (rst) {
                sb.append(", ");
            } else {
                rst = true;
            }
            sb.append(toJava(dt));
        }
        return String.format("DatatypeMap.newList(%s)", sb);
    }
    
    String getVariable(){
        return VAR + count++;
    }
    
    StringBuilder append(String val){
        return sb.append(val);
    }
    
    void nl(){
        append(NL);
    }
    
    /**
     * Generate a global variable for an URI, return the variable
     */
    String resource(IDatatype dt){
        String var = cache.get(dt);
        if (var == null){
            var = getVariable(dt, newResource(dt.stringValue()));
        }
        return var;
    }
    
    /**
     * Generate a global variable for a xsd:string, return the variable
     */
    String string(IDatatype dt){
        String var = cache.get(dt);
        if (var == null){
            var = getVariable(dt, newInstance(dt.stringValue()));
        }
        return var;
    }
     
     /**
     * Generate a global variable for a name (variable name, function name, etc.), return the variable
     */
    String string(String value) {
        String var = strCache.get(value);
        if (var == null){
            var = getVariable();
            strCache.put(value, var);
            append(String.format("static final IDatatype %s = %s;", var, newInstance(value)));
            nl();
        }
        return var;
    }
    
    /**
     * 
     */
    String getVariable(IDatatype dt, String value){
        String var = getVariable();
        cache.put(dt, var);
        append(String.format("static final IDatatype %s = %s;", var, value));
        nl();
        return var;
    }

    String newResource(String val) {
        return String.format("DatatypeMap.newResource(\"%s\")", val);
    }

    String newLiteral(IDatatype dt) {
        if (dt.hasLang()) {
            return String.format("DatatypeMap.newInstance(\"%s\", %s , \"%s\")", dt.stringValue(), null, dt.getLang());
        } else {
            return String.format("DatatypeMap.newInstance(\"%s\", \"%s\")", dt.stringValue(), dt.getDatatypeURI());
        }
    }
    
    String newInstance(int val) {
        return newInteger(val);
    }

    String newInteger(long val) {
        switch ((int)val) {
            case 0:
                return "DatatypeMap.ZERO";
            case 1:
                return "DatatypeMap.ONE";
            case 2:
                return "DatatypeMap.TWO";
            case 3:
                return "DatatypeMap.THREE";
            case 4:
                return "DatatypeMap.FOUR";
            case 5:
                return "DatatypeMap.FIVE";
            case 6:
                return "DatatypeMap.SIX";
            case 7:
                return "DatatypeMap.SEVEN";
            case 8:
                return "DatatypeMap.EIGHT";
            case 9:
                return "DatatypeMap.NINE";
        }
        return String.format("DatatypeMap.newInteger(%s)", val);
    }
    
    String newDecimal(double val) {
        return String.format("DatatypeMap.newDecimal(%s)", val);
    }

    String newInstance(String val) {
        return String.format("DatatypeMap.newInstance(\"%s\")", val);
    }

    String newInstance(double val) {
        return String.format("DatatypeMap.newDouble(%s)", val);
    }

    String newInstance(float val) {
        return String.format("DatatypeMap.newFloat(%s)", val);
    }
    
    String newInstance(long val) {
        return String.format("DatatypeMap.newLong(%s)", val);
    }

    String newInstance(boolean val) {
        if (val) {
            return "DatatypeMap.TRUE";
        } else {
            return "DatatypeMap.FALSE";
        }
    }

    
    
    
    
    class TreeData extends TreeMap<IDatatype, String> {

        TreeData() {
            super(new Compare());
        }
    }

    class Compare implements Comparator<IDatatype> {

        @Override
        public int compare(IDatatype dt1, IDatatype dt2) {
            return dt1.compareTo(dt2);
        }
    }
}
