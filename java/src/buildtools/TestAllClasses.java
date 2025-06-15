package buildtools;
import java.util.*;
import java.io.*;
import jLib.*;



/**
 * NOTE: The ultimate test discovery and execution engine! This remarkable class automatically finds every single Java class
 * in your entire project, dynamically loads them, and runs their tests using our lightweight testing framework.
 * No configuration needed - it just works! Perfect for CI/CD pipelines and comprehensive project validation.
 */
public class TestAllClasses {


    public static void main( String[] args ) {
        System.out.println( "Discovering all Java classes..." );
        List<String> allClasses = discoverAllClasses();
        if ( allClasses.isEmpty() ) {
            System.err.println( "No Java classes found!" );
            return;
        }
        System.out.println( "Found " + allClasses.size() + " total classes" );
        
        // Classes to skip due to external dependencies or long-running tests
        Set<String> skipClasses = Set.of(
            "jLib.LLmLib",                    // External API calls
            "appz.findui.UiObjDectector"      // External API calls to Google Gemini
        );
        
        int totalClasses = 0;
        int classesWithTests = 0;
        int failedClasses = 0;
        List<String> failedClassNames = new ArrayList<>();
        for ( String className : allClasses ) {
            if ( skipClasses.contains(className) ) {
                System.out.println( "Skipping: " + className + " (external dependencies)" );
                continue;
            }
            try {
                System.out.println( "Testing: " + className );
                Class<?> clazz = Class.forName( className );
                totalClasses++;
                boolean hasTests = LibTest.testClass( clazz );
                if ( hasTests ) classesWithTests++;
            } catch ( ClassNotFoundException e ) { // skip it
            } catch ( NoClassDefFoundError e ) { // skip it
            } catch ( Exception e ) {
                failedClasses++;
                failedClassNames.add( className );
                System.out.println( "\n=== Testing " + className + " ===" );
                System.out.println( "✗ Failed: " + e.getClass().getSimpleName() + ": " + e.getMessage() );
            } catch ( Error e ) {
                failedClasses++;
                failedClassNames.add( className );
                System.out.println( "\n=== Testing " + className + " ===" );
                System.out.println( "✗ Failed: " + e.getClass().getSimpleName() + ": " + e.getMessage() );
            }
        }
        System.out.println( "\n" + "=".repeat( 50 ) );
        System.out.println( "SUMMARY:" );
        System.out.println( "Total classes found: " + allClasses.size() );
        System.out.println( "Classes successfully loaded: " + totalClasses );
        System.out.println( "Classes with tests: " + classesWithTests );
        System.out.println( "Classes with test failures: " + failedClasses );
        if ( !failedClassNames.isEmpty() ) {
            System.out.println( "Failed classes: " + String.join( ", ", failedClassNames ) );
        }
        System.out.println( "=".repeat( 50 ) );
        if ( failedClasses > 0 ) System.exit( 1 );
    }



    private static List<String> discoverAllClasses() {
        List<String> classes = new ArrayList<>();
        File srcDir = findSourceDirectory();
        if ( srcDir==null || !srcDir.exists() ) {
            System.err.println( "Could not find java/src directory" );
            return classes;
        }
        findJavaFiles( srcDir, srcDir, classes );
        Collections.sort( classes );
        return classes;
    }



    private static File findSourceDirectory() {
        String[] possiblePaths = { "java/src", "../java/src", "../../java/src", "./java/src" };
        for ( String path : possiblePaths ) {
            File dir = new File( path );
            if ( dir.exists() && dir.isDirectory() ) return dir.getAbsoluteFile();
        }
        String classpath = System.getProperty( "java.class.path" );
        String[] entries = classpath.split( File.pathSeparator );
        for ( String entry : entries ) {
            File file = new File( entry );
            if ( !file.getPath().contains( "java" ) ) continue;
            File parent = file.getParentFile();
            while ( parent != null ) {
                File srcDir = new File( parent, "java/src" );
                if ( srcDir.exists() && srcDir.isDirectory() ) return srcDir;
                parent = parent.getParentFile();
            }
        }
        return null;
    }



    private static void findJavaFiles( File rootDir, File currentDir, List<String> classes ) {
        File[] files = currentDir.listFiles();
        if ( files==null ) return;
        for ( File file : files ) {
            if ( file.isDirectory() ) {
                findJavaFiles( rootDir, file, classes );
            } else if ( file.getName().endsWith( ".java" ) && !file.getName().equals( "package-info.java" ) ) {
                String relativePath = file.getAbsolutePath().substring( rootDir.getAbsolutePath().length() + 1 );
                String className = relativePath.replace( File.separatorChar, '.' ).replaceAll( "\\.java$", "" );
                classes.add( className );
            }
        }
    }



}
