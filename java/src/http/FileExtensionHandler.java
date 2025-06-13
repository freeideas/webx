package http;
import java.util.*;
import java.io.*;
import jLib.*;



public class FileExtensionHandler implements HttpHandler {



    private final Map<String,HttpHandler> extensionHandlers = new LinkedHashMap<>();
    private HttpHandler defaultHandler;



    public FileExtensionHandler addExtensionHandler( String extension, HttpHandler handler ) {
        if ( !extension.startsWith(".") ) extension = "." + extension;
        extensionHandlers.put( extension.toLowerCase(), handler );
        return this;
    }



    public FileExtensionHandler setDefaultHandler( HttpHandler handler ) {
        this.defaultHandler = handler;
        return this;
    }



    @Override
    public HttpResponse handle( HttpRequest req ) {
        String reqPath = req.headerBlock.getRequestPath();
        String extension = getFileExtension(reqPath);
        if ( extension != null ) {
            HttpHandler handler = extensionHandlers.get( extension.toLowerCase() );
            if ( handler != null ) return handler.handle(req);
        }
        if ( defaultHandler != null ) return defaultHandler.handle(req);
        return new HttpErrorResponse( 404, "No handler for request" );
    }



    private String getFileExtension( String path ) {
        if ( path == null ) return null;
        int queryPos = path.indexOf('?');
        if ( queryPos >= 0 ) path = path.substring(0, queryPos);
        int lastDot = path.lastIndexOf('.');
        if ( lastDot >= 0 && lastDot < path.length() - 1 ) {
            return path.substring(lastDot);
        }
        return null;
    }



    @SuppressWarnings("unused")
    private static boolean extensionParsing_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        FileExtensionHandler handler = new FileExtensionHandler();
        Lib.asrtEQ( handler.getFileExtension("/test.jss"), ".jss" );
        Lib.asrtEQ( handler.getFileExtension("/path/to/file.html"), ".html" );
        Lib.asrtEQ( handler.getFileExtension("/file.jss?param=value"), ".jss" );
        Lib.asrtEQ( handler.getFileExtension("/noextension"), null );
        Lib.asrtEQ( handler.getFileExtension("/path/"), null );
        Lib.asrtEQ( handler.getFileExtension("/.hidden"), ".hidden" );
        return true;
    }



    public static void main( String[] args ) throws Exception { Lib.testClass(); }
}