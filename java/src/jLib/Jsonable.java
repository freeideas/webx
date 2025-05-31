package jLib;
import java.util.*;



/**
 * Wraps any JSON-compatible object (Map, List, String, Number, Boolean, null) and provides Map-like access.
 */
@SuppressWarnings({"unchecked"})
public class Jsonable extends AbstractMap<Object,Object> {
    private Object data;


    public Jsonable( Object data ) { this.data = data; }


    public Object get() { return data; }


    @Override
    public Object get( Object key ) {
        if ( key instanceof Object[] || key instanceof List ) {
            return Jsonable.get( data, key );
        }
        if ( data instanceof Map ) {
            Map<?,?> map = (Map<?,?>) data;
            if ( map.containsKey( key ) ) return map.get( key );
            if ( key instanceof String && ((String)key).contains("/") ) {
                return getWithSlashPath( (String) key );
            }
            return null;
        }
        if ( data instanceof List ) {
            List<?> list = (List<?>) data;
            Integer index = null;
            if ( key instanceof Integer ) index = (Integer) key;
            else if ( key instanceof Number ) index = ((Number) key).intValue();
            else if ( key instanceof String ) {
                try { index = Integer.parseInt( (String) key ); }
                catch ( NumberFormatException e ) { return null; }
            }
            if ( index!=null && index>=0 && index<list.size() ) return list.get( index );
        }
        return null;
    }
    
    
    private Object getWithSlashPath( String path ) {
        String[] keys = path.split( "/" );
        Object current = data;
        for ( String key : keys ) {
            if ( current instanceof Jsonable ) current = ((Jsonable) current).data;
            if ( current instanceof Map ) {
                Map<?,?> map = (Map<?,?>) current;
                current = map.get( key );
                if ( current==null ) {
                    try {
                        Integer intKey = Integer.parseInt( key );
                        current = map.get( intKey );
                    } catch ( NumberFormatException e ) {}
                }
            } else if ( current instanceof List ) {
                try {
                    int index = Integer.parseInt( key );
                    List<?> list = (List<?>) current;
                    if ( index>=0 && index<list.size() ) current = list.get( index );
                    else return null;
                } catch ( NumberFormatException e ) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }
    
    
    public Object get( Object... keys ) {
        return Jsonable.get( data, keys );
    }


    @Override
    public Object put( Object key, Object value ) {
        if ( data instanceof Map ) {
            Map<Object,Object> map = (Map<Object,Object>) data;
            Object existing = map.get( key );
            try {
                if ( existing!=null && value!=null ) {
                    Object merged = merge( value, existing, true );
                    return map.put( key, merged );
                }
                return map.put( key, value );
            } catch ( UnsupportedOperationException e ) {
                map = new LinkedHashMap<>( map );
                data = map;
                if ( existing!=null && value!=null ) {
                    Object merged = merge( value, existing, true );
                    return map.put( key, merged );
                }
                return map.put( key, value );
            }
        }
        if ( data instanceof List && key instanceof Integer ) {
            List<Object> list = (List<Object>) data;
            int index = (Integer) key;
            if ( index>=0 && index<list.size() ) {
                Object existing = list.get( index );
                try {
                    if ( existing!=null && value!=null ) {
                        Object merged = merge( value, existing, true );
                        list.set( index, merged );
                        return existing;
                    }
                    Object old = list.get( index );
                    list.set( index, value );
                    return old;
                } catch ( UnsupportedOperationException e ) {
                    list = new ArrayList<>( list );
                    data = list;
                    if ( existing!=null && value!=null ) {
                        Object merged = merge( value, existing, true );
                        list.set( index, merged );
                        return existing;
                    }
                    Object old = list.get( index );
                    list.set( index, value );
                    return old;
                }
            }
        }
        throw new UnsupportedOperationException( "Cannot put to " + data.getClass().getSimpleName() );
    }


    @Override
    public Set<Entry<Object,Object>> entrySet() {
        if ( data instanceof Map ) return ((Map<Object,Object>) data).entrySet();
        if ( data instanceof List ) {
            List<?> list = (List<?>) data;
            Set<Entry<Object,Object>> entries = new LinkedHashSet<>();
            for ( int i=0; i<list.size(); i++ ) entries.add( new SimpleEntry<>( i, list.get( i ) ) );
            return entries;
        }
        return Collections.emptySet();
    }


    @Override
    public int size() {
        if ( data instanceof Map ) return ((Map<?,?>) data).size();
        if ( data instanceof List ) return ((List<?>) data).size();
        return 0;
    }


    @Override
    public boolean containsKey( Object key ) {
        if ( data instanceof Map ) return ((Map<?,?>) data).containsKey( key );
        if ( data instanceof List && key instanceof Integer ) {
            int index = (Integer) key;
            return index>=0 && index<((List<?>) data).size();
        }
        return false;
    }


    @Override
    public Object remove( Object key ) {
        if ( data instanceof Map ) return ((Map<?,?>) data).remove( key );
        if ( data instanceof List && key instanceof Integer ) {
            List<?> list = (List<?>) data;
            int index = (Integer) key;
            if ( index>=0 && index<list.size() ) return ((List<?>) data).remove( index );
        }
        return null;
    }


    @Override
    public void clear() {
        if ( data instanceof Map ) ((Map<?,?>) data).clear();
        else if ( data instanceof List ) ((List<?>) data).clear();
    }


    @Override
    public Set<Object> keySet() {
        if ( data instanceof Map ) return ((Map<Object,?>) data).keySet();
        if ( data instanceof List ) {
            List<?> list = (List<?>) data;
            Set<Object> keys = new LinkedHashSet<>();
            for ( int i=0; i<list.size(); i++ ) keys.add( i );
            return keys;
        }
        return Collections.emptySet();
    }


    @Override
    public Collection<Object> values() {
        if ( data instanceof Map ) return ((Map<?,Object>) data).values();
        if ( data instanceof List ) return new ArrayList<>( (List<?>) data );
        return Collections.emptyList();
    }


    /**
     * Follows every part of key into data, returning the value found at the end.
     * @param data a Map or List, which may contain Maps or Lists, etc.
     * @param keys a sequence of keys or indexes to traverse the tree.
     * Note: key can be a String, Collection, or Array, and/or a "/" delimited String
    **/
    public static Object get( Object data, Object keys ) {
        if ( data==null ) return null;
        if ( keys!=null && keys.getClass().isArray() ) return get( data, asList( keys ) );
        if ( keys instanceof String s ) return get( data, s.split( "/" ) );
        LinkedList<Object> keyList = new LinkedList<>( asList( keys ) );
        while ( !keyList.isEmpty() ) {
            Object key = keyList.removeFirst();
            if ( data!=null && data.getClass().isArray() ) data = asList( data );
            if ( data instanceof Map m ) {
                Object result = m.get( key );
                if ( result==null ) {
                    if ( key instanceof String ) {
                        try {
                            Integer intKey = Integer.parseInt( (String) key );
                            result = m.get( intKey );
                        } catch ( NumberFormatException e ) {}
                    } else if ( key instanceof Number ) {
                        result = m.get( key.toString() );
                    }
                }
                if ( keyList.isEmpty() ) return result;
                data = result;
            } else if ( data instanceof List lst ) {
                if ( key==null ) return null;
                String s = key.toString();
                Integer index = Integer.parseInt( s );
                if ( index<0 ) index = lst.size() + index;
                if ( index<0 || index>=lst.size() ) return null;
                if ( keyList.isEmpty() ) return lst.get( index );
                data = lst.get( index );
            } else {
                return null;
            }
        }
        return data;
    }
    @SuppressWarnings("unused")
    private static boolean get_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Object data = JsonDecoder.decode("""
            { "1":["one"], "2":{"two":[1,2,3,"dos"]} }
        """);
        Object key = new Object[]{ "2", "two", 3 };
        Object result = get(data,key);
        Object expected = "dos";
        Lib.asrtEQ(result,expected);
        return true;
    }




    /**
     * If both are Maps, then copies entries from copyFrom into copyInto, and returns copyInto.
     * If both are Lists, then copies each element of copyFrom to copyInto at the same index, and returns copyInto.
     * Otherwise, returns copyFrom.
     * If modifyCopyInto is true but some of the underlying data structures are immutable,
     * then a new copy is created as needed.
     **/
    public static Object merge( Object copyFrom, Object copyInto, Boolean modifyCopyInto ) {
        if (modifyCopyInto==null) modifyCopyInto=false;
        if ( copyFrom instanceof Map && copyInto instanceof Map ) {
            Map<Object,Object> fromMap = (Map<Object,Object>) copyFrom;
            Map<Object,Object> intoMap = (Map<Object,Object>) copyInto;
            for ( Map.Entry<Object,Object> entry : fromMap.entrySet() ) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if ( intoMap.containsKey(key) ) value = merge( value, intoMap.get(key), modifyCopyInto );
                try {
                    intoMap.put(key,value);
                } catch ( RuntimeException re ) {
                    Map<Object,Object> newIntoMap = new LinkedHashMap<>();
                    newIntoMap.putAll(intoMap);
                    intoMap = newIntoMap;
                    intoMap.put(key,value);
                }
            } return intoMap;
        }
        if ( copyFrom instanceof List && copyInto instanceof List ) {
            List<Object> fromList = (List<Object>) copyFrom;
            List<Object> intoList = (List<Object>) copyInto;
            for (int i=0; i<fromList.size(); i++) {
                Object value = fromList.get(i);
                if ( i<intoList.size() ) value = merge( value, intoList.get(i), modifyCopyInto );
                try {
                    if ( i< intoList.size() ) { intoList.set(i,value); }
                    else { intoList.add(value); }
                } catch ( RuntimeException re ) {
                    List<Object> newIntoList = new ArrayList<>();
                    newIntoList.addAll(intoList);
                    intoList = newIntoList;
                    if ( i< intoList.size() ) { intoList.set(i,value); }
                    else { intoList.add(value); }
                }
            } return intoList;
        }
        return copyFrom;
    }
    @SuppressWarnings("unused")
    private static boolean merge_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        { // simple map
            Map<Object,Object> srcMap = JsonDecoder.decodeMap("""
                { "one":1, "two":2, "three":3 }
            """);
            Map<Object,Object> tgtMap = JsonDecoder.decodeMap("""
                { "three":0, "four":4, "five":5 }
            """);
            Object result = merge(srcMap,tgtMap,true);
            assert result == tgtMap;
            Map<Object,Object> expected = JsonDecoder.decodeMap("""
                { "one":1, "two":2, "three":3, "four":4, "five":5 }
            """);
            Lib.asrtEQ(expected,result);
        }
        { // simple lists
            Object src = JsonDecoder.decode("""
                [1,2,3,4]
            """);
            Object dst = JsonDecoder.decode("""
                [-3,-2]
            """);
            Object result = merge(src,dst,true);
            Object expected = JsonDecoder.decode("""
                [1,2,3,4]
            """);
            Lib.asrtEQ(expected,result);
        }
        { // mixture of lists and maps
            Object src = JsonDecoder.decode("""
                { "a":[1,2,3], "b":2, "c":{1:"one",2:"two"} }
            """);
            Object dst = JsonDecoder.decode("""
                { "a":[1,2,3,4], "b":[1,2], "c":{3:"three",1:"ONE"}, "d":4 }
            """);
            Object result = merge(src,dst,true);
            Object expected = JsonDecoder.decode("""
                { "a":[1,2,3,4], "b":2, "c":{3:"three",1:"one",2:"two"}, "d":4 }
            """);
            String expStr = JsonEncoder.encode(expected);
            String resStr = JsonEncoder.encode(result);
            Lib.asrtEQ(expStr,resStr);
        }
        return true;
    }



    private static <T> List<T> asList( Object arr ) {
        if ( arr instanceof List ) return (List<T>) arr;
        if ( arr==null || !arr.getClass().isArray() ) return List.of( (T) arr );
        int len = java.lang.reflect.Array.getLength( arr );
        List<T> list = new ArrayList<>( len );
        for ( int i=0; i<len; i++ ) list.add( (T) java.lang.reflect.Array.get( arr, i ) );
        return list;
    }


    public static boolean test_TEST_() throws Exception {
        { // test with simple keys
            Map<String,Object> map = new HashMap<>();
            map.put( "key1", "value1" );
            map.put( "key2", 42 );
            
            Jsonable jsonableMap = new Jsonable( map );
            Lib.asrtEQ( jsonableMap.get( "key1" ), "value1" );
            Lib.asrtEQ( jsonableMap.get( "key2" ), 42 );
            Lib.asrtEQ( jsonableMap.size(), 2 );
            Lib.asrt( jsonableMap.containsKey( "key1" ) );
            Lib.asrtEQ( jsonableMap.get(), map );
            
            List<String> list = new ArrayList<>( Arrays.asList( "a", "b", "c" ) );
            Jsonable jsonableList = new Jsonable( list );
            Lib.asrtEQ( jsonableList.get( 0 ), "a" );
            Lib.asrtEQ( jsonableList.get( 1 ), "b" );
            Lib.asrtEQ( jsonableList.get( 2 ), "c" );
            Lib.asrtEQ( jsonableList.get( 3 ), null );
            Lib.asrtEQ( jsonableList.size(), 3 );
            Lib.asrt( jsonableList.containsKey( 1 ) );
            Lib.asrt( !jsonableList.containsKey( 3 ) );
            Lib.asrtEQ( jsonableList.get(), list );
            
            jsonableList.put( 1, "B" );
            Lib.asrtEQ( jsonableList.get( 1 ), "B" );
            Lib.asrtEQ( jsonableList.get( "1" ), "B" );
            Lib.asrtEQ( jsonableList.get( 1.0 ), "B" );
            
            Jsonable jsonableString = new Jsonable( "hello" );
            Lib.asrtEQ( jsonableString.size(), 0 );
            Lib.asrtEQ( jsonableString.get(), "hello" );
            
            Map<String,Object> nestedMap = new HashMap<>();
            nestedMap.put( "inner", new HashMap<>( Map.of( "a",1, "b",2 ) ) );
            Jsonable jsonableNested = new Jsonable( nestedMap );
            jsonableNested.put( "inner", Map.of( "b",20, "c",3 ) );
            Map<?,?> innerMap = (Map<?,?>) jsonableNested.get( "inner" );
            Lib.asrtEQ( innerMap.get( "a" ), 1 );
            Lib.asrtEQ( innerMap.get( "b" ), 20 );
            Lib.asrtEQ( innerMap.get( "c" ), 3 );
            
            List<Object> nestedList = new ArrayList<>();
            nestedList.add( new HashMap<>( Map.of( "x",10 ) ) );
            Jsonable jsonableNestedList = new Jsonable( nestedList );
            jsonableNestedList.put( 0, Map.of( "y",20 ) );
            Map<?,?> mergedMap = (Map<?,?>) jsonableNestedList.get( 0 );
            Lib.asrtEQ( mergedMap.get( "x" ), 10 );
            Lib.asrtEQ( mergedMap.get( "y" ), 20 );
        }

        { // reading multi-valued keys
            Jsonable jsonable = new Jsonable( Map.of( "a", Map.of( 2, Map.of( "c", "ok" ) ) ) );
            Lib.asrtEQ( jsonable.get( "a" ), Map.of( 2, Map.of( "c", "ok" ) ) );
            Lib.asrtEQ( jsonable.get( new Object[]{"a", 2} ), Map.of( "c", "ok" ) );
            Lib.asrtEQ( jsonable.get( new Object[]{"a", 2, "c"} ), "ok" );
            Lib.asrtEQ( jsonable.get( new Object[]{"a",2,"c"} ), "ok" );
            Lib.asrtEQ( jsonable.get( List.of("a",2,"c") ), "ok" );
            Lib.asrtEQ( jsonable.get( "a/2" ), Map.of( "c", "ok" ) );
            Lib.asrtEQ( jsonable.get( "a/2/c" ), "ok" );
            jsonable = new Jsonable( List.of( Map.of( "a", List.of( 1, Map.of( "b", 2 ) ) ) ) );
            Lib.asrtEQ( jsonable.get( new Object[]{0, "a", 1} ), Map.of( "b", 2 ) );
            Lib.asrtEQ( jsonable.get( new Object[]{0, "a", 1, "b"} ), 2 );
            Lib.asrtEQ( jsonable.get( new Object[]{0,"a",1,"b"} ), 2 );
        }
        { // writing multi-valued keys
            Jsonable jsonable = new Jsonable( Map.of("one",1) );
            jsonable.put( "two", 2 );
            Lib.asrtEQ( jsonable, Map.of("one",1,"two",2) );
        }
        { // merge complex data into complex data
            Jsonable jsonable = new Jsonable( JsonDecoder.decode( """
                {
                    "data": {
                        "one":[1], "two":[1,2], "three":[1,2]
                    }
                }
            """) );
            jsonable.put( "data", JsonDecoder.decode( """
                {
                    "three":[1,2,3], "four":[1,2,3,4]
                }
            """) );
            Object expected = JsonDecoder.decode( """
                {
                    "data": {
                        "one":[1], "two":[1,2], "three":[1,2,3], "four":[1,2,3,4]
                    }
                }
            """);
            Lib.asrtEQ(jsonable,expected);
        }
        { // edge cases
            Jsonable jsonable = new Jsonable( Map.of( "one/2/three", List.of(1,2,3) ) );
            Lib.asrtEQ( jsonable.get( "one/2/three" ), List.of(1,2,3), """
                should check to see if key exists before assuming it is a multi-key path
            """ );
        }
        return true;
    }


    public static void main( String[] args ) { Lib.testClass(); }
}
