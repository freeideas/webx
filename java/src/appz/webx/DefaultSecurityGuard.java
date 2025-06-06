package appz.webx;
import http.*;
import jLib.*;


public class DefaultSecurityGuard extends SecurityGuard {


    public DefaultSecurityGuard() {
        // Add default security rules here
        // Example: require Host header
        rules.add( req -> req.headerBlock.headers.containsKey("Host") );
        
        // Example: block requests with suspicious paths
        rules.add( req -> {
            String path = req.headerBlock.getRequestPath();
            return !path.contains("..") && !path.contains("//");
        });
    }


    public static boolean basic_TEST_() {
        DefaultSecurityGuard guard = new DefaultSecurityGuard();
        
        // Test with valid request
        java.util.Map<String,String> headers = new java.util.HashMap<>();
        headers.put("Host", "example.com");
        HttpHeaderBlock headerBlock = new HttpHeaderBlock("GET", "/test", headers);
        HttpRequest request = new HttpRequest(headerBlock, new byte[0]);
        Lib.asrt( guard.test(request) );
        
        // Test without Host header
        headers.clear();
        headerBlock = new HttpHeaderBlock("GET", "/test", headers);
        request = new HttpRequest(headerBlock, new byte[0]);
        Lib.asrt( ! guard.test(request) );
        
        // Test with suspicious path
        headers.put("Host", "example.com");
        headerBlock = new HttpHeaderBlock("GET", "/../etc/passwd", headers);
        request = new HttpRequest(headerBlock, new byte[0]);
        Lib.asrt( ! guard.test(request) );
        
        return true;
    }


    public static void main( String[] args ) throws Exception { Lib.testClass(); }
}