package appz.webx;

import java.io.*;
import java.nio.file.*;
import jLib.*;

public class WebXSimpleTest {
    
    public static void main(String[] args) throws Exception {
        // Simple test that uses Main.main() and WebShot
        
        String tempDb = "/tmp/webx-test-" + System.currentTimeMillis();
        
        // Start WebX in a thread with temp database
        Thread serverThread = new Thread(() -> {
            Main.main(new String[]{
                "--port", "13104",
                "--jdbc", "jdbc:hsqldb:file:" + tempDb + ";shutdown=true",
                "--run"
            });
        });
        serverThread.start();
        
        // Wait for server
        System.out.println("Waiting for server to start...");
        Thread.sleep(3000);
        
        try {
            // Find and run WebShot
            File webshot = findWebShot();
            if (webshot == null) {
                System.err.println("WebShot not found! Build it first.");
                return;
            }
            
            System.out.println("Running WebShot test...");
            Process p = new ProcessBuilder(
                webshot.getAbsolutePath(),
                "http://localhost:13104/webx-proxy-test-headless.html",
                "test-output.png",
                "1280x800"
            ).start();
            
            p.waitFor();
            
            // Wait for JS to complete
            Thread.sleep(2000);
            
            // Check database file
            File dbScript = new File(tempDb + ".script");
            if (dbScript.exists()) {
                String content = Files.readString(dbScript.toPath());
                if (content.contains("proxyTest")) {
                    System.out.println("✅ SUCCESS: Found proxyTest data in database!");
                    System.out.println("Test passed - JavaScript executed and wrote to database.");
                } else {
                    System.out.println("❌ FAIL: No proxyTest data found in database");
                }
            } else {
                System.out.println("❌ FAIL: Database file not created");
            }
            
        } finally {
            // Stop server
            serverThread.interrupt();
            
            // Cleanup
            new File(tempDb + ".script").delete();
            new File(tempDb + ".properties").delete();
            new File(tempDb + ".log").delete();
        }
    }
    
    private static File findWebShot() {
        String[] paths = {
            "../webshot/dist/webshot",
            "../webshot/dist/webshot.exe"
        };
        for (String path : paths) {
            File f = new File(path);
            if (f.exists()) return f;
        }
        return null;
    }
}