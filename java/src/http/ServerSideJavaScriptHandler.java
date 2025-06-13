package http;
import java.io.*;
import java.util.*;
import javax.script.*;
import jLib.*;



public class ServerSideJavaScriptHandler implements HttpHandler {



    private final String prefix;
    private final File rootDir;
    private final ScriptEngine engine;



    public ServerSideJavaScriptHandler( String prefix, File rootDir ) {
        this.prefix = prefix;
        this.rootDir = rootDir;
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
            Object result = ((Invocable)engine).invokeFunction( "handle", requestObj );
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
            ServerSideJavaScriptHandler handler = new ServerSideJavaScriptHandler( "/", tempDir );
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