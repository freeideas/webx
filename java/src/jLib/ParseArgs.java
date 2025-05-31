package jLib;
import java.util.*;


/**
 * ParseArgs - A flexible command-line argument parser with intelligent features:
 * 
 * - Supports multiple argument formats: -arg=value, --arg=value, arg=value
 * - Boolean arguments can be specified as just --arg (implies true)
 * - Argument name abbreviation: -p=8080 matches "port" parameter
 * - Type-safe getters for String, Integer, Float, Boolean, and multi-value lists
 * - Automatic help generation with beautiful ASCII table formatting
 * - Smart text wrapping that optimizes column widths for readability
 * - Default values with clear documentation in help output
 * - Descriptive parameter documentation shown in aligned columns
 *
 * Example: java MyApp --port=8080 --debug -f=config.txt --verbose
 */
public class ParseArgs {



    private String appName;
    private String descr;
    private String helpFooter;
    private String[] commandLine;
    private final Map< String, Map<String,Object> > name2usage = new LinkedHashMap<>();



    public ParseArgs( String[] commandLine ) {
        this.commandLine = commandLine;
    }



    public ParseArgs setAppName( String appName ) {
        this.appName = appName;
        return this;
    }
    public ParseArgs setDescr( String descr ) {
        this.descr = descr;
        return this;
    }
    public ParseArgs setHelpFooter( String helpFooter ) {
        this.helpFooter = helpFooter;
        return this;
    }



    public String getString( String keyName, String defaultValue, String argDesc ) {
        addUsage(keyName,"text",argDesc,defaultValue);
        String value = (String) findValue( keyName, "text" );
        if (value!=null) return value;
        return defaultValue;
    }
    public Integer getInteger( String keyName, Integer defaultValue, String argDesc ) {
        addUsage(keyName,"int",argDesc,defaultValue);
        Object value = findValue( keyName, "int" );
        if (value!=null) return (Integer)value;
        return defaultValue;
    }
    public Float getFloat( String keyName, Float defaultValue, String argDesc ) {
        addUsage(keyName,"float",argDesc,defaultValue);
        Object value = findValue( keyName, "float" );
        if (value!=null) return (Float)value;
        return defaultValue;
    }
    public Boolean getBoolean( String keyName, Boolean defaultValue, String argDesc ) {
        addUsage(keyName,"bool",argDesc,defaultValue);
        Object value = findValue( keyName, "bool" );
        if (value!=null) return (Boolean)value;
        return defaultValue;
    }
    public List<String> getMulti( String keyName, List<String> defaultValue, String argDesc ) {
        if (defaultValue==null) defaultValue = List.of();
        addUsage(keyName,"multi",argDesc,defaultValue);
        @SuppressWarnings("unchecked")
        List<String> value = (List<String>) findValue( keyName, "multi" );
        if (value!=null) return value;
        return Collections.unmodifiableList( new ArrayList<String>(defaultValue) );
    }



    public String getHelp() {
        if ( Lib.isEmpty(appName) ) appName =Lib.nvl( Lib.getMainExeFile(), "(this program)" );
        final var LINE_LEN = 78;
        StringBuilder sb = new StringBuilder();
        // print command-line args
        sb.append('\n');
        sb.append(descr).append('\n');
        sb.append('\n');
        sb.append("ARGS:");
        for (var attr : name2usage.entrySet() ) {
            var argName = (String)attr.getKey();
            var attrMap = attr.getValue();
            var argType = (String)attrMap.get("argType");
            var defaultValue = attrMap.get("defaultValue");
            sb.append(' ');
            if (defaultValue!=null) sb.append('[');
            sb.append( "-"+argName+"=("+argType+(argType.isBlank()?"":" ")+"value)" );
            if (defaultValue!=null) sb.append(']');
        }
        sb.append('\n');
        // print arg descriptions
        String[][] rows = new String[name2usage.size()+1][3];
        rows[0] = new String[]{"ARGNAME","TYPE","DESCRIPTION"};
        int entryIdx = 1;
        for (var attr : name2usage.entrySet() ) {
            var argName = (String)attr.getKey();
            var attrMap = attr.getValue();
            var argType = (String)attrMap.get("argType");
            var argDesc = (String)attrMap.get("argDesc");
            var defaultValue = attrMap.get("defaultValue");
            if (argType==null) argType="";
            if (argDesc==null) argDesc="";
            if (defaultValue==null) defaultValue="";
            var argDescr = argDesc;
            if (! Lib.isEmpty(defaultValue) ) {
                argDescr = argDescr+" (default: "+defaultValue+")";
            }
            rows[entryIdx++] = new String[]{
                argName, argType, argDescr
            };
        }
        String[][][] wrappedRows = wrap( rows, LINE_LEN );
        StringBuilder sepLine = new StringBuilder();
        for (int rowIdx=0; rowIdx<wrappedRows.length; rowIdx++) {
            String[][] row = wrappedRows[rowIdx];
            // add separator line
            for (int colIdx=0; colIdx<row.length; colIdx++) {
                int colLen = row[colIdx][0].length();
                if (colIdx>0) {
                    sb.append('|');
                    if (rowIdx==0) sepLine.append('|');
                }
                sb.append( "=".repeat(colLen) );
                if (rowIdx==0) sepLine.append( "=".repeat(colLen) );
            }
            sb.append('\n');
            if (rowIdx==0) sepLine.append('\n');
            // add row
            for (int lineIdx=0; lineIdx<row[0].length; lineIdx++) {
                for (int colIdx=0; colIdx<row.length; colIdx++) {
                    String fieldLine = row[colIdx][lineIdx];
                    if (colIdx>0) sb.append('|');
                    sb.append(fieldLine);
                }
                sb.append('\n');
            }
        }
        sb.append(sepLine);
        sb.append(Lib.normalSpace("""
            * Args can look like "-arg=str" or "arg=true", or for boolean, just "--arg".
        """)+"\n");
        sb.append( "* Argument names can be abbreviated.\n" );
        if (helpFooter!=null) sb.append(helpFooter);
        String s = sb.toString();
        return s;
    }



    private String[][][] wrap( String[][] lines, int lineLen ) {
        // Each element of the input is a row that consists of fields, which are strings.
        // This method will make each field into a String[] that is at most lineLen long.
        int rowCount = lines.length;
        if (rowCount==0) return new String[0][][];
        int colCount = lines[0].length;
        if (colCount==0) return new String[0][][];
        int lowestTotalFolds = Integer.MAX_VALUE;
        String[][][] bestLines = null;
        String[][][] linesCopy = new String[rowCount][colCount][];
        int[] bestWidths = null;
        // try every combination of column widths that add up to lineLen
        Iterable<int[]> it = () -> combinations(lineLen,colCount);
        OUTER_LOOP: for (int[] colWidths : it) {
            int totalFolds = 0;
            for (int rowIdx=0; rowIdx<lines.length; rowIdx++) {
                String[] row = lines[rowIdx];
                for (int colIdx=0; colIdx<row.length; colIdx++) {
                    String field = row[colIdx];
                    String[] wrapped = Lib.wrapText(field,colWidths[colIdx],false);
                    for (String word : wrapped) {
                        if ( word.length() > colWidths[colIdx] ) {
                            continue OUTER_LOOP; // doesn't fit
                        }
                    }
                    linesCopy[rowIdx][colIdx] = wrapped;
                    totalFolds += wrapped.length;
                }
            }
            if (totalFolds<lowestTotalFolds) {
                // we've found a better solution
                lowestTotalFolds = totalFolds;
                bestLines = (String[][][]) Lib.deepCopyArray(linesCopy);
                bestWidths = (int[]) Lib.deepCopyArray(colWidths);
            }
        }
        // center each field horizontally and vertically
        for (int rowIdx=0; rowIdx<bestLines.length; rowIdx++) {
            String[][] row = bestLines[rowIdx];
            int maxLines = 0;
            for (int colIdx=0; colIdx<row.length; colIdx++) {
                String[] fieldLines = row[colIdx];
                int lineCount = fieldLines.length;
                if (lineCount>maxLines) maxLines=lineCount;
            }
            for (int colIdx=0; colIdx<row.length; colIdx++) {
                String[] fieldLines = row[colIdx];
                fieldLines = centerHV( fieldLines, bestWidths[colIdx], maxLines );
                row[colIdx] = fieldLines;
            }
        }
        return bestLines;
    }

    private static String[] centerHV( String[] fieldLines, int width, int lineCount ) {
        List<String> lines = new ArrayList<>();
        lines.addAll( Arrays.asList(fieldLines) );
        boolean doCenterH = lines.size() <= 1;
        while ( lines.size() < lineCount ) {
            lines.add("");
            if ( lines.size() < lineCount ) lines.add(0,"");
        }
        for (int lineIdx=0,len=lines.size(); lineIdx<len; lineIdx++) {
            String line = lines.get(lineIdx);
            line = doCenterH ? Lib.centerPad(line,width) : Lib.rpad(line,width," ");
            lines.set(lineIdx,line);
        }
        return lines.toArray(new String[0]);
    }

    private static Iterator<int[]> combinations( int sum, int len ) {
        /**
        * Yields every combination of integers that add up to a given sum.
        **/
        if (len==0) return new ArrayList<int[]>().iterator();
        if (len==1) return Arrays.asList(new int[]{sum}).iterator();
        return new Iterator<int[]>() {
            int firstIdx = -1;
            Iterator<int[]> curIt = null;
            @Override
            public boolean hasNext() {
                if (curIt==null) nextIt();
                if (curIt==null) return false;
                if (curIt.hasNext()) return true;
                curIt = null;
                return hasNext();
            }
            @Override
            public int[] next() {
                if (! hasNext() ) throw new NoSuchElementException();
                int[] tail = curIt.next();
                int[] result = new int[ tail.length+1 ];
                result[0] = firstIdx;
                System.arraycopy( tail,0, result,1, tail.length );
                return result;
            }
            private void nextIt() {
                firstIdx++;
                curIt = null;
                if (firstIdx>sum) return;
                curIt = combinations( sum-firstIdx, len-1 );
                if (! curIt.hasNext() ) nextIt();
            }
        };
    }
    @SuppressWarnings("unused")
    private static boolean combinations_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        List<int[]> results = new ArrayList<>();
        for ( int[] comb: Lib.iterable( combinations(5,3) ) ) results.add(comb);
        Lib.asrt( results.size() == 21 );
        for ( int[] comb: results ) {
            Lib.asrt( comb.length == 3 );
            Lib.asrt( comb[0]+comb[1]+comb[2] == 5 );
        }
        return true;
    }



    /**
    * Type is one of: int, float, string, boolean, multi
    **/
    private ParseArgs addUsage(
        String argName, String argType, String argDesc, Object defaultValue
    ) {
        if ( Lib.isEmpty(argName) ) throw new IllegalArgumentException( "argName is required" );
        if ( Lib.isEmpty(argType) ) argType = "text";
        if ( Lib.isEmpty(argDesc) ) argDesc = "";
        if ( defaultValue==null ) defaultValue = argType.equals("multi") ? List.of() : null;
        Map<String,Object> attrMap = new LinkedHashMap<>();
        attrMap.put("argType",argType);
        attrMap.put("argDesc",argDesc);
        attrMap.put("defaultValue",defaultValue);
        name2usage.put(argName,attrMap);
        return this;
    }



    /**
    * Collects all values whose keys are prefixes of keyName.
    * Searches command line for "-keyName=value" or "--keyName=value" or "keyname=value".
    * If type is "multi", then the value is a list of strings, otherwise returns a String, Integer, Float, or Boolean.
    **/
    private Object findValue( String keyName, String type ) {
        if ( Lib.isEmpty(keyName) ) return null;
        keyName = keyName.trim().toLowerCase();
        if ( Lib.isEmpty(type) ) type = "text";
        type = type.trim().toLowerCase();
        List<Object> values = new ArrayList<>();
        for ( String argument: commandLine ) {
            String arg = argument.trim();
            while ( arg.startsWith("-") ) arg = arg.substring(1);
            String[] parts = arg.split( "=", 2 );
            if ( parts.length < 1 ) continue;
            String argName = parts[0];
            if (! keyName.startsWith( argName.trim().toLowerCase() ) ) continue;
            String argValue = parts.length > 1 ? parts[1] : "";
            if ( Lib.isEmpty(argValue) && type.equals("bool") ) argValue = "true";
            if ( Lib.isEmpty(argValue) ) continue;
            values.add(argValue);
        }
        Object defaultValue = name2usage.get(keyName).get("defaultValue");
        if ( values.isEmpty() ) return defaultValue;
        switch ( type ) {
            case "int": return Integer.parseInt( values.get(0).toString() );
            case "float": return Float.parseFloat( values.get(0).toString() );
            case "bool": return Lib.isTrue( values.get(0) );
            case "multi": return Collections.unmodifiableList(values);
            default: return values.get(0);
        }
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        {
            String[] args = new String[]{ "-a=10", "-b", "--ccc=a", "-dd=2.3", "e=A", "-ee=B", "--EEE=C" };
            ParseArgs p = new ParseArgs(args);
            p.setAppName("Test.exe");
            p.setDescr("This tests the arg parser");
            p.setHelpFooter("This is the footer");
            int aaa = p.getInteger( "aaa",1,"How many?" );
            boolean bbb = p.getBoolean( "bbb",false,"Is it on?" );
            String ccc = p.getString( "ccc","default CCC","What is it?" );
            float ddd = p.getFloat( "ddd",0.0f,"How much?" );
            List<String> eee = p.getMulti( "eee",List.of("e1","e2"),"What are they?" );
            String help = p.getHelp();
            System.out.println(help);
            Lib.asrtEQ( aaa, 10 );
            Lib.asrtEQ( bbb, true );
            Lib.asrtEQ( ccc, "a" );
            Lib.asrtEQ( ddd, 2.3f );
        }
        return true;
    }



    public static void main( String[] args ) throws Exception { Lib.testClass(); }
}
