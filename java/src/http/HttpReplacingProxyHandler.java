package http;
import java.util.*;
import java.util.regex.*;
import jLib.Lib;



public class HttpReplacingProxyHandler extends HttpProxyHandler {
    private final Pattern replacementPattern;
    private final List<Object> replacementValues;



    public HttpReplacingProxyHandler( Map<String,Object> replacements ) {
        if (replacements==null || replacements.isEmpty()) {
            this.replacementPattern = null;
            this.replacementValues = null;
            return;
        }
        StringBuilder patternBuilder = new StringBuilder();
        List<Object> values = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<String,Object> entry : replacements.entrySet()) {
            if (!first) patternBuilder.append( "|" );
            patternBuilder.append( "(" ).append( Pattern.quote( entry.getKey() ) ).append( ")" );
            values.add( entry.getValue() );
            first = false;
        }
        this.replacementPattern = Pattern.compile( patternBuilder.toString() );
        this.replacementValues = values;
    }



    @Override
    public HttpResponse handle( HttpRequest req ) {
        if (replacementPattern==null) return super.handle( req );
        String headerString = serializeHeaderBlock( req.headerBlock );
        headerString = applyReplacements( headerString );
        HttpHeaderBlock modifiedHeaders = parseHeaderBlock( headerString );
        byte[] modifiedBody = req.body;
        String contentType = modifiedHeaders.getHeaderValue( "Content-Type" );
        if (contentType!=null && isTextContent( contentType ) && req.body.length>0) {
            String bodyString = new String( req.body );
            String modifiedBodyString = applyReplacements( bodyString );
            if (!bodyString.equals( modifiedBodyString )) {
                modifiedBody = modifiedBodyString.getBytes();
                modifiedHeaders = updateContentLength( modifiedHeaders, modifiedBody.length );
            }
        }
        HttpRequest modifiedReq = new HttpRequest( modifiedHeaders, modifiedBody );
        return super.handle( modifiedReq );
    }



    private String applyReplacements( String input ) {
        if (replacementPattern==null || input==null) return input;
        Matcher matcher = replacementPattern.matcher( input );
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append( input.substring( lastEnd, matcher.start() ) );
            for (int i=1; i<=matcher.groupCount(); i++) {
                if (matcher.group( i )!=null) {
                    Object value = replacementValues.get( i-1 );
                    result.append( value.toString() );
                    break;
                }
            }
            lastEnd = matcher.end();
        }
        result.append( input.substring( lastEnd ) );
        return result.toString();
    }



    private boolean isTextContent( String contentType ) {
        if (contentType==null) return false;
        contentType = contentType.toLowerCase();
        return contentType.startsWith( "text/" ) ||
               contentType.contains( "application/json" ) ||
               contentType.contains( "application/xml" ) ||
               contentType.contains( "application/x-www-form-urlencoded" );
    }



    private String serializeHeaderBlock( HttpHeaderBlock headerBlock ) {
        StringBuilder sb = new StringBuilder();
        sb.append( headerBlock.firstLine ).append( "\r\n" );
        for (Map.Entry<String,String> entry : headerBlock.headers.entrySet()) {
            sb.append( entry.getKey() ).append( ": " ).append( entry.getValue() ).append( "\r\n" );
        }
        return sb.toString();
    }



    private HttpHeaderBlock parseHeaderBlock( String headerString ) {
        String[] lines = headerString.split( "\r\n" );
        if (lines.length==0) throw new IllegalArgumentException( "Empty header string" );
        String firstLine = lines[0];
        LinkedHashMap<String,String> headers = new LinkedHashMap<>();
        for (int i=1; i<lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            int colonIdx = line.indexOf( ": " );
            if (colonIdx>0) {
                String name = line.substring( 0, colonIdx );
                String value = line.substring( colonIdx+2 );
                headers.put( name, value );
            }
        }
        String[] parts = firstLine.split( " ", 3 );
        if (parts.length>=2) {
            return new HttpHeaderBlock( parts[0], parts[1], headers );
        }
        return new HttpHeaderBlock( firstLine, headers );
    }



    private HttpHeaderBlock updateContentLength( HttpHeaderBlock headerBlock, int newLength ) {
        LinkedHashMap<String,String> newHeaders = new LinkedHashMap<>();
        for (Map.Entry<String,String> entry : headerBlock.headers.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase( "Content-Length" )) {
                newHeaders.put( entry.getKey(), entry.getValue() );
            }
        }
        newHeaders.put( "Content-Length", String.valueOf( newLength ) );
        return new HttpHeaderBlock( headerBlock.firstLine, newHeaders );
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_basicReplacement_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        Map<String,Object> replacements = new LinkedHashMap<>();
        replacements.put( "{{test}}", "replaced-value" );
        replacements.put( "{{num}}", 42 );
        HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler( replacements );
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/echo?param={{test}}", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://httpbun.com/get?query={{test}}&num={{num}}" );
        headerBlock = headerBlock.withAddHeader( "X-Custom-Header", "Value is {{test}}" );
        HttpRequest req = new HttpRequest( headerBlock, new byte[0] );
        HttpResponse resp = handler.handle( req );
        if (resp.headerBlock.firstLine.contains( "200" )) {
            String body = new String( resp.body );
            Lib.asrt( body.contains( "query=replaced-value" ) || body.contains( "\"query\": \"replaced-value\"" ) );
            Lib.asrt( body.contains( "num=42" ) || body.contains( "\"num\": \"42\"" ) );
        }
        return true;
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_bodyReplacement_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        Map<String,Object> replacements = new LinkedHashMap<>();
        replacements.put( "SHORT", "LONGER_STRING" );
        HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler( replacements );
        String testBody = "This is SHORT text";
        String result = handler.applyReplacements( testBody );
        Lib.asrtEQ( result, "This is LONGER_STRING text" );
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "POST", "/test", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "Content-Type", "text/plain" );
        headerBlock = headerBlock.withAddHeader( "Content-Length", "18" );
        HttpRequest req = new HttpRequest( headerBlock, testBody.getBytes() );
        String headerString = handler.serializeHeaderBlock( req.headerBlock );
        headerString = handler.applyReplacements( headerString );
        HttpHeaderBlock modifiedHeaders = handler.parseHeaderBlock( headerString );
        byte[] modifiedBody = handler.applyReplacements( testBody ).getBytes();
        modifiedHeaders = handler.updateContentLength( modifiedHeaders, modifiedBody.length );
        Lib.asrtEQ( modifiedHeaders.getHeaderValue( "Content-Length" ), "26" );
        return true;
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_regexEscaping_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        Map<String,Object> replacements = new LinkedHashMap<>();
        replacements.put( "$price", "99.99" );
        replacements.put( "[item]", "widget" );
        replacements.put( "a.b", "replaced" );
        HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler( replacements );
        String testString = "Price: $price for [item] with code a.b";
        String result = handler.applyReplacements( testString );
        Lib.asrtEQ( result, "Price: 99.99 for widget with code replaced" );
        return true;
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_contentLengthUpdate_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        Map<String,Object> replacements = new LinkedHashMap<>();
        replacements.put( "SHORT", "VERY_LONG_REPLACEMENT_STRING" );
        HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler( replacements );
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "POST", "/test", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "Content-Type", "text/plain" );
        headerBlock = headerBlock.withAddHeader( "Content-Length", "5" );
        String body = "SHORT";
        HttpHeaderBlock modified = handler.parseHeaderBlock( handler.serializeHeaderBlock( headerBlock ) );
        byte[] modifiedBody = handler.applyReplacements( body ).getBytes();
        modified = handler.updateContentLength( modified, modifiedBody.length );
        Lib.asrtEQ( modified.getHeaderValue( "Content-Length" ), "28" );
        return true;
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_actualProxyWithReplacements_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        Map<String,Object> replacements = new LinkedHashMap<>();
        replacements.put( "{{user}}", "john-doe" );
        replacements.put( "{{action}}", "update" );
        replacements.put( "{{value}}", 123 );
        HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler( replacements );
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "POST", "/test", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://httpbun.com/post" );
        headerBlock = headerBlock.withAddHeader( "Content-Type", "application/json" );
        headerBlock = headerBlock.withAddHeader( "X-User", "{{user}}" );
        String jsonBody = "{\"user\":\"{{user}}\",\"action\":\"{{action}}\",\"value\":{{value}}}";
        HttpRequest req = new HttpRequest( headerBlock, jsonBody.getBytes() );
        HttpResponse resp = handler.handle( req );
        if (resp.headerBlock.firstLine.contains( "200" )) {
            String responseBody = new String( resp.body );
            Lib.asrt( responseBody.contains( "\"X-User\": \"john-doe\"" ) || responseBody.contains( "\\\"X-User\\\": \\\"john-doe\\\"" ) );
            Lib.asrt( responseBody.contains( "\"user\":\"john-doe\"" ) || responseBody.contains( "\\\"user\\\":\\\"john-doe\\\"" ) );
            Lib.asrt( responseBody.contains( "\"action\":\"update\"" ) || responseBody.contains( "\\\"action\\\":\\\"update\\\"" ) );
            Lib.asrt( responseBody.contains( "\"value\":123" ) || responseBody.contains( "\\\"value\\\":123" ) );
        }
        return true;
    }



    public static void main( String[] args ) throws Exception { Lib.testClass( HttpReplacingProxyHandler.class ); }
}

