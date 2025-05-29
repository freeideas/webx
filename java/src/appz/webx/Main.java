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
        if ( !proxyReplacementsFile.exists() ) {
            // Try fallback location
            proxyReplacementsFile = new File( "../api-keys.json" );
            if ( !proxyReplacementsFile.exists() ) {
                // Create example file
                createExampleApiKeysFile( proxyReplacementsFile );
                Lib.log( "Created example API keys file at " + proxyReplacementsFile.getAbsolutePath() );
            }
        }
        
        if ( proxyReplacementsFile.exists() ) {
            server.handlers.put( "/proxy", new HttpReplacingProxyHandler(proxyReplacementsFile) );
            Lib.log( "Proxy handler configured with replacements from " + proxyReplacementsFile );
        } else {
            server.handlers.put( "/proxy", new HttpProxyHandler() );
            Lib.log( "Proxy handler configured without replacements" );
        }
        
        // 3. JSON Database (/db)
        String jdbcUrl = "jdbc:hsqldb:file:./datafiles/dbf/webx-db";
        try( PersistentData pd = new PersistentData( jdbcUrl, "webx_data" ) ) {
            PersistentMap dbStorage = pd.getRootMap();
            server.handlers.put( "/db", new HttpJsonHandler(dbStorage) );
            
            Lib.log( "WebX server listening on port "+port );
            Lib.log( "Endpoints: /www (static files), /proxy (API proxy), /db (JSON database)" );
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