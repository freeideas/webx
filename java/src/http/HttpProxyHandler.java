package http;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

import jLib.Lib;



public class HttpProxyHandler implements HttpHandler {
    
    
    
    private static boolean isRestrictedHeader( String headerName ) {
        if (headerName==null) return true;
        String name = headerName.toLowerCase();
        // Headers that Java HTTP client restricts or manages automatically
        return name.equals( "connection" ) ||
               name.equals( "content-length" ) ||
               name.equals( "date" ) ||
               name.equals( "expect" ) ||
               name.equals( "from" ) ||
               name.equals( "host" ) ||
               name.equals( "if-modified-since" ) ||
               name.equals( "if-unmodified-since" ) ||
               name.equals( "if-match" ) ||
               name.equals( "if-none-match" ) ||
               name.equals( "if-range" ) ||
               name.equals( "origin" ) ||
               name.equals( "referer" ) ||
               name.equals( "upgrade" ) ||
               name.equals( "user-agent" ) ||
               name.equals( "via" ) ||
               name.equals( "warning" ) ||
               name.equals( "keep-alive" ) ||
               name.equals( "proxy-authorization" ) ||
               name.equals( "proxy-authenticate" ) ||
               name.equals( "proxy-connection" ) ||
               name.equals( "te" ) ||
               name.equals( "trailer" ) ||
               name.equals( "transfer-encoding" ) ||
               name.startsWith( "x-target-" ) ||  // Our internal headers
               name.startsWith( "x-file-" );     // Our internal headers
    }



    @Override
    public HttpResponse handle( HttpRequest req ) {
        String targetUrl = req.headerBlock.getHeaderValue( "X-Target-URL" );
        if (targetUrl==null) return new HttpErrorResponse( 400, "Missing X-Target-URL header" );
        try {
            HttpClient client = HttpClient.newBuilder()
                .followRedirects( HttpClient.Redirect.NEVER )
                .connectTimeout( Duration.ofSeconds( 10 ) )
                .build();
            java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                .uri( URI.create( targetUrl ) )
                .timeout( Duration.ofMinutes( 2 ) );
            for (Map.Entry<String,String> entry : req.headerBlock.headers.entrySet()) {
                String headerName = entry.getKey();
                String value = entry.getValue();
                if (isRestrictedHeader( headerName )) continue;
                requestBuilder.header( headerName, value );
            }
            java.net.http.HttpRequest request;
            String method = req.headerBlock.getMethod();
            byte[] body = req.body;
            switch (method) {
                case "GET":
                    request = requestBuilder.GET().build();
                    break;
                case "DELETE":
                    request = requestBuilder.DELETE().build();
                    break;
                case "POST":
                    request = requestBuilder.POST( java.net.http.HttpRequest.BodyPublishers.ofByteArray( body ) ).build();
                    break;
                case "PUT":
                    request = requestBuilder.PUT( java.net.http.HttpRequest.BodyPublishers.ofByteArray( body ) ).build();
                    break;
                case "OPTIONS":
                    request = requestBuilder.method( "OPTIONS", java.net.http.HttpRequest.BodyPublishers.noBody() ).build();
                    break;
                default:
                    request = requestBuilder.method( method,
                        body.length>0 ? java.net.http.HttpRequest.BodyPublishers.ofByteArray( body ) : java.net.http.HttpRequest.BodyPublishers.noBody() )
                        .build();
            }
            java.net.http.HttpResponse<byte[]> response = client.send( request, java.net.http.HttpResponse.BodyHandlers.ofByteArray() );
            HttpHeaderBlock resHead = new HttpHeaderBlock( response.statusCode(), "OK", new LinkedHashMap<>() );
            for (Map.Entry<String,List<String>> entry : response.headers().map().entrySet()) {
                String name = entry.getKey();
                if (name==null ||
                    name.equalsIgnoreCase( "connection" ) ||
                    name.equalsIgnoreCase( "keep-alive" )) continue;
                for (String value : entry.getValue()) resHead = resHead.withAddHeader( name, value );
            }
            resHead = resHead.withAddHeader( "Access-Control-Allow-Origin", "*" );
            resHead = resHead.withAddHeader( "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS" );
            resHead = resHead.withAddHeader( "Access-Control-Allow-Headers",
                "X-Target-URL, X-File-Path, X-File-Response-Format, X-Disable-Compression, Content-Type" );
            byte[] responseBody = response.body();
            return new HttpResponse( resHead, responseBody );
        } catch (Exception e) {
            return new HttpErrorResponse( 500, "Proxy error: " + e.getMessage() );
        }
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/", new LinkedHashMap<>() );
        HttpRequest req = new HttpRequest( headerBlock, new byte[0] );
        HttpProxyHandler handler = new HttpProxyHandler();
        HttpResponse resp = handler.handle( req );
        Lib.asrt( resp.headerBlock.firstLine.contains( "400" ) );
        Lib.asrt( new String( resp.body ).contains( "Missing X-Target-URL" ) );
        headerBlock = new HttpHeaderBlock( "OPTIONS", "/", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "X-Target-URL", "http://example.com" );
        req = new HttpRequest( headerBlock, new byte[0] );
        return true;
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_httpbun_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        HttpProxyHandler handler = new HttpProxyHandler();
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/proxy", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://httpbun.com/get" );
        headerBlock = headerBlock.withAddHeader( "User-Agent", "HttpProxyHandler Test" );
        HttpRequest req = new HttpRequest( headerBlock, new byte[0] );
        HttpResponse resp = handler.handle( req );
        Lib.asrt( resp.headerBlock.firstLine.contains( "200" ) );
        String responseBody = new String( resp.body );
        Lib.asrt( responseBody.contains( "\"method\": \"GET\"" ) );
        Lib.asrt( responseBody.contains( "\"User-Agent\": \"HttpProxyHandler Test\"" ) );
        headerBlock = new HttpHeaderBlock( "POST", "/proxy", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://httpbun.com/post" );
        headerBlock = headerBlock.withAddHeader( "Content-Type", "application/json" );
        String postData = "{\"test\":\"data\",\"number\":42}";
        req = new HttpRequest( headerBlock, postData.getBytes() );
        resp = handler.handle( req );
        Lib.asrt( resp.headerBlock.firstLine.contains( "200" ) );
        responseBody = new String( resp.body );
        Lib.asrt( responseBody.contains( "\"method\": \"POST\"" ) );
        Lib.asrt( responseBody.contains( "\"data\": \"{\\\"test\\\":\\\"data\\\",\\\"number\\\":42}\"" ) );
        headerBlock = new HttpHeaderBlock( "GET", "/proxy", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://httpbun.com/status/404" );
        req = new HttpRequest( headerBlock, new byte[0] );
        resp = handler.handle( req );
        Lib.asrt( resp.headerBlock.firstLine.contains( "404" ) );
        return true;
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_realApiKeysFile_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        try {
            // Test with the actual api-keys.json file that causes 500 errors
            File apiKeysFile = new File("../api-keys.json");
            if (!apiKeysFile.exists()) {
                System.out.println("api-keys.json not found, skipping test");
                return true;
            }
            
            HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler(apiKeysFile);
            
            // Test exact same calls that the JavaScript makes
            System.out.println("Testing JSONPlaceholder GET (same as JavaScript test1)...");
            HttpHeaderBlock headerBlock1 = new HttpHeaderBlock("GET", "/proxy", new LinkedHashMap<>());
            headerBlock1 = headerBlock1.withAddHeader("X-Target-URL", "https://jsonplaceholder.typicode.com/posts/1");
            HttpRequest req1 = new HttpRequest(headerBlock1, new byte[0]);
            HttpResponse resp1 = handler.handle(req1);
            System.out.println("GET Response status: " + resp1.headerBlock.firstLine);
            Lib.asrt(!resp1.headerBlock.firstLine.contains("500"), "GET should not return 500 error: " + resp1.headerBlock.firstLine);
            
            System.out.println("Testing JSONPlaceholder POST (same as JavaScript test2)...");
            HttpHeaderBlock headerBlock2 = new HttpHeaderBlock("POST", "/proxy", new LinkedHashMap<>());
            headerBlock2 = headerBlock2.withAddHeader("X-Target-URL", "https://jsonplaceholder.typicode.com/posts");
            headerBlock2 = headerBlock2.withAddHeader("Content-Type", "application/json");
            String postData2 = "{\"title\":\"WebX Proxy Test\",\"body\":\"Testing from headless browser\",\"userId\":1}";
            HttpRequest req2 = new HttpRequest(headerBlock2, postData2.getBytes());
            HttpResponse resp2 = handler.handle(req2);
            System.out.println("POST Response status: " + resp2.headerBlock.firstLine);
            Lib.asrt(!resp2.headerBlock.firstLine.contains("500"), "POST should not return 500 error: " + resp2.headerBlock.firstLine);
            
            System.out.println("Testing JSONPlaceholder Users GET (same as JavaScript test3)...");
            HttpHeaderBlock headerBlock3 = new HttpHeaderBlock("GET", "/proxy", new LinkedHashMap<>());
            headerBlock3 = headerBlock3.withAddHeader("X-Target-URL", "https://jsonplaceholder.typicode.com/users/1");
            HttpRequest req3 = new HttpRequest(headerBlock3, new byte[0]);
            HttpResponse resp3 = handler.handle(req3);
            System.out.println("Users GET Response status: " + resp3.headerBlock.firstLine);
            Lib.asrt(!resp3.headerBlock.firstLine.contains("500"), "Users GET should not return 500 error: " + resp3.headerBlock.firstLine);
            
            return true;
        } catch (Exception e) {
            System.err.println("Failed with real api-keys.json: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("unused")
    private static boolean _TEST_serverContextDebug_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        
        System.out.println("=== DEBUGGING SERVER CONTEXT 500 ERROR ===");
        
        // Start a minimal WebX server to replicate the exact same context
        int testPort = 15555;
        Thread serverThread = null;
        
        try {
            // Create the same server setup as WebXWebShotTest
            serverThread = new Thread(() -> {
                try {
                    String testDbUrl = "jdbc:hsqldb:mem:debug" + testPort;
                    System.out.println("Starting debug server on port " + testPort + " with JDBC: " + testDbUrl);
                    appz.webx.Main.main(new String[]{
                        "-port=" + testPort,
                        "-jdbc=" + testDbUrl,
                        "-run=true"
                    });
                } catch (Exception e) {
                    System.err.println("Debug server thread error: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            serverThread.start();
            
            // Wait for server to start
            Thread.sleep(3000);
            
            // Make the exact same request as proxyEndpointTest
            URL url = URI.create("http://localhost:" + testPort + "/proxy").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("X-Target-URL", "https://jsonplaceholder.typicode.com/posts");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            String requestBody = "{\"title\":\"WebX Test\",\"body\":\"Testing proxy\",\"userId\":1}";
            
            System.out.println("Making POST request to: " + url);
            System.out.println("Request headers: X-Target-URL=https://jsonplaceholder.typicode.com/posts, Content-Type=application/json");
            System.out.println("Request body: " + requestBody);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes());
            }
            
            int responseCode = conn.getResponseCode();
            System.out.println("Response code: " + responseCode);
            
            if (responseCode == 500) {
                System.out.println("=== 500 ERROR DETECTED - READING ERROR STREAM ===");
                try (InputStream errorStream = conn.getErrorStream()) {
                    if (errorStream != null) {
                        String errorBody = new String(errorStream.readAllBytes());
                        System.out.println("Error response body: " + errorBody);
                    } else {
                        System.out.println("No error stream available");
                    }
                } catch (Exception e) {
                    System.out.println("Failed to read error stream: " + e.getMessage());
                }
                
                // Also try to read normal response stream
                try (InputStream inputStream = conn.getInputStream()) {
                    if (inputStream != null) {
                        String responseBody = new String(inputStream.readAllBytes());
                        System.out.println("Response body: " + responseBody);
                    }
                } catch (Exception e) {
                    System.out.println("Failed to read response stream: " + e.getMessage());
                }
            } else {
                System.out.println("SUCCESS - Response code: " + responseCode);
                try (InputStream inputStream = conn.getInputStream()) {
                    String responseBody = new String(inputStream.readAllBytes());
                    System.out.println("Response body: " + responseBody.substring(0, Math.min(200, responseBody.length())) + "...");
                }
            }
            
            return responseCode != 500;
            
        } finally {
            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
            }
        }
    }

    public static void main( String[] args ) throws Exception { Lib.testClass( HttpProxyHandler.class ); }
}


