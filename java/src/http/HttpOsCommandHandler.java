package http;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import jLib.*;


public class HttpOsCommandHandler implements HttpHandler {
    
    private final String configPath;
    private final Map<String,CommandConfig> allowedCommands;
    private final int defaultTimeout;
    private final int maxConcurrentCommands;
    private final Semaphore commandSemaphore;
    
    
    
    
    public HttpOsCommandHandler( String configPath ) throws IOException {
        this.configPath=configPath;
        Map<String,Object> config=loadConfig();
        this.allowedCommands=parseAllowedCommands( (Map<String,Object>)config.get( "allowedCommands" ) );
        this.defaultTimeout=((Number)config.getOrDefault( "defaultTimeout", 3000 )).intValue();
        this.maxConcurrentCommands=((Number)config.getOrDefault( "maxConcurrentCommands", 10 )).intValue();
        this.commandSemaphore=new Semaphore( maxConcurrentCommands );
    }
    
    
    
    
    @Override
    public HttpResponse handle( HttpRequest request ) {
        String path=request.headerBlock.getRequestPath();
        String method=request.headerBlock.getMethod();
        
        if ( !path.equals( "/oscmd" ) ) return null;
        if ( !method.equals( "POST" ) ) return new HttpErrorResponse( 405, "Method not allowed" );
        
        try {
            if ( request.parsedBody==null ) return new HttpErrorResponse( 400, "Missing request body" );
            if ( !(request.parsedBody instanceof Map) ) return new HttpErrorResponse( 400, "Invalid request body" );
            
            @SuppressWarnings("unchecked")
            Map<Object,Object> body=(Map<Object,Object>)request.parsedBody;
            String command=(String)body.get( "command" );
            @SuppressWarnings("unchecked")
            List<String> args=body.containsKey( "args" ) ? (List<String>)body.get( "args" ) : Collections.emptyList();
            int timeout=body.containsKey( "timeout" ) ? ((Number)body.get( "timeout" )).intValue() : defaultTimeout;
            
            if ( command==null || command.isEmpty() ) return new HttpErrorResponse( 400, "Missing command" );
            
            CommandConfig config=allowedCommands.get( command );
            if ( config==null ) return new HttpErrorResponse( 403, "Command not allowed: "+command );
            
            if ( !validateArgs( config, args ) ) return new HttpErrorResponse( 403, "Invalid arguments for command: "+command );
            
            if ( timeout>config.maxTimeout ) timeout=config.maxTimeout;
            
            Map<String,Object> result=executeCommand( config, args, timeout );
            String jsonResponse=JsonEncoder.encode( result );
            HttpHeaderBlock responseHeader=new HttpHeaderBlock( 200, "OK", 
                Lib.mapOf( "Content-Type", "application/json" ) );
            return new HttpResponse( responseHeader, jsonResponse.getBytes() );
        }
        catch ( Exception e ) {
            return new HttpErrorResponse( 500, "Internal server error: "+e.getMessage() );
        }
    }
    
    
    
    
    private Map<String,Object> loadConfig() throws IOException {
        String json=Files.readString( Paths.get( configPath ) );
        Object decoded=JsonDecoder.decode( json );
        if ( !(decoded instanceof Map) ) throw new IOException( "Invalid config format" );
        @SuppressWarnings("unchecked")
        Map<String,Object> map=(Map<String,Object>)decoded;
        return map;
    }
    
    
    
    
    private Map<String,CommandConfig> parseAllowedCommands( Map<String,Object> commands ) {
        Map<String,CommandConfig> result=new HashMap<>();
        for ( Map.Entry<String,Object> entry:commands.entrySet() ) {
            String name=entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String,Object> cmdConfig=(Map<String,Object>)entry.getValue();
            CommandConfig config=new CommandConfig();
            config.executable=(String)cmdConfig.get( "executable" );
            config.allowAllArgs=(boolean)cmdConfig.getOrDefault( "allowAllArgs", false );
            config.maxTimeout=((Number)cmdConfig.getOrDefault( "maxTimeout", defaultTimeout )).intValue();
            
            @SuppressWarnings("unchecked")
            List<String> allowedArgs=(List<String>)cmdConfig.get( "allowedArgs" );
            if ( allowedArgs!=null ) {
                config.allowedArgPatterns=new ArrayList<>();
                for ( String pattern:allowedArgs ) {
                    config.allowedArgPatterns.add( Pattern.compile( pattern ) );
                }
            }
            
            result.put( name, config );
        }
        return result;
    }
    
    
    
    
    private boolean validateArgs( CommandConfig config, List<String> args ) {
        if ( config.allowAllArgs ) return true;
        if ( config.allowedArgPatterns==null ) return args.isEmpty();
        
        for ( String arg:args ) {
            boolean allowed=false;
            for ( Pattern pattern:config.allowedArgPatterns ) {
                if ( pattern.matcher( arg ).matches() ) {
                    allowed=true;
                    break;
                }
            }
            if ( !allowed ) return false;
        }
        return true;
    }
    
    
    
    
    private Map<String,Object> executeCommand( CommandConfig config, List<String> args, int timeout ) throws Exception {
        if ( !commandSemaphore.tryAcquire( timeout, TimeUnit.MILLISECONDS ) ) {
            throw new RuntimeException( "Too many concurrent commands" );
        }
        
        try {
            List<String> command=new ArrayList<>();
            command.add( config.executable );
            command.addAll( args );
            
            ProcessBuilder pb=new ProcessBuilder( command );
            Process process=pb.start();
            
            ByteArrayOutputStream stdout=new ByteArrayOutputStream();
            ByteArrayOutputStream stderr=new ByteArrayOutputStream();
            
            Thread stdoutReader=new Thread( ()->copyStream( process.getInputStream(), stdout ) );
            Thread stderrReader=new Thread( ()->copyStream( process.getErrorStream(), stderr ) );
            
            stdoutReader.start();
            stderrReader.start();
            
            boolean finished=process.waitFor( timeout, TimeUnit.MILLISECONDS );
            
            if ( !finished ) {
                process.destroyForcibly();
                throw new RuntimeException( "Command timed out after "+timeout+"ms" );
            }
            
            stdoutReader.join( 1000 );
            stderrReader.join( 1000 );
            
            Map<String,Object> result=new HashMap<>();
            result.put( "success", true );
            result.put( "stdout", stdout.toString() );
            result.put( "stderr", stderr.toString() );
            result.put( "exitCode", process.exitValue() );
            
            return result;
        }
        finally {
            commandSemaphore.release();
        }
    }
    
    
    
    
    private void copyStream( InputStream in, OutputStream out ) {
        try {
            byte[] buffer=new byte[8192];
            int n;
            while ( (n=in.read( buffer ))>0 ) {
                out.write( buffer, 0, n );
            }
        }
        catch ( IOException e ) { }
    }
    
    
    
    
    private static class CommandConfig {
        String executable;
        boolean allowAllArgs;
        List<Pattern> allowedArgPatterns;
        int maxTimeout;
    }
    
    
    
    
    private static int getStatusCode( HttpHeaderBlock headerBlock ) {
        String[] parts=headerBlock.firstLine.split( " " );
        return Integer.parseInt( parts[1] );
    }
    
    
    
    
    public static boolean mainTest_TEST_() throws Exception {
        String testConfig="{\n"+
            "  \"allowedCommands\": {\n"+
            "    \"echo\": {\n"+
            "      \"executable\": \"/bin/echo\",\n"+
            "      \"allowAllArgs\": true,\n"+
            "      \"maxTimeout\": 1000\n"+
            "    },\n"+
            "    \"ls\": {\n"+
            "      \"executable\": \"/bin/ls\",\n"+
            "      \"allowedArgs\": [\"^-[la]+$\", \"^/tmp.*\"],\n"+
            "      \"maxTimeout\": 5000\n"+
            "    }\n"+
            "  },\n"+
            "  \"defaultTimeout\": 3000,\n"+
            "  \"maxConcurrentCommands\": 10\n"+
            "}";
        
        File tempConfig=File.createTempFile( "oscmd-test", ".json" );
        tempConfig.deleteOnExit();
        Files.writeString( tempConfig.toPath(), testConfig );
        
        HttpOsCommandHandler handler=new HttpOsCommandHandler( tempConfig.getAbsolutePath() );
        
        Map<String,String> headers=new HashMap<>();
        headers.put( "Content-Type", "application/json" );
        
        HttpHeaderBlock headerBlock=new HttpHeaderBlock( "POST /oscmd HTTP/1.1", headers );
        String jsonBody="{\"command\":\"echo\",\"args\":[\"hello\",\"world\"]}";
        HttpMessage message=new HttpMessage( headerBlock, jsonBody.getBytes() );
        HttpRequest request=HttpRequest.newHttpRequest( message );
        
        HttpResponse response=handler.handle( request );
        Lib.asrt( getStatusCode( response.headerBlock )==200, "Echo command should succeed" );
        
        Object decoded=JsonDecoder.decode( new String( response.body ) );
        @SuppressWarnings("unchecked")
        Map<String,Object> result=(Map<String,Object>)decoded;
        Lib.asrt( (boolean)result.get( "success" ), "Command should be successful" );
        Lib.asrt( result.get( "stdout" ).toString().trim().equals( "hello world" ), "Output should match" );
        Lib.asrtEQ( ((Number)result.get( "exitCode" )).intValue(), 0, "Exit code should be 0" );
        
        jsonBody="{\"command\":\"invalid\",\"args\":[]}";
        message=new HttpMessage( headerBlock, jsonBody.getBytes() );
        request=HttpRequest.newHttpRequest( message );
        response=handler.handle( request );
        Lib.asrt( getStatusCode( response.headerBlock )==403, "Invalid command should be forbidden" );
        
        jsonBody="{\"command\":\"ls\",\"args\":[\"-la\",\"/tmp\"]}";
        message=new HttpMessage( headerBlock, jsonBody.getBytes() );
        request=HttpRequest.newHttpRequest( message );
        response=handler.handle( request );
        Lib.asrt( getStatusCode( response.headerBlock )==200, "Valid ls command should succeed" );
        
        jsonBody="{\"command\":\"ls\",\"args\":[\"-la\",\"/etc\"]}";
        message=new HttpMessage( headerBlock, jsonBody.getBytes() );
        request=HttpRequest.newHttpRequest( message );
        response=handler.handle( request );
        Lib.asrt( getStatusCode( response.headerBlock )==403, "ls with /etc should be forbidden" );
        
        return true;
    }
    
    
    
    
    public static void main( String[] args ) throws Exception { Lib.testClass( HttpOsCommandHandler.class ); }
}