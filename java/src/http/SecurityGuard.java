package http;
import java.util.function.Predicate;
import java.util.*;
import jLib.*;



public class SecurityGuard implements Predicate<HttpRequest> {



    public final List<Predicate<HttpRequest>> rules = new ArrayList<>();

    

    @Override
    public boolean test( HttpRequest request ) {
        for ( Predicate<HttpRequest> rule : rules ) {
            if (! rule.test(request) ) return false;
        }
        return true;
    }



    public static boolean basic_TEST_() {
        SecurityGuard guard = new SecurityGuard();
        Map<String,String> headers = new HashMap<>();
        headers.put("Host", "example.com");
        HttpHeaderBlock headerBlock = new HttpHeaderBlock("GET", "/", headers);
        HttpRequest request = new HttpRequest(headerBlock, new byte[0]);        
        LibTest.asrt( guard.test(request) );
        guard.rules.add( req -> req.headerBlock.headers.containsKey("Host") );
        LibTest.asrt( guard.test(request) );
        guard.rules.add( req -> req.headerBlock.headers.get("Host").equals("allowed.com") );
        LibTest.asrt( ! guard.test(request) );
        return true;
    }



    public static void main( String[] args ) throws Exception { LibTest.testClass(); }
}
