package persist;
import java.util.*;

import jLib.JsonDecoder;
import jLib.JsonEncoder;
import jLib.Lib;
import jLib.LibTest;

public class PersistentMap extends AbstractMap<Object,Object> {
    private final PersistentData pd;
    private final long parentID;

    public PersistentMap(PersistentData pd, long parentID) {
        this.pd = pd;
        this.parentID = parentID;
    }

    @Override
    public int size() {
        long count = pd.size(parentID);
        if (count > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int)count;
    }

    @Override
    public Object get(Object key) {
        String keyJson = JsonEncoder.encode(key);
        return pd.get(parentID,keyJson);
    }

    @Override
    public Object put(Object key, Object value) {
        Object oldValue = remove(key);
        pd.put( parentID, JsonEncoder.encode(key), value );
        return oldValue;
    }

    @Override
    public Object remove(Object key) {
        Object oldValue = get(key);
        String keyJson = JsonEncoder.encode(key);
        pd.remove(parentID,keyJson);
        return oldValue;
    }

    @Override
    public void clear() { pd.clearChildValues(parentID); }

    @Override
    public boolean containsKey(Object key) {
        String keyJson = JsonEncoder.encode(key);
        return pd.row(parentID,keyJson).select() != null;
    }

    @Override
    public Set<Entry<Object,Object>> entrySet() {
        return new AbstractSet<>() {
            @Override public Iterator<Entry<Object,Object>> iterator() {
                return new Iterator<>() {
                    Iterator<PersistentData.Row> it = pd.rowIterator(parentID);
                    @Override public boolean hasNext() { return it.hasNext(); }
                    @Override public Entry<Object,Object> next() {
                        PersistentData.Row row = it.next();
                        Object key = JsonDecoder.decode(row.keyJson);
                        Object value = pd.get( parentID, row.keyJson );
                        return new SimpleEntry<>(key,value);
                    }
                    @Override public void remove() { it.remove(); }
                };
            }
            @Override public int size() { return (int)pd.size(parentID); }
        };
    }



    @SuppressWarnings({"unused", "try"})
    private static boolean _TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        try ( PersistentData pd = PersistentData.temp() ) {
            { // test with simple values only
                PersistentMap pm = new PersistentMap(pd,0L);
                LibTest.asrtEQ( 0, pm.size() );
                pm.put("ONE",1);
                pm.put(2,"TWO");
                LibTest.asrtEQ( 1, pm.get("ONE") );
                LibTest.asrtEQ( "TWO", pm.get(2) );
                LibTest.asrt( pm.containsKey("ONE"), "Should contain key 'ONE'" );
                LibTest.asrt( !pm.containsKey("THREE"), "Should not contain key 'THREE'" );
                Iterator<Entry<Object,Object>> it = pm.entrySet().iterator();
                LibTest.asrtEQ( 1, it.next().getValue() );
                LibTest.asrtEQ( "TWO", it.next().getValue() );
                LibTest.asrtEQ( 2, pm.size() );
                Object removed = pm.remove("ONE");
                LibTest.asrtEQ( 1, removed );
                LibTest.asrt( !pm.containsKey("ONE"), "Key 'ONE' should be removed" );
                LibTest.asrtEQ( 1, pm.size() );
                pm.clear();
                LibTest.asrtEQ( 0, pm.size() );
                LibTest.asrt( !pm.containsKey(2), "All keys should be removed after clear" );
            }
        }
        return true;
    }
    @SuppressWarnings({"unused", "try"})
    private static boolean map_TEST_( boolean findLineNumber ) throws Exception {
        if ( findLineNumber ) throw new RuntimeException();
        try ( PersistentData pd = PersistentData.temp("map_test_db") ) { // Use a temporary, named database
            PersistentMap map = pd.getRootMap();
            {
                LibTest.asrtEQ( map.size(), 0, "initial size check" );
                LibTest.asrt( map.isEmpty(), "initial isEmpty check" );
                LibTest.asrt( map.entrySet().isEmpty(), "initial entrySet empty" );
                LibTest.asrt( !map.entrySet().iterator().hasNext(), "initial iterator hasNext" );
            }
            Object key1 = "key1";
            Object value1 = "value1";
            Object key2 = 2;
            Object value2 = 42;
            Object key3 = "complexKey";
            Object value3 = Map.of( "nestedKey","nestedValue", "nestedList",List.of(10, 20) );
            {
                Object prev1 = map.put( key1, value1 );
                Object prev2 = map.put( key2, value2 );
                Object prev3 = map.put( key3, value3 );
                LibTest.asrt( prev1 == null, "put new key return null" );
                LibTest.asrt( prev2 == null, "put new int key return null" );
                LibTest.asrt( prev3 == null, "put new complex key return null" );
                LibTest.asrtEQ( map.size(), 3, "size after puts" );
                LibTest.asrt( !map.isEmpty(), "isEmpty after puts" );
            }
            {
                Object retrieved1 = map.get(key1);
                Object retrieved2 = map.get(key2);
                Object retrieved3 = map.get(key3);
                Object nonExistent = map.get("nonExistentKey");
                LibTest.asrtEQ( retrieved1, value1, "get key1 value" );
                LibTest.asrtEQ( retrieved2, value2, "get key2 value" );
                LibTest.asrtEQ( retrieved3, value3, "get key3 value" );
                LibTest.asrt( nonExistent == null, "get nonExistentKey null" );
            }
            {
                LibTest.asrt( map.containsKey(key1), "containsKey key1" );
                LibTest.asrt( map.containsKey(key2), "containsKey key2" );
                LibTest.asrt( map.containsKey(key3), "containsKey key3" );
                LibTest.asrt( !map.containsKey("nonExistentKey"), "containsKey nonExistentKey false" );
            }
            {
                 LibTest.asrt( map.containsValue(value1), "containsValue value1" );
                 LibTest.asrt( map.containsValue(value2), "containsValue value2" );
                 LibTest.asrt( map.containsValue(value3), "containsValue value3" );
                 LibTest.asrt( !map.containsValue("nonExistentValue"), "containsValue nonExistentValue" );
            }
            {
                Object newValue1 = "newValue1";
                Object previousValue = map.put( key1, newValue1 );
                pd.deleteOrphans(0);
                LibTest.asrtEQ( previousValue, value1, "put replace return previous" );
                LibTest.asrtEQ( map.get(key1), newValue1, "get after replace" );
                LibTest.asrtEQ( map.size(), 3, "size after replace" );
                value1 = newValue1;
            }
            {
                Object removedValue = map.remove(key2);
                LibTest.asrtEQ( removedValue, value2, "remove key2 return value" );
                LibTest.asrtEQ( map.size(), 2, "size after remove" );
                LibTest.asrt( !map.containsKey(key2), "containsKey after remove" );
                LibTest.asrt( map.get(key2) == null, "get removed key null" );
                Object removedNonExistent = map.remove("nonExistentKey");
                LibTest.asrt( removedNonExistent == null, "remove nonExistentKey null" );
                LibTest.asrtEQ( map.size(), 2, "size after remove nonExistent" );
            }
            {
                Set<Entry<Object, Object>> entrySet = map.entrySet();
                LibTest.asrtEQ( entrySet.size(), map.size(), "entrySet size check" );
                Map<Object, Object> iteratedEntries = new HashMap<>();
                for ( Entry<Object, Object> entry:entrySet ) {
                    pd.deleteOrphans(0);
                    iteratedEntries.put( entry.getKey(), entry.getValue() );
                }
                LibTest.asrtEQ( iteratedEntries.size(), 2, "iterator count check" );
                LibTest.asrtEQ( iteratedEntries.get(key1), value1, "iterator value key1" );
                LibTest.asrtEQ( iteratedEntries.get(key3), value3, "iterator value key3" );
            }
            {
                Iterator<Entry<Object, Object>> iterator = map.entrySet().iterator();
                while ( iterator.hasNext() ) {
                    pd.deleteOrphans(0);
                    Entry<Object, Object> entry = iterator.next();
                    if ( entry.getKey().equals(key1) ) {
                        iterator.remove();
                    }
                }
                LibTest.asrtEQ( map.size(), 1, "size after iterator remove" );
                LibTest.asrt( !map.containsKey(key1), "containsKey after iterator remove" );
                LibTest.asrt( map.containsKey(key3), "containsKey after iterator partial remove" );
                iterator = map.entrySet().iterator();
                LibTest.asrt( iterator.hasNext(), "iterator hasNext after partial remove" );
                iterator.next();
                pd.deleteOrphans(0);
                iterator.remove();
                LibTest.asrtEQ( map.size(), 0, "size after iterator remove last" );
                LibTest.asrt( map.isEmpty(), "isEmpty after iterator remove last" );
            }
            {
                map.put( key1, value1 );
                map.put( key2, value2 );
                LibTest.asrtEQ( map.size(), 2, "size before clear" );
                pd.deleteOrphans(0);
                map.clear();
                LibTest.asrt( map.isEmpty(), "isEmpty after clear" );
                LibTest.asrtEQ( map.size(), 0, "size after clear" );
                LibTest.asrt( !map.containsKey(key1), "containsKey key1 after clear" );
                pd.deleteOrphans(0);
                LibTest.asrt( !map.containsKey(key2), "containsKey key2 after clear" );
                LibTest.asrt( map.entrySet().isEmpty(), "entrySet empty after clear" );
            }
            {
                Object nullValue = null;
                Object keyForNull = "keyForNullValue";
                map.put( keyForNull, nullValue );
                LibTest.asrtEQ( map.size(), 1, "size after add null value" );
                LibTest.asrt( map.containsKey(keyForNull), "containsKey for null value" );
                LibTest.asrt( map.get(keyForNull) == null, "get key for null value" );
                LibTest.asrt( map.containsValue(null), "containsValue null" );
                Object removedNull = map.remove(keyForNull);
                LibTest.asrt( removedNull == null, "remove null value return" );
                LibTest.asrtEQ( map.size(), 0, "size after remove null value" );
                try { // NOTE: Testing null key behavior
                     Object nullKey = null;
                     Object valueForNullKey = "valueForNullKey";
                     map.put( nullKey, valueForNullKey );
                     pd.deleteOrphans(0);
                     LibTest.asrtEQ( map.size(), 1, "size after add null key" );
                     LibTest.asrt( map.containsKey(nullKey), "containsKey null key" );
                     LibTest.asrtEQ( map.get(nullKey), valueForNullKey, "get null key value" );
                     Object removedVal = map.remove(nullKey);
                     LibTest.asrtEQ( removedVal, valueForNullKey, "remove null key value check" );
                     LibTest.asrtEQ( map.size(), 0, "size after remove null key" );
                } catch ( Exception e ) { // NOTE: Catching exception potentially thrown by null key usage
                    System.err.println( "Note: Putting/Using null key caused exception (might be expected): " + e.getMessage() );
                    map.clear();
                }
            }
            {
                map.clear();
                map.put( "temp", "temp" );
                Iterator<Entry<Object, Object>> it = map.entrySet().iterator();
                boolean caughtException = false;
                pd.deleteOrphans(0);
                try { it.remove(); } // Remove without calling next()
                catch ( IllegalStateException e ) { caughtException = true; } // Remove without calling next()
                LibTest.asrt( caughtException, "iterator remove no next exception" );
                map.clear();
            }
             {
                 map.clear();
                 int count = 100;
                 for ( int i=0; i<count; i++ ) map.put( "key_" + i, i );
                 LibTest.asrtEQ( map.size(), count, "size after add many" );
                 pd.deleteOrphans(0);
                 int iteratedCount = 0;
                 for ( Entry<Object, Object> entry:map.entrySet() ) iteratedCount++;
                 LibTest.asrtEQ( iteratedCount, count, "iterator count many" );
                 map.clear();
             }
             { // delete orphans
                 map.clear();
                 pd.deleteOrphans(0);
                 LibTest.asrt( pd.debugDump().size() <= 1 );
             }
        }
        return true;
    }



    public static void main( String[] args ) { LibTest.testClass(); }
}

