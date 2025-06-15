package buildtools;
import java.io.*;
import java.util.*;
import jLib.*;



/**
 * Build system for creating WebX JAR and native executables.
 *
 * IMPORTANT: Directory Structure
 * - ./java/tmp/     - Temporary class files used by javac.sh and IDE (can be replaced anytime)
 * - ./java/classes/ - Production class files compiled by this Build tool
 * - ./java/lib/     - External JAR dependencies
 * - ./java/dist/    - Final JAR and executable output
 *
 * This Build tool compiles to ./java/classes/ (not ./java/tmp/) to ensure:
 * 1. Full control over compilation flags and process
 * 2. Clean separation from IDE/development builds
 * 3. All library JARs can be extracted to the same location before JAR creation
 * 4. Consistent and reproducible builds
 */
public class Build {



    public static final Class<?> ENTRY_POINT = appz.webx.Main.class;
    public static final String APP_NAME = "WebX";
    public static final File SRC_DIR = new File( "./java/src" );
    public static final File CLS_DIR = new File( "./java/classes" );
    public static final File LIB_DIR = new File( "./java/lib" );
    public static final File DIST_DIR = new File( "./java/dist" );



    public static void main( String[] args ) throws Exception {
        LibTest.testClass();
        compile();
        File jarFile = buildJar();
        // buildExe( jarFile );  // Disabled for now - native binary creation is slow
    }



    public static void compile() throws Exception {
        LibApp.archiveLogFiles();
        {
            Lib.log( "removing all class files" );
            LibFile.rm( CLS_DIR );
            CLS_DIR.mkdirs();
        }
        {
            Lib.log("compiling from entry point");
            File srcFile = srcFile(ENTRY_POINT);
            List<String> compileCommand = new ArrayList<>();
            compileCommand.add("javac");
            compileCommand.add("-sourcepath");
            compileCommand.add(LibFile.getCanonicalPath(SRC_DIR));
            compileCommand.add("-d");
            compileCommand.add(LibFile.getCanonicalPath(CLS_DIR));
            compileCommand.add("-cp");
            compileCommand.add(LibFile.getCanonicalPath(LIB_DIR) + "/*");
            compileCommand.add("--release");
            compileCommand.add("22");
            compileCommand.add(LibFile.getCanonicalPath(srcFile));
            Lib.log("Executing compilation command");
            Process process = Lib.osCmd(compileCommand, null, null);
            int result = Lib.OSProcIO(process, null, System.out, System.err);
            if (result != 0) {
                Lib.log("Compilation failed with error code: " + result);
                throw new Exception("Compilation failed");
            } else {
                Lib.log("Compilation successful");
            }
        }
    }



    public static File buildJar() throws Exception {
        String timestamp = Lib.timeStamp("yyyy-MM-dd'_'HH-mm-ss");
        DIST_DIR.mkdirs();
        String jarFilename = APP_NAME + "_" + timestamp + ".jar";
        File jarFile = new File(DIST_DIR, jarFilename);
        Lib.log( "Extracting dependencies from LIB_DIR to CLS_DIR" );
        File[] libFiles = LIB_DIR.listFiles();
        if ( libFiles != null ) {
            for ( File file : libFiles ) {
                if ( !file.getName().endsWith( ".jar" ) ) continue;
                try {
                    Lib.log( "Extracting: " + file.getName() );
                    LibFile.unzip( file, CLS_DIR, null, null );
                } catch ( IOException e ) {
                    Lib.log( "Error extracting " + file.getName() + ": " + e.getMessage() );
                }
            }
        }
        Lib.log("Creating JAR file: " + LibFile.getCanonicalPath(jarFile));
        List<String> jarCommand = new ArrayList<>();
        jarCommand.add("jar");
        jarCommand.add("--create");
        jarCommand.add("--file");
        jarCommand.add(LibFile.getCanonicalPath(jarFile));
        jarCommand.add("--main-class");
        jarCommand.add(ENTRY_POINT.getName());
        jarCommand.add("-C");
        jarCommand.add(LibFile.getCanonicalPath(CLS_DIR));
        jarCommand.add(".");
        Process process = Lib.osCmd(jarCommand, null, null);
        int result = Lib.OSProcIO(process, null, System.out, System.err);
        if (result != 0) {
            Lib.log("JAR creation failed with error code: " + result);
            throw new Exception("Failed to create JAR file");
        } else {
            File destFile = new File( DIST_DIR, APP_NAME+".jar" );
            LibFile.cp( jarFile, destFile );
            jarFile = destFile;
            Lib.log("JAR creation successful: " + LibFile.getCanonicalPath(jarFile));
        }
        return jarFile;
    }



    public static File buildExe( File jarFile ) throws Exception {
        String timestamp = Lib.timeStamp("yyyy-MM-dd'_'HH-mm-ss");
        DIST_DIR.mkdirs();
        String exeFilename = APP_NAME + "_" + timestamp;
        File exeFile = new File(DIST_DIR, exeFilename);
        if (jarFile == null || !jarFile.exists()) {
            Lib.log("Failed to build JAR file");
            throw new Exception("Failed to build JAR file");
        }
        Lib.log("Creating native executable: " + LibFile.getCanonicalPath(exeFile));
        List<String> nativeImageCommand = new ArrayList<>();
        nativeImageCommand.add("native-image");
        { // Optimization flags for standalone executable
            nativeImageCommand.add("-Os");  // Optimize for size
            nativeImageCommand.add("--no-fallback");
        }
        nativeImageCommand.add("-jar");
        nativeImageCommand.add(LibFile.getCanonicalPath(jarFile));
        nativeImageCommand.add(LibFile.getCanonicalPath(exeFile));
        Process process = Lib.osCmd(nativeImageCommand, null, null);
        File logFile = new File( LibFile.backupFilespec( "./log/native_image_out.txt" ) );
        int result = Lib.OSProcIO(process, null, new java.io.PrintStream(logFile), System.err);
        if (result != 0) {
            Lib.log("Native image creation failed with error code: " + result);
            Lib.log("Check the log file for details: " + LibFile.getCanonicalPath(logFile));
            throw new Exception("Failed to create native executable");
        } else {
            File destFile = new File( DIST_DIR, APP_NAME );
            LibFile.cp( exeFile, destFile );
            exeFile = destFile;
            Lib.log("Native executable creation successful: " + LibFile.getCanonicalPath(exeFile));
        }
        return exeFile;
    }



    private static File srcFile( Class<?> cls ) {
        String className = cls.getName();
        String fileName = className.replace('.','/')+".java";
        File srcFile = new File( SRC_DIR, fileName );
        Lib.asrt( srcFile.isFile() );
        return srcFile;
    }
    @SuppressWarnings("unused")
    private static boolean srcFile_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Lib.asrt( srcFile(Build.class).isFile(), "srcFile() should return a valid file" );
        return true;
    }






}
