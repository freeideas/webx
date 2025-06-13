package http;
import java.io.*;
import java.util.*;
import javax.script.*;
import jLib.*;
import persist.*;



public class HttpJssHandler implements HttpHandler {



    private final String prefix;
    private final File rootDir;
    private final ScriptEngine engine;
    private final Map<Object,Object> database;



    public HttpJssHandler( String prefix, File rootDir ) {
        this( prefix, rootDir, null );
    }



    public HttpJssHandler( String prefix, File rootDir, Map<Object,Object> database ) {
        this.prefix = prefix;
        this.rootDir = rootDir;
        this.database = database;
        ScriptEngineManager manager = new ScriptEngineManager();
        this.engine = manager.getEngineByName( "JavaScript" );
        if ( engine==null ) throw new RuntimeException( "JavaScript engine not available" );
    }



    @Override
    public HttpResponse handle( HttpRequest req ) {
        String reqPath = req.headerBlock.getRequestPath();
        reqPath = reqPath.substring( prefix.length() );
        if ( reqPath.startsWith( "/" ) ) reqPath = reqPath.substring( 1 );
        File jsFile = new File( rootDir, reqPath );
        if ( !jsFile.exists() || !jsFile.isFile() ) {
            return new HttpErrorResponse( 404, "JavaScript file not found" );
        }
        try {
            String jsCode = Lib.file2string( jsFile );
            Object requestObj = createRequestObject( req );
            engine.eval( jsCode );
            Object handleFunc = engine.get( "handle" );
            if ( handleFunc==null ) {
                return new HttpErrorResponse( 500, "No handle() function found in " + jsFile.getName() );
            }
            Object result;
            if ( database!=null ) {
                try {
                    result = ((Invocable)engine).invokeFunction( "handle", requestObj, database );
                } catch ( Exception e ) {
                    if ( e.getMessage()!=null && e.getMessage().contains( "wrong number of arguments" ) ) {
                        result = ((Invocable)engine).invokeFunction( "handle", requestObj );
                    } else {
                        throw e;
                    }
                }
            } else {
                result = ((Invocable)engine).invokeFunction( "handle", requestObj );
            }
            return createHttpResponse( result );
        } catch ( Exception e ) {
            Lib.log( e );
            return new HttpErrorResponse( 500, "JavaScript execution error: " + e.getMessage() );
        }
    }



    private Object createRequestObject( HttpRequest req ) {
        try {
            String requestJson = JsonEncoder.encode( Lib.mapOf(
                "method", req.headerBlock.getMethod(),
                "url", req.headerBlock.getUri(),
                "headers", req.headerBlock.headers,
                "body", req.body!=null ? new String( req.body ) : "",
                "parsedBody", req.parsedBody,
                "params", req.allParms
            ) );
            return engine.eval( "(" + requestJson + ")" );
        } catch ( Exception e ) {
            Lib.log( e );
            return new LinkedHashMap<>();
        }
    }



    @SuppressWarnings( "unchecked" )
    private HttpResponse createHttpResponse( Object result ) {
        if ( result==null ) return new HttpErrorResponse( 500, "JavaScript function returned null" );
        try {
            Map<String,Object> responseMap;
            if ( result instanceof Map ) {
                responseMap = (Map<String,Object>) result;
            } else {
                return new HttpErrorResponse( 500, "JavaScript function must return an object" );
            }
            Object statusObj = responseMap.get( "status" );
            int status = statusObj instanceof Number ? ((Number)statusObj).intValue() : 200;
            Object headersObj = responseMap.get( "headers" );
            Map<String,String> headers = new LinkedHashMap<>();
            if ( headersObj instanceof Map ) {
                Map<?,?> headerMap = (Map<?,?>) headersObj;
                for ( Map.Entry<?,?> entry : headerMap.entrySet() ) {
                    headers.put( String.valueOf( entry.getKey() ), String.valueOf( entry.getValue() ) );
                }
            }
            Object bodyObj = responseMap.get( "body" );
            String body = bodyObj!=null ? String.valueOf( bodyObj ) : "";
            byte[] bodyBytes = body.getBytes();
            headers.put( "Content-Length", String.valueOf( bodyBytes.length ) );
            if ( !headers.containsKey( "Content-Type" ) ) headers.put( "Content-Type", "text/html" );
            String statusLine = "HTTP/1.1 " + status + " " + getStatusText( status );
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( statusLine, headers );
            return new HttpResponse( headerBlock, bodyBytes );
        } catch ( Exception e ) {
            Lib.log( e );
            return new HttpErrorResponse( 500, "Error processing JavaScript response: " + e.getMessage() );
        }
    }



    private String getStatusText( int status ) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }



    @SuppressWarnings( "unused" )
    private static boolean database_TEST_( boolean findLineNumber ) throws Exception {
        if ( findLineNumber ) throw new RuntimeException();
        File tempDir = new File( Lib.tmpDir(), "jss_db_test_" + System.currentTimeMillis() );
        tempDir.mkdirs();
        try ( PersistentData pd = PersistentData.temp( "jss_test_db" ) ) {
            PersistentMap database = pd.getRootMap();
            
            File jsFile = new File( tempDir, "db_test.jss" );
            String jsCode = """
                function handle(request, database) {
                    var count = database.get("testCount") || 0;
                    count++;
                    database.put("testCount", count);
                    database.put("lastMethod", request.method);
                    return {
                        status: 200,
                        headers: {"Content-Type": "application/json"},
                        body: JSON.stringify({
                            count: count,
                            hasDb: database != null,
                            dbSize: database.size(),
                            lastMethod: database.get("lastMethod")
                        })
                    };
                }
            """;
            try ( FileWriter writer = new FileWriter( jsFile ) ) {
                writer.write( jsCode );
            }
            
            HttpJssHandler handler = new HttpJssHandler( "/", tempDir, database );
            
            for ( int i = 1; i <= 3; i++ ) {
                HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET /db_test.jss HTTP/1.1", new HashMap<>() );
                HttpRequest req = new HttpRequest( headerBlock, new byte[0] );
                HttpResponse response = handler.handle( req );
                
                Lib.asrt( response.headerBlock.firstLine.contains( "200" ) );
                String body = new String( response.body );
                Lib.asrt( body.contains( "\"count\":" + i ) );
                Lib.asrt( body.contains( "\"hasDb\":true" ) );
                Lib.asrt( body.contains( "\"lastMethod\":\"GET\"" ) );
            }
            
            Lib.asrtEQ( database.get("testCount"), 3 );
            Lib.asrtEQ( database.get("lastMethod"), "GET" );
            return true;
        } finally {
            Lib.rm( tempDir );
        }
    }



    @SuppressWarnings( "unused" )
    private static boolean backwardsCompatibility_TEST_( boolean findLineNumber ) throws Exception {
        if ( findLineNumber ) throw new RuntimeException();
        File tempDir = new File( Lib.tmpDir(), "jss_compat_test_" + System.currentTimeMillis() );
        tempDir.mkdirs();
        try ( PersistentData pd = PersistentData.temp( "compat_test_db" ) ) {
            PersistentMap database = pd.getRootMap();
            
            File jsFile = new File( tempDir, "compat_test.jss" );
            String jsCode = """
                function handle(request) {
                    return {
                        status: 200,
                        headers: {"Content-Type": "text/plain"},
                        body: "Legacy handler works: " + request.method
                    };
                }
            """;
            try ( FileWriter writer = new FileWriter( jsFile ) ) {
                writer.write( jsCode );
            }
            
            HttpJssHandler handler = new HttpJssHandler( "/", tempDir, database );
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( "POST /compat_test.jss HTTP/1.1", new HashMap<>() );
            HttpRequest req = new HttpRequest( headerBlock, new byte[0] );
            HttpResponse response = handler.handle( req );
            
            Lib.asrt( response.headerBlock.firstLine.contains( "200" ) );
            String body = new String( response.body );
            Lib.asrt( body.contains( "Legacy handler works: POST" ) );
            return true;
        } finally {
            Lib.rm( tempDir );
        }
    }



    @SuppressWarnings( "unused" )
    private static boolean databasePersistence_TEST_( boolean findLineNumber ) throws Exception {
        if ( findLineNumber ) throw new RuntimeException();
        File tempDir = new File( Lib.tmpDir(), "jss_persist_test_" + System.currentTimeMillis() );
        tempDir.mkdirs();
        try ( PersistentData pd = PersistentData.temp( "persist_test_db" ) ) {
            PersistentMap database = pd.getRootMap();
            
            File jsFile = new File( tempDir, "persist_test.jss" );
            String jsCode = """
                function handle(request, database) {
                    if (request.method === "POST") {
                        var users = database.get("users") || {};
                        var newUser = {
                            name: "TestUser",
                            created: new Date().toISOString()
                        };
                        users["user1"] = newUser;
                        database.put("users", users);
                        return {
                            status: 201,
                            headers: {"Content-Type": "application/json"},
                            body: JSON.stringify({success: true, user: newUser})
                        };
                    } else {
                        var users = database.get("users") || {};
                        return {
                            status: 200,
                            headers: {"Content-Type": "application/json"},
                            body: JSON.stringify({users: users, userCount: users.size ? users.size() : Object.keys(users).length})
                        };
                    }
                }
            """;
            try ( FileWriter writer = new FileWriter( jsFile ) ) {
                writer.write( jsCode );
            }
            
            HttpJssHandler handler = new HttpJssHandler( "/", tempDir, database );
            
            HttpHeaderBlock postHeader = new HttpHeaderBlock( "POST /persist_test.jss HTTP/1.1", new HashMap<>() );
            HttpRequest postReq = new HttpRequest( postHeader, new byte[0] );
            HttpResponse postResponse = handler.handle( postReq );
            Lib.asrt( postResponse.headerBlock.firstLine.contains( "201" ) );
            
            HttpHeaderBlock getHeader = new HttpHeaderBlock( "GET /persist_test.jss HTTP/1.1", new HashMap<>() );
            HttpRequest getReq = new HttpRequest( getHeader, new byte[0] );
            HttpResponse getResponse = handler.handle( getReq );
            Lib.asrt( getResponse.headerBlock.firstLine.contains( "200" ) );
            String body = new String( getResponse.body );
            Lib.asrt( body.contains( "\"userCount\":1" ) );
            Lib.asrt( body.contains( "TestUser" ) );
            
            return true;
        } finally {
            Lib.rm( tempDir );
        }
    }



    @SuppressWarnings( "unused" )
    private static boolean basic_TEST_( boolean findLineNumber ) throws Exception {
        if ( findLineNumber ) throw new RuntimeException();
        File tempDir = new File( Lib.tmpDir(), "jss_test_" + System.currentTimeMillis() );
        tempDir.mkdirs();
        try {
            File jsFile = new File( tempDir, "test.jss" );
            String jsCode = """
                function handle(request) {
                    return {
                        status: 200,
                        headers: {"Content-Type": "text/plain"},
                        body: "Hello from " + request.method + " " + request.url
                    };
                }
            """;
            try ( FileWriter writer = new FileWriter( jsFile ) ) {
                writer.write( jsCode );
            }
            HttpJssHandler handler = new HttpJssHandler( "/", tempDir );
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET /test.jss HTTP/1.1", new HashMap<>() );
            HttpRequest req = new HttpRequest( headerBlock, new byte[0] );
            HttpResponse response = handler.handle( req );
            Lib.asrt( response.headerBlock.firstLine.contains( "200" ) );
            String responseBody = new String( response.body );
            Lib.asrt( responseBody.contains( "Hello from GET" ) );
            return true;
        } finally {
            Lib.rm( tempDir );
        }
    }



    public static void main( String[] args ) throws Exception { Lib.testClass(); }
}