package http;
import java.util.function.Predicate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import jLib.*;



public class HttpServer {



    public static final String VER = "20250601a";
    public final LinkedHashMap<String,HttpHandler> handlers = new LinkedHashMap<>();
    public final int port;
    public final boolean useHttps;
    public Predicate<HttpRequest> requestFilter = new SecurityGuard();
    private String shutdownCode = null;
    private volatile boolean shouldShutdown = false;



    public HttpServer( int port ) {
        this( port, true );
    }



    public HttpServer( int port, boolean useHttps ) {
        this.port = port;
        this.useHttps = useHttps;
    }



    public void setShutdownCode( String shutdownCode ) {
        this.shutdownCode = shutdownCode;
    }



    public boolean isShutdown() {
        return shouldShutdown;
    }



    public void start() {
        try ( ServerSocket serverSocket = useHttps ? Lib.createServerSocket( port, true, null, null, null ) : new ServerSocket(port); ) {
            serverSocket.setSoTimeout(1000); // Set timeout so we can check shutdown flag
            while (!shouldShutdown) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> {
                        try { handleClient(clientSocket); }
                        catch (IOException e) { Lib.log(e); }
                    }).start();
                } catch (java.net.SocketTimeoutException e) {
                    // Timeout is expected, just continue to check shutdown flag
                }
            }
            Lib.log("Server shutdown requested, stopping...");
        } catch (IOException e) {
            if (!shouldShutdown) { // Only log if not shutting down intentionally
                Lib.log(e);
            }
        }
    }



    private HttpHandler findHandler( HttpHeaderBlock headerBlock ) {
        String path = headerBlock.getRequestPath();
        while (true) {
            HttpHandler handler = handlers.get(path);
            if (handler!=null) return handler;
            if ( path.equals("/") ) break;
            path = Lib.normalizePath( path+"/.." );
        }
        return new HttpErrorHandler( 404, "no matching handler" );
    }

    public static String shutdownTimestamp() {
        String currentTimeStamp = Lib.timeStamp();
        return currentTimeStamp.replaceAll("[^0-9]", "").substring(0, 10); // YYYYMMDDHH
    }

    private boolean shouldShutDown( HttpHeaderBlock headerBlock ) {
        if ( shutdownCode == null || headerBlock.firstLine == null || !headerBlock.firstLine.contains(shutdownCode) ) {
            return false;
        }

        // Generate current UTC timestamp truncated to hour (YYYYMMDDHH)
        String currentHourStamp = shutdownTimestamp();

        // Extract digits-only version of first line to check for timestamp
        String firstLineDigits = headerBlock.firstLine.replaceAll("[^0-9]", "");

        if ( firstLineDigits.contains(currentHourStamp) ) {
            Lib.log( "Shutdown code and valid timestamp detected: " + shutdownCode + " with hour stamp: " + currentHourStamp );
            return true;
        } else {
            Lib.log( "Shutdown code detected but timestamp validation failed. Expected hour stamp: " + currentHourStamp + ", found digits: " + firstLineDigits );
            return false;
        }
    }

    private void handleClient( Socket clientSocket ) throws IOException {
        InputStream rawSockInp = null;
        OutputStream rawSockOut = null;
        InputStream sockInp = null;
        OutputStream sockOut = null;
        String remoteAddr = "(unknown)";
        try {
            remoteAddr = Lib.getRemoteAddr(clientSocket).replaceAll( "[^a-zA-Z0-9]", "-" );
            rawSockInp = clientSocket.getInputStream();
            rawSockOut = clientSocket.getOutputStream();
            sockInp = rawSockInp;
            sockOut = rawSockOut;
            while (! clientSocket.isClosed() ) {
                String requestId = Lib.timeStamp().replaceAll( "[^0-9]", "" );
                Result<HttpHeaderBlock,Exception> headerResult = HttpHeaderBlock.readFrom(sockInp);
                if (! headerResult.isOk() ) return;
                HttpHeaderBlock headerBlock = headerResult.ok();

                // Check for shutdown request
                if ( shouldShutDown(headerBlock) ) {
                    shouldShutdown = true;
                    HttpResponse shutdownResponse = new HttpResponse(
                        new HttpHeaderBlock( "HTTP/1.1 200 OK", new LinkedHashMap<>() ),
                        "Server shutting down".getBytes()
                    );
                    logResponse( requestId, shutdownResponse );
                    shutdownResponse.write(sockOut);
                    return;
                } else if ( shutdownCode != null && headerBlock.firstLine != null && headerBlock.firstLine.contains(shutdownCode) ) {
                    // Shutdown code detected but timestamp validation failed
                    HttpResponse response = new HttpErrorResponse( 403, "Invalid shutdown request" );
                    logResponse( requestId, response );
                    response.write(sockOut);
                    return;
                }
                HttpHandler foundHandler = findHandler(headerBlock);
                if (foundHandler==null) foundHandler = new HttpErrorHandler( 404, "no matching handler" );
                Result<HttpMessage,Exception> msgResult = HttpMessage.readHttpMessage(headerBlock,sockInp);
                if (! msgResult.isOk() ) {
                    Lib.log( msgResult.err() );
                    return;
                }
                HttpRequest req = HttpRequest.newHttpRequest( msgResult.ok() );
                Lib.log( requestId + " " + remoteAddr + " " + headerBlock.firstLine );
                logRequest( requestId, req );
                if ( requestFilter != null && ! requestFilter.test(req) ) {
                    HttpResponse response = new HttpErrorResponse( 403, "Forbidden" );
                    logResponse( requestId, response );
                    response.write(sockOut);
                    return;
                }
                HttpResponse response = foundHandler.handle(req);
                logResponse( requestId, response );
                Result<Long,Exception> writeResult = response.write(sockOut);
                if (! writeResult.isOk() ) {
                    Lib.log( writeResult.err() );
                    return;
                }
                if ( "close".equalsIgnoreCase( headerBlock.headers.get("Connection") ) ) break;
            }
        } catch ( Throwable t ) {
            Lib.log(t);
        } finally {
            try{ rawSockInp.close(); }catch(Throwable ignore){}
            try{ rawSockOut.close(); }catch(Throwable ignore){}
            try{ clientSocket.close(); }catch(Throwable ignore){}
        }
    }



    private void logRequest( String requestId, HttpRequest request ) {
        try {
            String filename = "./log/" + requestId + ".log";
            StringBuilder content = new StringBuilder();
            content.append( "=== REQUEST ===\n" );
            content.append( "Time: " ).append( Lib.timeStamp() ).append( "\n" );
            content.append( request.headerBlock.firstLine ).append( "\n" );
            for ( Map.Entry<String,String> header : request.headerBlock.headers.entrySet() ) {
                content.append( header.getKey() ).append( ": " ).append( header.getValue() ).append( "\n" );
            }
            content.append( "\n" );
            if ( request.body!=null && request.body.length>0 ) {
                content.append( new String( request.body, StandardCharsets.UTF_8 ) );
            }
            File logFile = new File(filename);
            logFile.delete();
            Lib.append2file( logFile, content.toString() );
        } catch ( Exception e ) {
            Lib.log( "Failed to log request: " + e.getMessage() );
        }
    }



    private void logResponse( String requestId, HttpResponse response ) {
        try {
            String filename = "./log/" + requestId + ".log";
            StringBuilder content = new StringBuilder();
            content.append( "\n\n=== RESPONSE ===\n" );
            content.append( "Time: " ).append( Lib.timeStamp() ).append( "\n" );
            content.append( response.headerBlock.firstLine ).append( "\n" );
            for ( Map.Entry<String,String> header : response.headerBlock.headers.entrySet() ) {
                content.append( header.getKey() ).append( ": " ).append( header.getValue() ).append( "\n" );
            }
            content.append( "\n" );
            if ( response.body!=null && response.body.length>0 ) {
                content.append( new String( response.body, StandardCharsets.UTF_8 ) );
            }
            Lib.append2file( new File(filename), content.toString() );
        } catch ( Exception e ) {
            Lib.log( "Failed to log response: " + e.getMessage() );
        }
    }



    public static boolean basic_TEST_() {
        HttpServer server = new HttpServer(8080);
        Lib.asrtEQ( server.port, 8080 );
        Lib.asrt( server.handlers != null );
        Lib.asrt( server.handlers.isEmpty() );
        Lib.asrt( server.requestFilter != null ); // Now defaults to SecurityGuard
        Lib.asrt( server.shutdownCode == null );
        Lib.asrt( !server.isShutdown() );

        server.requestFilter = req -> req.headerBlock.headers.containsKey("Authorization");
        Lib.asrt( server.requestFilter != null );

        server.setShutdownCode( "SHUTDOWN123" );
        Lib.asrtEQ( server.shutdownCode, "SHUTDOWN123" );
        Lib.asrt( !server.isShutdown() ); // Should still be false

        return true;
    }

    public static boolean shutdownTimestamp_TEST_() {
        // Test the timestamp validation logic for shutdown
        String currentHourStamp = shutdownTimestamp();

        // Test valid shutdown request format
        String validShutdownLine = "GET /SHUTDOWN123" + currentHourStamp + "456 HTTP/1.1";
        String validDigits = validShutdownLine.replaceAll("[^0-9]", "");
        Lib.asrt( validDigits.contains(currentHourStamp), "Valid shutdown line should contain current hour stamp" );

        // Test invalid shutdown request (wrong hour)
        String wrongHour = currentHourStamp.substring(0, 8) + "99"; // Change hour to 99
        String invalidShutdownLine = "GET /SHUTDOWN123" + wrongHour + "456 HTTP/1.1";
        String invalidDigits = invalidShutdownLine.replaceAll("[^0-9]", "");
        Lib.asrt( !invalidDigits.contains(currentHourStamp), "Invalid shutdown line should not contain current hour stamp" );

        return true;
    }

    public static boolean shutdownTimestamp_method_TEST_() {
        // Test the shutdownTimestamp() method
        String timestamp = shutdownTimestamp();
        Lib.asrt( timestamp != null, "Timestamp should not be null" );
        Lib.asrt( timestamp.length() == 10, "Timestamp should be 10 characters (YYYYMMDDHH)" );
        Lib.asrt( timestamp.matches("\\d{10}"), "Timestamp should be all digits" );

        // Test that it represents current time (roughly)
        String fullTimestamp = Lib.timeStamp().replaceAll("[^0-9]", "");
        Lib.asrt( fullTimestamp.startsWith(timestamp), "Timestamp should match current time prefix" );

        return true;
    }

    public static boolean shouldShutDown_TEST_() {
        HttpServer server = new HttpServer(8080);
        server.setShutdownCode("SHUTDOWN123");

        String currentHourStamp = shutdownTimestamp();

        // Test valid shutdown request
        Map<String,String> headers = new HashMap<>();
        HttpHeaderBlock validHeader = new HttpHeaderBlock("GET /SHUTDOWN123" + currentHourStamp + " HTTP/1.1", headers);
        Lib.asrt( server.shouldShutDown(validHeader), "Should shutdown with valid timestamp" );

        // Test invalid shutdown request (wrong timestamp)
        String wrongHour = currentHourStamp.substring(0, 8) + "99";
        HttpHeaderBlock invalidHeader = new HttpHeaderBlock("GET /SHUTDOWN123" + wrongHour + " HTTP/1.1", headers);
        Lib.asrt( !server.shouldShutDown(invalidHeader), "Should not shutdown with invalid timestamp" );

        // Test no shutdown code
        HttpHeaderBlock noCodeHeader = new HttpHeaderBlock("GET /normal-request HTTP/1.1", headers);
        Lib.asrt( !server.shouldShutDown(noCodeHeader), "Should not shutdown without shutdown code" );

        return true;
    }

    public static boolean requestLogging_TEST_() throws Exception {
        // Test request/response logging functionality
        HttpServer server = new HttpServer(8080);

        // Create test request
        Map<String,String> headers = new HashMap<>();
        headers.put( "Host", "example.com" );
        headers.put( "User-Agent", "TestClient/1.0" );
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET /test HTTP/1.1", headers );
        HttpMessage message = new HttpMessage( headerBlock, "Test body".getBytes() );
        HttpRequest request = HttpRequest.newHttpRequest( message );

        // Create test response
        HttpHeaderBlock respHeaders = new HttpHeaderBlock( "HTTP/1.1 200 OK", new HashMap<>() );
        HttpResponse response = new HttpResponse( respHeaders, "Response body".getBytes() );

        // Generate request ID and log
        String requestId = Lib.timeStamp().replaceAll( "[^0-9]", "" );
        server.logRequest( requestId, request );
        server.logResponse( requestId, response );

        // Verify log file exists and contains expected content
        File logFile = new File( "./log/" + requestId + ".log" );
        Lib.asrt( logFile.exists(), "Log file should exist" );

        String logContent = LibFile.file2string( logFile );
        Lib.asrt( logContent.contains( "=== REQUEST ===" ), "Should contain request header" );
        Lib.asrt( logContent.contains( "GET /test HTTP/1.1" ), "Should contain request line" );
        Lib.asrt( logContent.contains( "Host: example.com" ), "Should contain Host header" );
        Lib.asrt( logContent.contains( "Test body" ), "Should contain request body" );

        Lib.asrt( logContent.contains( "=== RESPONSE ===" ), "Should contain response header" );
        Lib.asrt( logContent.contains( "HTTP/1.1 200 OK" ), "Should contain response line" );
        Lib.asrt( logContent.contains( "Response body" ), "Should contain response body" );

        // Clean up
        logFile.delete();

        return true;
    }

    public static void main( String[] args ) throws Exception { LibTest.testClass(); }



}
