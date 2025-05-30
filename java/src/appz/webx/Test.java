package appz.webx;

import java.util.*;
import java.io.*;
import java.net.*;
import jLib.*;
import http.*;

public class Test {

    public static void main(String[] args) {
        String[] classes = {
            "http.AuthToken",
            "http.HttpErrorHandler",
            "http.HttpErrorResponse",
            "http.HttpFileHandler",
            "http.HttpHandler",
            "http.HttpHeaderBlock",
            "http.HttpJsonHandler",
            "http.HttpLoginHandler",
            "http.HttpMessage",
            "http.HttpProxyHandler",
            "http.HttpRequest",
            "http.HttpResponse",
            "http.HttpServer",
            "jLib.JsonDecoder",
            "jLib.JsonEncoder",
            "jLib.JsonSerializable",
            "jLib.Lib",
            "jLib.LruCache",
            "jLib.ParseArgs",
            "jLib.Result",
            "persist.PersistentData",
            "persist.PersistentList",
            "persist.PersistentMap",
            "build.Build",
            "build.DownloadJars",
            "build.HelloRoot",
            "appz.webx.Test"  // Include this class itself for webxServerBasic_TEST_ and webxJavaScriptTests_TEST_
        };

        int totalTests = 0;
        int passedClasses = 0;
        int failedClasses = 0;
        List<String> failedClassNames = new ArrayList<>();

        for (String className : classes) {
            try {
                Class<?> clazz = Class.forName(className);
                System.out.println("\n=== Testing " + className + " ===");
                boolean testsPassed = Lib.testClass(clazz);
                if (testsPassed) {
                    totalTests++;
                    passedClasses++;
                    System.out.println("✓ Tests passed");
                } else {
                    failedClasses++;
                    failedClassNames.add(className);
                    System.out.println("✗ Tests failed");
                }
            } catch (Exception e) {
                failedClasses++;
                failedClassNames.add(className);
                System.out.println("\n=== Testing " + className + " ===");
                System.out.println("✗ Failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (Error e) {
                failedClasses++;
                failedClassNames.add(className);
                System.out.println("\n=== Testing " + className + " ===");
                System.out.println("✗ Failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        System.out.println("\n" + "=".repeat(50));
        System.out.println("SUMMARY:");
        System.out.println("Total classes tested: " + totalTests);
        System.out.println("Classes with passing tests: " + passedClasses);
        System.out.println("Classes with failures: " + failedClasses);
        if (!failedClassNames.isEmpty()) {
            System.out.println("Failed classes: " + String.join(", ", failedClassNames));
        }
        System.out.println("=".repeat(50));
    }

    /**
     * Basic integration test of the WebX server
     * Tests that the server can start and serve basic endpoints
     */
    @SuppressWarnings("unused")
    private static boolean webxServerBasic_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        
        // Use a random port to avoid conflicts
        int testPort = 10000 + (int)(Math.random() * 10000);
        
        try ( Lib.TmpDir tmpDir = new Lib.TmpDir() ) {
            // Create test directories
            File wwwDir = new File( tmpDir.dir, "www" );
            wwwDir.mkdirs();
            File dbDir = new File( tmpDir.dir, "db" );
            dbDir.mkdirs();
            
            // Create a simple test HTML file
            File testHtml = new File( wwwDir, "test.html" );
            Lib.string2file( "<html><body><h1>WebX Test Page</h1></body></html>", testHtml, false );
            
            // Create test JSON data file
            File testJson = new File( wwwDir, "data.json" );
            Lib.string2file( "{\"test\":\"data\",\"number\":42}", testJson, false );
            
            // Prepare server arguments
            String jdbcUrl = "jdbc:hsqldb:mem:test_" + System.currentTimeMillis();
            String[] serverArgs = {
                "--port=" + testPort,
                "--www=" + wwwDir.getAbsolutePath(),
                "--proxy=/proxy",
                "--db=/db",
                "--jdbc=" + jdbcUrl,
                "--run"
            };
            
            // Start server in background thread
            Thread serverThread = new Thread( () -> {
                try {
                    Main.main( serverArgs );
                } catch ( Exception e ) {
                    Lib.log( "Server error: " + e );
                }
            });
            serverThread.setDaemon( true );
            serverThread.start();
            
            // Wait for server to start
            Thread.sleep( 2000 );
            
            String baseUrl = "http://localhost:" + testPort;
            boolean allTestsPassed = true;
            
            // Test 1: Static file serving (HTML)
            System.out.println( "Test 1: Static HTML file..." );
            try {
                HttpResponse resp = httpGet( baseUrl + "/test.html" );
                if ( resp.statusCode != 200 ) {
                    System.err.println( "  FAIL: Expected 200, got " + resp.statusCode );
                    allTestsPassed = false;
                } else if ( !resp.body.contains( "WebX Test Page" ) ) {
                    System.err.println( "  FAIL: Content mismatch" );
                    allTestsPassed = false;
                } else {
                    System.out.println( "  PASS: HTML served correctly" );
                }
            } catch ( Exception e ) {
                System.err.println( "  FAIL: " + e.getMessage() );
                allTestsPassed = false;
            }
            
            // Test 2: Static file serving (JSON)
            System.out.println( "Test 2: Static JSON file..." );
            try {
                HttpResponse resp = httpGet( baseUrl + "/data.json" );
                if ( resp.statusCode != 200 ) {
                    System.err.println( "  FAIL: Expected 200, got " + resp.statusCode );
                    allTestsPassed = false;
                } else {
                    Map<String,Object> data = (Map<String,Object>) JsonDecoder.decode( resp.body );
                    if ( !"data".equals( data.get("test") ) || !Integer.valueOf(42).equals( data.get("number") ) ) {
                        System.err.println( "  FAIL: JSON data mismatch" );
                        allTestsPassed = false;
                    } else {
                        System.out.println( "  PASS: JSON served correctly" );
                    }
                }
            } catch ( Exception e ) {
                System.err.println( "  FAIL: " + e.getMessage() );
                allTestsPassed = false;
            }
            
            // Test 3: Database endpoint (POST and retrieve)
            System.out.println( "Test 3: Database endpoint..." );
            try {
                // POST some data
                String testData = "{\"users\":{\"test_user\":{\"name\":\"Test User\",\"id\":123}}}";
                HttpResponse postResp = httpPost( baseUrl + "/db", testData, "application/json" );
                if ( postResp.statusCode != 200 ) {
                    System.err.println( "  FAIL: POST failed with " + postResp.statusCode );
                    allTestsPassed = false;
                } else {
                    // The response should contain our data merged with any existing data
                    Map<String,Object> respData = (Map<String,Object>) JsonDecoder.decode( postResp.body );
                    Map<String,Object> users = (Map<String,Object>) respData.get("users");
                    if ( users == null || !users.containsKey("test_user") ) {
                        System.err.println( "  FAIL: Posted data not in response" );
                        allTestsPassed = false;
                    } else {
                        System.out.println( "  PASS: Database POST working" );
                    }
                }
                
                // POST more data to test merging
                String moreData = "{\"users\":{\"another_user\":{\"name\":\"Another\"}},\"config\":{\"version\":\"1.0\"}}";
                HttpResponse mergeResp = httpPost( baseUrl + "/db", moreData, "application/json" );
                if ( mergeResp.statusCode == 200 ) {
                    Map<String,Object> merged = (Map<String,Object>) JsonDecoder.decode( mergeResp.body );
                    Map<String,Object> users = (Map<String,Object>) merged.get("users");
                    if ( users != null && users.containsKey("test_user") && users.containsKey("another_user") 
                         && merged.containsKey("config") ) {
                        System.out.println( "  PASS: Database merging working" );
                    } else {
                        System.err.println( "  FAIL: Data not properly merged" );
                        allTestsPassed = false;
                    }
                }
            } catch ( Exception e ) {
                System.err.println( "  FAIL: " + e.getMessage() );
                allTestsPassed = false;
            }
            
            // Test 4: Test HTML file is served
            System.out.println( "Test 4: Test HTML file serving..." );
            try {
                // Copy test HTML files to temp directory
                File testHtmlSrc = new File( "./datafiles/www/webx-test.html" );
                File testAutoSrc = new File( "./datafiles/www/webx-test-auto.html" );
                
                if ( testHtmlSrc.exists() ) {
                    File testHtmlDest = new File( wwwDir, "webx-test.html" );
                    Lib.string2file( Lib.file2string(testHtmlSrc), testHtmlDest, false );
                    
                    HttpResponse testPageResp = httpGet( baseUrl + "/webx-test.html" );
                    if ( testPageResp.statusCode == 200 && testPageResp.body.contains("WebX Server Test Suite") ) {
                        System.out.println( "  PASS: Test HTML page served correctly" );
                    } else {
                        System.err.println( "  FAIL: Test HTML page not served correctly" );
                        allTestsPassed = false;
                    }
                }
                
                if ( testAutoSrc.exists() ) {
                    File testAutoDest = new File( wwwDir, "webx-test-auto.html" );
                    Lib.string2file( Lib.file2string(testAutoSrc), testAutoDest, false );
                }
            } catch ( Exception e ) {
                System.err.println( "  FAIL: " + e.getMessage() );
                allTestsPassed = false;
            }
            
            // Test 5: 404 handling
            System.out.println( "Test 5: 404 handling..." );
            try {
                HttpResponse notFoundResp = httpGet( baseUrl + "/does-not-exist.html" );
                if ( notFoundResp.statusCode == 404 ) {
                    System.out.println( "  PASS: 404 returned for missing file" );
                } else {
                    System.err.println( "  FAIL: Expected 404, got " + notFoundResp.statusCode );
                    allTestsPassed = false;
                }
            } catch ( Exception e ) {
                System.err.println( "  FAIL: " + e.getMessage() );
                allTestsPassed = false;
            }
            
            // Give server time to finish any pending operations
            Thread.sleep( 500 );
            
            System.out.println( "\nNOTE: This test verifies basic server functionality." );
            System.out.println( "For full integration testing including JavaScript execution:" );
            System.out.println( "1. Start the server: ./java/java.sh appz.webx.Main --run" );
            System.out.println( "2. Open http://localhost:13102/webx-test.html in a browser" );
            System.out.println( "3. Click 'Run All Tests' to execute the full test suite" );
            
            return allTestsPassed;
            
        } catch ( Exception e ) {
            System.err.println( "Test setup failed: " + e );
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Tests JavaScript functionality using webshot to run browser-based tests
     * Requires webshot tool to be available
     */
    @SuppressWarnings("unused")
    private static boolean webxJavaScriptTests_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        
        // Check if webshot is available
        String webshotPath = System.getProperty( "webshot.path", "../webshot/dist/webshot" );
        File webshotExe = new File( webshotPath );
        if ( !webshotExe.exists() ) {
            System.out.println( "SKIP: webshot not found at " + webshotPath );
            System.out.println( "      Set -Dwebshot.path=/path/to/webshot or build webshot in ../webshot/" );
            return true; // Skip test but don't fail
        }
        
        // Use a random port to avoid conflicts
        int testPort = 10000 + (int)(Math.random() * 10000);
        
        try {
            // Prepare server arguments
            String[] serverArgs = {
                "--port=" + testPort,
                "--www=./datafiles/www",
                "--db=/db",
                "--jdbc=jdbc:hsqldb:mem:jstest_" + System.currentTimeMillis(),
                "--run"
            };
            
            // Start server in background thread
            Thread serverThread = new Thread( () -> {
                try {
                    Main.main( serverArgs );
                } catch ( Exception e ) {
                    Lib.log( "Server error: " + e );
                }
            });
            serverThread.setDaemon( true );
            serverThread.start();
            
            // Wait for server to start
            Thread.sleep( 2000 );
            
            String baseUrl = "http://localhost:" + testPort;
            
            // Clear any existing test results by POSTing empty object
            httpPost( baseUrl + "/db", "{}", "application/json" );
            
            System.out.println( "Running JavaScript tests via webshot..." );
            System.out.println( "URL: " + baseUrl + "/webx-test-auto.html" );
            
            // Create temporary directory for screenshot
            File tmpDir = new File( "./log/tmp" );
            tmpDir.mkdirs();
            File screenshot = new File( tmpDir, "webx-test-" + System.currentTimeMillis() + ".png" );
            
            // Run webshot
            ProcessBuilder pb = new ProcessBuilder(
                webshotExe.getAbsolutePath(),
                baseUrl + "/webx-test-auto.html",
                screenshot.getAbsolutePath(),
                "1280x720"
            );
            pb.redirectErrorStream( true );
            Process process = pb.start();
            
            // Capture webshot output
            try ( BufferedReader reader = new BufferedReader( new InputStreamReader( process.getInputStream() ) ) ) {
                String line;
                while ( (line = reader.readLine()) != null ) {
                    System.out.println( "  webshot: " + line );
                }
            }
            
            int exitCode = process.waitFor();
            if ( exitCode != 0 ) {
                System.err.println( "webshot failed with exit code: " + exitCode );
                return false;
            }
            
            // Give JavaScript tests time to complete and POST results
            Thread.sleep( 2000 );
            
            // Retrieve test results from database
            System.out.println( "\nRetrieving test results from database..." );
            HttpResponse dbResp = httpGet( baseUrl + "/db" );
            
            if ( dbResp.statusCode != 200 ) {
                System.err.println( "Failed to retrieve test results: HTTP " + dbResp.statusCode );
                return false;
            }
            
            // Parse test results
            Map<String,Object> dbData = (Map<String,Object>) JsonDecoder.decode( dbResp.body );
            Map<String,Object> testResults = (Map<String,Object>) dbData.get( "test_results" );
            
            if ( testResults == null ) {
                System.err.println( "No test results found in database" );
                System.err.println( "Database contents: " + dbResp.body );
                return false;
            }
            
            // Extract test data
            Map<String,Object> tests = (Map<String,Object>) testResults.get( "tests" );
            Map<String,Object> summary = (Map<String,Object>) testResults.get( "summary" );
            
            if ( tests == null || summary == null ) {
                System.err.println( "Invalid test result format" );
                System.err.println( "Got: " + testResults );
                return false;
            }
            
            // Display results
            System.out.println( "\nJavaScript Test Results:" );
            System.out.println( "========================" );
            
            for ( Map.Entry<String,Object> entry : tests.entrySet() ) {
                String testName = entry.getKey();
                Map<String,Object> testData = (Map<String,Object>) entry.getValue();
                Boolean passed = (Boolean) testData.get( "passed" );
                String message = (String) testData.get( "message" );
                String error = (String) testData.get( "error" );
                
                System.out.println( testName + ": " + (passed ? "PASS" : "FAIL") );
                if ( message != null ) {
                    System.out.println( "  Message: " + message );
                }
                if ( error != null ) {
                    System.out.println( "  Error: " + error );
                }
            }
            
            // Summary
            System.out.println( "\nSummary:" );
            System.out.println( "  Total tests: " + summary.get( "total" ) );
            System.out.println( "  Passed: " + summary.get( "passed" ) );
            System.out.println( "  Failed: " + summary.get( "failed" ) );
            
            Integer failed = (Integer) summary.get( "failed" );
            boolean allPassed = failed != null && failed == 0;
            
            if ( screenshot.exists() ) {
                System.out.println( "\nScreenshot saved to: " + screenshot.getAbsolutePath() );
            }
            
            return allPassed;
            
        } catch ( Exception e ) {
            System.err.println( "JavaScript test failed: " + e );
            e.printStackTrace();
            return false;
        }
    }
    
    // Helper class for HTTP responses
    private static class HttpResponse {
        final int statusCode;
        final String body;
        final Map<String,String> headers;
        
        HttpResponse( int statusCode, String body, Map<String,String> headers ) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }
    }
    
    // Helper method for simple HTTP GET
    private static HttpResponse httpGet( String urlStr ) throws Exception {
        return httpGetWithHeaders( urlStr, Map.of() );
    }
    
    // Helper method for HTTP GET with headers
    private static HttpResponse httpGetWithHeaders( String urlStr, Map<String,String> headers ) throws Exception {
        URL url = new URL( urlStr );
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod( "GET" );
        conn.setConnectTimeout( 5000 );
        conn.setReadTimeout( 5000 );
        
        for ( Map.Entry<String,String> header : headers.entrySet() ) {
            conn.setRequestProperty( header.getKey(), header.getValue() );
        }
        
        return readResponse( conn );
    }
    
    // Helper method for HTTP POST
    private static HttpResponse httpPost( String urlStr, String body, String contentType ) throws Exception {
        return httpPostWithHeaders( urlStr, body, contentType, Map.of() );
    }
    
    // Helper method for HTTP POST with headers
    private static HttpResponse httpPostWithHeaders( String urlStr, String body, String contentType, 
                                                     Map<String,String> headers ) throws Exception {
        URL url = new URL( urlStr );
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod( "POST" );
        conn.setDoOutput( true );
        conn.setConnectTimeout( 5000 );
        conn.setReadTimeout( 5000 );
        conn.setRequestProperty( "Content-Type", contentType );
        
        for ( Map.Entry<String,String> header : headers.entrySet() ) {
            conn.setRequestProperty( header.getKey(), header.getValue() );
        }
        
        try ( OutputStreamWriter out = new OutputStreamWriter( conn.getOutputStream() ) ) {
            out.write( body );
        }
        
        return readResponse( conn );
    }
    
    // Helper to read HTTP response
    private static HttpResponse readResponse( HttpURLConnection conn ) throws Exception {
        int statusCode = conn.getResponseCode();
        
        Map<String,String> respHeaders = new HashMap<>();
        for ( Map.Entry<String,List<String>> entry : conn.getHeaderFields().entrySet() ) {
            if ( entry.getKey() != null && !entry.getValue().isEmpty() ) {
                respHeaders.put( entry.getKey(), entry.getValue().get(0) );
            }
        }
        
        InputStream inputStream = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if ( inputStream == null ) {
            return new HttpResponse( statusCode, "", respHeaders );
        }
        
        try ( BufferedReader reader = new BufferedReader( new InputStreamReader( inputStream ) ) ) {
            StringBuilder response = new StringBuilder();
            String line;
            while ( (line = reader.readLine()) != null ) {
                response.append( line ).append( "\n" );
            }
            return new HttpResponse( statusCode, response.toString().trim(), respHeaders );
        }
    }
}