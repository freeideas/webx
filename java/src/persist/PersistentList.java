package persist;
import java.util.*;

import jLib.JsonEncoder;
import jLib.Lib;
import jLib.LibTest;

/**
 * NOTE: supports only data that can be translated to and from json.
 */
public class PersistentList extends AbstractList<Object> {
    private final PersistentData pd;
    private final long parentID;

    public PersistentList(PersistentData pd, long parentID) {
        this.pd = pd;
        this.parentID = parentID;
    }

    @Override
    public Object get(int index) {
        Long entryOrder = entryOrder(index,null);
        if (entryOrder==null) throw new IndexOutOfBoundsException();
        return pd.get(parentID,entryOrder);
    }

    @Override
    public int size() {
        long count = pd.size(parentID);
        count = Math.min( Integer.MAX_VALUE, count );
        return (int)count;
    }

    @Override
    public Object set(int index, Object element) {
        Long entryOrder = entryOrder(index,null);
        if (entryOrder==null) throw new IndexOutOfBoundsException();
        Object oldValue = pd.get(parentID,entryOrder);
        pd.put( parentID, entryOrder, element );
        return oldValue;
    }

    @Override
    public void add( int index, Object element ) {
        long[] minMaxEntryOrder = pd.getMinMaxEntryOrder(parentID);
        long size = minMaxEntryOrder==null ? 0 : 1 + minMaxEntryOrder[1] - minMaxEntryOrder[0];
        if (index==size) { // append
            long entryOrder = minMaxEntryOrder==null ? 0 : minMaxEntryOrder[1]+1;
            pd.put(parentID,entryOrder,element);
        } else { // insert
            Long entryOrder = entryOrder(index,minMaxEntryOrder);
            if (entryOrder==null) throw new IndexOutOfBoundsException();
            pd.insert(parentID,entryOrder,element);
        }
    }

    @Override
    public Object remove(int index) {
        Long entryOrder = entryOrder(index,null);
        if (entryOrder==null) throw new IndexOutOfBoundsException();
        Object oldValue = pd.get(parentID,entryOrder);
        pd.remove(parentID,entryOrder);
        return oldValue;
    }

    @Override
    public void clear() { pd.clearChildValues(parentID); }

    public void push( Object element ) { add(element); }
    public Object pop() { return remove( size()-1 ); }
    public void unshift( Object element ) { add( 0, element ); }
    public Object shift() { return remove(0); }

    private Long entryOrder( int index, long[] minMaxEntryOrder ) {
        if (minMaxEntryOrder==null) minMaxEntryOrder = pd.getMinMaxEntryOrder(parentID);
        if (minMaxEntryOrder==null) return null;
        long size = 1 + minMaxEntryOrder[1] - minMaxEntryOrder[0];
        long indexL = index;
        if (indexL<0) indexL = ( size - ( (-index) % size ) ); // NOTE: wrap around negative indexes
        if ( indexL<0 || indexL>=size ) return null;
        long entryOrder = indexL + minMaxEntryOrder[0];
        return entryOrder;
    }



    @SuppressWarnings({"unused", "try"})
    private static boolean _TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        try ( PersistentData pd = PersistentData.temp() ) {
            if (false) { // test with simple values
                PersistentList pL = pd.getRootList();
                Lib.asrtEQ( 0, pL.size() );
                pL.add(0);
                pL.add(5);
                pL.add(1,4);
                pL.add(1,1);
                pL.add(2,3);
                pL.add(1,2);
                pd.debugDump();
                Iterator<Object> it = pL.iterator();
                for (int i=0; i<6; i++) {
                    Lib.asrtEQ( i, pL.get(i) );
                    Lib.asrtEQ( i, it.next() );
                }
            }
            { // test with complex values
                PersistentList pL = new PersistentList(pd,0L);
                pL.add( List.of(1,2,3) );
                pL.add( Map.of("a",1,"b",2,"c",3) );
                List<Object> expected = List.of(
                    List.of(1,2,3),
                    Map.of("a",1,"b",2,"c",3)
                );
                String expectJson = JsonEncoder.encode(expected);
                String actualJson = JsonEncoder.encode(pL);
                Lib.asrtEQ( expectJson, JsonEncoder.encode(pL) );
            }
        }
        return true;
    }
    @SuppressWarnings({"unused", "try"})
    private static boolean list_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        try ( PersistentData pd = PersistentData.temp() ) {
            PersistentList list = pd.getRootList();
            { // initial state
                Lib.asrtEQ(list.size(), 0, "Cleared list should be empty");
                Lib.asrt(list.isEmpty(), "Cleared list should be empty");
            }
            { // add operation
                Object obj1 = "test1";
                Object obj2 = Integer.valueOf(42);
                Object obj3 = Map.of("key", "value");  // Using a JSON-compatible type instead
                list.add(obj1);
                list.add(obj2);
                list.add(obj3);
                Lib.asrtEQ(list.size(), 3, "List size incorrect after adds");
                Lib.asrt(!list.isEmpty(), "List should not be empty after adds");
            }
            { // get operation
                Object obj1 = list.get(0);
                Object obj2 = list.get(1);
                Object obj3 = list.get(2);
                Lib.asrtEQ(obj1, "test1", "Get operation returned incorrect element at index 0");
                Lib.asrtEQ(obj2, 42, "Get operation returned incorrect element at index 1");
                Lib.asrt(obj3 != null, "Get operation returned incorrect element at index 2");
            }
            { // contains operation
                Lib.asrt(list.contains("test1"), "Contains operation failed for obj1");
                Lib.asrt(list.contains(42), "Contains operation failed for obj2");
            }
            { // indexOf operation
                Lib.asrtEQ(list.indexOf("test1"), 0, "IndexOf returned incorrect index for obj1");
                Lib.asrtEQ(list.indexOf(42), 1, "IndexOf returned incorrect index for obj2");
            }
            { // remove by index
                Object removed = list.remove(1);
                Lib.asrtEQ(removed, 42, "Remove by index returned incorrect element");
                Lib.asrtEQ(list.size(), 2, "List size incorrect after remove by index");
                Lib.asrt(!list.contains(42), "List should not contain removed element");
            }
            { // remove by object
                boolean wasRemoved = list.remove("test1");
                Lib.asrt(wasRemoved, "Remove by object should return true");
                Lib.asrtEQ(list.size(), 1, "List size incorrect after remove by object");
                Lib.asrt(!list.contains("test1"), "List should not contain removed element");
            }
            { // clear operation
                list.clear();
                Lib.asrt(list.isEmpty(), "List should be empty after clear");
                Lib.asrtEQ(list.size(), 0, "List size should be 0 after clear");
            }
            { // add at index
                list.add("test1");
                list.add(0,42);
                Lib.asrtEQ(list.size(), 2, "List size incorrect after add at index");
                Lib.asrtEQ(list.get(0), 42, "Element at index 0 incorrect after add at index");
                Lib.asrtEQ(list.get(1), "test1", "Element at index 1 incorrect after add at index");
            }
            { // set operation
                Map<String, String> obj3 = Map.of("test", "value"); // Use a JSON-serializable object instead
                Object previous = list.set(0,obj3);
                Lib.asrtEQ(previous, 42, "Set operation returned incorrect previous element");
                Lib.asrtEQ(list.get(0), obj3, "Element at index 0 incorrect after set");
            }
            { // iterator
                list.clear();
                list.add("test1");
                list.add(42);
                Object obj3 = Map.of("key", "value");  // Changed from new Object()
                list.add(obj3);
                Iterator<Object> iterator = list.iterator();
                int count = 0;
                while (iterator.hasNext()) {
                    pd.deleteOrphans(0);
                    Object obj = iterator.next();
                    if (count==0) Lib.asrtEQ(obj, "test1", "Returned incorrect element at position 0");
                    if (count==1) Lib.asrtEQ(obj, 42, "Returned incorrect element at position 1");
                    if (count==2) Lib.asrtEQ(obj, obj3, "Returned incorrect element at position 2");
                    count++;
                }
                Lib.asrtEQ(count, 3, "Iterator didn't iterate through all elements");
            }
            { // iterator remove
                Iterator<Object> iterator = list.iterator();
                iterator.next();
                pd.deleteOrphans(0);
                iterator.remove();
                Lib.asrtEQ(list.size(), 2, "List size incorrect after iterator remove");
                Lib.asrt(!list.contains("test1"), "List should not contain element removed by iterator");
            }
            { // null handling
                list.add(null);
                Lib.asrtEQ(list.size(), 3, "List size incorrect after adding null");
                Lib.asrt(list.contains(null), "List should contain null after adding it");
            }
            { // exception handling
                boolean caughtException = false;
                try { list.get(10); }
                catch (IndexOutOfBoundsException e) { caughtException = true; }
                Lib.asrt(caughtException, "Should throw IndexOutOfBoundsException for invalid index");
            }
            { // subList operation
                list.clear();
                list.add("test1");
                pd.deleteOrphans(0);
                list.add(42);
                Object obj3 = Map.of("key", "value");  // Changed from new Object()
                list.add(obj3);
                List<Object> subList = list.subList(0,2);
                Lib.asrtEQ(subList.size(), 2, "SubList size incorrect");
                Lib.asrt(subList.contains("test1"), "SubList should contain obj1");
                Lib.asrt(subList.contains(42), "SubList should contain obj2");
                Lib.asrt(!subList.contains(obj3), "SubList should not contain obj3");
            }
            { // deque operations
                list.clear();
                list.unshift("(delete this)");
                list.unshift("FOUR");
                list.push("(delete this)");
                list.push("FIVE");
                pd.deleteOrphans(0);
                list.unshift("THREE");
                list.remove(2);
                list.remove(-2);
                list.unshift("TWO");
                list.unshift("ONE");
                String actualJson = JsonEncoder.encode(list);
                String expectJson = JsonEncoder.encode( List.of("ONE","TWO","THREE","FOUR","FIVE") );
                Lib.asrtEQ(expectJson,actualJson);
            }
            { // delete orphans
                list.clear();
                pd.deleteOrphans(0);
                Lib.asrt( pd.debugDump().size() <= 1 );
            }
        }
        return true;
    }



    public static void main( String[] args ) { LibTest.testClass(); }
}








