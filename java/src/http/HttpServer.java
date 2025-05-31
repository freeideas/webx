package http;
import java.util.function.Predicate;
import java.util.LinkedHashMap;
import java.net.*;
import java.io.*;
import jLib.*;



public class HttpServer {



    public static final String VER = "20250523a";
    public final LinkedHashMap<String,HttpHandler> handlers = new LinkedHashMap<>();
    public final int port;
    public Predicate<HttpRequest> requestFilter = null;



    public HttpServer( int port ) {
        this.port = port;
    }



    public void start() {
        try ( ServerSocket serverSocket = new ServerSocket(port); ) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> {
                    try { handleClient(clientSocket); }
                    catch (IOException e) { Lib.log(e); }
                }).start();
            }
        } catch (IOException e) { Lib.log(e); }
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
        Lib.asrt( server.requestFilter == null );
        
        server.requestFilter = req -> req.headerBlock.headers.containsKey("Authorization");
        Lib.asrt( server.requestFilter != null );
        
        return true;
    }



    public static void main( String[] args ) throws Exception { Lib.testClass(); }



}