package http;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import jLib.*;


public class HttpOsCommandHandler implements HttpHandler {
    
    private final SecurityGuard securityGuard;
    private final int defaultTimeout;
    private final int maxConcurrentCommands;
    private final Semaphore commandSemaphore;
    
    
    
    
    public HttpOsCommandHandler( SecurityGuard securityGuard ) {
        this( securityGuard, 3000, 10 );
    }
    
    
    
    
    public HttpOsCommandHandler( SecurityGuard securityGuard, int defaultTimeout, int maxConcurrentCommands ) {
        this.securityGuard=securityGuard;
        this.defaultTimeout=defaultTimeout;
        this.maxConcurrentCommands=maxConcurrentCommands;
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
            
            if ( !securityGuard.test( request ) ) return new HttpErrorResponse( 403, "Command not allowed by security guard" );
            
            Map<String,Object> result=executeCommand( command, args, timeout );
            String jsonResponse=JsonEncoder.encode( result );
            HttpHeaderBlock responseHeader=new HttpHeaderBlock( 200, "OK", 
                Lib.mapOf( "Content-Type", "application/json" ) );
            return new HttpResponse( responseHeader, jsonResponse.getBytes() );
        }
        catch ( Exception e ) {
            return new HttpErrorResponse( 500, "Internal server error: "+e.getMessage() );
        }
    }
    
    
    
    
    public static Map<String,String> getCommandExecutables() {
        Map<String,String> executables=new HashMap<>();
        executables.put( "echo", "/bin/echo" );
        executables.put( "ls", "/bin/ls" );
        executables.put( "date", "/bin/date" );
        executables.put( "pwd", "/bin/pwd" );
        executables.put( "whoami", "/usr/bin/whoami" );
        executables.put( "claude", "/home/ace/.npm-global/bin/claude" );
        executables.put( "cd", "/bin/bash" );
        return executables;
    }
    
    
    
    
    private Map<String,Object> executeCommand( String command, List<String> args, int timeout ) throws Exception {
        if ( !commandSemaphore.tryAcquire( timeout, TimeUnit.MILLISECONDS ) ) {
            throw new RuntimeException( "Too many concurrent commands" );
        }
        
        try {
            String executable=getCommandExecutables().get( command );
            if ( executable==null ) throw new RuntimeException( "Unknown command: "+command );
            
            List<String> commandList=new ArrayList<>();
            commandList.add( executable );
            commandList.addAll( args );
            
            ProcessBuilder pb=new ProcessBuilder( commandList );
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
    
    
    
    
    
    
    
    
    private static int getStatusCode( HttpHeaderBlock headerBlock ) {
        String[] parts=headerBlock.firstLine.split( " " );
        return Integer.parseInt( parts[1] );
    }
    
    
    
    
    public static boolean mainTest_TEST_() throws Exception {
        SecurityGuard testGuard=new SecurityGuard() {
            @Override
            public boolean test( HttpRequest request ) {
                if ( request.parsedBody==null || !(request.parsedBody instanceof Map) ) return false;
                @SuppressWarnings("unchecked")
                Map<Object,Object> body=(Map<Object,Object>)request.parsedBody;
                String command=(String)body.get( "command" );
                @SuppressWarnings("unchecked")
                List<String> args=body.containsKey( "args" ) ? (List<String>)body.get( "args" ) : Collections.emptyList();
                
                if ( "echo".equals( command ) ) return true;
                if ( "ls".equals( command ) ) {
                    for ( String arg:args ) {
                        if ( !arg.matches( "^-[la]+$" ) && !arg.startsWith( "/tmp" ) ) return false;
                    }
                    return true;
                }
                return false;
            }
        };
        
        HttpOsCommandHandler handler=new HttpOsCommandHandler( testGuard );
        
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