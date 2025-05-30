package appz.webx;
import java.util.*;
import http.*;
import jLib.*;
import persist.*;
import java.io.File;
import java.io.FileWriter;



public class Main {



    public static void main( String[] args ) {
        Lib.archiveLogFiles();
        int originalArgCount = args.length;
        ParseArgs p = new ParseArgs(args);
        p.setAppName( Lib.getAppName() );
        p.setDescr( "WebX - Simple Web Application Server" );
        
        // Parse all arguments (order matters for help display)
        int port = p.getInteger( "port", 13102, "listen to which port" );
        String wwwPath = p.getString( "www", "./datafiles/www", "directory to serve static files from" );
        String proxyPath = p.getString( "proxy", "/proxy", "path for proxy endpoint (use '(none)' to disable)" );
        String dbPath = p.getString( "db", "/db", "path for database endpoint" );
        String jdbcUrl = p.getString( "jdbc", "jdbc:hsqldb:file:./datafiles/dbf/webx-db", "JDBC URL for database connection" );
        boolean run = p.getBoolean( "run", false, "start the server" );
        
        // Show help if not running
        if ( !run ) {
            System.out.print( p.getHelp() );
            if ( originalArgCount > 0 ) {
                // Show configuration summary if any args were provided  
                System.out.println( "\nCONFIGURATION SUMMARY:" );
                System.out.println( "  Server will run on port: " + port );
                System.out.println( "  Static files directory: " + wwwPath );
                if ( proxyPath.equalsIgnoreCase("(none)") ) {
                    System.out.println( "  Proxy endpoint: DISABLED" );
                } else {
                    System.out.println( "  Proxy endpoint: " + proxyPath );
                }
                System.out.println( "  Database endpoint: " + dbPath );
                System.out.println( "  Database JDBC URL: " + jdbcUrl );
                System.out.println( "\nTo start the server, add --run to the command line." );
            }
            return;
        }
        
        HttpServer server = new HttpServer(port);        
        // Static File Server
        File wwwDir = new File(wwwPath);
        if (! wwwDir.exists() ) {
            Lib.log( "ERROR: " + wwwPath + " does not exist" );
        } else {
            server.handlers.put( "/", new HttpFileHandler("/", wwwDir) );
            Lib.log( "Static files configured at / from " + wwwDir.getAbsolutePath() );
        }
        // Proxy with replacements from ../api-keys.json
        if ( !proxyPath.equalsIgnoreCase("(none)") ) {
            if ( !proxyPath.startsWith("/") ) proxyPath = "/" + proxyPath;
            File apiKeysFile = new File( "../api-keys.json" );
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
        // JSON Database
        if ( !dbPath.startsWith("/") ) dbPath = "/" + dbPath;
        try( PersistentData pd = new PersistentData( jdbcUrl, "webx_data" ) ) {
            PersistentMap dbStorage = pd.getRootMap();
            server.handlers.put( dbPath, new HttpJsonHandler(dbStorage) );
            
            Lib.log( "WebX server listening on port "+port );
            StringBuilder endpoints = new StringBuilder("Endpoints: ");
            if ( wwwDir.exists() ) {
                endpoints.append("/ (static files), ");
            }
            if ( !proxyPath.equalsIgnoreCase("(none)") ) {
                endpoints.append(proxyPath).append(" (API proxy), ");
            }
            endpoints.append(dbPath).append(" (JSON database)");
            Lib.log( endpoints.toString() );
            server.start();
            Lib.log( "WebX server stopped" );
        } catch ( Exception e ) {
            Lib.log(e);
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