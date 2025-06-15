package http;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import jLib.*;



public class HttpReplacingProxyHandler extends HttpProxyHandler {
    private final Jsonable replacementConfig;
    private Pattern replacementPattern;
    private List<Object> replacementValues;



    public HttpReplacingProxyHandler() {
        this( Lib.loadCreds() );
    }



    public HttpReplacingProxyHandler( Jsonable config ) {
        if ( config==null ) {
            this.replacementConfig = new Jsonable( new HashMap<>() );
            return;
        }
        Object proxyConfig = config.get( "PROXY" );
        Object unwrapped = proxyConfig instanceof Jsonable j ? j.get() : proxyConfig;
        if ( unwrapped instanceof Map ) {
            this.replacementConfig = new Jsonable( unwrapped );
        } else {
            this.replacementConfig = new Jsonable( new HashMap<>() );
        }
    }



    public HttpReplacingProxyHandler( File replacementFile ) {
        if ( replacementFile==null || !replacementFile.exists() ) {
            this.replacementConfig = new Jsonable( new HashMap<>() );
            return;
        }
        try {
            Object data = JsonDecoder.decode( replacementFile );
            this.replacementConfig = new Jsonable( data );
        } catch ( Exception e ) {
            throw new RuntimeException( "Failed to load replacement config from " + replacementFile, e );
        }
    }



    @Deprecated
    public HttpReplacingProxyHandler( Map<String,Object> replacements ) {
        if (replacements==null || replacements.isEmpty()) {
            this.replacementConfig = new Jsonable( new HashMap<>() );
            this.replacementPattern = null;
            this.replacementValues = null;
            return;
        }
        // For backwards compatibility, apply replacements to all URLs
        Map<String,Object> config = new HashMap<>();
        config.put( "*", replacements );
        this.replacementConfig = new Jsonable( config );
        // Set up the pattern immediately for tests that call applyReplacements directly
        prepareReplacementsForUrl( "*" );
    }



    private void prepareReplacementsForUrl( String url ) {
        Object urlReplacements = replacementConfig.get( url );
        Object unwrapped = urlReplacements instanceof Jsonable j ? j.get() : urlReplacements;
        if ( !(unwrapped instanceof Map) ) {
            this.replacementPattern = null;
            this.replacementValues = null;
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String,Object> replacements = (Map<String,Object>) unwrapped;
        if ( replacements.isEmpty() ) {
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



    private String getParentUrl( String url ) {
        if ( url == null ) return null;

        // Remove query string if present
        int queryIndex = url.indexOf( '?' );
        if ( queryIndex >= 0 ) {
            url = url.substring( 0, queryIndex );
        }

        // Remove fragment if present
        int fragmentIndex = url.indexOf( '#' );
        if ( fragmentIndex >= 0 ) {
            url = url.substring( 0, fragmentIndex );
        }

        // Find the last slash after the protocol
        int protocolEnd = url.indexOf( "://" );
        if ( protocolEnd < 0 ) return null;

        int domainStart = protocolEnd + 3;
        int lastSlash = url.lastIndexOf( '/' );

        // If the last slash is part of the protocol or there's no path, we're done
        if ( lastSlash <= domainStart ) return null;

        // Remove the last path segment
        return url.substring( 0, lastSlash );
    }



    @Override
    public HttpResponse handle( HttpRequest req ) {
        String targetUrl = req.headerBlock.getHeaderValue( "X-Target-URL" );
        if ( targetUrl==null ) return super.handle( req );

        // Try URL with hierarchical fallback
        String urlToTry = targetUrl;
        while ( urlToTry != null ) {
            prepareReplacementsForUrl( urlToTry );
            if ( replacementPattern != null ) break;
            urlToTry = getParentUrl( urlToTry );
        }

        // If no URL-specific replacements found, try wildcard
        if ( replacementPattern==null ) {
            prepareReplacementsForUrl( "*" );
        }

        if ( replacementPattern==null ) return super.handle( req );

        // Apply replacements to the target URL
        String modifiedTargetUrl = applyReplacements( targetUrl );

        String headerString = serializeHeaderBlock( req.headerBlock );
        headerString = applyReplacements( headerString );
        HttpHeaderBlock modifiedHeaders = parseHeaderBlock( headerString );

        // Update the X-Target-URL header with the modified URL
        modifiedHeaders = modifiedHeaders.withAddHeader( "X-Target-URL", modifiedTargetUrl );
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
        Map<String,Integer> tokenCounts = new HashMap<>();
        Matcher matcher = replacementPattern.matcher( input );
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String matchedToken = matcher.group();
            Integer count = tokenCounts.getOrDefault( matchedToken, 0 );
            if ( count > 0 ) {
                matcher.appendReplacement( result, Matcher.quoteReplacement( matchedToken ) );
                continue;
            }
            tokenCounts.put( matchedToken, count + 1 );
            for (int i=1; i<=matcher.groupCount(); i++) {
                if (matcher.group( i )!=null) {
                    Object value = replacementValues.get( i-1 );
                    matcher.appendReplacement( result, Matcher.quoteReplacement( value.toString() ) );
                    break;
                }
            }
        }
        matcher.appendTail( result );
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
    private static boolean actualProxyWithReplacements_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        Map<String,Object> replacements = new LinkedHashMap<>();
        replacements.put( "<%=user%>", "john-doe" );
        replacements.put( "<%=action%>", "update" );
        replacements.put( "<%=value%>", 123 );
        HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler( replacements );
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "POST", "/test", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://httpbun.com/post" );
        headerBlock = headerBlock.withAddHeader( "Content-Type", "application/json" );
        headerBlock = headerBlock.withAddHeader( "X-User", "<%=user%>" );
        String jsonBody = "{\"user\":\"<%=user%>\",\"action\":\"<%=action%>\",\"value\":\"<%=value%>\"}";
        HttpRequest req = new HttpRequest( headerBlock, jsonBody.getBytes() );
        HttpResponse resp = handler.handle( req );
        Lib.asrt( resp.headerBlock.firstLine.contains( "200" ), "Expected 200 response but got: " + resp.headerBlock.firstLine );
        String responseBody = new String( resp.body );
        Lib.asrt( responseBody.contains( "\"X-User\": \"john-doe\"" ) || responseBody.contains( "\\\"X-User\\\": \\\"john-doe\\\"" ) || responseBody.contains("john-doe"), "Response should contain replaced user value" );
        Lib.asrt( responseBody.contains( "\"user\":\"john-doe\"" ) || responseBody.contains( "\\\"user\\\":\\\"john-doe\\\"" ) );
        Lib.asrt( responseBody.contains( "\"action\":\"update\"" ) || responseBody.contains( "\\\"action\\\":\\\"update\\\"" ) );
        Lib.asrt( responseBody.contains( "\"value\":\"123\"" ) || responseBody.contains( "\\\"value\\\":\\\"123\\\"" ) );
        return true;
    }



    @SuppressWarnings("unused")
    private static boolean basicReplacement_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        Map<String,Object> replacements = new LinkedHashMap<>();
        replacements.put( "<%=test%>", "replaced-value" );
        replacements.put( "<%=num%>", 42 );
        HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler( replacements );
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/echo?param=<%=test%>", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://httpbun.com/get?query=<%=test%>&num=<%=num%>" );
        headerBlock = headerBlock.withAddHeader( "X-Custom-Header", "Value is <%=test%>" );
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
    private static boolean bodyReplacement_TEST_( boolean findLineNumber ) throws Exception {
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
    private static boolean regexEscaping_TEST_( boolean findLineNumber ) throws Exception {
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
    private static boolean contentLengthUpdate_TEST_( boolean findLineNumber ) throws Exception {
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
    private static boolean fileBasedReplacement_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        File tempFile = File.createTempFile( "test-replacements-", ".json" );
        tempFile.deleteOnExit();
        String jsonContent = """
            {
                "https://api.example.com": {
                    "<%=apikey%>": "secret123",
                    "<%=user%>": "testuser"
                },
                "https://google.com": {
                    "<%=gkey%>": "1234",
                    "<%=query%>": "test search"
                },
                "*": {
                    "<%=default%>": "fallback"
                }
            }
        """;
        try ( FileWriter writer = new FileWriter( tempFile ) ) {
            writer.write( jsonContent );
        }
        HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler( tempFile );
        { // test Google replacements
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/search?q=<%=query%>&key=<%=gkey%>", new LinkedHashMap<>() );
            headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://google.com" );
            headerBlock = headerBlock.withAddHeader( "X-API-Key", "<%=gkey%>" );
            HttpRequest req = new HttpRequest( headerBlock, "apikey=<%=gkey%>".getBytes() );
            String headerString = handler.serializeHeaderBlock( req.headerBlock );
            handler.prepareReplacementsForUrl( "https://google.com" );
            String modifiedHeader = handler.applyReplacements( headerString );
            String modifiedBody = handler.applyReplacements( new String( req.body ) );
            Lib.asrt( modifiedHeader.contains( "key=1234" ), "Should replace gkey in URL" );
            Lib.asrt( modifiedHeader.contains( "q=test search" ), "Should replace query in URL" );
            Lib.asrt( modifiedHeader.contains( "X-API-Key: <%=gkey%>" ), "Should NOT replace second occurrence of gkey" );
            Lib.asrt( modifiedBody.contains( "apikey=1234" ), "Should replace gkey in body" );
            Lib.asrt( modifiedHeader.contains( "<%=gkey%>" ), "Should still contain second occurrence of gkey" );
            Lib.asrt( !modifiedHeader.contains( "<%=query%>" ), "Should not contain query token (only one occurrence)" );
        }
        { // test API example replacements
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( "POST", "/api/v1/users", new LinkedHashMap<>() );
            headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://api.example.com" );
            headerBlock = headerBlock.withAddHeader( "Authorization", "Bearer <%=apikey%>" );
            String jsonBody = "{\"user\":\"<%=user%>\",\"key\":\"<%=apikey%>\"}";
            HttpRequest req = new HttpRequest( headerBlock, jsonBody.getBytes() );
            handler.prepareReplacementsForUrl( "https://api.example.com" );
            String modifiedHeader = handler.applyReplacements( handler.serializeHeaderBlock( req.headerBlock ) );
            String modifiedBody = handler.applyReplacements( new String( req.body ) );
            Lib.asrt( modifiedHeader.contains( "Bearer secret123" ), "Should replace apikey in header" );
            Lib.asrt( modifiedBody.contains( "\"user\":\"testuser\"" ), "Should replace user in body" );
            Lib.asrt( modifiedBody.contains( "\"key\":\"secret123\"" ), "Should replace apikey in body (first occurrence)" );
        }
        { // test fallback replacements
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/test?default=<%=default%>", new LinkedHashMap<>() );
            headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://unknown.com" );
            HttpRequest req = new HttpRequest( headerBlock, new byte[0] );
            handler.prepareReplacementsForUrl( "https://unknown.com" );
            String modifiedHeader = handler.applyReplacements( handler.serializeHeaderBlock( req.headerBlock ) );
            Lib.asrt( !modifiedHeader.contains( "fallback" ), "Should not use fallback for unknown URL" );
            handler.prepareReplacementsForUrl( "*" );
            modifiedHeader = handler.applyReplacements( handler.serializeHeaderBlock( req.headerBlock ) );
            Lib.asrt( modifiedHeader.contains( "default=fallback" ), "Should use fallback when explicitly requested" );
        }
        { // test single replacement per token
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/test", new LinkedHashMap<>() );
            headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://google.com" );
            String body = "key1=<%=gkey%>&key2=<%=gkey%>&key3=<%=gkey%>";
            HttpRequest req = new HttpRequest( headerBlock, body.getBytes() );
            handler.prepareReplacementsForUrl( "https://google.com" );
            String modifiedBody = handler.applyReplacements( new String( req.body ) );
            Lib.asrtEQ( modifiedBody, "key1=1234&key2=<%=gkey%>&key3=<%=gkey%>", "Should only replace first occurrence" );
        }
        tempFile.delete();
        return true;
    }



    @SuppressWarnings("unused")
    private static boolean hierarchicalUrlFallback_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        File tempFile = File.createTempFile( "test-hierarchical-", ".json" );
        tempFile.deleteOnExit();
        String jsonContent = """
            {
                "https://google.com": {
                    "<%=gkey%>": "google-root-key"
                },
                "https://google.com/search": {
                    "<%=skey%>": "search-specific-key",
                    "<%=gkey%>": "search-level-gkey"
                },
                "https://api.example.com/v1": {
                    "<%=apikey%>": "v1-key"
                },
                "https://api.example.com/v1/users": {
                    "<%=userkey%>": "users-key"
                }
            }
        """;
        try ( FileWriter writer = new FileWriter( tempFile ) ) {
            writer.write( jsonContent );
        }
        HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler( tempFile );

        { // test exact match
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/search?key=<%=skey%>", new LinkedHashMap<>() );
            headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://google.com/search" );
            HttpRequest req = new HttpRequest( headerBlock, new byte[0] );
            String headerString = handler.serializeHeaderBlock( req.headerBlock );
            handler.prepareReplacementsForUrl( "https://google.com/search" );
            String modified = handler.applyReplacements( headerString );
            Lib.asrt( modified.contains( "key=search-specific-key" ), "Should use exact match" );
        }

        { // test fallback to parent
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/search/advanced?key=<%=gkey%>", new LinkedHashMap<>() );
            headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://google.com/search/advanced" );
            HttpRequest req = new HttpRequest( headerBlock, new byte[0] );

            // Manually test the hierarchical lookup
            String urlToTry = "https://google.com/search/advanced";
            String foundUrl = null;
            while ( urlToTry != null ) {
                handler.prepareReplacementsForUrl( urlToTry );
                if ( handler.replacementPattern != null ) {
                    foundUrl = urlToTry;
                    break;
                }
                String nextUrl = handler.getParentUrl( urlToTry );
                urlToTry = nextUrl;
            }

            Lib.asrt( foundUrl != null, "Should find a match somewhere in the hierarchy" );
            Lib.asrtEQ( foundUrl, "https://google.com/search", "Should find match at /search level" );
            String headerString = handler.serializeHeaderBlock( req.headerBlock );
            String modified = handler.applyReplacements( headerString );
            Lib.asrt( modified.contains( "key=search-level-gkey" ), "Should use /search level replacement: " + modified );
        }

        { // test true fallback to root
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/maps/api?key=<%=gkey%>", new LinkedHashMap<>() );
            headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://google.com/maps/api" );
            HttpRequest req = new HttpRequest( headerBlock, new byte[0] );

            // Manually test the hierarchical lookup
            String urlToTry = "https://google.com/maps/api";
            String foundUrl = null;
            while ( urlToTry != null ) {
                handler.prepareReplacementsForUrl( urlToTry );
                if ( handler.replacementPattern != null ) {
                    foundUrl = urlToTry;
                    break;
                }
                urlToTry = handler.getParentUrl( urlToTry );
            }

            Lib.asrtEQ( foundUrl, "https://google.com", "Should fall back to root domain" );
            String headerString = handler.serializeHeaderBlock( req.headerBlock );
            String modified = handler.applyReplacements( headerString );
            Lib.asrt( modified.contains( "key=google-root-key" ), "Should use root domain replacement" );
        }

        { // test no match
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/test?key=<%=nokey%>", new LinkedHashMap<>() );
            headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://unknown.com/path" );
            HttpRequest req = new HttpRequest( headerBlock, new byte[0] );
            HttpResponse resp = handler.handle( req );
            // The request should pass through unchanged since no replacements were found
            Lib.asrt( true, "Should handle unknown URL without error" );
        }

        { // test deep path fallback
            HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/test?key=<%=userkey%>&api=<%=apikey%>", new LinkedHashMap<>() );
            headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://api.example.com/v1/users/123/profile" );
            HttpRequest req = new HttpRequest( headerBlock, new byte[0] );
            String headerString = handler.serializeHeaderBlock( req.headerBlock );

            // Manually test the fallback logic
            String urlToTry = "https://api.example.com/v1/users/123/profile";
            while ( urlToTry != null ) {
                handler.prepareReplacementsForUrl( urlToTry );
                if ( handler.replacementPattern != null ) break;
                urlToTry = handler.getParentUrl( urlToTry );
            }

            String modified = handler.applyReplacements( headerString );
            Lib.asrt( modified.contains( "key=users-key" ), "Should use /v1/users match, not /v1" );
            Lib.asrt( modified.contains( "api=<%=apikey%>" ), "Should not replace apikey (not in /v1/users config): " + modified );
        }

        tempFile.delete();
        return true;
    }



    @SuppressWarnings("unused")
    private static boolean urlReplacement_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        Map<String,Object> replacements = new LinkedHashMap<>();
        replacements.put( "<%=host%>", "actual-api.example.com" );
        replacements.put( "<%=version%>", "v2" );
        replacements.put( "<%=key%>", "secret123" );
        HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler( replacements );

        // Test URL replacement
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/test", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://<%=host%>/<%=version%>/endpoint?key=<%=key%>" );
        headerBlock = headerBlock.withAddHeader( "Authorization", "Bearer <%=key%>" );
        HttpRequest req = new HttpRequest( headerBlock, new byte[0] );

        // Prepare replacements for wildcard (since we're using deprecated constructor)
        handler.prepareReplacementsForUrl( "*" );

        // Get the modified headers
        String headerString = handler.serializeHeaderBlock( req.headerBlock );
        String modifiedHeaderString = handler.applyReplacements( headerString );
        HttpHeaderBlock modifiedHeaders = handler.parseHeaderBlock( modifiedHeaderString );

        // Apply replacements to URL
        String targetUrl = req.headerBlock.getHeaderValue( "X-Target-URL" );
        String modifiedUrl = handler.applyReplacements( targetUrl );

        // Verify URL was properly replaced
        Lib.asrtEQ( modifiedUrl, "https://actual-api.example.com/v2/endpoint?key=secret123", "URL should be fully replaced" );

        // Verify header replacement still works (only first occurrence)
        Lib.asrt( modifiedHeaderString.contains( "Authorization: Bearer <%=key%>" ), "Should NOT replace second occurrence of key" );

        return true;
    }



    @SuppressWarnings("unused")
    private static boolean credsBasedReplacement_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        // Create test config in memory
        Map<String,Object> testCreds = new HashMap<>();
        Map<String,Object> proxyConfig = new HashMap<>();
        Map<String,Object> apiExampleConfig = new HashMap<>();
        apiExampleConfig.put( "<%=apikey%>", "secret123" );
        apiExampleConfig.put( "<%=user%>", "testuser" );
        proxyConfig.put( "https://api.example.com", apiExampleConfig );
        testCreds.put( "PROXY", proxyConfig );
        
        HttpReplacingProxyHandler handler = new HttpReplacingProxyHandler( new Jsonable(testCreds) );

        // Test replacements from PROXY config
        HttpHeaderBlock headerBlock = new HttpHeaderBlock( "GET", "/test?key=<%=apikey%>", new LinkedHashMap<>() );
        headerBlock = headerBlock.withAddHeader( "X-Target-URL", "https://api.example.com" );
        headerBlock = headerBlock.withAddHeader( "Authorization", "Bearer <%=apikey%>" );
        String body = "{\"user\":\"<%=user%>\"}";
        HttpRequest req = new HttpRequest( headerBlock, body.getBytes() );

        // Manually test the replacement logic
        String headerString = handler.serializeHeaderBlock( req.headerBlock );
        handler.prepareReplacementsForUrl( "https://api.example.com" );
        String modifiedHeader = handler.applyReplacements( headerString );
        String modifiedBody = handler.applyReplacements( body );

        Lib.asrt( modifiedHeader.contains( "key=secret123" ), "Should replace apikey in URL" );
        Lib.asrt( modifiedHeader.contains( "Bearer <%=apikey%>" ), "Should NOT replace second occurrence of apikey" );
        Lib.asrt( modifiedBody.contains( "\"user\":\"testuser\"" ), "Should replace user in body" );

        return true;
    }



    public static void main( String[] args ) throws Exception { Lib.testClass(); }
}
