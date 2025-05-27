package http;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

import jLib.Lib;



public class HttpProxyHandler implements HttpHandler {



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
                if (headerName.equalsIgnoreCase( "Host" ) ||
                    headerName.equalsIgnoreCase( "Content-Length" ) ||
                    headerName.startsWith( "X-Target-" ) ||
                    headerName.startsWith( "X-File-" )) continue;
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



    public static void main( String[] args ) throws Exception { Lib.testClass( HttpProxyHandler.class ); }
}


