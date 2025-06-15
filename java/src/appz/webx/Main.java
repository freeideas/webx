package appz.webx;
import http.*;
import jLib.*;
import persist.*;
import java.io.*;



public class Main {



    @SuppressWarnings("try")
    public static void main( String[] args ) {
        if ( System.currentTimeMillis()<0 && ( args==null || args.length==0 ) ) { // for manual testing
            args = new String[]{
                "--port=13102",
                "--base=/app001",
                "--static=ww@./datafiles/www",
                "--proxy=prx@../api-keys.json",
                "--db=db@jdbc:hsqldb:mem:webx-db",
                "--login=login@WebX",
                "--shutdown=SHUTDOWN13102",
                "--run"
            };
        }
        LibApp.archiveLogFiles();
        ParseArgs p = new ParseArgs(args);
        p.setAppName( LibApp.getAppName() );
        p.setDescr( "WebX - Simple Web Application Server" );

        int port = p.getInteger( "port", 13102, "listen to which port" );
        boolean https = p.getBoolean( "https", false, "use HTTPS (true) or HTTP (false)" );
        String basePath = p.getString( "base", "webx", "base path for all endpoints (e.g., '/app001')" );
        String staticConfig = p.getString( "static", "www@./datafiles/www", "static files endpoint as path@directory (use 'NONE' to disable)" );
        String proxyConfig = p.getString( "proxy", "proxy@../api-keys.json", "proxy endpoint as path@config-file (use 'NONE' to disable)" );
        String dbConfig = p.getString( "db", "db@jdbc:hsqldb:file:./datafiles/dbf/webx-db", "database endpoint as path@jdbc-url (use 'NONE' to disable)" );
        String loginConfig = p.getString( "login", "login@WebX", "login endpoint as path@app-name (use 'NONE' to disable)" );
        String shutdownCode = p.getString( "shutdown", null, "shutdown code - if provided, server will exit when this code appears in the first line of any request" );
        boolean run = p.getBoolean( "run", false, "start the server" );

        // Normalize base path
        if ( basePath!=null && !basePath.isEmpty() ) {
            basePath = Lib.normalizePath( basePath );
            if ( !basePath.startsWith("/") ) basePath = "/" + basePath;
        }

        System.out.print( p.getHelp() );
        System.out.println( "\nCONFIGURATION SUMMARY:" );
        System.out.println( "  Server will run on port: " + port + " (" + (https ? "HTTPS" : "HTTP") + ")" );
        if ( basePath!=null && !basePath.isEmpty() ) System.out.println( "  Base path: " + basePath );

        if ( staticConfig.equalsIgnoreCase("NONE") ) {
            System.out.println( "  Static files: DISABLED" );
        } else {
            String[] staticParts = staticConfig.split("@", 2);
            String staticPath = staticParts.length>0 ? staticParts[0] : "www";
            String staticDir = staticParts.length>1 ? staticParts[1] : "./datafiles/www";
            if ( !staticPath.startsWith("/") ) staticPath = "/" + staticPath;
            System.out.println( "  " + basePath + staticPath + " serves static files from " + staticDir );
        }

        if ( proxyConfig.equalsIgnoreCase("NONE") ) {
            System.out.println( "  Proxy endpoint: DISABLED" );
        } else {
            String[] proxyParts = proxyConfig.split("@", 2);
            String proxyPath = proxyParts.length>0 ? proxyParts[0] : "proxy";
            String proxyConfigFile = proxyParts.length>1 ? proxyParts[1] : "../api-keys.json";
            if ( !proxyPath.startsWith("/") ) proxyPath = "/" + proxyPath;
            System.out.println( "  " + basePath + proxyPath + " proxies external APIs (config: " + proxyConfigFile + ")" );
        }

        if ( dbConfig.equalsIgnoreCase("NONE") ) {
            System.out.println( "  Database: DISABLED" );
        } else {
            String[] dbParts = dbConfig.split("@", 2);
            String dbPath = dbParts.length>0 ? dbParts[0] : "db";
            String jdbcUrl = dbParts.length>1 ? dbParts[1] : "jdbc:hsqldb:file:./datafiles/dbf/webx-db";
            if ( !dbPath.startsWith("/") ) dbPath = "/" + dbPath;
            System.out.println( "  " + basePath + dbPath + " provides JSON database storage (" + jdbcUrl + ")" );
        }

        if ( loginConfig.equalsIgnoreCase("NONE") ) {
            System.out.println( "  Login: DISABLED" );
        } else {
            String[] loginParts = loginConfig.split("@", 2);
            String loginPath = loginParts.length>0 ? loginParts[0] : "login";
            String appName = loginParts.length>1 ? loginParts[1] : "WebX";
            if ( !loginPath.startsWith("/") ) loginPath = "/" + loginPath;
            System.out.println( "  " + basePath + loginPath + " provides email authentication for '" + appName + "'" );
        }

        System.out.println( "  run is " + run + ", so " + (run ? "service will start now" : "exiting") );

        if ( !run ) return;

        // Check if port is already in use
        if ( Lib.isPortListening( port ) ) {
            Log.log( "ERROR: Port " + port + " is already in use. Another instance may be running." );
            return;
        }

        // Check lock file to prevent multiple instances
        File lockFile = new File( "./log/webx.lock" );
        if ( LibApp.alreadyRunning( lockFile ) ) {
            Log.log( "ERROR: Another instance of WebX appears to be running (lock file exists)." );
            return;
        }

        HttpServer server = new HttpServer( port, https );
        if ( shutdownCode!=null ) server.setShutdownCode( shutdownCode );

        // Create security guard
        DefaultSecurityGuard securityGuard = new DefaultSecurityGuard();
        server.requestFilter = securityGuard;

        // Initialize database if enabled
        PersistentMap dbStorage = null;
        PersistentData pd = null;
        if ( !dbConfig.equalsIgnoreCase("NONE") ) {
            String[] dbParts = dbConfig.split("@", 2);
            String jdbcUrl = dbParts.length>1 ? dbParts[1] : "jdbc:hsqldb:file:./datafiles/dbf/webx-db";
            pd = new PersistentData( jdbcUrl, "webx_data" );
            dbStorage = pd.getRootMap();
        }

        if ( !staticConfig.equalsIgnoreCase("NONE") ) {
            String[] staticParts = staticConfig.split("@", 2);
            String staticPath = staticParts.length>0 ? staticParts[0] : "www";
            String staticDir = staticParts.length>1 ? staticParts[1] : "./datafiles/www";
            if ( !staticPath.startsWith("/") ) staticPath = "/" + staticPath;
            String fullStaticPath = basePath + staticPath;
            File wwwDir = new File(staticDir);
            if ( !wwwDir.exists() ) {
                Log.log( "ERROR: " + staticDir + " does not exist" );
            } else {
                server.handlers.put( fullStaticPath, new FileExtensionHandler()
                    .addExtensionHandler( ".jss", new HttpJssHandler( fullStaticPath, wwwDir, dbStorage ) )
                    .setDefaultHandler( new HttpFileHandler( fullStaticPath, wwwDir ) ) );
                Log.log( "Static files configured at " + fullStaticPath + " from " + wwwDir.getAbsolutePath() );
            }
        }

        if ( !proxyConfig.equalsIgnoreCase("NONE") ) {
            String proxyPath = proxyConfig.split("@", 2)[0];
            if ( !proxyPath.startsWith("/") ) proxyPath = "/" + proxyPath;
            String fullProxyPath = basePath + proxyPath;
            server.handlers.put( fullProxyPath, new HttpReplacingProxyHandler() );
            Log.log( "Proxy handler configured at " + fullProxyPath + " with replacements from .creds.json" );
        }

        if ( !loginConfig.equalsIgnoreCase("NONE") ) {
            String[] loginParts = loginConfig.split("@", 2);
            String loginPath = loginParts.length>0 ? loginParts[0] : "login";
            String appName = loginParts.length>1 ? loginParts[1] : "WebX";
            if ( !loginPath.startsWith("/") ) loginPath = "/" + loginPath;
            String fullLoginPath = basePath + loginPath;
            HttpLoginHandler.appName = appName;
            server.handlers.put( fullLoginPath, new HttpLoginHandler() );
            Log.log( "Login handler configured at " + fullLoginPath + " for app: " + appName );
        }


        if ( !dbConfig.equalsIgnoreCase("NONE") ) {
            String[] dbParts = dbConfig.split("@", 2);
            String dbPath = dbParts.length>0 ? dbParts[0] : "db";
            if ( !dbPath.startsWith("/") ) dbPath = "/" + dbPath;
            String fullDbPath = basePath + dbPath;
            server.handlers.put( fullDbPath, new HttpJsonHandler(dbStorage) );
        }

        if ( pd != null ) {
            try ( PersistentData pdResource = pd ) {
                Log.log( "WebX server listening on port "+port );
                StringBuilder endpoints = new StringBuilder("Endpoints: ");
                if ( !staticConfig.equalsIgnoreCase("NONE") ) {
                    String[] staticParts = staticConfig.split("@", 2);
                    String staticPath = staticParts.length>0 ? staticParts[0] : "www";
                    if ( !staticPath.startsWith("/") ) staticPath = "/" + staticPath;
                    String staticDir = staticParts.length>1 ? staticParts[1] : "./datafiles/www";
                    File wwwDir = new File(staticDir);
                    if ( wwwDir.exists() ) endpoints.append(basePath).append(staticPath).append(" (static files), ");
                }
                if ( !proxyConfig.equalsIgnoreCase("NONE") ) {
                    String proxyPath = proxyConfig.split("@", 2)[0];
                    if ( !proxyPath.startsWith("/") ) proxyPath = "/" + proxyPath;
                    endpoints.append(basePath).append(proxyPath).append(" (API proxy), ");
                }
                if ( !loginConfig.equalsIgnoreCase("NONE") ) {
                    String loginPath = loginConfig.split("@", 2)[0];
                    if ( !loginPath.startsWith("/") ) loginPath = "/" + loginPath;
                    endpoints.append(basePath).append(loginPath).append(" (login), ");
                }
                if ( !dbConfig.equalsIgnoreCase("NONE") ) {
                    String[] dbParts = dbConfig.split("@", 2);
                    String dbPath = dbParts.length>0 ? dbParts[0] : "db";
                    if ( !dbPath.startsWith("/") ) dbPath = "/" + dbPath;
                    endpoints.append(basePath).append(dbPath).append(" (JSON database)");
                }
                Log.log( endpoints.toString() );
                server.start();
                Log.log( "WebX server stopped" );
            } catch ( Exception e ) { Log.log(e); }
        } else {
            Log.log( "WebX server listening on port "+port );
            StringBuilder endpoints = new StringBuilder("Endpoints: ");
            if ( !staticConfig.equalsIgnoreCase("NONE") ) {
                String[] staticParts = staticConfig.split("@", 2);
                String staticPath = staticParts.length>0 ? staticParts[0] : "www";
                if ( !staticPath.startsWith("/") ) staticPath = "/" + staticPath;
                String staticDir = staticParts.length>1 ? staticParts[1] : "./datafiles/www";
                File wwwDir = new File(staticDir);
                if ( wwwDir.exists() ) endpoints.append(staticPath).append(" (static files), ");
            }
            if ( !proxyConfig.equalsIgnoreCase("NONE") ) {
                String proxyPath = proxyConfig.split("@", 2)[0];
                if ( !proxyPath.startsWith("/") ) proxyPath = "/" + proxyPath;
                endpoints.append(proxyPath).append(" (API proxy), ");
            }
            if ( !loginConfig.equalsIgnoreCase("NONE") ) {
                String loginPath = loginConfig.split("@", 2)[0];
                if ( !loginPath.startsWith("/") ) loginPath = "/" + loginPath;
                endpoints.append(loginPath).append(" (login), ");
            }
            String endpointsStr = endpoints.toString();
            if ( endpointsStr.endsWith(", ") ) endpointsStr = endpointsStr.substring(0, endpointsStr.length()-2);
            if ( endpointsStr.equals("Endpoints: ") ) endpointsStr = "No endpoints configured";
            Log.log( endpointsStr );
            server.start();
            Log.log( "WebX server stopped" );
        }
    }






}
