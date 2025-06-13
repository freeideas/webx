package http;
import java.util.*;
import jLib.*;

/**
 * HttpJsonHandler - A Universal HTTP Database Interface
 *
 * This class takes ANY plain old Java Map and makes it accessible via HTTP as a JSON API.
 * It's beautifully simple: give it a Map, and it becomes a RESTful database endpoint.
 *
 * The magic happens when we pass it a PersistentMap from persist.PersistentData:
 * - PersistentData makes a real SQL database (HSQLDB/SQLite) look like Java Maps and Lists
 * - Those Maps/Lists are backed by persistent storage but behave like normal collections
 * - HttpJsonHandler exposes them via HTTP with GET (read) and POST/PUT (write) operations
 * - Deep merging ensures nested objects are properly combined rather than replaced
 *
 * Architecture:
 * HTTP Request → HttpJsonHandler → PersistentMap → PersistentData → SQL Database
 *
 * The result: A complete database system that feels like working with simple Maps and Lists.
 * accessible from any HTTP client, with zero configuration or schema definition needed.
 * Just start the server and begin storing JSON data - it automatically persists forever.
 *
 * Usage:
 * GET /db → Returns entire database as JSON
 * POST /db + JSON → Merges JSON into database, returns updated database
 * PUT /db + JSON → Same as POST (merges data)
 *
 * Example:
 * POST /db: {"users": {"john": {"name": "John", "age": 30}}}
 * POST /db: {"users": {"john": {"email": "john@example.com"}}}
 * Result: {"users": {"john": {"name": "John", "age": 30, "email": "john@example.com"}}}
 */
public class HttpJsonHandler implements HttpHandler {



    private final Map<Object,Object> dataMap;



    public HttpJsonHandler( Map<Object,Object> dataMap ) {
        this.dataMap = dataMap;
    }



    @Override
    public HttpResponse handle( HttpRequest req ) {
        String method = req.headerBlock.getMethod();

        if ( "GET".equals(method) ) {
            // Return current data as JSON
            String jsonData = JsonEncoder.encode( dataMap );
            HttpHeaderBlock responseHeader = new HttpHeaderBlock( 200, "OK",
                Lib.mapOf("Content-Type", "application/json") );
            return new HttpResponse( responseHeader, jsonData.getBytes() );
        }

        if ( !("POST".equals(method) || "PUT".equals(method)) ) {
            return new HttpErrorResponse( 405, "Method Not Allowed" );
        }
        if ( req.parsedBody == null ) {
            return new HttpErrorResponse( 400, "Bad Request: Empty or invalid body" );
        }
        if ( !(req.parsedBody instanceof Map) ) {
            return new HttpErrorResponse( 400, "Bad Request: Body must be a JSON object" );
        }
        @SuppressWarnings("unchecked")
        Map<Object,Object> requestData = (Map<Object,Object>) req.parsedBody;

        // Debug: log database requests
        if ( requestData.size() > 0 ) {
            Lib.log( "Database POST received with keys: " + requestData.keySet() );
            if ( requestData.containsKey("test_results") ) {
                Lib.log( "Test results detected!" );
            }
        }

        mergeMap( requestData, dataMap );

        // Return merged data as JSON
        String jsonData = JsonEncoder.encode( dataMap );
        HttpHeaderBlock responseHeader = new HttpHeaderBlock( 200, "OK",
            Lib.mapOf("Content-Type", "application/json") );
        return new HttpResponse( responseHeader, jsonData.getBytes() );
    }



    public static void mergeMap( Map<Object,Object> source, Map<Object,Object> target ) {
        for ( Map.Entry<Object,Object> entry : source.entrySet() ) {
            Object key = entry.getKey();
            Object sourceValue = entry.getValue();
            if ( sourceValue instanceof Map ) {
                Object targetValue = target.get(key);
                if ( targetValue instanceof Map ) {
                    @SuppressWarnings("unchecked")
                    Map<Object,Object> targetMap = (Map<Object,Object>) targetValue;
                    @SuppressWarnings("unchecked")
                    Map<Object,Object> sourceMap = (Map<Object,Object>) sourceValue;
                    mergeMap( sourceMap, targetMap );
                } else {
                    target.put( key, sourceValue );
                }
            } else {
                target.put( key, sourceValue );
            }
        }
    }



    @SuppressWarnings("unused")
    private static boolean handle_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        Map<Object,Object> dataMap = new HashMap<>();
        dataMap.put( "existing", "value" );
        HttpJsonHandler handler = new HttpJsonHandler(dataMap);
        Map<String,String> headers = new HashMap<>();
        headers.put( "Content-Type", "application/json" );
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "POST /test HTTP/1.1", headers );
        String jsonBody = "{\"new\":\"data\",\"count\":42}";
        HttpMessage message = new HttpMessage( headerBlock, jsonBody.getBytes() );
        HttpRequest request = HttpRequest.newHttpRequest(message);
        HttpResponse response = handler.handle(request);
        Lib.asrt( response.headerBlock.firstLine.contains("200") );
        Lib.asrt( dataMap.get("existing").equals("value") );
        Lib.asrt( dataMap.get("new").equals("data") );
        Lib.asrt( dataMap.get("count").equals(42) );
        return true;
    }



    public static void main( String[] args ) throws Exception { Lib.testClass(); }



}
