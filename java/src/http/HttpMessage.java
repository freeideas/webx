package http;
import java.io.*;
import java.util.*;
import jLib.*;



public class HttpMessage {



    public final HttpHeaderBlock headerBlock;
    public final byte[] body;
    public final Object parsedBody;
    public final Map<String,Object> allParms; // query string, body form, and cookie data; in that order of precidence



    public HttpMessage( HttpHeaderBlock headerBlock, byte[] body ) {
        this.headerBlock = headerBlock;
        this.body = body != null ? body : new byte[0];
        String contentType = headerBlock.getContentType();
        String encoding = Lib.nvl( headerBlock.getHeaderValue("Content-Encoding"), "utf-8" );
        Object parsed = null;
        if ( this.body.length > 0 ) {
            try {
                String bodyStr = new String(this.body,encoding);
                if ( contentType.toUpperCase().indexOf("JSON") >= 0 ) {
                    parsed = JsonDecoder.decode(bodyStr);
                } else
                if ( contentType.toUpperCase().indexOf("URLENCODED") >= 0 ) {
                    parsed = parseUrlEncoded(bodyStr);
                } else {
                    parsed = bodyStr;
                }
            } catch ( Exception e ) {
                parsed = null;
            }
        }
        this.parsedBody = parsed;
        Map<String,Object> allParms = new LinkedHashMap<>();
        allParms.putAll( headerBlock.cookieMap() );
        if ( parsed instanceof Map<?,?> m ) {
            @SuppressWarnings("unchecked")
            Map<String,Object> parsedMap = (Map<String,Object>) m;
            allParms.putAll( parsedMap );
        }
        allParms.putAll( headerBlock.queryMap() );
        this.allParms = Collections.unmodifiableMap(allParms);
    }




    public static Map<String,Object> parseUrlEncoded( String s ) {
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        String[] pairs = s.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            String k = kv[0];
            String v = (kv.length>1 ? kv[1] : "");
            k = Lib.urlDecode(k);
            v = Lib.urlDecode(v);
            result.put(k,v);
        }
        return Collections.unmodifiableMap(result);
    }
    @SuppressWarnings("unused")
    private static boolean parseUrlEncoded_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        String s = "a=1&b=2&c=3";
        Map<String,Object> result = parseUrlEncoded(s);
        Lib.asrt( result.size() == 3 );
        Lib.asrt( result.get("a").equals("1") );
        Lib.asrt( result.get("b").equals("2") );
        Lib.asrt( result.get("c").equals("3") );
        return true;
    }




    public static Result<HttpMessage,Exception> readHttpMessage( HttpHeaderBlock headerBlock, InputStream inp ) {
        Long contentLength = headerBlock.getContentLength();
        if ( contentLength==null ) contentLength = 0L;
        if ( contentLength<0 || contentLength>Integer.MAX_VALUE ) {
            return Result.err( new IllegalArgumentException("invalid content length: "+contentLength) );
        }
        byte[] body = new byte[ contentLength.intValue() ];
        int totalRead = 0;
        while ( totalRead < body.length ) {
            int read;
            try { read = inp.read( body, totalRead, body.length-totalRead ); }
            catch ( IOException ioe ) { return Result.err(ioe); }
            if ( read < 0 ) return Result.err( new IOException("incomplete body") );
            totalRead += read;
        }
        Result<HttpMessage,Exception> result = Result.ok( new HttpMessage(headerBlock,body) );
        return result;
    }
    public static Result<HttpMessage,Exception> readHttpMessage( InputStream inp ) {
        Result<HttpHeaderBlock,Exception> headerResult = HttpHeaderBlock.readFrom(inp);
        if (! headerResult.isOk() ) return Result.err( headerResult.err() );
        return readHttpMessage( headerResult.ok(), inp );
    }



    public Result<Long,Exception> write( OutputStream out ) {
        HttpHeaderBlock newHeaderBlock = headerBlock;
        if ( headerBlock.getContentLength() == null ) {
            newHeaderBlock = headerBlock.withAddHeader( "Content-Length", ""+body.length );
        }
        Result<Long,Exception> headerResult = newHeaderBlock.write(out);
        if (! headerResult.isOk() ) return headerResult;
        try { out.write(body); }
        catch ( IOException ioe ) { return Result.err(ioe); }
        return Result.ok( headerResult.ok() + body.length );
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        {
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
            Result<HttpMessage,Exception> result = readHttpMessage( new ByteArrayInputStream(rawHeader.getBytes()) );
            if (! result.isOk() ) throw result.err();
            HttpMessage msg = result.ok();
            Lib.asrt( msg.body.length == 10 );
            Lib.asrt( msg.body[0] == (int)'1' );
        }
        { // json post
            String rawHeader = Lib.unindent("""
                POST / HTTP/1.1
                Date: Mon, 27 Jul 2009 12:28:53 GMT
                Server: Apache/2.2.14 (Win32)
                Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT
                Content-Length: 19
                Content-Type: application/json

                {"a":1,"b":2,"c":3}
            """).trim();
            rawHeader = rawHeader.replaceAll( "(?<!\\r)\\n", "\r\n" ); // convert to CRLF
            Result<HttpMessage,Exception> result = readHttpMessage( new ByteArrayInputStream(rawHeader.getBytes()) );
            if (! result.isOk() ) throw result.err();
            HttpMessage msg = result.ok();
            Lib.asrt( msg.parsedBody instanceof Map );
            @SuppressWarnings("unchecked")
            Map<String,Object> parsed = (Map<String,Object>) msg.parsedBody;
            Lib.asrt( parsed.size() == 3 );
            Lib.asrt( parsed.get("a").equals(1) );
            Lib.asrt( parsed.get("b").equals(2) );
            Lib.asrt( parsed.get("c").equals(3) );
        }
        { // urlencoded post
            String rawHeader = Lib.unindent("""
                POST / HTTP/1.1
                Date: Mon, 27 Jul 2009 12:28:53 GMT
                Server: Apache/2.2.14 (Win32)
                Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT
                Content-Length: 11
                Content-Type: application/x-www-form-urlencoded

                a=1&b=2&c=3
            """).trim();
            rawHeader = rawHeader.replaceAll( "(?<!\\r)\\n", "\r\n" ); // convert to CRLF
            Result<HttpMessage,Exception> result = readHttpMessage( new ByteArrayInputStream(rawHeader.getBytes()) );
            if (! result.isOk() ) throw result.err();
            HttpMessage msg = result.ok();
            Lib.asrt( msg.parsedBody instanceof Map );
            @SuppressWarnings("unchecked")
            Map<String,Object> parsed = (Map<String,Object>) msg.parsedBody;
            Lib.asrt( parsed.size() == 3 );
            Lib.asrt( parsed.get("a").equals("1") );
            Lib.asrt( parsed.get("b").equals("2") );
            Lib.asrt( parsed.get("c").equals("3") );
        }
        return true;
    }



    public static void main( String[] args ) throws Exception { Lib.testClass(); }
}
