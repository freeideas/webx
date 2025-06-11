package buildtools;
import java.io.*;
import java.util.*;
import jLib.*;



public class Build {



    public static final Class<?> ENTRY_POINT = Build.class; // NOTE: Change this to your main class
    public static final String APP_NAME = "WebX";
    public static final String LIB_XML = Lib.unindent("""
        <dependencies>
        </dependencies>
    """);
    /* // Examples of dependencies used in other apps
    public static final String LIB_XML = Lib.unindent("""
        <dependencies>

            <!-- JavaMail API -->
            <dependency>
            <groupId>com.sun.mail</groupId>
            <artifactId>javax.mail</artifactId>
            <version>1.6.2</version>
            </dependency>
            <dependency>
            <groupId>javax.activation</groupId>
            <artifactId>activation</artifactId>
            <version>1.1.1</version>
            </dependency>

            <!-- OpenCV -->
            <dependency>
            <groupId>org.openpnp</groupId>
            <artifactId>opencv</artifactId>
            <version>4.3.0-2</version>
            </dependency>

            <!-- yaml parser -->
            <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
            <version>1.30</version>
            </dependency>

            <!-- JImageHash -->
            <dependency>
                <groupId>dev.brachtendorf</groupId>
                <artifactId>JImageHash</artifactId>
                <version>1.0.0</version>
            </dependency>

            <!-- Everit JSON Schema validator -->
            <dependency>
                <groupId>com.github.erosb</groupId>
                <artifactId>everit-json-schema</artifactId>
                <version>1.14.5</version>
            </dependency>
            <!-- org.json dependency (required by Everit) -->
            <dependency>
                <groupId>org.json</groupId>
                <artifactId>json</artifactId>
                <version>20220924</version>
            </dependency>

            <!-- SQLite JDBC -->
            <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.49.1.0</version>
            </dependency>

            <!-- Rhino JavaScript Engine -->
            <dependency>
            <groupId>org.mozilla</groupId>
            <artifactId>rhino</artifactId>
            <version>1.8.0</version>
            </dependency>

            <!-- Rhino Engine (for ScriptEngine support) -->
            <dependency>
            <groupId>org.mozilla</groupId>
            <artifactId>rhino-engine</artifactId>
            <version>1.8.0</version>
            </dependency>

        </dependencies>
    """);
    */



    public static final File SRC_DIR = new File( "./java/src" );
    public static final File CLS_DIR = new File( "./java/classes" );
    public static final File LIB_DIR = new File( "./java/lib" );
    public static final File DIST_DIR = new File( "./java/dist" );



    public static void main(String[] args) throws Exception {
        //Lib.testClass();
        compile();
        // Skip downloadLibs() - manually place JAR dependencies in java/lib/
        File jarFile = buildJar();
        buildExe(jarFile);
    }



    public static void compile() throws Exception {
        Lib.archiveLogFiles();
        {
            Lib.log( "removing all class files" );
            Lib.rm(CLS_DIR);
            CLS_DIR.mkdirs();
        }
        {
            Lib.log("compiling");
            File srcFile = srcFile(ENTRY_POINT);
            List<String> compileCommand = new ArrayList<>();
            compileCommand.add("javac");
            compileCommand.add("-sourcepath");
            compileCommand.add(Lib.getCanonicalPath(SRC_DIR));
            compileCommand.add("-d");
            compileCommand.add(Lib.getCanonicalPath(CLS_DIR));
            compileCommand.add("--release");
            compileCommand.add("22");
            compileCommand.add(Lib.getCanonicalPath(srcFile));
            Lib.log("Executing compilation command");
            Process process = Lib.osCmd(compileCommand, null, null);
            int result = Lib.OSProcIO(process, null, System.out, System.err);
            if (result != 0) {
                Lib.log("Compilation failed with error code: " + result);
            } else {
                Lib.log("Compilation successful");
            }
        }
    }



    public static void downloadLibs() throws Exception {
        Lib.archiveLogFiles();
        Lib.log("Downloading library dependencies");
        File libDir = LIB_DIR;
        Lib.rm(libDir);
        libDir.mkdirs();
        File tempPomFile = new File("./log/temp_pom.xml");
        String pomContent = Lib.unindent("""
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>dependency-downloader</artifactId>
              <version>1.0-SNAPSHOT</version>
              <properties>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                <maven.compiler.source>22</maven.compiler.source>
                <maven.compiler.target>22</maven.compiler.target>
              </properties>
            """ + LIB_XML + """
            </project>
            """);
        try (FileWriter writer = new FileWriter(tempPomFile)) {
            writer.write(pomContent);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("mvn");
        cmd.add("-f");
        cmd.add( tempPomFile.getPath() );
        cmd.add("dependency:copy-dependencies");
        cmd.add("-DoutputDirectory=" + Lib.getCanonicalPath(LIB_DIR));
        Process process = Lib.osCmd(cmd, null, null);
        File logFile = new File(Lib.backupFilespec("./log/maven_out.txt"));
        int result = Lib.OSProcIO(process, null, new java.io.PrintStream(logFile), System.err);
        if (result==0) { Lib.log("Dependencies downloaded successfully"); }
        else { Lib.log("Failed. Check the log file for details: " + Lib.getCanonicalPath(logFile)); }
    }



    public static File buildJar() throws Exception {
        String timestamp = Lib.timeStamp("yyyy-MM-dd'_'HH-mm-ss");
        DIST_DIR.mkdirs();
        String jarFilename = APP_NAME + "_" + timestamp + ".jar";
        File jarFile = new File(DIST_DIR, jarFilename);
        Lib.log("Extracting dependencies from LIB_DIR to CLS_DIR");
        File[] libFiles = LIB_DIR.listFiles();
        if (libFiles != null) {
            for (File file : libFiles) {
                try {
                    Lib.log("Extracting: " + file.getName());
                    Lib.unzip(file, CLS_DIR, null, null);
                } catch (IOException e) {
                    Lib.log("Error extracting " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        Lib.log("Creating JAR file: " + Lib.getCanonicalPath(jarFile));
        List<String> jarCommand = new ArrayList<>();
        jarCommand.add("jar");
        jarCommand.add("--create");
        jarCommand.add("--file");
        jarCommand.add(Lib.getCanonicalPath(jarFile));
        jarCommand.add("--main-class");
        jarCommand.add(ENTRY_POINT.getName());
        jarCommand.add("-C");
        jarCommand.add(Lib.getCanonicalPath(CLS_DIR));
        jarCommand.add(".");
        Process process = Lib.osCmd(jarCommand, null, null);
        int result = Lib.OSProcIO(process, null, System.out, System.err);
        if (result != 0) {
            Lib.log("JAR creation failed with error code: " + result);
            throw new Exception("Failed to create JAR file");
        } else {
            File destFile = new File( DIST_DIR, APP_NAME+".jar" );
            Lib.cp(jarFile,destFile);
            jarFile = destFile;
            Lib.log("JAR creation successful: " + Lib.getCanonicalPath(jarFile));
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
        Lib.log("Creating native executable: " + Lib.getCanonicalPath(exeFile));
        List<String> nativeImageCommand = new ArrayList<>();
        nativeImageCommand.add("native-image");
        { // Optimization flags for standalone executable
            nativeImageCommand.add("-Os");  // Optimize for size
            nativeImageCommand.add("--no-fallback");
        }
        nativeImageCommand.add("-jar");
        nativeImageCommand.add(Lib.getCanonicalPath(jarFile));
        nativeImageCommand.add(Lib.getCanonicalPath(exeFile));
        Process process = Lib.osCmd(nativeImageCommand, null, null);
        File logFile = new File(Lib.backupFilespec("./log/native_image_out.txt"));
        int result = Lib.OSProcIO(process, null, new java.io.PrintStream(logFile), System.err);
        if (result != 0) {
            Lib.log("Native image creation failed with error code: " + result);
            Lib.log("Check the log file for details: " + Lib.getCanonicalPath(logFile));
            throw new Exception("Failed to create native executable");
        } else {
            File destFile = new File( DIST_DIR, APP_NAME );
            Lib.cp(exeFile,destFile);
            exeFile = destFile;
            Lib.log("Native executable creation successful: " + Lib.getCanonicalPath(exeFile));
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
        srcFile( Build.class );
        return true;
    }



}
