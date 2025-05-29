package appz.webx;
import java.util.*;
import http.*;
import jLib.*;
import java.io.File;



public class Main {



    public static void main( String[] args ) {
        Lib.archiveLogFiles();
        ParseArgs p = new ParseArgs(args);
        p.setAppName( Lib.getAppName() );
        p.setDescr( "a small simple web server" );
        //p.setHelpFooter("This is the footer");
        int port = p.getInteger( "port",13102,"listen to which port" );
        List<String> rootDirs = p.getMulti( "dir", List.of("/:./datafiles/www"), Lib.nw("""
            directory to serve, in the form of /WEBROOT:FILESPEC
        """) );
        HttpServer server = new HttpServer(port);
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
        server.handlers.put( "/login", new HttpLoginHandler() );
        HttpLoginHandler.appName = Lib.getAppName();
        HttpLoginHandler.emailFormUrl = "./login.html";
        HttpLoginHandler.loginCodeUrl = "./loginCode.html";
        HttpLoginHandler.loggedInUrl = "./loggedIn.html";
        server.handlers.put( "/proxy", new HttpProxyHandler() );
        Lib.log( "listening to port "+port );
        server.start();
    }



}