import jLib.Jsonable;
import java.util.*;

public class TestJsonable {
    public static void main(String[] args) {
        Jsonable jsonable = new Jsonable( Map.of( "a", Map.of( 2, Map.of( "c", "ok" ) ) ) );
        System.out.println("get(\"a\", 2) = " + jsonable.get("a", 2));
        System.out.println("get(new Object[]{\"a\",2}) = " + jsonable.get(new Object[]{"a",2}));
        
        // Direct static call
        Object data = Map.of( "a", Map.of( 2, Map.of( "c", "ok" ) ) );
        System.out.println("Jsonable.get(data, new Object[]{\"a\",2}) = " + Jsonable.get(data, new Object[]{"a",2}));
    }
}
