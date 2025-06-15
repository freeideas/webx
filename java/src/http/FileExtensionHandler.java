package http;
import java.util.*;
import java.io.*;
import jLib.*;



public class FileExtensionHandler implements HttpHandler {



    private final Map<String,HttpHandler> extensionHandlers = new LinkedHashMap<>();
    private HttpHandler defaultHandler;
    private String prefix;
    private File rootDir;



    public FileExtensionHandler addExtensionHandler( String extension, HttpHandler handler ) {
        if ( !extension.startsWith(".") ) extension = "." + extension;
        extensionHandlers.put( extension.toLowerCase(), handler );
        return this;
    }



    public FileExtensionHandler setDefaultHandler( HttpHandler handler ) {
        this.defaultHandler = handler;
        if ( handler instanceof HttpFileHandler ) {
            HttpFileHandler fileHandler = (HttpFileHandler) handler;
            this.prefix = fileHandler.prefix;
            this.rootDir = fileHandler.rootDir;
        }
        return this;
    }



    @Override
    public HttpResponse handle( HttpRequest req ) {
        String uri = req.headerBlock.getUri();
        if ( uri != null && uri.endsWith("/") ) return handleDirectory( req );
        
        String reqPath = req.headerBlock.getRequestPath();
        String extension = getFileExtension(reqPath);
        if ( extension != null ) {
            HttpHandler handler = extensionHandlers.get( extension.toLowerCase() );
            if ( handler != null ) return handler.handle(req);
        }
        if ( defaultHandler != null ) return defaultHandler.handle(req);
        return new HttpErrorResponse( 404, "No handler for request" );
    }



    private HttpResponse handleDirectory( HttpRequest req ) {
        String basePath = req.headerBlock.getRequestPath();
        if ( !basePath.endsWith("/") ) basePath += "/";
        HttpRequest indexJssReq = createIndexRequest( req, basePath + "index.jss" );
        HttpHandler jssHandler = extensionHandlers.get(".jss");
        if ( jssHandler != null ) {
            HttpResponse jssResponse = jssHandler.handle(indexJssReq);
            if ( !(jssResponse instanceof HttpErrorResponse) ) return jssResponse;
        }
        HttpRequest indexHtmlReq = createIndexRequest( req, basePath + "index.html" );
        if ( defaultHandler != null ) {
            HttpResponse htmlResponse = defaultHandler.handle(indexHtmlReq);
            if ( !(htmlResponse instanceof HttpErrorResponse) ) return htmlResponse;
        }
        return generateDirectoryListing( req );
    }



    private HttpRequest createIndexRequest( HttpRequest originalReq, String newPath ) {
        String newUri = newPath;
        String originalUri = originalReq.headerBlock.getUri();
        if ( originalUri != null && originalUri.contains("?") ) {
            newUri += originalUri.substring( originalUri.indexOf("?") );
        }
        HttpHeaderBlock newHeaderBlock = new HttpHeaderBlock(
            originalReq.headerBlock.getMethod() + " " + newUri + " HTTP/1.1",
            originalReq.headerBlock.headers
        );
        return new HttpRequest( newHeaderBlock, originalReq.body );
    }



    private HttpResponse generateDirectoryListing( HttpRequest req ) {
        String dirPath = req.headerBlock.getRequestPath();
        if ( rootDir==null || prefix==null ) {
            String html = """
                <html>
                    <head><title>Directory Listing</title></head>
                    <body>
                        <h1>Directory Listing for %s</h1>
                        <p>Directory listing not available</p>
                    </body>
                </html>
                """.formatted(dirPath);
            byte[] htmlBytes = html.getBytes();
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( 
                200, "OK", 
                Lib.mapOf( "Content-Type", "text/html", "Content-Length", String.valueOf(htmlBytes.length) ) 
            );
            return new HttpResponse( headerBlock, htmlBytes );
        }
        String reqPath = dirPath.substring( prefix.length() );
        if ( reqPath.startsWith("/") ) reqPath = reqPath.substring(1);
        File dir = new File( rootDir, reqPath );
        if ( !dir.exists() || !dir.isDirectory() ) {
            return new HttpErrorResponse( 404, "Directory not found" );
        }
        StringBuilder html = new StringBuilder();
        html.append( "<html><head><title>Directory Listing</title></head><body>" );
        html.append( "<h1>Directory Listing for " ).append( dirPath ).append( "</h1>" );
        html.append( "<table border=\"1\"><tr><th>Name</th><th>Type</th><th>Size</th></tr>" );
        if ( !reqPath.isEmpty() ) {
            html.append( "<tr><td><a href=\"../\">../</a></td><td>Directory</td><td>-</td></tr>" );
        }
        File[] files = dir.listFiles();
        if ( files != null ) {
            for ( File f : files ) {
                String name = f.getName();
                String link = f.isDirectory() ? name + "/" : name;
                String type = f.isDirectory() ? "Directory" : "File";
                String size = f.isDirectory() ? "-" : String.valueOf(f.length());
                html.append( "<tr><td><a href=\"" ).append( link ).append( "\">" ).append( name );
                if ( f.isDirectory() ) html.append( "/" );
                html.append( "</a></td><td>" ).append( type ).append( "</td><td>" ).append( size ).append( "</td></tr>" );
            }
        }
        html.append( "</table></body></html>" );
        byte[] htmlBytes = html.toString().getBytes();
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( 
            200, "OK", 
            Lib.mapOf( "Content-Type", "text/html", "Content-Length", String.valueOf(htmlBytes.length) ) 
        );
        return new HttpResponse( headerBlock, htmlBytes );
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
        LibTest.asrtEQ( handler.getFileExtension("/test.jss"), ".jss" );
        LibTest.asrtEQ( handler.getFileExtension("/path/to/file.html"), ".html" );
        LibTest.asrtEQ( handler.getFileExtension("/file.jss?param=value"), ".jss" );
        LibTest.asrtEQ( handler.getFileExtension("/noextension"), null );
        LibTest.asrtEQ( handler.getFileExtension("/path/"), null );
        LibTest.asrtEQ( handler.getFileExtension("/.hidden"), ".hidden" );
        return true;
    }
    @SuppressWarnings("unused")
    private static boolean directoryHandling_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        FileExtensionHandler handler = new FileExtensionHandler();
        handler.setDefaultHandler( new HttpHandler() {
            public HttpResponse handle( HttpRequest req ) {
                String path = req.headerBlock.getRequestPath();
                if ( path.endsWith("index.html") ) {
                    String html = "<html><body>Index HTML</body></html>";
                    byte[] htmlBytes = html.getBytes();
                    HttpHeaderBlock headerBlock = new HttpHeaderBlock( 200, "OK", 
                        Lib.mapOf( "Content-Type", "text/html", "Content-Length", String.valueOf(htmlBytes.length) ) );
                    return new HttpResponse( headerBlock, htmlBytes );
                }
                return new HttpErrorResponse( 404, "Not found" );
            }
        });
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET /test/ HTTP/1.1", Lib.mapOf() );
        HttpRequest req = new HttpRequest( headerBlock, new byte[0] );
        HttpResponse response = handler.handle( req );
        
        String responseBody = new String( response.body );
        boolean hasDirectoryListing = responseBody.contains("Directory Listing");
        boolean is200Response = response.headerBlock.firstLine.contains("200");
        LibTest.asrt( hasDirectoryListing || is200Response, "Expected directory listing or 200 response" );
        return true;
    }



    public static void main( String[] args ) throws Exception { LibTest.testClass(); }
}