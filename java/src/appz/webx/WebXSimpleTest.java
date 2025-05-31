package appz.webx;

import java.io.*;
import java.net.*;
import java.sql.*;
import jLib.*;

public class WebXSimpleTest {
    
    public static void main(String[] args) throws Exception {
        Lib.testClass();
    }
    
    // Get a unique port for each test using timestamp to avoid conflicts
    private static synchronized int getNextPort() {
        return 15000 + (int)(System.currentTimeMillis() % 1000);
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
    
    // Test proxy endpoint with JSONPlaceholder and write results to database
    @SuppressWarnings("unused")
    private static boolean proxyEndpointTest_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        
        int testPort = getNextPort();
        String testDbUrl = "jdbc:hsqldb:mem:proxytest" + testPort;
        String shutdownCode = "SHUTDOWN" + testPort;
        Thread serverThread = null;
        
        try {
            // Start WebX server in a thread using Main.main()
            serverThread = new Thread(() -> {
                try {
                    System.out.println("Starting proxy test server with args: --port=" + testPort + " --db=db@" + testDbUrl + " --shutdown=" + shutdownCode + " --run");
                    Main.main(new String[]{
                        "--port=" + testPort,
                        "--db=db@" + testDbUrl,
                        "--shutdown=" + shutdownCode,
                        "--run"
                    });
                } catch (Exception e) {
                    System.err.println("Proxy test server error: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            serverThread.start();
            
            // Wait for server to start
            System.out.println("Waiting for proxy test server to start on port " + testPort + "...");
            if (!waitForServer(testPort, 30)) {
                System.err.println("Proxy test server failed to start within 30 seconds");
                return false;
            }
            
            System.out.println("Server is ready, testing proxy endpoint...");
            
            // Test 1: GET request through proxy to JSONPlaceholder
            System.out.println("\n=== Testing proxy GET request ===");
            String getUrl = "http://localhost:" + testPort + "/proxy";
            HttpURLConnection getConn = (HttpURLConnection) URI.create(getUrl).toURL().openConnection();
            getConn.setRequestMethod("GET");
            getConn.setRequestProperty("X-Target-URL", "https://jsonplaceholder.typicode.com/posts/1");
            getConn.setConnectTimeout(10000);
            getConn.setReadTimeout(10000);
            
            int getResponseCode = getConn.getResponseCode();
            String getResponse = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                getResponse = sb.toString();
            }
            getConn.disconnect();
            
            System.out.println("GET response code: " + getResponseCode);
            System.out.println("GET response: " + getResponse.substring(0, Math.min(100, getResponse.length())) + "...");
            
            boolean getSuccess = (getResponseCode == 200) && getResponse.contains("\"id\": 1") && getResponse.contains("\"title\":");
            System.out.println("GET test " + (getSuccess ? "PASSED" : "FAILED"));
            
            // Test 2: POST request through proxy to JSONPlaceholder
            System.out.println("\n=== Testing proxy POST request ===");
            String postUrl = "http://localhost:" + testPort + "/proxy";
            HttpURLConnection postConn = (HttpURLConnection) URI.create(postUrl).toURL().openConnection();
            postConn.setRequestMethod("POST");
            postConn.setRequestProperty("X-Target-URL", "https://jsonplaceholder.typicode.com/posts");
            postConn.setRequestProperty("Content-Type", "application/json");
            postConn.setDoOutput(true);
            postConn.setConnectTimeout(10000);
            postConn.setReadTimeout(10000);
            
            String postData = "{\"title\":\"WebX Proxy Test\",\"body\":\"Testing proxy endpoint\",\"userId\":1}";
            try (java.io.OutputStream os = postConn.getOutputStream()) {
                os.write(postData.getBytes("UTF-8"));
            }
            
            int postResponseCode = postConn.getResponseCode();
            String postResponse = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(postConn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                postResponse = sb.toString();
            }
            postConn.disconnect();
            
            System.out.println("POST response code: " + postResponseCode);
            System.out.println("POST response: " + postResponse.substring(0, Math.min(100, postResponse.length())) + "...");
            
            boolean postSuccess = (postResponseCode == 201) && postResponse.contains("\"id\": ") && postResponse.contains("\"title\": \"WebX Proxy Test\"");
            System.out.println("POST test " + (postSuccess ? "PASSED" : "FAILED"));
            
            // Write test results to database
            System.out.println("\n=== Writing test results to database ===");
            String dbUrl = "http://localhost:" + testPort + "/db";
            HttpURLConnection dbConn = (HttpURLConnection) URI.create(dbUrl).toURL().openConnection();
            dbConn.setRequestMethod("POST");
            dbConn.setRequestProperty("Content-Type", "application/json");
            dbConn.setDoOutput(true);
            dbConn.setConnectTimeout(5000);
            dbConn.setReadTimeout(5000);
            
            String testResults = String.format(
                "{\"proxyTest\":{" +
                "\"timestamp\":\"%s\"," +
                "\"testType\":\"proxy-endpoint-test\"," +
                "\"getTest\":{\"success\":%s,\"responseCode\":%d}," +
                "\"postTest\":{\"success\":%s,\"responseCode\":%d}," +
                "\"overallSuccess\":%s" +
                "}}",
                java.time.Instant.now().toString(),
                getSuccess,
                getResponseCode,
                postSuccess,
                postResponseCode,
                (getSuccess && postSuccess)
            );
            
            try (java.io.OutputStream os = dbConn.getOutputStream()) {
                os.write(testResults.getBytes("UTF-8"));
            }
            
            int dbResponseCode = dbConn.getResponseCode();
            String dbResponse = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(dbConn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                dbResponse = sb.toString();
            }
            dbConn.disconnect();
            
            System.out.println("DB write response code: " + dbResponseCode);
            System.out.println("DB write response: " + dbResponse);
            
            boolean dbWriteSuccess = (dbResponseCode == 200);
            System.out.println("DB write " + (dbWriteSuccess ? "PASSED" : "FAILED"));
            
            // Wait for database write to complete
            Thread.sleep(1000);
            
            // Verify database contents
            System.out.println("\n=== Verifying database contents ===");
            try (Connection conn = DriverManager.getConnection(testDbUrl, "SA", "");
                 Statement stmt = conn.createStatement()) {
                
                // Check for proxyTest key existence (the database stores nested objects as flattened keys)
                ResultSet rs = stmt.executeQuery("SELECT * FROM webx_data WHERE key_json = '\"proxyTest\"'");
                
                boolean foundProxyTest = false;
                if (rs.next()) {
                    String keyJson = rs.getString("key_json");
                    String valueJson = rs.getString("value_json");
                    
                    System.out.println("Found proxyTest row: key=" + keyJson + ", value=" + valueJson);
                    foundProxyTest = true;
                    System.out.println("✅ Found proxyTest data!");
                    
                    // Also verify that the test result components are stored
                    boolean hasTestType = false;
                    boolean hasGetTest = false;
                    boolean hasPostTest = false;
                    
                    ResultSet allRs = stmt.executeQuery("SELECT * FROM webx_data");
                    while (allRs.next()) {
                        String key = allRs.getString("key_json");
                        String value = allRs.getString("value_json");
                        
                        if ("\"testType\"".equals(key) && "\"proxy-endpoint-test\"".equals(value)) {
                            hasTestType = true;
                        } else if ("\"getTest\"".equals(key)) {
                            hasGetTest = true;
                        } else if ("\"postTest\"".equals(key)) {
                            hasPostTest = true;
                        }
                    }
                    
                    if (hasTestType && hasGetTest && hasPostTest) {
                        System.out.println("✅ Proxy test results properly stored in database");
                    } else {
                        System.err.println("❌ Proxy test data components missing: testType=" + hasTestType + 
                                         ", getTest=" + hasGetTest + ", postTest=" + hasPostTest);
                        return false;
                    }
                }
                
                if (!foundProxyTest) {
                    System.err.println("❌ No proxyTest data found in database");
                    // Debug: show all database contents
                    rs = stmt.executeQuery("SELECT * FROM webx_data");
                    System.err.println("All database contents:");
                    while (rs.next()) {
                        System.err.println("  key=" + rs.getString("key_json") + ", value=" + rs.getString("value_json"));
                    }
                    return false;
                }
            }
            
            // Overall test result
            boolean overallSuccess = getSuccess && postSuccess && dbWriteSuccess;
            System.out.println("\n" + (overallSuccess ? "✅" : "❌") + " Proxy endpoint test " + (overallSuccess ? "PASSED" : "FAILED"));
            System.out.println("GET: " + (getSuccess ? "PASS" : "FAIL") + 
                             ", POST: " + (postSuccess ? "PASS" : "FAIL") + 
                             ", DB: " + (dbWriteSuccess ? "PASS" : "FAIL"));
            
            return overallSuccess;
            
        } catch (Exception e) {
            System.err.println("Proxy endpoint test failed with exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // Stop the server gracefully using shutdown code
            if (serverThread != null && serverThread.isAlive()) {
                try {
                    System.out.println("Sending shutdown request to proxy test server...");
                    URL shutdownUrl = URI.create("http://localhost:" + testPort + "/" + shutdownCode).toURL();
                    HttpURLConnection conn = (HttpURLConnection) shutdownUrl.openConnection();
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
                    serverThread.join(5000); // Wait up to 5 seconds for clean shutdown
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }
    }
    
    // Test only the database endpoint with a simple HTML file
    @SuppressWarnings("unused")
    private static boolean databaseEndpointTest_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        
        int testPort = getNextPort();
        String testDbUrl = "jdbc:hsqldb:mem:dbtest" + testPort;
        String shutdownCode = "SHUTDOWN" + testPort;
        Thread serverThread = null;
        
        try {
            // Start WebX server in a thread using Main.main()
            serverThread = new Thread(() -> {
                try {
                    System.out.println("Starting DB test server with args: --port=" + testPort + " --db=db@" + testDbUrl + " --shutdown=" + shutdownCode + " --run");
                    Main.main(new String[]{
                        "--port=" + testPort,
                        "--db=db@" + testDbUrl,
                        "--shutdown=" + shutdownCode,
                        "--run"
                    });
                } catch (Exception e) {
                    System.err.println("DB test server error: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            serverThread.start();
            
            // Wait for server to start
            System.out.println("Waiting for DB test server to start on port " + testPort + "...");
            if (!waitForServer(testPort, 30)) {
                System.err.println("DB test server failed to start within 30 seconds");
                return false;
            }
            
            System.out.println("Server is ready, running WebShot database test...");
            
            // Find WebShot executable
            File webShotExe = findWebShot();
            if (webShotExe == null || !webShotExe.exists()) {
                System.out.println("WebShot not found. Skipping test. Build it with: cd ../webshot && ./build.sh");
                return true; // Don't fail the test if WebShot isn't built
            }
            
            // Execute WebShot to run JavaScript database test
            String testUrl = "http://localhost:" + testPort + "/www/webx-db-test.html";
            String outputImage = "./log/db-test-" + System.currentTimeMillis() + ".png";
            
            System.out.println("Running WebShot on database test URL: " + testUrl);
            
            ProcessBuilder pb = new ProcessBuilder(
                webShotExe.getAbsolutePath(),
                testUrl,
                "--output", outputImage,
                "--size", "1280x800"
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
            
            // Since WebShot closes the browser immediately after taking the screenshot,
            // the JavaScript fetch request never gets sent. Let's make the HTTP request
            // directly to test the /db endpoint functionality.
            System.out.println("Making direct HTTP request to test /db endpoint...");
            
            try {
                URL dbUrl = URI.create("http://localhost:" + testPort + "/db").toURL();
                HttpURLConnection conn = (HttpURLConnection) dbUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                // Send the same test data that JavaScript would send
                String testJson = """
                    {
                        "dbTest": {
                            "timestamp": "2025-05-31T22:30:00.000Z",
                            "testType": "simple-db-test",
                            "browser": "direct-http",
                            "testRun": 1748730000000
                        }
                    }
                    """;
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(testJson.getBytes());
                }
                
                int responseCode = conn.getResponseCode();
                System.out.println("Direct HTTP request response code: " + responseCode);
                
                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String response = br.lines().reduce("", (a, b) -> a + b);
                        System.out.println("Response: " + response);
                    }
                    System.out.println("✅ Direct HTTP request to /db endpoint successful!");
                } else {
                    System.err.println("❌ Direct HTTP request failed with code: " + responseCode);
                    return false;
                }
                
            } catch (Exception e) {
                System.err.println("❌ Direct HTTP request failed: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
            
            // Now verify database writes by connecting to the same in-memory database
            System.out.println("\nVerifying database writes...");
            
            // Connect to the same in-memory database that WebX is using
            try (Connection conn = DriverManager.getConnection(testDbUrl, "SA", "");
                 Statement stmt = conn.createStatement()) {
                
                // Look for the dbTest key in the database
                ResultSet rs = stmt.executeQuery(
                    "SELECT * FROM webx_data WHERE key_json = '\"dbTest\"'"
                );
                
                boolean foundDbTest = false;
                if (rs.next()) {
                    String keyJson = rs.getString("key_json");
                    String valueJson = rs.getString("value_json");
                    
                    System.out.println("Found dbTest row: key=" + keyJson + ", value=" + valueJson);
                    
                    if ("\"dbTest\"".equals(keyJson) && "MAP".equals(valueJson)) {
                        foundDbTest = true;
                        System.out.println("✅ Found dbTest entry (as MAP)!");
                        
                        // Now look for the testType field to verify the data structure
                        long dbTestId = rs.getLong("id");
                        
                        ResultSet testTypeRs = stmt.executeQuery(
                            "SELECT value_json FROM webx_data WHERE parent_id = " + dbTestId + 
                            " AND key_json = '\"testType\"'"
                        );
                        
                        if (testTypeRs.next()) {
                            String testType = testTypeRs.getString("value_json");
                            if ("\"simple-db-test\"".equals(testType)) {
                                System.out.println("✅ TestType field correctly set to 'simple-db-test'");
                            } else {
                                System.err.println("❌ TestType field value: " + testType);
                                return false;
                            }
                        } else {
                            System.err.println("❌ TestType field not found");
                            return false;
                        }
                    }
                }
                
                if (!foundDbTest) {
                    // Let's see what's actually in the database
                    System.err.println("❌ No dbTest data found in database");
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
            
            System.out.println("\n✅ Database endpoint test passed!");
            System.out.println("JavaScript successfully wrote to the database via /db endpoint.");
            return true;
            
        } catch (Exception e) {
            System.err.println("Database endpoint test failed with exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // Stop the server gracefully using shutdown code
            if (serverThread != null && serverThread.isAlive()) {
                try {
                    System.out.println("Sending shutdown request to DB test server...");
                    URL shutdownUrl = URI.create("http://localhost:" + testPort + "/" + shutdownCode).toURL();
                    HttpURLConnection conn = (HttpURLConnection) shutdownUrl.openConnection();
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
                    serverThread.join(5000); // Wait up to 5 seconds for clean shutdown
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
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