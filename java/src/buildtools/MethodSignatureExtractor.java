package buildtools;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import jLib.*;

public class MethodSignatureExtractor {
    
    public static void main( String[] args ) throws Exception {
        Map<String,List<String>> signatures = new TreeMap<>();
        Path classRoot = Paths.get( "java/tmp" );
        
        Files.walk( classRoot )
            .filter( p -> p.toString().endsWith( ".class" ) )
            .forEach( classFile -> extractSignatures( classFile, classRoot, signatures ) );
        
        String json = JsonEncoder.encode( signatures, " " );
        Files.writeString( Paths.get( "java/method-signatures.json" ), json );
        System.out.println( "Wrote method signatures for " + signatures.size() + " classes to java/method-signatures.json" );
    }
    
    
    
    private static void extractSignatures( Path classFile, Path classRoot, Map<String,List<String>> signatures ) {
        try {
            Process proc = Lib.osCmd( List.of( "javap", "-p", classFile.toString() ), null, null );
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream( baos );
            
            if ( Lib.OSProcIO( proc, null, ps, System.err )!=0 ) return;
            
            String[] lines = baos.toString().split( "\n" );
            String className = null;
            List<String> methods = new ArrayList<>();
            
            for ( String line : lines ) {
                line = line.trim();
                
                if ( line.contains( "class " ) || line.contains( "interface " ) || line.contains( "enum " ) ) {
                    int idx = line.indexOf( "class " );
                    if ( idx==-1 ) idx = line.indexOf( "interface " );
                    if ( idx==-1 ) idx = line.indexOf( "enum " );
                    
                    String[] parts = line.substring( idx ).split( " " );
                    if ( parts.length>1 ) {
                        className = parts[1];
                        if ( className.contains( "{" ) ) className = className.substring( 0, className.indexOf( "{" ) );
                    }
                }
                
                if ( line.contains( "(" ) && line.contains( ")" ) && !line.contains( "static {}" ) ) {
                    if ( !line.trim().startsWith( "private" ) ) {
                        String cleanLine = line;
                        int throwsIdx = cleanLine.indexOf( " throws " );
                        if ( throwsIdx != -1 ) {
                            cleanLine = cleanLine.substring( 0, throwsIdx ).trim() + ";";
                        }
                        methods.add( cleanLine );
                    }
                }
            }
            
            if ( className!=null && !methods.isEmpty() ) {
                String relativePath = classRoot.relativize( classFile ).toString();
                String fullClassName = relativePath.replace( File.separator, "." )
                    .replace( ".class", "" );
                
                if ( !fullClassName.contains( "$" ) ) {
                    signatures.put( fullClassName, methods );
                }
            }
        } catch ( Exception e ) {
            System.err.println( "Error processing " + classFile + ": " + e.getMessage() );
        }
    }
}