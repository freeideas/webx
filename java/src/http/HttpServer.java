package http;
import java.util.function.Predicate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.net.*;
import java.io.*;
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
        FileOutputStream logStream = null;
        String remoteAddr = "(unknown)";
        try {
            remoteAddr = Lib.getRemoteAddr(clientSocket).replaceAll( "[^a-zA-Z0-9]", "-" );
            Lib.log( "connection from "+remoteAddr );
            rawSockInp = clientSocket.getInputStream();
            rawSockOut = clientSocket.getOutputStream();
            logStream = new FileOutputStream( Lib.backupFilespec( "./log/"+remoteAddr+".log" ) );
            sockInp = Lib.multicast(rawSockInp,logStream);
            sockOut = Lib.multicast(rawSockOut,logStream);
            while (! clientSocket.isClosed() ) {
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
                    shutdownResponse.write(sockOut);
                    return;
                } else if ( shutdownCode != null && headerBlock.firstLine != null && headerBlock.firstLine.contains(shutdownCode) ) {
                    // Shutdown code detected but timestamp validation failed
                    HttpResponse response = new HttpErrorResponse( 403, "Invalid shutdown request" );
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
                if ( requestFilter != null && ! requestFilter.test(req) ) {
                    HttpResponse response = new HttpErrorResponse( 403, "Forbidden" );
                    response.write(sockOut);
                    return;
                }
                HttpResponse response = foundHandler.handle(req);
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
            try{ sockInp.close(); }catch(Throwable ignore){}
            try{ sockOut.close(); }catch(Throwable ignore){}
            try{ clientSocket.close(); }catch(Throwable ignore){}
            try{ logStream.close(); }catch(Throwable ignore){}
            Lib.log( "socket closed: "+remoteAddr );
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

    public static void main( String[] args ) throws Exception { Lib.testClass(); }



}