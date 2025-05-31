package appz.webx;

import java.io.*;
import java.nio.file.*;

public class WebXSimpleTest {
    
    public static void main(String[] args) throws Exception {
        // Simple test that uses Main.main() and WebShot
        
        String tempDb = "/tmp/webx-test-" + System.currentTimeMillis();
        String shutdownCode = "SHUTDOWN13104";
        
        // Start WebX in a thread with temp database
        Thread serverThread = new Thread(() -> {
            Main.main(new String[]{
                "--port=13104",
                "--db=db@jdbc:hsqldb:file:" + tempDb + ";shutdown=true",
                "--shutdown=" + shutdownCode,
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
                "http://localhost:13104/www/webx-proxy-test-headless.html",
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
            // Stop server gracefully using shutdown code
            if (serverThread != null && serverThread.isAlive()) {
                try {
                    System.out.println("Sending shutdown request to server...");
                    java.net.URL shutdownUrl = java.net.URI.create("http://localhost:13104/" + shutdownCode).toURL();
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) shutdownUrl.openConnection();
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    conn.getResponseCode(); // Trigger the request
                    conn.disconnect();
                    System.out.println("Shutdown request sent");
                } catch (Exception e) {
                    System.out.println("Shutdown request failed, using interrupt: " + e.getMessage());
                    serverThread.interrupt();
                }
                try {
                    serverThread.join(3000); // Wait up to 3 seconds for clean shutdown
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
            
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