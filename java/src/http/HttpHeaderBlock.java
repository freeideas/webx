package http;
import java.io.*;
import java.util.*;

import jLib.Lib;
import jLib.Result;



public class HttpHeaderBlock {



    public final String firstLine;
    public final Map<String,String> headers;



    public HttpHeaderBlock( String firstLine, Map<String,String> headers ) {
        this.firstLine = firstLine;
        this.headers = headers==null ? Lib.mapOf() : Collections.unmodifiableMap(headers);
    }



    public HttpHeaderBlock( int responseCode, String responseText, Map<String,String> headers ) {
        this( "HTTP/1.1 "+responseCode+" "+responseText, headers==null?Map.of():headers );
    }



    public HttpHeaderBlock( String method, String uri, Map<String,String> headers ) {
        this( method.toUpperCase()+" "+uri+" HTTP/1.1", headers==null?Map.of():headers );
    }



    public static HttpHeaderBlock redirect( String uri ) {
        return new HttpHeaderBlock( 302, "Found", Map.of("Location",uri) );
    }



    public HttpHeaderBlock withAddHeader( String name, String value ) {
        Map<String,String> newHeaders = new LinkedHashMap<>(headers);
        newHeaders.put( name, value );
        return new HttpHeaderBlock( firstLine, newHeaders );
    }



    public static Result<HttpHeaderBlock,Exception> readFrom( InputStream inp ) {
        StringBuilder sb = new StringBuilder();
        try {
            while (true) {
                int c = inp.read();
                if (c<0) return Result.err( new IOException("incomplete header") );
                sb.append( (char)c );
                if ( sb.toString().endsWith("\r\n\r\n") ) break;
            }
        } catch (IOException ioe) { return Result.err(ioe); }
        return parseFrom( sb.toString() );
    }



    public static Result<HttpHeaderBlock,Exception> parseFrom( String rawHeader ) {
        String[] lines = rawHeader.split( "[\r\n]+" );
        if ( lines.length<1 ) return Result.err( new IllegalArgumentException("no header lines") );
        String firstLine = lines[0];
        Map<String,String> headers = new LinkedHashMap<>();
        for ( int i=1; i<lines.length; i++ ) {
            String[] parts = lines[i].split(": ",2);
            if ( parts.length<2 ) continue;
            headers.put( parts[0], parts[1] );
        }
        return Result.ok( new HttpHeaderBlock(firstLine,headers) );
    }



    public String getHeaderValue( String name ) {
        return headers.get(name);
    }



    public Map<String,String> cookieMap() {
        String cookieStr = headers.get("Cookie");
        if (cookieStr==null) return Lib.mapOf();
        Map<String,String> map = new LinkedHashMap<>();
        for (String part : cookieStr.split("; ")) {
            String[] parts = part.split("=",2);
            if (parts.length<2) continue;
            map.put(parts[0], parts[1]);
        }
        return Collections.unmodifiableMap(map);
    }



    public Map<String,String> queryMap() {
        String uri = getUri();
        if (uri==null) return Lib.mapOf();
        if (! uri.contains("?") ) return Lib.mapOf();
        String queryStr = uri.split("\\?",2)[1];
        if (queryStr==null) return Lib.mapOf();
        Map<String,String> map = new LinkedHashMap<>();
        for (String part : queryStr.split("&")) {
            String[] parts = part.split("=",2);
            if (parts.length<2) continue;
            map.put(parts[0], parts[1]);
        }
        return Collections.unmodifiableMap(map);
    }



    public String getUri() {
        if (firstLine==null) return null;
        String[] lineParts = firstLine.split(" ");
        if (lineParts.length<2) return null;
        return lineParts[1];
    }



    public String getContentType() {
        return headers.get("Content-Type");
    }



    public Long getContentLength() {
        String contentLengthStr = headers.get("Content-Length");
        if (contentLengthStr==null) {
            if ( List.of("GET","HEAD","OPTIONS","TRACE").contains( getMethod() ) ) return 0L;
            return null;
        }
        try {
            return Long.parseLong(contentLengthStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }



    public String getMethod() {
        if (firstLine==null) return null;
        String[] lineParts = firstLine.split(" ");
        if (lineParts.length<1) return null;
        return lineParts[0];
    }



    public Result<Long,Exception> write( OutputStream out ) {
        try {
            byte[] bytes = toString().getBytes();
            out.write(bytes);
            return Result.ok( (long)bytes.length );
            /*
            long outCount = 0;
            byte[] outArr = null;
            outArr = (firstLine+"\r\n").getBytes();
            out.write(outArr);
            outCount += outArr.length;
            for ( Map.Entry<String,String> e : headers.entrySet() ) {
                outArr = (e.getKey()+": "+e.getValue()+"\r\n").getBytes();
                out.write(outArr);
                outCount += outArr.length;
            }
            outArr = "\r\n".getBytes();
            out.write(outArr);
            outCount += outArr.length;
            return Result.ok(outCount);
            */
        } catch ( IOException ioe ) { return Result.err(ioe); }
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        String rawHeader = Lib.unindent("""
            HTTP/1.1 200 OK
            Date: Mon, 27 Jul 2009 12:28:53 GMT
            Server: Apache/2.2.14 (Win32)
            Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT
            Content-Length: 10
            Content-Type: text/html

            1234567890
        """).trim();
        rawHeader = rawHeader.replaceAll( "(?<!\\r)\\n", "\r\n" ); // convert to CRLF
        InputStream inp = new ByteArrayInputStream( rawHeader.getBytes() );
        Result<HttpHeaderBlock,Exception> result = readFrom(inp);
        if (! result.isOk() ) throw result.err();
        HttpHeaderBlock headerBlock = result.ok();
        Lib.asrt( headerBlock.getContentLength() == 10 );
        Lib.asrt( inp.read() == (int)'1' );
        return true;
    }



    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append( firstLine ).append("\r\n");
        for ( Map.Entry<String,String> e : headers.entrySet() ) {
            sb.append( e.getKey() ).append(": ").append( e.getValue() ).append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }



    public String getRequestPath() {
        String uri = getUri();
        if (uri == null) return null;
        int idx = uri.indexOf('?');
        if (idx != -1) uri = uri.substring(0, idx);
        idx = uri.indexOf('#');
        if (idx != -1) uri = uri.substring(0, idx);
        if (!uri.startsWith("/")) uri = "/" + uri;
        uri = Lib.normalizePath(uri);
        return uri;
    }
    @SuppressWarnings("unused")
    private static boolean getRequestPath_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        HttpHeaderBlock headerBlock = new HttpHeaderBlock(
            "GET /path/ignore/..?query=1#fragment HTTP/1.1", Lib.mapOf()
        );
        String actual = headerBlock.getRequestPath();
        String expected = "/path";
        Lib.asrtEQ(actual,expected);
        return true;
    }




    public static void main( String[] args ) throws Exception { Lib.testClass(); }
}
