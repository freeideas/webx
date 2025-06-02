package appz.webx;
import http.*;
import jLib.*;
import persist.*;
import java.io.File;
import java.io.FileWriter;



public class Main {



    @SuppressWarnings("try")
    public static void main( String[] args ) {
        Lib.archiveLogFiles();
        int originalArgCount = args.length;
        ParseArgs p = new ParseArgs(args);
        p.setAppName( Lib.getAppName() );
        p.setDescr( "WebX - Simple Web Application Server" );
        
        // Parse all arguments (order matters for help display)
        int port = p.getInteger( "port", 13102, "listen to which port" );
        String staticConfig = p.getString( "static", "www@./datafiles/www", "static files endpoint as path@directory (use 'NONE' to disable)" );
        String proxyConfig = p.getString( "proxy", "proxy@../api-keys.json", "proxy endpoint as path@config-file (use 'NONE' to disable)" );
        String dbConfig = p.getString( "db", "db@jdbc:hsqldb:file:./datafiles/dbf/webx-db", "database endpoint as path@jdbc-url (use 'NONE' to disable)" );
        String loginConfig = p.getString( "login", "login@WebX", "login endpoint as path@app-name (use 'NONE' to disable)" );
        String shutdownCode = p.getString( "shutdown", null, "shutdown code - if provided, server will exit when this code appears in the first line of any request" );
        boolean run = p.getBoolean( "run", false, "start the server" );
        
        // Show help if not running
        if ( !run ) {
            System.out.print( p.getHelp() );
            if ( originalArgCount > 0 ) {
                // Show configuration summary if any args were provided  
                System.out.println( "\nCONFIGURATION SUMMARY:" );
                System.out.println( "  Server will run on port: " + port );
                if ( staticConfig.equalsIgnoreCase("NONE") ) {
                    System.out.println( "  Static files: DISABLED" );
                } else {
                    System.out.println( "  Static files: " + staticConfig );
                }
                if ( proxyConfig.equalsIgnoreCase("NONE") ) {
                    System.out.println( "  Proxy endpoint: DISABLED" );
                } else {
                    System.out.println( "  Proxy: " + proxyConfig );
                }
                if ( dbConfig.equalsIgnoreCase("NONE") ) {
                    System.out.println( "  Database: DISABLED" );
                } else {
                    System.out.println( "  Database: " + dbConfig );
                }
                if ( loginConfig.equalsIgnoreCase("NONE") ) {
                    System.out.println( "  Login: DISABLED" );
                } else {
                    System.out.println( "  Login: " + loginConfig );
                }
                System.out.println( "\nTo start the server, add --run to the command line." );
            }
            return;
        }
        
        HttpServer server = new HttpServer(port);
        if ( shutdownCode != null ) {
            server.setShutdownCode( shutdownCode );
        }        
        // Static File Server
        if ( !staticConfig.equalsIgnoreCase("NONE") ) {
            String[] staticParts = staticConfig.split("@", 2);
            String staticPath = staticParts.length > 0 ? staticParts[0] : "www";
            String staticDir = staticParts.length > 1 ? staticParts[1] : "./datafiles/www";
            if (!staticPath.startsWith("/")) staticPath = "/" + staticPath;
            
            File wwwDir = new File(staticDir);
            if (! wwwDir.exists() ) {
                Lib.log( "ERROR: " + staticDir + " does not exist" );
            } else {
                server.handlers.put( staticPath, new HttpFileHandler(staticPath, wwwDir) );
                Lib.log( "Static files configured at " + staticPath + " from " + wwwDir.getAbsolutePath() );
            }
        }
        // Proxy handler
        if ( !proxyConfig.equalsIgnoreCase("NONE") ) {
            String[] proxyParts = proxyConfig.split("@", 2);
            String proxyPath = proxyParts.length > 0 ? proxyParts[0] : "proxy";
            String proxyConfigFile = proxyParts.length > 1 ? proxyParts[1] : "../api-keys.json";
            if ( !proxyPath.startsWith("/") ) proxyPath = "/" + proxyPath;
            
            File apiKeysFile = new File( proxyConfigFile );
            if ( !apiKeysFile.exists() ) {
                // Create example file
                createExampleApiKeysFile( apiKeysFile );
                Lib.log( "Created example API keys file at " + apiKeysFile.getAbsolutePath() );
            }
            if ( apiKeysFile.exists() ) {
                server.handlers.put( proxyPath, new HttpReplacingProxyHandler(apiKeysFile) );
                Lib.log( "Proxy handler configured at " + proxyPath + " with replacements from " + apiKeysFile );
            } else {
                server.handlers.put( proxyPath, new HttpProxyHandler() );
                Lib.log( "Proxy handler configured at " + proxyPath + " without replacements" );
            }
        }
        // Login handler
        if ( !loginConfig.equalsIgnoreCase("NONE") ) {
            String[] loginParts = loginConfig.split("@", 2);
            String loginPath = loginParts.length > 0 ? loginParts[0] : "login";
            String appName = loginParts.length > 1 ? loginParts[1] : "WebX";
            if ( !loginPath.startsWith("/") ) loginPath = "/" + loginPath;
            
            HttpLoginHandler.appName = appName;
            server.handlers.put( loginPath, new HttpLoginHandler() );
            Lib.log( "Login handler configured at " + loginPath + " for app: " + appName );
        }
        // JSON Database
        if ( !dbConfig.equalsIgnoreCase("NONE") ) {
            String[] dbParts = dbConfig.split("@", 2);
            String dbPath = dbParts.length > 0 ? dbParts[0] : "db";
            String jdbcUrl = dbParts.length > 1 ? dbParts[1] : "jdbc:hsqldb:file:./datafiles/dbf/webx-db";
            if ( !dbPath.startsWith("/") ) dbPath = "/" + dbPath;
            
            try( PersistentData pd = new PersistentData( jdbcUrl, "webx_data" ) ) {
                PersistentMap dbStorage = pd.getRootMap();
                server.handlers.put( dbPath, new HttpJsonHandler(dbStorage) );
                
                Lib.log( "WebX server listening on port "+port );
                StringBuilder endpoints = new StringBuilder("Endpoints: ");
                if ( !staticConfig.equalsIgnoreCase("NONE") ) {
                    String[] staticParts = staticConfig.split("@", 2);
                    String staticPath = staticParts.length > 0 ? staticParts[0] : "www";
                    if (!staticPath.startsWith("/")) staticPath = "/" + staticPath;
                    String staticDir = staticParts.length > 1 ? staticParts[1] : "./datafiles/www";
                    File wwwDir = new File(staticDir);
                    if ( wwwDir.exists() ) {
                        endpoints.append(staticPath).append(" (static files), ");
                    }
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
                endpoints.append(dbPath).append(" (JSON database)");
                Lib.log( endpoints.toString() );
                server.start();
                Lib.log( "WebX server stopped" );
            } catch ( Exception e ) {
                Lib.log(e);
            }
        } else {
            Lib.log( "WebX server listening on port "+port );
            StringBuilder endpoints = new StringBuilder("Endpoints: ");
            if ( !staticConfig.equalsIgnoreCase("NONE") ) {
                String[] staticParts = staticConfig.split("@", 2);
                String staticPath = staticParts.length > 0 ? staticParts[0] : "www";
                if (!staticPath.startsWith("/")) staticPath = "/" + staticPath;
                String staticDir = staticParts.length > 1 ? staticParts[1] : "./datafiles/www";
                File wwwDir = new File(staticDir);
                if ( wwwDir.exists() ) {
                    endpoints.append(staticPath).append(" (static files), ");
                }
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
            // Remove trailing comma and space if present
            String endpointsStr = endpoints.toString();
            if (endpointsStr.endsWith(", ")) {
                endpointsStr = endpointsStr.substring(0, endpointsStr.length() - 2);
            }
            if (endpointsStr.equals("Endpoints: ")) {
                endpointsStr = "No endpoints configured";
            }
            Lib.log( endpointsStr );
            server.start();
            Lib.log( "WebX server stopped" );
        }
    }



    private static void createExampleApiKeysFile( File file ) {
        String exampleContent = """
            {
                "https://api.openai.com": {
                    "<%=apikey%>": "sk-YOUR-OPENAI-API-KEY-HERE",
                    "<%=org%>": "org-YOUR-ORG-ID-HERE"
                },
                "https://api.openai.com/v1/chat/completions": {
                    "<%=model%>": "gpt-4",
                    "<%=apikey%>": "sk-YOUR-OPENAI-API-KEY-HERE"
                },
                "https://api.anthropic.com": {
                    "<%=apikey%>": "sk-ant-YOUR-ANTHROPIC-KEY-HERE",
                    "<%=model%>": "claude-3-opus-20240229"
                },
                "https://www.googleapis.com": {
                    "<%=apikey%>": "YOUR-GOOGLE-API-KEY-HERE"
                },
                "https://www.googleapis.com/customsearch": {
                    "<%=apikey%>": "YOUR-GOOGLE-API-KEY-HERE",
                    "<%=cx%>": "YOUR-CUSTOM-SEARCH-ENGINE-ID"
                },
                "https://api.github.com": {
                    "<%=token%>": "ghp_YOUR-GITHUB-TOKEN-HERE",
                    "<%=user%>": "your-github-username"
                },
                "*": {
                    "<%=default_key%>": "FALLBACK-KEY-FOR-OTHER-APIS"
                }
            }
        """;
        
        try {
            // Ensure parent directory exists
            File parentDir = file.getParentFile();
            if ( parentDir != null && !parentDir.exists() ) {
                parentDir.mkdirs();
            }
            
            try ( FileWriter writer = new FileWriter( file ) ) {
                writer.write( exampleContent );
            }
        } catch ( Exception e ) {
            Lib.log( "Failed to create example API keys file: " + e.getMessage() );
        }
    }



}