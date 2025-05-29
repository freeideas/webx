package appz.webx;
import java.util.*;
import http.*;
import jLib.*;
import persist.*;
import java.io.File;



public class Main {



    public static void main( String[] args ) {
        Lib.archiveLogFiles();
        ParseArgs p = new ParseArgs(args);
        p.setAppName( Lib.getAppName() );
        p.setDescr( "WebX - Simple Web Application Server" );
        
        int port = p.getInteger( "port", 13102, "listen to which port" );
        List<String> rootDirs = p.getMulti( "dir", List.of("/www:./datafiles/www"), Lib.nw("""
            directory to serve, in the form of /WEBROOT:FILESPEC
        """) );
        
        HttpServer server = new HttpServer(port);
        
        // 1. Static File Server (/www)
        for ( String rootDir : rootDirs ) {
            String[] parts = rootDir.split(":",2);
            String webRoot = parts[0];
            if (! webRoot.startsWith("/") ) webRoot = "/"+webRoot;
            String filespec = parts[1];
            File f = new File(filespec);
            if (! f.exists() ) {
                Lib.log( "ERROR: "+filespec+" does not exist" );
                continue;
            }
            server.handlers.put( webRoot, new HttpFileHandler(webRoot,f) );
        }
        
        // 2. API Proxy (/proxy) with replacements from file
        File proxyReplacementsFile = new File( "./datafiles/proxy-replacements.json" );
        if ( proxyReplacementsFile.exists() ) {
            server.handlers.put( "/proxy", new HttpReplacingProxyHandler(proxyReplacementsFile) );
            Lib.log( "Proxy handler configured with replacements from " + proxyReplacementsFile );
        } else {
            server.handlers.put( "/proxy", new HttpProxyHandler() );
            Lib.log( "Proxy handler configured without replacements (no proxy-replacements.json found)" );
        }
        
        // 3. JSON Database (/db)
        String jdbcUrl = "jdbc:hsqldb:file:./datafiles/dbf/webx-db";
        try( PersistentData pd = new PersistentData( jdbcUrl, "webx_data" ) ) {
            PersistentMap dbStorage = pd.getRootMap();
            server.handlers.put( "/db", new HttpJsonHandler(dbStorage) );
            Lib.log( "WebX server listening on port "+port );
            Lib.log( "Endpoints: /www (static files), /proxy (API proxy), /db (JSON database), /login (auth)" );
            server.start();
            Lib.log( "WebX server stopped" );
        } catch ( Exception e ) {
            Lib.log(e);
        }
    }



}