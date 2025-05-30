package build;
import jLib.Lib;
import java.io.File;
import java.util.List;

public class DownloadJars {
    public static void main(String[] args) throws Exception {
        // Create log directory
        new File("./log").mkdirs();
        // Build the Maven command
        List<String> cmd;
        String outputDir = new File("./java/lib").getAbsolutePath();
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            cmd = List.of(
                "cmd", "/c",
                "mvn", "-f", "./java/mvn_config.cfg",
                "dependency:copy-dependencies", "-DoutputDirectory=" + outputDir
            );
        } else {
            cmd = List.of(
                "mvn", "-f", "./java/mvn_config.cfg",
                "dependency:copy-dependencies", "-DoutputDirectory=" + outputDir
            );
        }

        // delete jars from lib
        File libDir = new File("./java/lib");
        Lib.rm(libDir);
        libDir.mkdirs();

        // Execute Maven command and redirect output to log file
        Process process = Lib.osCmd(cmd, null, null);
        File logFile = new File(Lib.backupFilespec("./log/maven_out.txt"));
        int result = Lib.OSProcIO(process, null, new java.io.PrintStream(logFile), System.err);
        if (result == 0) {
            System.out.println("Dependencies downloaded successfully");
        } else {
            System.err.println("Failed to download dependencies. Check the log file for details.");
            System.exit(1);
        }
    }
}