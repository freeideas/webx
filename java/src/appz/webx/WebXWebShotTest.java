package appz.webx;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import jLib.*;
import persist.*;

public class WebXWebShotTest {
    private static final int TEST_PORT_BASE = 14000; // Use higher port range to avoid conflicts
    
    public static void main(String[] args) throws Exception {
        Lib.testClass(WebXWebShotTest.class);
    }
    
    // Get a unique port for each test using timestamp to avoid conflicts
    private static synchronized int getNextPort() {
        return TEST_PORT_BASE + (int)(System.currentTimeMillis() % 1000);
    }
    
    public static boolean webShotIntegrationTest_TEST_() throws Exception {
        // Find WebShot executable
        File webShotExe = findWebShot();
        if (webShotExe == null || !webShotExe.exists()) {
            System.out.println("WebShot not found. Skipping test. Build it with: cd ../webshot && ./build.sh");
            return true; // Don't fail the test if WebShot isn't built
        }
        
        int testPort = getNextPort();
        String testDbUrl = "jdbc:hsqldb:mem:webxtest" + testPort;
        Thread serverThread = null;
        
        try {
            // Start WebX server in a thread using Main.main()
            serverThread = new Thread(() -> {
                try {
                    System.out.println("Starting server with args: --port=" + testPort + " --db=db@" + testDbUrl + " --run");
                    Main.main(new String[]{
                        "--port=" + testPort,
                        "--db=db@" + testDbUrl,
                        "--run"
                    });
                } catch (Exception e) {
                    System.err.println("Server thread error: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            serverThread.start();
            
            // Wait for server to start
            System.out.println("Waiting for WebX server to start on port " + testPort + "...");
            if (!waitForServer(testPort, 30)) {
                System.err.println("Server failed to start within 30 seconds");
                return false;
            }
            
            System.out.println("Server is ready on port " + testPort);
            
            // Create output directory
            File outputDir = new File("./log");
            outputDir.mkdirs();
            
            // Execute WebShot to run JavaScript tests
            String testUrl = "http://localhost:" + testPort + "/www/webx-proxy-test-headless.html";
            String outputImage = outputDir.getAbsolutePath() + "/webshot-test-" + System.currentTimeMillis() + ".png";
            
            System.out.println("Running WebShot to execute JavaScript tests...");
            System.out.println("Test URL: " + testUrl);
            
            ProcessBuilder pb = new ProcessBuilder(
                webShotExe.getAbsolutePath(),
                testUrl,
                outputImage,
                "1280x800"
            );
            
            Process process = pb.start();
            
            // Capture WebShot output for debugging
            StringBuilder output = new StringBuilder();
            StringBuilder errors = new StringBuilder();
            
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        System.out.println("WebShot: " + line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            
            Thread errorReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errors.append(line).append("\n");
                        System.err.println("WebShot ERROR: " + line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            
            outputReader.start();
            errorReader.start();
            
            boolean completed = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            outputReader.join(1000);
            errorReader.join(1000);
            
            if (!completed) {
                process.destroyForcibly();
                System.err.println("WebShot timed out");
                return false;
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                System.err.println("WebShot exited with code: " + exitCode);
                return false;
            }
            
            // Check if screenshot was created
            File screenshot = new File(outputImage);
            if (!screenshot.exists() || screenshot.length() == 0) {
                System.err.println("Screenshot was not created");
                return false;
            }
            
            System.out.println("Screenshot created: " + outputImage + " (size: " + screenshot.length() + " bytes)");
            
            // Wait for JavaScript to complete database writes
            System.out.println("Waiting for JavaScript to write to database...");
            Thread.sleep(3000);
            
            // Now verify database writes by connecting to the same in-memory database
            System.out.println("\nVerifying database writes...");
            
            // Connect to the same in-memory database that WebX is using
            try (Connection conn = DriverManager.getConnection(testDbUrl, "SA", "");
                 Statement stmt = conn.createStatement()) {
                
                // First, let's see what tables exist
                ResultSet tables = conn.getMetaData().getTables(null, null, "%", null);
                System.out.println("Tables in database:");
                while (tables.next()) {
                    System.out.println("  Table: " + tables.getString("TABLE_NAME"));
                }
                
                // Query the database directly to check what JavaScript wrote
                ResultSet rs = stmt.executeQuery(
                    "SELECT * FROM webx_data WHERE value_json LIKE '%proxyTest%'"
                );
                
                boolean foundProxyTest = false;
                while (rs.next()) {
                    String keyJson = rs.getString("key_json");
                    String valueJson = rs.getString("value_json");
                    
                    System.out.println("Found row: key=" + keyJson + ", value=" + valueJson);
                    
                    if (valueJson.contains("proxyTest")) {
                        foundProxyTest = true;
                        System.out.println("✅ Found proxyTest data!");
                        System.out.println("Found row: key=" + keyJson + ", value=" + valueJson);
                        
                        // Decode the JSON value to verify structure
                        Object value = JsonDecoder.decode(valueJson);
                        if (value instanceof Map) {
                            Map<?, ?> rootData = (Map<?, ?>) value;
                            
                            // Check if proxyTest is nested under the root
                            Object proxyTestObj = rootData.get("proxyTest");
                            if (proxyTestObj instanceof Map) {
                                Map<?, ?> proxyTestData = (Map<?, ?>) proxyTestObj;
                                boolean hasAllFields = 
                                    proxyTestData.containsKey("timestamp") && 
                                    proxyTestData.containsKey("browser") && 
                                    proxyTestData.containsKey("testRun");
                                    
                                if (hasAllFields) {
                                    System.out.println("✅ ProxyTest data has all expected fields:");
                                    System.out.println("  timestamp: " + proxyTestData.get("timestamp"));
                                    System.out.println("  browser: " + proxyTestData.get("browser"));
                                    System.out.println("  testRun: " + proxyTestData.get("testRun"));
                                    
                                    // Verify browser field is "headless"
                                    if ("headless".equals(proxyTestData.get("browser"))) {
                                        System.out.println("✅ Browser field correctly set to 'headless'");
                                    } else {
                                        System.err.println("❌ Browser field not set to 'headless'");
                                        return false;
                                    }
                                } else {
                                    System.err.println("❌ ProxyTest data missing expected fields");
                                    return false;
                                }
                            } else {
                                System.err.println("❌ ProxyTest is not a nested object");
                                return false;
                            }
                        }
                    }
                }
                
                if (!foundProxyTest) {
                    // Let's see what's actually in the database
                    System.err.println("❌ No proxyTest data found in database");
                    System.err.println("Dumping all database contents:");
                    
                    rs = stmt.executeQuery("SELECT * FROM webx_data");
                    int rowCount = 0;
                    while (rs.next()) {
                        rowCount++;
                        System.err.println("Row " + rowCount + ": key=" + rs.getString("key_json") + 
                                         ", value=" + rs.getString("value_json"));
                    }
                    
                    if (rowCount == 0) {
                        System.err.println("Database is empty!");
                    }
                    
                    return false;
                }
            }
            
            System.out.println("\n✅ All tests passed!");
            System.out.println("JavaScript executed successfully and wrote to the database.");
            return true;
            
        } catch (Exception e) {
            System.err.println("Test failed with exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // Stop the server
            if (serverThread != null) {
                serverThread.interrupt();
                try {
                    serverThread.join(5000); // Wait up to 5 seconds for clean shutdown
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }
    }
    
    // Helper method to wait for server to start
    private static boolean waitForServer(int port, int timeoutSeconds) {
        for (int i = 0; i < timeoutSeconds; i++) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("localhost", port), 1000);
                socket.close();
                return true;
            } catch (Exception e) {
                // Server not ready yet
            }
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false;
    }
    
    // Test direct database operations with in-memory DB
    public static boolean inMemoryDatabaseTest_TEST_() throws Exception {
        String testDbUrl = "jdbc:hsqldb:mem:directtest" + System.currentTimeMillis();
        
        // Write data
        try (PersistentData pd = new PersistentData(testDbUrl, "test_table")) {
            PersistentMap rootMap = pd.getRootMap();
            
            Map<String, Object> testData = new HashMap<>();
            testData.put("testKey", "testValue");
            testData.put("timestamp", System.currentTimeMillis());
            rootMap.put("directTest", testData);
        }
        
        // Read data back in a new connection
        try (PersistentData pd = new PersistentData(testDbUrl, "test_table")) {
            PersistentMap rootMap = pd.getRootMap();
            
            Object retrieved = rootMap.get("directTest");
            if (retrieved instanceof Map) {
                Map<?, ?> retrievedMap = (Map<?, ?>) retrieved;
                boolean success = "testValue".equals(retrievedMap.get("testKey"));
                System.out.println("Direct database test: " + (success ? "PASS" : "FAIL"));
                return success;
            }
            
            return false;
        }
    }
    
    // Test that verifies the proxy endpoint works
    public static boolean proxyEndpointTest_TEST_() throws Exception {
        int testPort = getNextPort();
        String testDbUrl = "jdbc:hsqldb:mem:proxytest" + testPort;
        
        // Start server
        Thread testThread = new Thread(() -> {
            try {
                System.out.println("Starting proxy test server with args: --port=" + testPort + " --db=db@" + testDbUrl + " --run");
                Main.main(new String[]{
                    "--port=" + testPort,
                    "--db=db@" + testDbUrl,
                    "--run"
                });
            } catch (Exception e) {
                System.err.println("Proxy test server error: " + e.getMessage());
                e.printStackTrace();
            }
        });
        testThread.start();
        
        try {
            System.out.println("Starting proxy test server on port " + testPort);
            if (!waitForServer(testPort, 30)) {
                System.err.println("Proxy test server failed to start");
                return false;
            }
            
            // Test proxy endpoint
            URL url = URI.create("http://localhost:" + testPort + "/proxy").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("X-Target-URL", "https://jsonplaceholder.typicode.com/posts");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            
            String postData = "{\"title\":\"WebX Test\",\"body\":\"Testing proxy\",\"userId\":1}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes());
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode > 299) {
                System.err.println("Proxy returned " + responseCode);
                return false;
            }
            
            // Read response
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            String responseStr = response.toString();
            boolean success = responseStr.contains("\"id\":") && responseStr.contains("\"title\":");
            
            System.out.println("Proxy test: " + (success ? "PASS" : "FAIL"));
            if (!success) {
                System.out.println("Response: " + responseStr.substring(0, Math.min(200, responseStr.length())) + "...");
            }
            
            return success;
            
        } catch (Exception e) {
            System.err.println("Proxy test error: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            testThread.interrupt();
            try {
                testThread.join(5000);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }
    
    private static File findWebShot() {
        // Look for WebShot in expected locations
        String[] possiblePaths = {
            "../webshot/dist/webshot",
            "../webshot/dist/webshot.exe",
            "../../webshot/dist/webshot",
            "../../webshot/dist/webshot.exe"
        };
        
        for (String path : possiblePaths) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                System.out.println("Found WebShot at: " + f.getAbsolutePath());
                return f;
            }
        }
        
        // Try to find it relative to current directory
        File currentDir = new File(".").getAbsoluteFile();
        System.out.println("Current directory: " + currentDir);
        
        return null;
    }
}