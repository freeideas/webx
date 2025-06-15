package jLib;
import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.lang.reflect.*;
import java.time.*;
import java.time.format.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;
import java.util.stream.*;
import javax.script.*;
import javax.net.ssl.*;
import java.util.zip.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.channels.*;



public class Lib {
    public static final java.lang.ref.Cleaner cleaner = java.lang.ref.Cleaner.create(); // so everyone can use one cleaner instead of having many
    public static final String CRLF = "\r\n";



    public static String normalizePath( String path ) {
        if (path==null) return null;
        boolean startsWithSlash = path.startsWith("/") || path.startsWith("\\");
        boolean isURL = path.matches("\\w+://.*");
        String[] parts = path.split("[/\\\\]");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if ( part.equals("..") && !result.isEmpty() ) {
                    result.remove(result.size() - 1);
                    continue;
            }
            result.add(part);
        }
        String resStr = (startsWithSlash ? "/" : "") + String.join("/", result);
        if (isURL) resStr = resStr.replaceFirst("^(\\w+):/+", "$1://");
        return resStr;
    }
    @SuppressWarnings("unused")
    private static boolean normalizePath_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        asrtEQ( normalizePath("a/b/c"), "a/b/c" );
        asrtEQ( normalizePath("//a/b/c/"), "/a/b/c" );
        asrtEQ( normalizePath("sftp://a.com/b//c/..//"), "sftp://a.com/b" );
        asrtEQ( normalizePath(""), "" );
        asrtEQ( normalizePath(null), null );
        return true;
    }



    public static class Pair<A,B> implements Map.Entry<A,B> {
        public final A a;
        public final B b;
        public Pair( A a, B b ) {
            this.a = a;
            this.b = b;
        }
        public int hashCode() {
            return a.hashCode() ^ b.hashCode();
        }
        public boolean equals( Object o ) {
            if ( o == null ) return false;
            if ( o == this ) return true;
            if ( o.getClass() != getClass() ) return false;
            Pair<?,?> p = (Pair<?,?>)o;
            return a.equals(p.a) && b.equals(p.b);
        }
        public String toString() {
            return "(" + a + "," + b + ")";
        }
        @Override
        public A getKey() { return a; }
        @Override
        public B getValue() { return b; }
        @Override
        public B setValue(B value) {
            throw new UnsupportedOperationException("immutable");
        }
    }
    public static <A,B> Pair<A,B> pair( A a, B b ) {
        return new Pair<A,B>(a,b);
    }
    @SuppressWarnings("unused")
    private static boolean pair_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Pair<String,String> p = pair("a","b");
        return p.a.equals("a") && p.b.equals("b");
    }
    public static class Trio<A,B,C> {
        public final A a;
        public final B b;
        public final C c;
        public Trio( A a, B b, C c ) {
            this.a = a;
            this.b = b;
            this.c = c;
        }
        public int hashCode() {
            return a.hashCode() ^ b.hashCode() ^ c.hashCode();
        }
        public boolean equals( Object o ) {
            if ( o == null ) return false;
            if ( o == this ) return true;
            if ( o.getClass() != getClass() ) return false;
            Trio<?,?,?> p = (Trio<?,?,?>)o;
            return a.equals(p.a) && b.equals(p.b) && c.equals(p.c);
        }
        public String toString() {
            return "(" + a + "," + b + "," + c + ")";
        }
    }
    @SuppressWarnings("unused")
    private static boolean trio_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Trio<String,String,String> p = new Trio<String,String,String>("a","b","c");
        return p.a.equals("a") && p.b.equals("b") && p.c.equals("c");
    }



    public static String xmlSafeText( Object txt ) {
        return xmlSafeText(txt,null);
    }
    public static String xmlSafeText( Object txt, Boolean encodeAll ) {
        if (txt==null) txt="";
        String str = txt.toString();
        int c, len=str.length();
        StringBuffer buf = new StringBuffer( len + len<<1 );
        for (int i=0; i<len; i++) {
            c = str.charAt(i);
            if ( encodeAll==Boolean.FALSE && !( c=='<' || c=='&' ) ) {
                // in this mode, encode only < and &
                buf.append( (char)c );
                continue;
            }
            if ( encodeAll!=Boolean.TRUE && c=='&' ) {
                // in these modes, skip already-encoded entities
                int end = Math.min( i+12, str.length() );
                if ( str.substring(i,end).matches("^&(([a-zA-Z0-9]+)|(#\\d+));.*$") ) {
                    while (c!=';') {
                        buf.append( (char)c );
                        i+=1; c=str.charAt(i);
                    }
                    buf.append( (char)c );
                    continue;
                }
            }
            boolean foundCode = false;
            for ( int codeIdx=0; codeIdx<XMLENTITIES.length; codeIdx++ ) {
                String code = XMLENTITIES[codeIdx][0];
                String repl = XMLENTITIES[codeIdx][1];
                if ( c == repl.charAt(0) ) {
                    buf.append(code);
                    foundCode = true;
                }
            }
            if (foundCode) continue;
            switch (c) {
                case '\'' : buf.append("&#39;"); break;
                case ']'  : buf.append("&#93;"); break;
                case '['  : buf.append("&#91;"); break;
                case '\\' : buf.append("&#92;"); break;
                default : {
                    if ( c<32 || c>127 || encodeAll==Boolean.TRUE ) {
                        buf.append('&');
                        buf.append('#');
                        buf.append(c);
                        buf.append(';');
                    } else {
                        buf.append( (char)c );
                    }
                } break;
            }
        }
        return buf.toString();
    }
    public static String unescapeXML( final String text ) {
        StringBuilder result = new StringBuilder( text.length() );
        for ( int txtIdx=0,len=text.length(); txtIdx<len; txtIdx++ ) {
            char charAt = text.charAt(txtIdx);
            if ( charAt != '&' ) {
                result.append(charAt);
                continue;
            }
            if ( text.regionMatches(txtIdx,"&#",0,2) ) {
                try {
                    String s = text.substring(
                        txtIdx, Math.min(text.length(),txtIdx+9)
                    ).replaceFirst( "^&#(x?\\d+);.*", "$1" );
                    int n;
                    if ( s.charAt(0) == 'x' ) {
                        n = Integer.parseInt( s.substring(1), 16 );
                    } else {
                        n = Integer.parseInt(s,10);
                    }
                    txtIdx += ( 2 + s.length() );
                    result.append( (char) n );
                } catch ( Throwable t ) {
                    result.append(charAt);
                }
                continue;
            }
            boolean foundCode = false;
            for ( int codeIdx=0; codeIdx<XMLENTITIES.length; codeIdx++ ) {
                String code = XMLENTITIES[codeIdx][0];
                String repl = XMLENTITIES[codeIdx][1];
                if (text.regionMatches( true, txtIdx, code, 0, code.length() )) {
                    result.append(repl);
                    txtIdx += code.length() - 1;
                    foundCode = true;
                    break;
                }
            }
            if (foundCode) continue;
            result.append(charAt);
        }
        return result.toString();
    }
    public static final String[][] XMLENTITIES = {
        { "&lt;",   "<" },
        { "&gt;",   ">" },
        { "&amp;",  "&" },
        { "&apos;", "'" },
        { "&quot;", "\"" },
    };
    @SuppressWarnings("unused")
    private static boolean unescapeXML_TEST_() {
        {
            String s = "~\t\0`!@#$%^&*()_ok123=+{[ }]|\\?/<,>.'\"";
            String escaped, unescaped;
            escaped = xmlSafeText(s,null);
            unescaped = unescapeXML(escaped);
            Lib.asrt(unescaped.equals(s), "unescapeXML test 1 failed");
            escaped = xmlSafeText(s,true);
            Lib.asrt(unescaped.equals(s), "unescapeXML test 2 failed");
            escaped = xmlSafeText(s,false);
            Lib.asrt(unescaped.equals(s), "unescapeXML test 3 failed");
            Lib.asrt(unescapeXML("&#x20;").equals(" "), "unescapeXML test 4 failed");
            Lib.asrt(unescapeXML("&#92;&#92;").equals("\\\\"), "unescapeXML test 5 failed");
        }
        for ( String s : new String[]{"&","&#","&#;","&#0"} ) {
            // these are not unescape-able
            Lib.asrt(unescapeXML(s).equals(s), "unescapeXML test 6 failed");
        }
        for ( String s : new String[]{
            "&aMp;", "&#1234;", "He read &#22;War &amp; Peace&QUOT;."
        } ) {
            // these are already escaped
            String escaped = xmlSafeText(s);
            Lib.asrt(escaped.equals(s), "unescapeXML test 7 failed");
        }
        // make sure an html comment is escaped
        Lib.asrt(! xmlSafeText("-->").equals("-->"), "need safety inside html comments" );
        return true;
    }



    public static ServerSocket createServerSocket(
        int port, boolean tls, File cert, String keystorePassword, String bindAddress
    ) throws IOException {
        InetAddress addr = null;
        if ( bindAddress!=null ) {
            try { addr = InetAddress.getByName(bindAddress); }
            catch ( Exception e ) { addr = null; }
        }
        if (!tls) {
            if ( addr!=null ) return new ServerSocket( port, 50, addr );
            return new ServerSocket( port, 50 );
        }
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            if (cert != null) {
                // Load the keystore from the provided certificate file
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (FileInputStream fis = new FileInputStream(cert)) {
                    keyStore.load(fis, keystorePassword.toCharArray());
                }
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
                sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            } else {
                sslContext.init(null, null, null);
            }
            SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
            if ( addr!=null ) return sslServerSocketFactory.createServerSocket( port, 50, addr );
            return sslServerSocketFactory.createServerSocket( port, 50 );
        }
        catch (IOException ioe) { throw ioe; }
        catch (Exception e) { throw new IOException(e); }
    }
    @SuppressWarnings("unused")
    private static boolean createServerSocket_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        // Test null bindAddress (all interfaces)
        try ( ServerSocket ss = createServerSocket( 19999, false, null, null, null ) ) {
            asrt( ss.getLocalSocketAddress().toString().contains("0.0.0.0") );
        }
        // Test localhost
        try ( ServerSocket ss = createServerSocket( 19999, false, null, null, "localhost" ) ) {
            asrt( ss.getLocalSocketAddress().toString().contains("127.0.0.1") );
        }
        // Test specific IP
        try ( ServerSocket ss = createServerSocket( 19999, false, null, null, "127.0.0.1" ) ) {
            asrt( ss.getLocalSocketAddress().toString().contains("127.0.0.1") );
        }
        // Test invalid address (fallback to all interfaces)
        try ( ServerSocket ss = createServerSocket( 19999, false, null, null, "badaddress" ) ) {
            asrt( ss.getLocalSocketAddress().toString().contains("0.0.0.0") );
        }
        return true;
    }



    /**
     * Looks for a file named ".creds.json" in the current directory or any parent directory.
     * Loads it as json and returns it as a Jsonable object.
     * The file contents are cached so only the first call will do any I/O.
    **/
    public static Jsonable loadCreds() {
        if ( loadCreds_cache != null ) return loadCreds_cache;
        // find .creds.json and retry
        File credsFile = new File("./.creds.json");
        try{ credsFile = credsFile.getCanonicalFile(); }
        catch ( IOException unlikely ) { throw new RuntimeException(unlikely); }
        while (! credsFile.isFile() ) {
            File pf = credsFile.getParentFile().getParentFile();
            if ( pf==null || pf.equals(credsFile) ) throw new RuntimeException("Can't find .creds.json");
            credsFile = new File( pf, ".creds.json" );
        }
        loadCreds_cache = new Jsonable( JsonDecoder.decode(credsFile) );
        return loadCreds_cache;
    } private static Jsonable loadCreds_cache = null;
    @SuppressWarnings("unused")
    private static boolean loadCreds_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Jsonable creds = loadCreds();
        asrt( creds != null );
        Object secretObj = creds.get("SECRET");
        Object secret = secretObj instanceof Jsonable j ? j.get() : secretObj;
        asrt(! isEmpty( secret ) );
        return true;
    }



    /**
     * If both are Maps, then copies entries from copyFrom into copyInto, and returns copyInto.
     * If both are Lists, then copies each element of copyFrom to copyInto at the same index, and returns copyInto.
     * Otherwise, returns copyFrom.
     * If modifyCopyInto is true but some of the underlying data structures are immutable,
     * then a new copy is created as needed.
     **/
    public static Object merge( Object copyFrom, Object copyInto, Boolean modifyCopyInto ) {
        if (modifyCopyInto==null) modifyCopyInto=false;
        if ( copyFrom instanceof Map && copyInto instanceof Map ) {
            @SuppressWarnings("unchecked")
            Map<Object,Object> fromMap = (Map<Object,Object>) copyFrom;
            @SuppressWarnings("unchecked")
            Map<Object,Object> intoMap = (Map<Object,Object>) copyInto;
            for ( Map.Entry<Object,Object> entry : fromMap.entrySet() ) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if ( intoMap.containsKey(key) ) value = merge( value, intoMap.get(key), modifyCopyInto );
                try {
                    intoMap.put(key,value);
                } catch ( RuntimeException re ) {
                    Map<Object,Object> newIntoMap = new LinkedHashMap<>();
                    newIntoMap.putAll(intoMap);
                    intoMap = newIntoMap;
                    intoMap.put(key,value);
                }
            } return intoMap;
        }
        if ( copyFrom instanceof List && copyInto instanceof List ) {
            @SuppressWarnings("unchecked")
            List<Object> fromList = (List<Object>) copyFrom;
            @SuppressWarnings("unchecked")
            List<Object> intoList = (List<Object>) copyInto;
            for (int i=0; i<fromList.size(); i++) {
                Object value = fromList.get(i);
                if ( i<intoList.size() ) value = merge( value, intoList.get(i), modifyCopyInto );
                try {
                    if ( i< intoList.size() ) { intoList.set(i,value); }
                    else { intoList.add(value); }
                } catch ( RuntimeException re ) {
                    List<Object> newIntoList = new ArrayList<>();
                    newIntoList.addAll(intoList);
                    intoList = newIntoList;
                    if ( i< intoList.size() ) { intoList.set(i,value); }
                    else { intoList.add(value); }
                }
            } return intoList;
        }
        return copyFrom;
    }
    @SuppressWarnings("unused")
    private static boolean merge_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        { // simple map
            Map<Object,Object> srcMap = JsonDecoder.decodeMap("""
                { "one":1, "two":2, "three":3 }
            """);
            Map<Object,Object> tgtMap = JsonDecoder.decodeMap("""
                { "three":0, "four":4, "five":5 }
            """);
            Object result = merge(srcMap,tgtMap,true);
            assert result == tgtMap;
            Map<Object,Object> expected = JsonDecoder.decodeMap("""
                { "one":1, "two":2, "three":3, "four":4, "five":5 }
            """);
            asrtEQ(expected,result);
        }
        { // simple lists
            Object src = JsonDecoder.decode("""
                [1,2,3,4]
            """);
            Object dst = JsonDecoder.decode("""
                [-3,-2]
            """);
            Object result = merge(src,dst,true);
            Object expected = JsonDecoder.decode("""
                [1,2,3,4]
            """);
            asrtEQ(expected,result);
        }
        { // mixture of lists and maps
            Object src = JsonDecoder.decode("""
                { "a":[1,2,3], "b":2, "c":{1:"one",2:"two"} }
            """);
            Object dst = JsonDecoder.decode("""
                { "a":[1,2,3,4], "b":[1,2], "c":{3:"three",1:"ONE"}, "d":4 }
            """);
            Object result = merge(src,dst,true);
            Object expected = JsonDecoder.decode("""
                { "a":[1,2,3,4], "b":2, "c":{3:"three",1:"one",2:"two"}, "d":4 }
            """);
            String expStr = JsonEncoder.encode(expected);
            String resStr = JsonEncoder.encode(result);
            asrtEQ(expStr,resStr);
        }
        return true;
    }



    public static String randToken() {
        return randToken( null, 8, false );
    }
    public static String randToken( String prefix, int suffixLen, boolean digitsOnly  ) {
        if (suffixLen<=0) suffixLen=8;
        final String alpha = digitsOnly ? "0123456789" : "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        int alphaLen = alpha.length();
        Random rand = new Random( System.currentTimeMillis() );
        StringBuilder sb = new StringBuilder();
        if (prefix!=null) sb.append(prefix);
        if (Lib.isEmpty(prefix) && !digitsOnly) {
            sb.append(alpha.charAt( rand.nextInt(26) ));
            suffixLen--;
        }
        while ( suffixLen-- > 0 ) {
            sb.append(alpha.charAt( rand.nextInt(alphaLen) ));
        }
        return sb.toString();
    }
    @SuppressWarnings("unused")
    private static boolean randToken_TEST_() {
        String token = randToken( null, 8, false );
        if ( token.length()!=8 ) throw new RuntimeException("fail len");
        token = randToken( null, 8, true );
        if ( token.length()!=8 ) throw new RuntimeException("fail len");
        token = randToken( "abc", 8, false );
        if ( token.length()!=11 ) throw new RuntimeException("fail len");
        token = randToken( "abc", 8, true );
        if ( token.length()!=11 ) throw new RuntimeException("fail len");
        return true;
    }



    public static String toISO8601( String utcTimestamp ) {
        utcTimestamp = utcTimestamp.replaceAll("[^0-9]","");
        final int NEED_DIGIT_COUNT = 23;
        if ( utcTimestamp.length() < NEED_DIGIT_COUNT ) {
            utcTimestamp = utcTimestamp + "0".repeat( NEED_DIGIT_COUNT - utcTimestamp.length() );
        } else if ( utcTimestamp.length() > NEED_DIGIT_COUNT ) {
            utcTimestamp = utcTimestamp.substring(0,NEED_DIGIT_COUNT);
        }
        utcTimestamp = ( // add the T and everything else Instant.parse will expect
            utcTimestamp.substring(0,4) +"-"+
            utcTimestamp.substring(4,6) +"-"+
            utcTimestamp.substring(6,8) +"T"+
            utcTimestamp.substring(8,10) +":"+
            utcTimestamp.substring(10,12) +":"+
            utcTimestamp.substring(12,14) +"."+
            utcTimestamp.substring(14) +"Z"
        );
        return utcTimestamp;
    }



    public static long microsSinceEpoch( String utcTimestamp ) {
        utcTimestamp = toISO8601(utcTimestamp);
        Instant instant = Instant.parse(utcTimestamp);
        return toMicrosSinceEpoch(instant);
    }
    @SuppressWarnings("unused")
    private static boolean microsSinceEpoch_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        long now = System.currentTimeMillis() * 1000 + 1L;
        long micros = microsSinceEpoch( timeStamp(now) );
        long diff = Math.abs(micros - now);
        asrt(diff==0);
        return true;
    }



    public static long toMicrosSinceEpoch( Instant instant ) {
        return instant.getEpochSecond() * 1000000 + instant.getNano() / 1000;
    }
    @SuppressWarnings("unused")
    private static boolean toMicrosSinceEpoch_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        Instant now = Instant.now();
        long millisSinceEpoch = System.currentTimeMillis();
        long microsSinceEpoch = toMicrosSinceEpoch(now);
        long expected = millisSinceEpoch * 1000;
        long diff = Math.abs(microsSinceEpoch - expected);
        asrt( diff < 2000 ); // i.e. within 2 millis
        return true;
    }






    /*
     * a thread that remembers its parent thread, and can be closed
     */
    @SuppressWarnings("this-escape")
    public static class ChildThread extends Thread implements AutoCloseable {
        { cleaner.register( this, ()->close() ); }
        private Thread parent;
        private String internalName = this.getClass().getSimpleName()+"_"+timeStamp();
        public ChildThread() { parent = Thread.currentThread(); }
        public ChildThread( Runnable r ) {
            super(r);
            parent = Thread.currentThread();
            setName(internalName);
            setDaemon(true);
        }
        @Override
        public void start() {
            parent = Thread.currentThread();
            super.start();
        }
        public Thread getParent() { return parent; }
        @Override public void close() { interrupt(); }
        public String toString() { return getName(); }
        public int hashCode() { return internalName.hashCode(); }
        public boolean equals( Object o ) { return o==this; }
    }









    public static String urlEncode( Map<String,Object> map ) {
        StringBuilder sb = new StringBuilder();
        for ( Map.Entry<String,Object> entry : map.entrySet() ) {
            if ( sb.length() > 0 ) sb.append("&");
            String k = entry.getKey();
            Object v = entry.getValue();
            if (v==null) continue;
            sb.append( Lib.urlEncode(k,false) );
            sb.append('=');
            sb.append( Lib.urlEncode(v.toString(),false) );
        }
        return sb.toString();
    }
    public static String urlEncode( Object obj ) {
        return urlEncode(obj,null);
    }
    public static String urlEncode( Object obj, Boolean encodeAll ) {
        if (obj==null) return "";
        if ( obj instanceof Number ) return urlEncode( obj.toString(), encodeAll );
        if ( obj instanceof char[] cArr ) return urlEncode( new String(cArr), encodeAll );
        if ( obj instanceof byte[] bArr ) return urlEncode( new String(bArr), encodeAll );
        String inputStr = obj.toString();
        final String HEX_ALPHA = "0123456789ABCDEF";
        String safeAlpha = (
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "abcdefghijklmnopqrstuvwxyz" +
            "0123456789"
        );
        if ( encodeAll != null ) {
            if (encodeAll) {
                safeAlpha = "";
            } else {
                safeAlpha += (
                    " _*.-" + // allegedly not changed by urlEncoding
                    "!\"#$&'(),/:;<=>?@[\\]^`{|}~"
                );
            }
        }
        StringBuilder sb = new StringBuilder();
        for ( int i=0; i<inputStr.length(); i++ ) {
            char c = inputStr.charAt(i);
            if ( safeAlpha.indexOf(c) >= 0 ) {
                sb.append(c);
            } else {
                if (c>255) {
                    sb.append(URLEncoder.encode( ""+c, StandardCharsets.UTF_8 ));
                } else {
                    sb.append( "%" );
                    sb.append( HEX_ALPHA.charAt( (c>>4) & 0xF ) );
                    sb.append( HEX_ALPHA.charAt( c & 0xF ) );
                }
            }
        }
        return sb.toString();
    }
    public static String urlDecode( String s ) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8 );
    }
    @SuppressWarnings("unused")
    private static boolean urlEncode_TEST_() {
        String s = "Crunchwrap Supreme� (Beef or Spicy Chicken) + tortilla chips";
        asrt( s.equals(
            URLDecoder.decode( URLEncoder.encode(s,StandardCharsets.UTF_8), StandardCharsets.UTF_8 )
        ) );
        String encodedA = urlEncode(s,null);
        String encodedB = urlEncode(s,true);
        String encodedC = urlEncode(s,false);
        asrtEQ( urlDecode(encodedA), s );
        asrtEQ( urlDecode(encodedB), s );
        asrtEQ( urlDecode(encodedC), s );
        return true;
    }



    public static InputStream multicast( InputStream inp, OutputStream... streams ) {
        return new MultiplexedInputStream(inp,streams);
    }
    public static OutputStream multicast( OutputStream... streams ) {
        return new MultiplexedOutputStream(streams);
    }
    @SuppressWarnings("unused")
    private static boolean multicast_TEST_() throws Exception {
        ByteArrayOutputStream baosA = new ByteArrayOutputStream();
        ByteArrayOutputStream baosB = new ByteArrayOutputStream();
        String s = "Hello world!";
        OutputStream os = multicast(baosA,baosB);
        os.write( s.getBytes() );
        os.flush();
        os.close();
        Lib.asrtEQ( s, new String( baosA.toByteArray() ) );
        baosA.reset();
        baosB.reset();
        InputStream inp = multicast( new ByteArrayInputStream( s.getBytes() ), baosA, baosB );
        byte[] buf = new byte[1024];
        int len = inp.read(buf);
        Lib.asrt( len == s.length() );
        Lib.asrtEQ( s, new String( baosB.toByteArray() ) );
        return true;
    }



    public static class MultiplexedOutputStream extends OutputStream {
        public final List<OutputStream> outs = Collections.synchronizedList( new LinkedList<>() );
        public MultiplexedOutputStream( OutputStream... outs ) {
            for ( OutputStream out : outs ) if(out!=null)this.outs.add(out);
        }
        @Override public void write( byte[] bArr, int off, int len ) {
            synchronized (outs) {
                for ( OutputStream out : outs ) {
                    try{ out.write(bArr,off,len); }catch(Throwable t){}
                }
            }
        }
        @Override public void write( byte[] bArr ) {
            write( bArr, 0, bArr.length );
        }
        @Override public void write( int b ) {
            byte[] bArr = { (byte)b };
            write(bArr,0,1);
        }
        @Override public void flush() {
            synchronized (outs) {
                for ( OutputStream out : outs ) {
                    try{ out.flush(); }catch(Throwable t){}
                }
            }
        }
        @Override public void close() {
            synchronized (outs) {
                for ( OutputStream out : outs ) {
                    try{ out.close(); }catch(Throwable t){}
                }
            }
        }
    }



    public static class MultiplexedInputStream extends InputStream {
        public InputStream inp;
        public final MultiplexedOutputStream outs;
        public MultiplexedInputStream( InputStream inp, OutputStream... outs ) {
            this.inp = inp;
            this.outs = new MultiplexedOutputStream(outs);
        }
        @Override public int read( byte[] b, int off, int len ) throws IOException {
            int n = inp.read(b,off,len);
            outs.write(b,off,n);
            return n;
        }
        @Override public int read( byte[] b ) throws IOException {
            return read( b, 0, b.length );
        }
        @Override public int read() throws IOException {
            byte[] bArr = new byte[1];
            int byteCount = read(bArr,0,1);
            if (byteCount<0) return -1;
            return bArr[0];
        }
        @Override public void close() throws IOException {
            inp.close();
            outs.close();
        }
        @Override public int available() throws IOException { return inp.available(); }
        @Override public void mark( int readlimit ) { throw new UnsupportedOperationException(); }
        @Override public void reset() throws IOException { throw new UnsupportedOperationException(); }
        @Override public boolean markSupported() { return false; }
        @Override public long skip( long n ) throws IOException { throw new UnsupportedOperationException(); }
    }



    /**
     * Creates a new full-path filename with a timestamp prefix or suffix.
     * Replaces the timestamp if it already exists in the filename.
     * Can also remove the timestamp via undo
    **/



    public static String getRemoteAddr( Socket sock ) {
        InetAddress inetAddress = sock.getInetAddress();
        String ip = inetAddress.getHostAddress();
        if (inetAddress instanceof Inet6Address) {
            byte[] addr = inetAddress.getAddress();
            if (addr.length == 16) {
                boolean isIPv4Mapped = true;
                for (int i = 0; i < 10; i++) {
                    if (addr[i] != 0) { isIPv4Mapped = false; break; }
                }
                if (isIPv4Mapped && addr[10] == (byte)0xff && addr[11] == (byte)0xff) {
                    ip = (addr[12] & 0xff) + "." + (addr[13] & 0xff) + "." +
                         (addr[14] & 0xff) + "." + (addr[15] & 0xff);
                }
            }
        }
        return ip;
    }



    /**
     * NOTE: This will return true if either given path is the parent of the other.
     */
    public static boolean isParentChildPath( String pathA, String pathB ) {
        if ( pathA==null || pathB==null ) return false;
        String normA = normalizePath(pathA);
        String normB = normalizePath(pathB);
        if (! normA.endsWith("/") ) normA += "/";
        if (! normB.endsWith("/") ) normB += "/";
        return normA.startsWith(normB) || normB.startsWith(normA);
    }
    @SuppressWarnings("unused")
    private static boolean isParentChildPath_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        asrt( isParentChildPath("a/b/c","a/b/c/d") );
        asrt( isParentChildPath("a/b\\c/d","a/b//c") );
        asrt( isParentChildPath("a/b/c","a/b/c") );
        asrt( isParentChildPath("/a/b/c","\\a/b/c/..") );
        asrt( isParentChildPath("a/b/./c/..","a/b/c") );
        asrt(! isParentChildPath("a/b/c","a/b/c/../d") );
        asrt( isParentChildPath("a/b/c/../d","a/b/d") );
        asrt(! isParentChildPath("a/b/c","a/b/c/../../d") );
        return true;
    }



    public static byte[] toBytes( InputStream inp, boolean close ) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(inp,out);
            return out.toByteArray();
        } finally {
            if (close) try{ inp.close(); }catch(Exception ignore){}
        }
    }



    public static class ReaderInputStream extends InputStream {
        private final Reader r;
        private Charset cs = StandardCharsets.UTF_8;
        InputStream saved = new ByteArrayInputStream( new byte[0] );

        public ReaderInputStream( Reader r ) {
            this.r = r;
        }
        public ReaderInputStream( Reader r, Charset cs ) {
            this.r = r;
            this.cs = cs;
        }

        @Override
        public int read() throws IOException {
            if ( saved.available() > 0 ) return saved.read();
            int c = r.read();
            if (c<0) return -1;
            String s = ""+(char)c;
            byte[] bArr = s.getBytes(cs);
            if ( bArr.length > 1 ) {
                saved = new ByteArrayInputStream(bArr);
                return saved.read();
            }
            return bArr[0];
        }

    }
    @SuppressWarnings("unused")
    private static boolean readerInputStream_TEST_() throws Exception {
        String testString = (
            "0¢£¤¥¦§¨©ª«¬®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ"
        );
        Reader r = new StringReader(testString);
        InputStream inp = new ReaderInputStream(r);
        byte[] bArr = toBytes(inp,true);
        String result = new String( bArr, StandardCharsets.UTF_8 );
        asrtEQ(result,testString);
        return true;
    }



    public static class WriterOutputStream extends OutputStream {
        private final Writer w;
        private final Charset cs;
        ByteArrayOutputStream saved = new ByteArrayOutputStream();

        public WriterOutputStream( Writer w ) {
            this(w,null);
        }
        public WriterOutputStream( Writer writer, Charset cs ) {
            if (cs==null) cs = StandardCharsets.UTF_8;
            this.w = writer;
            this.cs = cs;
        }
        @Override
        public void write(int b) throws IOException {
            saved.write(b);
            attemptDecode();
        }
        @Override
        public void flush() throws IOException {
            w.flush();
        }
        @Override
        public void close() throws IOException {
            for (byte b : saved.toByteArray() ) w.write(b);
            w.flush();
            w.close();
        }
        private static char[] decode( byte[] bArr, Charset cs ) {
            if ( bArr==null || bArr.length==0 ) return new char[0];
            String s = new String(bArr,cs);
            if ( s==null || s.length()==0 ) return new char[0]; // probably not possible
            StringBuilder sb = new StringBuilder();
            final char invalidChar = '�';
            // copy all but invalid characters
            for (int i=0; i<s.length(); i++) {
                char c = s.charAt(i);
                if (c==invalidChar) continue;
                sb.append(c);
            }
            return sb.toString().toCharArray();
        }

        private void attemptDecode() throws IOException {
            if ( saved.size() <= 0 ) return;
            byte[] bArr = saved.toByteArray();
            char[] cArr = decode(bArr,cs);
            if (cArr.length==0) return;
            saved.reset();
            w.write(cArr);
        }
    }
    @SuppressWarnings("unused")
    private static boolean writerOutputStream_TEST_() throws Exception {
        String testString = (
            "0¢£¤¥¦§¨©ª«¬®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ"
        );
        StringWriter sw = new StringWriter();
        OutputStream os = new WriterOutputStream(sw);
        byte[] testBytes = testString.getBytes(StandardCharsets.UTF_8);
        for (byte b : testBytes) os.write(b);
        os.close();
        String result = sw.toString();
        asrtEQ(result,testString);
        return true;
    }



    public static String getAppName() { return findAppClassName().replaceAll( ".*\\.","" ); }
    public static File getAppDir() { return getAppDir(null); }
    public static File getAppDir( String appName ) {
        return new File(".");
        /*
        if (appName==null) appName = getAppName();
        File f = new File(
            System.getProperty("user.home"),
            ".appdata"
        );
        f = new File( f, appName.replaceAll( "[/\\\\:*?\"<>|\\x00-\\x1F\\x7F]+", "_" ) );
        f.mkdirs();
        return f;
        */
    }
    public static String findAppClassName() {
        // uses a stack trace to find the non-library class that called any methods that called this method
        // returns null if this doesn't work
        // caches the first non-null result
        synchronized(_appClassName) {
            String appClassName = _appClassName.get();
            if (appClassName!=null) return appClassName;
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            for ( int i=st.length-1; i>=0; i-- ) {
                String c = st[i].getClassName();
                if ( c.startsWith("java.") ) continue;
                if ( c.startsWith("javax.") ) continue;
                if ( c.startsWith("sun.") ) continue;
                if ( c.startsWith("com.sun.") ) continue;
                appClassName = st[i].getClassName();
                _appClassName.set(appClassName);
                return appClassName;
            }
            return null;
        }
    }
    public static AtomicReference<String> _appClassName = new AtomicReference<>();









    public static boolean isEmail( String s ) {
        if (s==null) return false;
        return s.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");
    }



    public static char[] readFully( Reader r ) {
        if (r==null) return null;
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int len;
            while ( (len=r.read(buf)) > 0 ) sb.append(buf,0,len);
            return sb.toString().toCharArray();
        } catch ( Throwable t ) {
            log(t);
            return null;
        }
    }
    public static byte[] readFully( InputStream inp ) {
        if (inp==null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ( (len=inp.read(buf)) > 0 ) baos.write(buf,0,len);
            return baos.toByteArray();
        } catch ( Throwable t ) {
            log(t);
            return null;
        }
    }



    public static boolean logOnce( Object o ) { return logOnce( o, 5000 ); }
    public static boolean logOnce( Object o, long perMillis ) { return logOnce( null, o, perMillis ); }
    /**
     * Logs a message, but only -- at most -- once per specified time period.
     * Useful for logging potentially over-frequent messages.
     *
     * @return true if the message was logged, false if it was suppressed
     *
     * WARNING: a cache entry per msgID is stored for up to an hour,
     *          so do not use a large number of unique msgIDs.
     * NOTE: perMillis over one hour is treated as one hour,
     *       so we don't need to store more than an hour of entries.
    **/
    public static boolean logOnce( String msgID, Object msg, long perMillis ) {
        if (msgID==null) msgID="";
        if ( perMillis < 1 ) perMillis = 5000;
        if (msg==null) msg=msgID;
        String msgTxt = msg.toString();
        boolean didLog = false;
        long now = System.currentTimeMillis();
        Long lastLogTime = _logOnceCache.get(msgID);
        if (lastLogTime==null) {
            lastLogTime = 0L;
        } else {
            //System.out.println("DEBUG");
        }
        if ( now - lastLogTime > perMillis ) {
            didLog = true;
            log(msgTxt);
            _logOnceCache.put(msgID,now);
        }
        return didLog;
    }
    public static LruCache<String,Long> _logOnceCache = new LruCache<>( -1, 1000*60*60, false );
    @SuppressWarnings("unused")
    private static boolean logOnce_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        asrt(  logOnce( "testing logOnce", 500 ) ); // first time should work
        asrt(! logOnce( "testing logOnce", 500 ) ); // second time should be suppressed
        try{ Thread.sleep(501); }catch(InterruptedException ignore){}
        asrt( logOnce( "testing logOnce", 500 ) ); // after wait for message to expire, should work again
        return true;
    }



    public static long copy( Reader inp, Writer... outs )
        throws IOException
    {
        long totalCount = 0;
        char[] buf = new char[8192];
        int count = -1;
        try {
            count = inp.read(buf);
            if (count>0) totalCount+=count;
        } catch ( Throwable t ) {}
        while (count>=0) {
            if (count==0) {
                try { Thread.sleep(100); } catch ( InterruptedException ie ) {}
            } else {
                for (Writer out:outs) if(out!=null) out.write(buf,0,count);
            }
            count = inp.read(buf);
        }
        for (Writer out:outs) if(out!=null) out.flush();
        return totalCount;
    }
    public static long copy( InputStream inp, OutputStream... outs )
        throws IOException
    {
        int MAX_BUF_SIZE = 32*1024;
        long totalCount = 0;
        byte[] buf = new byte[512];
        while (true) {
            long nanoTimer = System.nanoTime();
            int count = inp.read(buf);
            nanoTimer = System.nanoTime() - nanoTimer;
            if ( count>0 && nanoTimer > count*1000*1000 ) { // more than 1 milli per byte
                long millis = nanoTimer / (1000*1000);
                if (millis>1000)logOnce(
                    "Lib.copy_slowRead", "copy() took "+millis+" millis to read "+count+" bytes", 5000
                );
            }
            if (count<0) break;
            if (count==0) {
                try {
                    logOnce( "copy() read 0 bytes, sleeping" );
                    Thread.sleep(100);
                } catch ( InterruptedException ie ) {}
                continue;
            }
            nanoTimer = System.nanoTime();
            for (OutputStream out : outs) if(out!=null) out.write(buf,0,count);
            nanoTimer = System.nanoTime() - nanoTimer;
            if ( count>0 && nanoTimer > count*1000*1000 ) { // more than 1 milli per byte
                long millis = nanoTimer / (1000*1000);
                if (millis>1000)logOnce(
                    "Lib.copy_slowWrite", "copy() took "+millis+" millis to write "+count+" bytes", 5000
                );
            }
            totalCount += count;
            if (count==buf.length && buf.length<MAX_BUF_SIZE) {
                buf = new byte[buf.length*2];
            }
        }
        long nanoTimer = System.nanoTime();
        for (OutputStream out : outs) if(out!=null) out.flush();
        nanoTimer = System.nanoTime() - nanoTimer;
        if ( nanoTimer > 500*1000*1000 ) {
            if ( logOnce("copy() took > 500 millis to flush") ) {
                long millis = nanoTimer / (1000*1000);
                log( "copy() took "+millis+" millis to flush" );
            }
        }
        return totalCount;
    }
    @SuppressWarnings("unused")
    private static boolean copy_slow_TEST_() throws Exception {
        // create a fake OutputStream to similuate a slow write
        OutputStream slowOut = new OutputStream() {
            public void write( int b ) throws IOException {
                try { Thread.sleep(1); } catch ( InterruptedException ie ) {}
            }
        };
        byte[] bArr = new byte[1000];
        for (int i=0; i<bArr.length; i++) bArr[i] = (byte)i;
        InputStream inp = new ByteArrayInputStream(bArr);
        copy(inp,slowOut);
        asrt(!( // one of these messages should have been logged already
            logOnce( "", "Lib.copy_slowRead", 5000 ) &&
            logOnce( "", "Lib.copy_slowWrite", 5000 )
        ));
        return true;
    }



    public static InputStream toInputStream( Object o ) throws IOException {
        if (o==null) o="";
        if ( o instanceof URL url ) return url.openStream();
        if ( o instanceof byte[] bArr ) return new ByteArrayInputStream(bArr);
        if ( o instanceof InputStream ) return (InputStream)o;
        if ( o instanceof Reader r ) return new ReaderInputStream(r);
        if ( o instanceof File ) {
            File f = (File)o;
            if ( f.isFile() ) return new FileInputStream(f);
            o = "";
        }
        if ( o instanceof CharSequence s ) return new ByteArrayInputStream( s.toString().getBytes() );
        return toInputStream( JsonEncoder.encode(o) );
    }



    /**
    * Appends to the given file in a reasonably synchronized way. Not fast.
    **/
    public static Throwable append2file( File f, Object data ) {
        Closeable toClose = null;
        try {
            String canonicalPath = f.getCanonicalPath();
            synchronized ( canonicalPath.intern() ) {
                try (
                    FileOutputStream fout = new FileOutputStream(f,true);
                    BufferedOutputStream bout = new BufferedOutputStream(fout);
                ) {
                    InputStream inp = null;
                    if ( data instanceof InputStream dataInp ) {
                        inp = dataInp;
                    } else {
                        inp = toInputStream(data);
                        toClose = inp;
                    }
                    copy(inp,bout);
                    bout.flush();
                    fout.flush();
                }
            }
            return null;
        } catch ( Throwable t ) {
            return t;
        } finally {
            if (toClose!=null) {
                try{ toClose.close(); }catch(Throwable ignore){}
            }
        }
    }
    @SuppressWarnings("unused")
    private static boolean append2file_TEST_() throws IOException {
        File f = File.createTempFile( "append2file_TEST_", ".tmp" );
        f.deleteOnExit();
        Throwable t = append2file(f,"Hello, world!");
        if (t!=null) throw new RuntimeException(t);
        t = append2file(f," Goodbye.");
        if (t!=null) throw new RuntimeException(t);
        // asrtEQ( "Hello, world! Goodbye.", file2string(f,null) ); // file2string removed
        asrt( f.delete() );
        return true;
    }




    public static boolean isText( byte[] bArr ) {
        if ( Lib.isEmpty(bArr) ) return true; // empty file can be processed as text
        for (int i=0; i<bArr.length; i++) {
            byte b = bArr[i];
            if ( b>126 || ( b<32 && b!=9 && b!=10 && b!=13 ) ) break;
            if ( i == bArr.length-1 ) return true; // all usASCII7
        }
        try { // try to decode as with platform default character encoding
              // ^ (always UTF-8 in recent versions of java, unless deliberately configured otherwise)
            String s = new String(bArr);
            if ( Lib.isEmpty(s) ) return false; // bytes in, nothing out: not normal text
        } catch (Throwable t) {
            return false;
        }
        return true; // if we get this far, it can at least pass for text
    }
    public static boolean isBinary( byte[] bArr ) {
        return ! isText(bArr);
    }



    public static String toString( byte[] bArr, int off, int len ) {
        if (bArr==null) return "";
        len = Math.min( len, bArr.length-off );
        byte[] b = new byte[len];
        System.arraycopy( bArr,off, b,0, len );
        return JsonEncoder.encode(b);
    }
    public static String toString( Object o ) {
        if (o==null) return "";
        if ( o instanceof InputStream inp ) {
            try { inp.mark( Integer.MAX_VALUE ); }catch(Throwable ignore){}
            try {
                byte[] bArr = readFully(inp);
                try { inp.reset(); }catch(Throwable ignore){}
                return toString(bArr);
            } catch (Throwable t) {
                return toString(t);
            }
        }
        try {
            if ( o instanceof byte[] b ) {
                if ( isBinary(b) ) return toString(b,0,b.length);
                o = new String(b);
            }
            return JsonEncoder.encode(o," ");
        } catch (Throwable t) {
            return ""+o;
        }
    }



    public static Object logException( Object msg ) {
        RuntimeException re = msg instanceof RuntimeException r ? r : null;
        String s = msg instanceof String str ? str : toString(msg);
        try { if (re==null) throw new RuntimeException(s); } catch ( RuntimeException e ) { re = e; }
        log(re);
        return msg;
    }
    public static Object log( Object o ) {
        try {
            String msg = o==null ? "null" : (o instanceof Throwable t) ? formatException(t) : JsonEncoder.encode(o);
            msg = timeStamp() + "@" + Thread.currentThread().getName() + ": " + msg;
            System.err.println(msg);
            Lib.append2file( new File( "./log/"+Lib.getAppName()+".log" ), msg+"\n" );
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return o;
    }



    @SuppressWarnings({"rawtypes","unchecked"})
    public static <T> Iterable<T> asIterable( Iterator<T> it ) {
        return new Iterable() { public Iterator iterator() { return it; } };
    }
    @SuppressWarnings("unused")
    private static boolean asIterable_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Iterator<?> it = List.of( "whatev" ).iterator();
        Iterable<?> itbl = asIterable(it);
        for ( Object o : itbl ) asrtEQ( "whatev", o );
        return true;
    }



    public static String thisMethodName() {
        List<String> stackTrace = getStackTrace();
        String mostRecentCall = stackTrace.get(0);
        String methodName = mostRecentCall.replaceAll( "(.*)\\.([^\\.]*)\\:(.*)", "$2" );
        return methodName;
    }
    public static String thisClassName() {
        List<String> stackTrace = getStackTrace();
        String mostRecentCall = stackTrace.get(0);
        String className = mostRecentCall.replaceAll( "(.*)\\.([^\\.]*)\\:(.*)", "$1" );
        return className;
    }
    public static List<String> getStackTrace() {
        return getStackTrace(null,true,0);
    }
    public static List<String> getStackTrace( boolean removeLibraryCalls ) {
        return getStackTrace(null,removeLibraryCalls,0);
    }
    public static List<String> getStackTrace( Throwable t, boolean removeLibraryCalls ) {
        return getStackTrace(t,removeLibraryCalls,0);
    }
    public static List<String> getStackTrace( Throwable t, boolean removeLibraryCalls, int skipRecent ) {
        StackTraceElement[] stes = t==null ? Thread.currentThread().getStackTrace() : t.getStackTrace();
        ArrayList<String> traceList = new ArrayList<>(50);
        String thisClassName = Lib.class.getName();
        for (int i=0; i<stes.length; i++) {
            StackTraceElement ste = stes[i];
            String clas = ste.getClassName();
            if ( removeLibraryCalls && (
                clas.startsWith(thisClassName) ||
                clas.startsWith("jdk.") ||
                clas.startsWith("java.") ||
                clas.startsWith("javax.") ||
                clas.startsWith("sun.") ||
                clas.startsWith("org.apache.") ||
                clas.startsWith("net.sourceforge.") ||
                clas.startsWith("oracle.") ||
                clas.startsWith("org.sqlite.") ||
                clas.startsWith("bsh.")
            )) continue;
            if ( (--skipRecent) >= 0 ) continue;
            traceList.add(
                ste.getClassName()+'.'+ste.getMethodName()+":"+ste.getLineNumber()
            );
        }
        traceList.trimToSize();
        return traceList;
    }



    public static boolean isPortListening( int port ) {
        return isPortListening("localhost", port);
    }
    public static boolean isPortListening( String  urlStr ) {
        // I wish this arg could be a proper java.net.URL, but it doesn't know how to handle default ports
        URL url = new Url(urlStr).url;
        int port = url.getPort();
        if (port<=0) port = url.getDefaultPort();
        return isPortListening( url.getHost(), port );
    }
    public static boolean isPortListening( String host, int port ) {
        try {
            Socket s = new Socket(host,port);
            s.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }



    private static final Map<File, FileLock> activeLocks = new ConcurrentHashMap<>();

    /**
     * Checks if another instance is already running using a lock file.
     * If not running, acquires lock and returns false.
     * If already running, returns true.
     * The lock is automatically released when the process exits.
     */
    public static boolean alreadyRunning( File lockFile ) {
        if ( lockFile==null ) return false;
        try {
            // Ensure parent directory exists
            File parentDir = lockFile.getParentFile();
            if ( parentDir!=null && !parentDir.exists() ) parentDir.mkdirs();

            // Open channel for the lock file
            RandomAccessFile raf = new RandomAccessFile( lockFile, "rw" );
            FileChannel channel = raf.getChannel();

            // Try to acquire exclusive lock
            FileLock lock = channel.tryLock();

            if ( lock==null ) {
                // Lock is held by another process
                channel.close();
                raf.close();
                return true;
            }

            // We got the lock - store it to keep it alive
            activeLocks.put( lockFile, lock );

            // Register cleanup on JVM shutdown
            cleaner.register( lockFile, () -> {
                FileLock storedLock = activeLocks.remove( lockFile );
                if ( storedLock!=null ) {
                    try { storedLock.release(); } catch ( Exception ignore ) {}
                    try { storedLock.channel().close(); } catch ( Exception ignore ) {}
                }
            });

            return false;
        } catch ( Exception e ) {
            Lib.log( "Error checking lock file: " + e.getMessage() );
            return false;
        }
    }



    public static String evalUrlTemplate( String template, Map<?,?> vars ) {
        return evalTemplate(template,"\\$","\\$",vars);
    }
    public static String evalTemplate( File templateFile, Map<?,?> vars ) throws IOException {
        // Read file contents inline since file2string was removed
        char[] cArr = new char[8192];
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fInp = new FileInputStream(templateFile);
             InputStreamReader rInp = new InputStreamReader(fInp);
             BufferedReader bInp = new BufferedReader(rInp)) {
            while (true) {
                int readCount = bInp.read(cArr);
                if (readCount<0) break;
                if (readCount>0) sb.append(cArr, 0, readCount);
            }
        }
        return evalTemplate( sb.toString(), vars );
    }
    public static String evalTemplate( String template, Map<?,?> vars ) {
        return evalTemplate(template,"\\{\\{\\s*","\\s*\\}\\}",vars);
    }
    public static String evalTemplate( String template, String openRegex, String closeRegex, Map<?,?> vars ) {
        if (template==null) return null;
        if (vars==null) return template;
        Matcher mat = null;
        try {
            mat = Pattern.compile(
                openRegex +"\\s*(.*?)\\s*"+ closeRegex
            ).matcher(template);
        } catch ( PatternSyntaxException e ) {
            System.err.println( formatException(e) );
            return template;
        }
        ScriptEngineManager manager = null;
        ScriptEngine engine = null;
        StringBuffer result = new StringBuffer();
        Map<String,Object> normalVars = new LinkedHashMap<>();
        for ( Map.Entry<?,?> entry : vars.entrySet() ) {
            Object key = entry.getKey();
            if (key==null) continue;
            Object val = entry.getValue();
            normalVars.put( key.toString(), val );
        }
        while ( mat.find() ) {
            //String wholeExpression = mat.group();
            String varName = mat.group(1);
            String keyName = findKey(varName,normalVars);
            Object value = null;
            if ( keyName!=null && vars.containsKey(keyName) ) {
                value = vars.get(keyName);
            } else {
                if (manager==null) {
                    manager = new ScriptEngineManager();
                    engine = manager.getEngineByName("js");
                    if ( engine==null ) {
                        List<ScriptEngineFactory> factories = manager.getEngineFactories();
                        for (ScriptEngineFactory factory : factories) {
                            engine = factory.getScriptEngine();
                            break;
                        }
                    }
                    if (engine!=null) {
                        Bindings bindings = engine.createBindings();
                        for( Map.Entry<String,Object> entry : normalVars.entrySet() ) {
                            bindings.put(entry.getKey(), entry.getValue());
                        }
                        engine.setBindings( bindings, ScriptContext.ENGINE_SCOPE );
                    }
                }
                try {
                    value = engine==null ? null : engine.eval(varName);
                } catch ( ScriptException e ) {
                    System.err.println( formatException(e) );
                }
            }
            if (value==null) value="";
            mat.appendReplacement( result, Matcher.quoteReplacement( value.toString() ) );
        }
        mat.appendTail(result);
        return result.toString();
    }
    @SuppressWarnings("unused")
    private static boolean evalTemplate_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        { // mustache-like
            String template = "The {{ NOUN }} in {{ COUNTRY }} stays mainly on the {{ pLaCe }}.";
            Map<String,Object> map = new LinkedHashMap<>();
            map.put("nOuN","rain");
            map.put("country","Spain");
            map.put("place","plain");
            String result = evalTemplate(template,map);
            String expected = "The rain in Spain stays mainly on the plain.";
            Lib.asrt(result.equals(expected), "evalTemplate jsp-like test failed");
        }
        { // url-safe
            String template = "The $NOUN$ in $cOuNtrY$ stays mainly on the $PLACE$.";
            Map<String,Object> map = new LinkedHashMap<>();
            map.put("noun","rain");
            map.put("country","Spain");
            map.put("PLACE","plain");
            String result = evalUrlTemplate(template,map);
            String expected = "The rain in Spain stays mainly on the plain.";
            Lib.asrt(result.equals(expected), "evalUrlTemplate test failed");
        }
        return true;
    }



    public static String formatException( String msg ) {
        try { throw new RuntimeException(msg); } catch ( Throwable t ) { return formatException(t); }
    }
    public static String formatException( Throwable t ) {
        t = getRootCause(t);
        String s = getStackTrace(t,true).toString();
        s = s.replaceAll("\\s","");
        s = t.getClass().getSimpleName() +":"+ t.getMessage() +" @"+ s;
        return s;
    }
    public static Throwable getRootCause( Throwable t ) {
        if (t==null) return null;
        Throwable t2 = t.getCause();
        if ( t2!=null && t2!=t ) return getRootCause(t2);
        return t;
    }



    @SuppressWarnings({"rawtypes","unchecked"})
    public static Object put( Object data, Collection keys, Object value ) {
        LinkedList keyList = new LinkedList(keys);
        while (! keys.isEmpty() ) {
            Object key = keyList.removeFirst();
            if ( data instanceof Map m ) {
                if ( keyList.isEmpty() ) return m.put(key,value);
                if ( m.containsKey(key) ) {
                    data = m.get(key);
                } else {
                    if ( keyList.get(0) instanceof Integer ) {
                        data = new ArrayList();
                        m.put(key,data);
                    } else {
                        data = new LinkedHashMap();
                        m.put(key,data);
                        data = m.get(key); // in case put did something tricky, as with persistent Maps
                    }
                }
            } else
            if ( data instanceof List lst ) {
                Integer index = null;
                String s = key.toString();
                index = Integer.parseInt(s);
                if ( index < 0 ) index = lst.size() + index;
                while ( index >= lst.size() ) lst.add(null);
                if ( keyList.isEmpty() ) return lst.set(index,value);
                data = lst.get(index);
                if (data==null) {
                    data = ( keyList.get(0) instanceof Integer ) ? new ArrayList() : new LinkedHashMap<>();
                    lst.set(index,data);
                    data = lst.get(index); // in case set did something tricky, as with persistent Lists
                }
            } else {
                throw new RuntimeException( "can't put "+keys+" into "+data );
            }
        }
        throw new RuntimeException();
    }
    @SuppressWarnings("unused")
    private static boolean put_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Object tree = new LinkedHashMap<Object,Object>();
        { // build the tree
            put( tree, List.of("a",2,"c"), "ok" );
            String result = JsonEncoder.encode(tree);
            String expected = JsonEncoder.encode( JsonDecoder.decode( """
                { "a" : [ null, null, {"c":"ok"} ] }
            """ ) );
            Lib.asrtEQ(result,expected);
        }
        { // modify the tree
            Object wasVal = put(
                tree, List.of("a",2,"c"),
                new LinkedHashMap<Object,Object>( Map.of("whatev",3) )
            );
            Lib.asrtEQ( wasVal, "ok" );
            String result = JsonEncoder.encode(tree);
            String expected = JsonEncoder.encode( JsonDecoder.decode( """
                { "a" : [ null, null, {"c":{"whatev":3}} ] }
            """ ) );
            Lib.asrtEQ(result,expected);
        }
        return true;
    }



    public static Long toLong( Object o ) {
        if ( o instanceof Long l0ng ) return l0ng;
        if ( o instanceof Number n ) return n.longValue();
        if ( o instanceof CharSequence s ) {
            try {
                return Long.parseLong( s.toString() );
            } catch ( NumberFormatException ignore ) {}
        }
        return null;
    }
    public static Float toFloat( Object o ) {
        if ( o instanceof Float f ) return f;
        if ( o instanceof Number n ) return n.floatValue();
        if ( o instanceof CharSequence s ) {
            try {
                return Float.parseFloat( s.toString() );
            } catch ( NumberFormatException ignore ) {}
        }
        return null;
    }
    public static Double toDouble( Object o ) {
        if ( o instanceof Double d ) return d;
        if ( o instanceof Number n ) return n.doubleValue();
        if ( o instanceof CharSequence s ) {
            try {
                return Double.parseDouble( s.toString() );
            } catch ( NumberFormatException ignore ) {}
        }
        return null;
    }
    public static Integer toInteger( Object o ) {
        if ( o instanceof Integer i ) return i;
        if ( o instanceof Number n ) return n.intValue();
        if ( o instanceof CharSequence s ) {
            try {
                return Integer.parseInt( s.toString() );
            } catch ( NumberFormatException ignore ) {}
        }
        return null;
    }



    /**
     * Uses PBEKeySpec to hash a password.
     * Returns a 3-part string, separated by ":"
     * 1) The number of hashing iterations that were performed, in base 36
     * 2) The salt used, in base 64
     * 3) The hashed password, in base 64
     * minStrengthMillis is not exactly a "strength" per se,
     * but requires a number of hashing iterations that takes at least this many milliseconds to complete.
    **/
    public static String hashPassword( String password, int minStrengthMillis ) {
        class InnerMethods {
            private static byte[] hashPassword(char[] password, byte[] salt, int iterations)
                throws NoSuchAlgorithmException, java.security.spec.InvalidKeySpecException
            {
                PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 64 * 8);
                SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
                return skf.generateSecret(spec).getEncoded();
            }
            private static byte[] getSalt() throws NoSuchAlgorithmException {
                SecureRandom sr = SecureRandom.getInstanceStrong();
                byte[] salt = new byte[16];
                sr.nextBytes(salt);
                return salt;
            }
        }
		try {
			byte[] salt = InnerMethods.getSalt();
			char[] chars = password.toCharArray();
			byte[] hash;
			int iterations = 512;
			while (true) {
				long startTime = System.currentTimeMillis();
				hash = InnerMethods.hashPassword(chars, salt, iterations);
				if ( System.currentTimeMillis()-startTime > minStrengthMillis ) break;
				iterations += ( iterations / 2 );
			}
			String saltBase64 = Base64.getEncoder().encodeToString(salt);
			String hashBase64 = Base64.getEncoder().encodeToString(hash);
			return Integer.toString(iterations, 36) +":"+ saltBase64 +":"+ hashBase64;
		} catch ( Throwable t ) {
			if ( t instanceof RuntimeException ) throw (RuntimeException)t;
			throw new RuntimeException(t);
		}
    }
    public static String hashPassword( String password ) { return hashPassword(password,100); }
    public static boolean verifyPassword( String password, String hashedPassword ) {
        if (password==null) return false;
		try {
			String[] parts = hashedPassword.split(":");
			int iterations = Integer.parseInt(parts[0], 36);
			byte[] salt = Base64.getDecoder().decode(parts[1]);
			byte[] storedHash = Base64.getDecoder().decode(parts[2]);
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, storedHash.length * 8);
			SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			byte[] testHash = skf.generateSecret(spec).getEncoded();
			int diff = storedHash.length ^ testHash.length;
			for (int i = 0; i < storedHash.length && i < testHash.length; i++) {
				diff |= storedHash[i] ^ testHash[i];
			}
			return diff == 0;
		} catch ( Throwable fail ) { return false; }
    }
	@SuppressWarnings("unused")
    private static boolean hashPassword_TEST_() {
		String password = "password";
		int minStrengthMillis = 100;
		long startTime = System.currentTimeMillis();
		String hashed = hashPassword(password,minStrengthMillis);
		long elapsedTime = System.currentTimeMillis() - startTime;
		asrt( elapsedTime >= minStrengthMillis );
		asrt( verifyPassword(password,hashed) );
        //System.out.println( "number of iterations: "+ Long.parseLong(hashed.split(":")[0],36) );
		return true;
	}



    /*
     * 	Removing leading and trailing whitespace,
     * 	and replaces all other whitespace with a single space.
     */
    public static String nw( CharSequence cs ) {
        return normalSpace(cs);
    }
    public static String normalSpace( CharSequence cs ) {
        String s = cs.toString();
        s = s.trim().replaceAll( "\\s+", " " );
        return s;
    }
    @SuppressWarnings("unused")
    private static boolean normalSpace_TEST_() {
        String s = """
            part1   part2
            \n part3 \t \r
        """;
        s = normalSpace(s);
        return s.equals("part1 part2 part3");
    }



    public static String onlyAlphaNum( Object o ) {
        if (o==null) return "";
        String str = o.toString();
        int i, len=str.length();
        StringBuffer buf = new StringBuffer( Math.min(10*1024,len) );
        for (i=0; i<len; i++) {
            char c = str.charAt(i);
            if (
                ( c>='a' && c<='z' ) ||
                ( c>='A' && c<='Z' ) ||
                ( c>='0' && c<='9' )
            ) {
                buf.append(c);
            }
        }
        return buf.toString();
    }
    @SuppressWarnings("unused")
    private static boolean onlyAlphaNum_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        asrtEQ( "abc123", onlyAlphaNum("abc123") );
        asrtEQ( "abc123", onlyAlphaNum("abc123!") );
        asrtEQ( "abc123", onlyAlphaNum("abc123!") );
        return true;
    }



    public static String uniqID() {
        return uniqID(20);
    }
    public static String uniqID( int len ) {
        return uniqID("U",len);
    }
    public static String uniqID( String prefix ) {
        return uniqID(prefix,-1);
    }
    public static String uniqID( String prefix, int suffixLen ) {
        if (suffixLen<0) suffixLen=8;
        int totalLen = prefix.length() + suffixLen;
        StringBuilder buf = new StringBuilder();
        buf.append( Lib.isEmpty(prefix) ? "U" : prefix );
        int charsToFill = totalLen - buf.length();
        int STAMP_LEN = "YYYYMMDDHHMMSS".length(); // not every possible milli-value is possible on some platforms
        if ( charsToFill > 4+STAMP_LEN ) {
            String timeStamp = onlyAlphaNum( timeStamp() );
            if ( timeStamp.length() > STAMP_LEN ) timeStamp = timeStamp.substring(0,STAMP_LEN);
            buf.append(timeStamp);
        }
        String counterStr = toBase62( _uniqID.incrementAndGet() );
        charsToFill = totalLen - buf.length();
        if ( charsToFill >= counterStr.length() ) {
            buf.append( counterStr );
        } else {
            buf.append( counterStr.substring(0,charsToFill) );
        }
        charsToFill = totalLen - buf.length();
        while (charsToFill>0) {
            String rand = toBase62( new Random().nextLong() );
            buf.append(rand.substring( 0, Math.min(rand.length(),charsToFill) ));
            charsToFill = totalLen - buf.length();
        }
        return buf.toString();
    }
    public static AtomicLong _uniqID = new AtomicLong(0);
    @SuppressWarnings("unused")
    private static boolean uniqID_TEST_() {
        // generate 100 with same prefix, and make sure they are unique
        String prefix = "P";
        int len = 5;
        Set<String> ids = new HashSet<>();
        for (int i=0; i<100; i++) {
            String id = uniqID(prefix,len);
            asrtEQ( id.length(), len+1, "length wrong" );
            asrt(! ids.contains(id), "not unique!" );
            ids.add(id);
        }
        return true;
    }



    public static String toBase62(long value) {
        // good for IDs
        value = Math.abs(value);
        StringBuilder sb = new StringBuilder();
        final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        do {
            int i = (int)(value % 62);
            sb.append( DIGITS.charAt(i) );
            value /= 62;
        } while (value>0);
        return sb.reverse().toString();
    }
    @SuppressWarnings("unused")
    private static boolean toBase62_TEST_( boolean findLineNumber ) {
        asrtEQ( toBase62(0), "0" );
        asrtEQ( toBase62(1), "1" );
        asrtEQ( toBase62(61), "z" );
        asrtEQ( toBase62(62), "10" );
        return true;
    }









    /**
     * A wrapper for java's URL to take the pain out.
     * (e.g. having to catch exceptions, having to construct with URI's, and having to figure out default ports)
     * No need to use this class for anything except construction, e.g.:
     *    new Lib.Url("http://www.google.com/").url // no mandatory try/catch block needed
     * or
     *    new Lib.Url("https://www.google.com/").port;
     *    // ^ returns 443, even though java.net.URL's getPort() would return -1
    **/
    public static class Url {
        public final java.net.URL url;
        public final String protocol;
        public final String host;
        public final int port;
        public final String path;
        public final String query;
        public final String fragment;
        public final boolean isHttps;
        public Url( String urlStr ) {
            try {
                url = new URI(urlStr).toURL();
                protocol = url.getProtocol();
                host = url.getHost();
                int p = url.getPort();
                path = url.getPath();
                query = url.getQuery();
                fragment = url.getRef();
                isHttps = "https".equals( protocol.toLowerCase() );
                port = p<0 ? ( isHttps ? 443 : 80 ) : p;
            } catch (Throwable t) {
                if (t instanceof RuntimeException) throw (RuntimeException)t;
                throw new RuntimeException(t);
            }
        }
    }



    /**
    * a better Map.of; which has ordered keys, allows nulls, is immutable, and toString is json.
    **/
    public static <K,V> Map<K,V> mapOf( Object... keyVals ) {
        Map<K,V> map = new LinkedHashMap<>();
        for (int i=0; i<keyVals.length; i+=2) {
            @SuppressWarnings("unchecked")
            K k = (K) keyVals[i];
            if ( keyVals.length == (i+1) ) {
                if (k==null) break; // ok if null key has no value
                throw new IllegalArgumentException("last key -- "+ k +" -- has no value");
            }
            @SuppressWarnings("unchecked")
            V v = (V) keyVals[i+1];
            map.put(k,v);
        }
        return new AbstractMap<K,V>() {
            public Set< Entry<K,V> > entrySet() {
                return map.entrySet();
            }
            public String toString() {
                return JsonEncoder.encode(map);
            }
        };
    }
    @SuppressWarnings("unused")
    private static boolean mapOf_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Map<String,Integer> map = mapOf( "a",1, "b",2, "c",3, null,null );
        String[] keys = map.keySet().toArray(new String[0]);
        Integer[] vals = map.values().toArray(new Integer[0]);
        List<String> expectedKeys = listOf("a","b","c",null);
        List<Integer> expectedVals = listOf(1,2,3,null);
        asrtEQ(keys,expectedKeys);
        asrtEQ(vals,expectedVals);
        String expectedStr = "{\"a\":1,\"b\":2,\"c\":3,null:null}";
        asrtEQ( map.toString(), expectedStr );
        // odd number of args ok as long as last one is null
        map = mapOf( "a",1, "b",2, "c",3, null );
        asrtEQ( map.size(), 3 );
        try {
            map = mapOf( "a",1, "b",2, "c",3, "d" );
            throw new RuntimeException("should have thrown IllegalArgumentException");
        } catch ( IllegalArgumentException e ) {} // expected
        return true;
    }
    /**
    * a better List.of; which allows nulls, is immutable, and toString is json.
    **/
    @SafeVarargs
    public static <T> List<T> listOf( T... vals ) {
        ArrayList<Object> list = new ArrayList<>( vals.length );
        for ( Object val: vals ) list.add(val);
        return new AbstractList<T>() {
            @SuppressWarnings("unchecked")
            public T get(int index) { return (T)list.get(index); }
            public int size() { return list.size(); }
            public String toString() { return JsonEncoder.encode(list); }
        };
    }
    @SuppressWarnings("unused")
    private static boolean listOf_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        List<Integer> list = listOf( 1,2,3,null );
        Integer[] vals = list.toArray(new Integer[0]);
        Integer[] expectedVals = {1,2,3,null};
        asrtEQ(vals,expectedVals);
        String expectedStr = "[1,2,3,null]";
        asrtEQ( list.toString(), expectedStr );
        return true;
    }



    /**
     * If micros are not specified, guarantees that the same stamp is not returned twice.
    **/
    public static String timeStamp( Long microsSinceEpoch, Boolean utc, String format ) {
        if ( microsSinceEpoch==null ) microsSinceEpoch = currentTimeMicros();
        if (utc==null) utc=Boolean.TRUE;
        if (format==null) format = "yyyy-MM-dd'_'HH-mm-ss-SSSSSSSSS";
        Instant now = Instant.ofEpochSecond( microsSinceEpoch/1000000, (microsSinceEpoch%1000000)*1000 );
        ZonedDateTime zonedNow = utc
            ? ZonedDateTime.ofInstant(now, ZoneId.of("UTC"))
            : ZonedDateTime.ofInstant(now, ZoneId.systemDefault())
        ;
        String stamp = zonedNow.format( DateTimeFormatter.ofPattern(format) );
        return stamp;
    }
    public static String timeStamp() { return timeStamp( null, null, null ); }
    public static String timeStamp( String format ) { return timeStamp( null, null, format ); }
    public static String timeStamp( Long microsSinceEpoch ) {
        return timeStamp( microsSinceEpoch, null, null );
    }
    @SuppressWarnings("unused")
    private static boolean timeStamp_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        String stamp = timeStamp();
        asrt( stamp.matches( "\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{9}" ) );
        for (int i=0; i<10; i++) {
            String nextStamp = timeStamp();
            asrt( nextStamp.compareTo(stamp) > 0 );
            stamp = nextStamp;
        }
        return true;
    }















    public static void archiveLogFiles() {
        File logDir = new File("./log");
        File oldDir = new File("./old");
        if (!oldDir.exists()) oldDir.mkdirs();
        if (!logDir.exists()) logDir.mkdirs();
        for (String filename : logDir.list()) {
            File srcFilespec = new File(logDir,filename);
            File dstFilespec = new File(oldDir,filename);
            // fileCopy removed - using rename instead
            srcFilespec.renameTo(dstFilespec);
        }
    }



    public static long currentTimeMicros() {
        class TimeHelper {
            // List of reliable endpoints for time validation
            private static final List<String> RELIABLE_ENDPOINTS = Arrays.asList(
                "http://www.google.com",
                "http://www.amazon.com",
                "http://www.example.com",
                "http://httpbin.org"
            );
            static long validateSystemTime() {
                long minRoundTrip = Long.MAX_VALUE;
                long bestDiff = 0;
                int endpointsAgreeingSystemWrong = 0;
                for (String endpoint : RELIABLE_ENDPOINTS) {
                    try {
                        long beforeRequest = System.currentTimeMillis();
                        URL url = URI.create(endpoint).toURL();
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("HEAD");
                        try {
                            conn.connect();
                            long afterRequest = System.currentTimeMillis();
                            long roundTripMs = afterRequest - beforeRequest;
                            String dateHeader = conn.getHeaderField("Date");
                            if (dateHeader == null) continue;
                            long serverTimeMs;
                            try {
                                DateTimeFormatter formatter = DateTimeFormatter
                                    .RFC_1123_DATE_TIME
                                    .withZone(ZoneOffset.UTC);
                                serverTimeMs = Instant.from(formatter.parse(dateHeader)).toEpochMilli();
                            } catch (DateTimeParseException e){continue;}
                            boolean isSystemTimeWrong = false;
                            if (beforeRequest < serverTimeMs) isSystemTimeWrong = true; // our time is earlier
                            long latestPossibleTime = serverTimeMs + 1000 + roundTripMs; // our time is too far later
                            if (afterRequest > latestPossibleTime) isSystemTimeWrong = true;
                            if (!isSystemTimeWrong) return 0L; // if any endpoint says our time is good, we trust it
                            endpointsAgreeingSystemWrong++;
                            // remember fastest response
                            if (roundTripMs < minRoundTrip) {
                                minRoundTrip = roundTripMs;
                                bestDiff = serverTimeMs - beforeRequest;
                            }
                        } finally { conn.disconnect(); }
                    } catch (IOException e) {
                        System.err.println( "Failed to validate time with " + endpoint + ": " + e.getMessage() );
                    }
                }
                // distrust local clock only if all responding endpoints agree system time is wrong
                if ( endpointsAgreeingSystemWrong>0 && endpointsAgreeingSystemWrong==RELIABLE_ENDPOINTS.size() ) {
                    return bestDiff;
                } else { return 0L; }
            }
        }
        long currentMicros;
        synchronized (_currentTimeMicros_millisAdjustment) {
            Long diff = _currentTimeMicros_millisAdjustment.get();
            if (diff==null) {
                diff = TimeHelper.validateSystemTime();
                _currentTimeMicros_millisAdjustment.set(diff);
            }
            long micros = TimeUnit.NANOSECONDS.toMicros( System.nanoTime() ) % 1000;
            currentMicros = 1000*( System.currentTimeMillis() + diff ) + micros;
        }
        // ensure never return the same value twice, and never go backwards in time
        synchronized (_currentTimeMicros_lastReturned) {
            long lastReturned = 1 + _currentTimeMicros_lastReturned.get();
            if ( currentMicros < lastReturned ) currentMicros=lastReturned;
            _currentTimeMicros_lastReturned.set(currentMicros);
            return currentMicros;
        }
    }
    public static final AtomicReference<Long> _currentTimeMicros_millisAdjustment =
        new AtomicReference<Long>(null);
    public static final AtomicLong _currentTimeMicros_lastReturned = new AtomicLong(0);
    @SuppressWarnings("unused")
    private static boolean currentTimeMicros_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        long lastTime = currentTimeMicros();
        for (int i=0; i<10; i++) {
            long thisTime = currentTimeMicros();
            if ( lastTime >= thisTime ) throw new RuntimeException("Time is not increasing!");
            lastTime = thisTime;
        }
        { // make sure currentTimeMicros is close to System.currentTimeMillis() * 1000
            long sysTime = System.currentTimeMillis() * 1000;
            long libTime = currentTimeMicros();
            final int MAX_DIFF_MICROS = 3*1000*1000;
            long diff = Math.abs(sysTime-libTime);
            asrt( diff<MAX_DIFF_MICROS, "time diff:"+(sysTime-libTime)+" micros" );
        }
        return true;
    }



    /**
     * Finds smallest indent of any non-blank line, and removes that indent from all lines.
    **/
    public static String unindent( String s ) {
        String[] lines = s.split( "(?:\\r\\n)|(?:\\n)|(?:\\r)+" );
        int minIndent = Integer.MAX_VALUE;
        for (int i=0; i<lines.length; i++) {
            String line = lines[i];
            if ( line.isBlank() ) continue;
            int indent = 0;
            for (int j=0, len=line.length(); j<len; j++) {
                if ( line.charAt(j)!=' ' ) break;
                indent++;
            }
            if ( indent < minIndent ) minIndent = indent;
        }
        for (int i=0; i<lines.length; i++) {
            String line = lines[i];
            if ( line.isBlank() ) {
                lines[i] = "";
                continue;
            }
            lines[i] = line.substring(minIndent);
        }
        return String.join("\n",lines);
    }
    @SuppressWarnings("unused")
    private static boolean unindent_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        String s = """
            This is a test of the unindent function.
            It should remove the smallest indent from all lines.
             This line should have 1 space indent.
        """;
        String s2 = unindent(s);
        asrt( s2.startsWith("This is a test of the unindent function.") );
        asrt( s2.contains("\n This line should have 1 space indent.") );
        return true;
    }



    public static String dblQuot( Object o ) { return quot(o,'"'); }
    public static String quot(Object o, String openQuot, String closeQuot) {
        String str = ( o == null ? "" : o.toString() );
        StringBuilder result = new StringBuilder(str.length() + openQuot.length() + closeQuot.length());
        result.append(openQuot);
        char escapeChar = closeQuot.charAt(0);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == escapeChar) result.append('\\');
            result.append(c);
        }
        result.append(closeQuot);
        return result.toString();
    }
    public static String quot(Object o, String quot) {
        String str = ( o == null ? "" : o.toString() );
        String closeQuot;
        switch (quot.charAt(0)) {
            case '[': closeQuot = "]"; break;
            case '{': closeQuot = "}"; break;
            case '(': closeQuot = ")"; break;
            default: closeQuot = quot;
        }
        return quot(str, quot, closeQuot);
    }
    public static String quot(Object o, char quot) {
        String str = ( o == null ? "" : o.toString() );
        return quot(str, String.valueOf(quot));
    }
    @SuppressWarnings("unused")
    private static boolean quot_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        assert quot("Hello World", '"').equals("\"Hello World\"");
        assert quot("Hello \"World\"", '"').equals("\"Hello \\\"World\\\"\"");
        assert quot("Hello \"World\"", "[").equals("[Hello \"World\"]");
        assert quot("Hello ```to the``` World", "```").equals("```Hello \\`\\`\\`to the\\`\\`\\` World```");
        assert quot("Hello {World}", "{", "}").equals("{Hello \\}World\\}}");
        assert quot("Test (data)", "(", ")").equals("(Test \\)data\\))");
        return true;
    }



    /**
     * Checks if an object is empty.
     */
    public static boolean isEmpty( Object o ) {
        if ( o==null ) return true;
        if ( o instanceof CharSequence s ) {
            if ( s.length()==0 ) return true;
            for ( int i=0, len=s.length(); i<len; i++ ) if ( s.charAt(i)>' ' ) return false;
            return true;
        }
        if ( o instanceof Number n ) return n.doubleValue()<.0001 && n.doubleValue()>(-.9999);
        if ( o instanceof Collection<?> c ) return c.isEmpty();
        if ( o instanceof Map<?,?> m ) return m.isEmpty();
        if ( o.getClass().isArray() ) return Array.getLength(o)==0;
        return false;
    }
    @SuppressWarnings({"unused"})
    private static boolean isEmpty_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        asrt( isEmpty(null), "null should be empty" );
        asrt( isEmpty(""), "empty string should be empty" );
        asrt( isEmpty(" \t\n\r"), "whitespace should be empty" );
        asrt( !isEmpty("x"), "non-empty string should not be empty" );
        asrt( isEmpty(0), "zero should be empty" );
        asrt( !isEmpty(1), "non-zero should not be empty" );
        asrt( isEmpty(new ArrayList<>()), "empty list should be empty" );
        asrt( !isEmpty(List.of(1)), "non-empty list should not be empty" );
        asrt( isEmpty(new HashMap<>()), "empty map should be empty" );
        asrt( !isEmpty(Map.of(1,2)), "non-empty map should not be empty" );
        asrt( isEmpty(new int[0]), "empty array should be empty" );
        asrt( !isEmpty(new int[]{1}), "non-empty array should not be empty" );
        return true;
    }



    public static boolean notEmpty( Object o ) { return !isEmpty(o); }
    @SuppressWarnings({"unused"})
    private static boolean notEmpty_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        asrt( !notEmpty(null), "null should not be not-empty" );
        asrt( !notEmpty(""), "empty string should not be not-empty" );
        asrt( !notEmpty(" \t\n\r"), "whitespace should not be not-empty" );
        asrt( notEmpty("x"), "non-empty string should be not-empty" );
        return true;
    }



    public static String nvl( Object o, String def ) { return o==null ? def : o.toString(); }
    public static <T> T nvl( T o, T def ) { return o==null ? def : o; }
    @SuppressWarnings({"unused"})
    private static boolean nvl_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        asrtEQ( "default", nvl(null, "default"), "null should return default" );
        asrtEQ( "value", nvl("value", "default"), "non-null should return value" );
        Integer i = 42;
        asrtEQ( i, nvl(i, 0), "non-null should return value" );
        asrtEQ( 0, nvl((Integer)null, 0), "null should return default" );
        return true;
    }



    /**
     * Evaluates if an object should be considered "true"
     */
    @SuppressWarnings({"rawtypes"})
    public static boolean isTrue( Object o ) {
        if ( o==null ) return false;
        if ( o instanceof Number ) return ((Number)o).doubleValue()>.1 || ((Number)o).doubleValue()<(-.1);
        if ( o instanceof Boolean ) return ((Boolean)o).booleanValue();
        if ( o instanceof List ) return ((List)o).size()>0;
        if ( o instanceof Map ) return ((Map)o).size()>0;
        if ( o.getClass().isArray() ) return Array.getLength(o)>0;

        String s = o.toString().trim().toUpperCase();
        if ( s.length()==0 ) return false;
        if ( s.equals("TRUE") || s.equals("T") ) return true;
        if ( s.equals("FALSE") || s.equals("F") ) return false;
        if ( s.equals("ON") || s.equals("YES") || s.equals("Y") ) return true;
        if ( s.equals("OFF") || s.equals("NO") || s.equals("N") ) return false;
        if ( s.equals("NONE") ) return false;
        if ( s.equals("1") || s.equals("-1") ) return true;
        if ( s.matches("(0|\\s)*") ) return false;
        return true;
    }
    @SuppressWarnings({"unused"})
    private static boolean isTrue_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        asrt( !isTrue(null), "null should not be true" );
        asrt( isTrue(true), "true should be true" );
        asrt( !isTrue(false), "false should not be true" );
        asrt( isTrue(1), "1 should be true" );
        asrt( !isTrue(0), "0 should not be true" );
        asrt( isTrue("true"), "\"true\" should be true" );
        asrt( isTrue("TRUE"), "\"TRUE\" should be true" );
        asrt( isTrue("t"), "\"t\" should be true" );
        asrt( isTrue("yes"), "\"yes\" should be true" );
        asrt( isTrue("y"), "\"y\" should be true" );
        asrt( isTrue("on"), "\"on\" should be true" );
        asrt( !isTrue("false"), "\"false\" should not be true" );
        asrt( !isTrue("FALSE"), "\"FALSE\" should not be true" );
        asrt( !isTrue("f"), "\"f\" should not be true" );
        asrt( !isTrue("no"), "\"no\" should not be true" );
        asrt( !isTrue("n"), "\"n\" should not be true" );
        asrt( !isTrue("off"), "\"off\" should not be true" );
        asrt( !isTrue("none"), "\"none\" should not be true" );
        asrt( isTrue("1"), "\"1\" should be true" );
        asrt( isTrue("-1"), "\"-1\" should be true" );
        asrt( !isTrue("0"), "\"0\" should not be true" );
        asrt( isTrue(List.of(1)), "non-empty list should be true" );
        asrt( !isTrue(new ArrayList<>()), "empty list should not be true" );
        return true;
    }



    /**
     * Wraps an Iterator with an Iterable.
     */
    public static <T> Iterable<T> iterable( Iterator<T> it ) {
        final AtomicBoolean alreadyUsed = new AtomicBoolean(false);
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                if ( alreadyUsed.getAndSet(true) ) throw new IllegalStateException("iterator() already called");
                return it;
            }
        };
    }
    @SuppressWarnings({"unused"})
    private static boolean iterable_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        Iterator<Integer> it = List.of(1, 2, 3).iterator();
        Iterable<Integer> itbl = iterable(it);
        List<Integer> result = new ArrayList<>();
        for ( Integer i:itbl ) result.add(i);
        asrtEQ( List.of(1, 2, 3), result, "iterable should iterate over all elements" );
        try {
            for ( Integer i:itbl ) { System.err.println("Should not be able to reach here"); return false; }
        } catch ( Exception expected ) {}
        return true;
    }



    /**
     * Assertion method that throws AssertionError if condition is false
     */
    public static Object asrt( Object o, Object... msgs ) {
        if ( isTrue(o) ) return o;
        String msg = null;
        for ( Object m:msgs ) {
            if ( msg==null ) msg = "" + m;
            //System.err.println(m);
        }
        if ( msg==null ) msg = "" + o;
        throw new AssertionError("asrt failed: " + msg);
    }
    @SuppressWarnings({"unused"})
    private static boolean asrt_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        try {
            asrt(true, "This should not fail");
            asrt(1>0, "This should not fail");
            try {
                asrt(false);
                return false;
            } catch ( AssertionError e ) {}
            try {
                asrt(1==2);
                return false;
            } catch ( AssertionError e ) {}
            return true;
        } catch ( Throwable t ) {
            t.printStackTrace();
            return false;
        }
    }



    /**
     * Assertion method that checks equality and throws AssertionError if not equal
     */
    public static boolean asrtEQ( Object expected, Object actual, Object... msgs ) {
        if ( isEqual(expected,actual) ) return true;
        String msg = "expected=" + expected + "; actual=" + actual;
        //System.err.println(msg);
        if ( msgs.length==0 ) msgs = new String[]{msg};
        asrt(false, msgs);
        return false;
    }
    @SuppressWarnings({"unused"})
    private static boolean asrtEQ_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        try {
            asrtEQ(1, 1, "This should not fail");
            asrtEQ("a", "a", "This should not fail");
            asrtEQ(null, null, "This should not fail");
            try {
                asrtEQ(1,2);
                return false;
            } catch ( AssertionError e ) {}
            try {
                asrtEQ("a", "b", " " );
                return false;
            } catch ( AssertionError e ) {}
            try {
                asrtEQ(null, "a" );
                return false;
            } catch ( AssertionError e ) {}
            return true;
        } catch ( Throwable t ) {
            t.printStackTrace();
            return false;
        }
    }



    /*
     * Returns an immutable list version of whatever is passed to it.
    */
    @SuppressWarnings({"rawtypes","unchecked"})
    public static List asList( Object o ) {
        if (o==null) return Collections.emptyList();
        if (o instanceof List) return Collections.unmodifiableList( (List)o );
        if (o instanceof Iterable) return asList( ((Iterable)o).iterator() );
        if (o instanceof Iterator) {
            List<Object> list = new ArrayList<>();
            Iterator it = (Iterator)o;
            while (it.hasNext()) list.add(it.next());
            return Collections.unmodifiableList(list);
        }
        if ( o.getClass().isArray() ) {
            return new AbstractList() {
                private final int length = Array.getLength(o);
                public int size() { return length; }
                public Object get(int index) { return Array.get(o, index); }
            };
        }
        if ( o instanceof Stream<?> ) return ((Stream<?>)o).collect( Collectors.toList() );
        return Collections.singletonList(o);
    }
    @SuppressWarnings("unused")
    private static boolean asList_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        List<?> l = asList( List.of(1,2,3).iterator() );
        asrtEQ( 3, l.size() );
        asrtEQ( 1, l.get(0) );
        asrtEQ( 2, l.get(1) );
        asrtEQ( 3, l.get(2) );
        return true;
    }



    /**
    * Compares without regard to case or difference in space or type;
    * e.g. "123" matches 123, " testing \t 123 " matches "TEStING 123"
    **/
    @SuppressWarnings({"unchecked"})
    public static <T> T findFirst( Collection<T> list, T... items ) {
        for ( T item : items ) {
            for ( T listitem : list ) {
                if ( isEqual(item,listitem) ) return item;
            }
        }
        return null;
    }
    @SuppressWarnings({"unchecked"})
    public static <T> T findValue( T value, Collection<T> values ) {
        return findFirst(values,value);
    }
    /**
    * Returns quickly if the key exists in the map.
    * If the key does not exist in the map, searches for a key that LOOKS LIKE the given key,
    * without regard to case or difference in space or type;
    * e.g. "123" matches 123, " testing \t 123 " matches "TEStING 123"
    **/
    @SuppressWarnings({"unchecked"})
    public static <K> K findKey( Map<K,?> map, K... keys2find ) {
        for ( K key2find : keys2find ) {
            if ( map==null || key2find==null ) return null;
            if ( map.containsKey(key2find) ) return key2find;
        }
        for (Iterator<K> it=map.keySet().iterator(); it.hasNext();) {
            K tryK = it.next();
            for ( K key2find : keys2find ) {
                if ( isEqual(tryK,key2find) ) return tryK;
            }
        }
        return null;
    }
    @SuppressWarnings("unchecked")
    public static <K> K findKey( K key, Map<K,?> map ) {
        return findKey(map,key);
    }
    @SuppressWarnings("unused")
    private static boolean findKey_TEST_() {
        HashMap<Object,Object> map = new HashMap<>();
        map.put( 123, "456" );
        asrtEQ( findKey(" 123 ",map), (Object)123 );
        map.put( " testing \t 123 ", "OK" );
        asrtEQ( findKey("TESTING 123",map), " testing \t 123 " );
        asrtEQ( findValue(" ok \r\n",map.values()), "OK" );
        asrt( findKey(88.8,map) == null );
        return true;
    }



    public static boolean isEqual( Object a, Object b ) {
        if ( a==null && b==null ) return true;
        if ( a==null || b==null ) return false;
        if ( a instanceof Number && b instanceof Number ) {
            return (
                ((Number)a).doubleValue() == ((Number)b).doubleValue()
            ) || (
                a.toString().equals( b.toString() )
                // e.g. NaN fails == test. This is hard-coded into the JVM, not the java.lang.Double class.
            );
        }
        if ( (a instanceof byte[] aArr) && (b instanceof byte[] bArr) ) {
            if ( aArr.length != bArr.length ) return false;
            for ( int i=0; i<aArr.length; i++ ) if ( aArr[i] != bArr[i] ) return false;
            return true;
        }
        if ( (a instanceof Map<?,?> aMap) && (b instanceof Map<?,?> bMap) ) {
            if ( aMap.size() != bMap.size() ) return false;
            for ( Object k : aMap.keySet() ) {
                @SuppressWarnings("unchecked")
                Map<Object,Object> bm = (Map<Object,Object>)b;
                Object bKey = findKey(k,bm);
                if ( k!=null && bKey==null ) return false;
                if (! isEqual(aMap.get(k),bMap.get(bKey)) ) return false;
            }
            return true;
        }
        { // try making sets and comparing them
            @SuppressWarnings("unchecked")
            Set<Object> aSet = new LinkedHashSet<Object>( asList(a) );
            @SuppressWarnings("unchecked")
            Set<Object> bSet = new LinkedHashSet<Object>( asList(b) );
            if ( aSet.size() != bSet.size() ) return false;
            if (!( aSet.contains(a) && bSet.contains(b) )) {
                // looks like one or both of them were unpacked
                for ( Object aObj : aSet ) {
                    if ( bSet.contains(aObj) ) continue;
                    return false;
                }
                return true;
            }
        }
        if ( a.getClass().getName().compareTo(b.getClass().getName()) > 0 ) {
            // swap so we can know the order of the types
            Object tmp=a; a=b; b=tmp;
        }
        if ( (a instanceof Character c) && (b instanceof Number n) ) {
            int cInt = (int)c;
            int nInt = n.intValue();
            return cInt==nInt;
        }
        return normalSpace( a.toString() ).equalsIgnoreCase(normalSpace( b.toString() ));
    }
    @SuppressWarnings("unused")
    private static boolean isEqual_TEST_() {
        Lib.asrt( isEqual(null,null), "isEqual test 1 failed" );
        Lib.asrt( !isEqual(null,""), "isEqual test 2 failed" );
        Lib.asrt( isEqual("oK","Ok"), "isEqual test 3 failed" );
        Lib.asrt( isEqual(1,"1"), "isEqual test 4 failed" );
        return true;
    }



    /**
     * Gets the class name at a specific frame in the stack trace
     */
    public static String getClassName( int backupFrames ) {
        return new Throwable().getStackTrace()[backupFrames].getClassName();
    }
    @SuppressWarnings({"unused"})
    private static boolean getClassName_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        asrtEQ( Lib.class.getName(), getClassName(0), "getClassName(0) should return this class" );
        return true;
    }



    /**
     * Gets the current class
     */
    @SuppressWarnings({"rawtypes"})
    public static Class thisClass() {
        try { return Class.forName(getClassName(2)); }
        catch ( ClassNotFoundException e ) { throw new RuntimeException(e); }
    }
    @SuppressWarnings({"unused"})
    private static boolean thisClass_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        asrt( thisClass().getName().endsWith("Lib"), "thisClass() should return Lib class" );
        return true;
    }



    /**
     * Finds the main class that is executing
     */
    public static Class<?> findExecutingMainClass() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        List<String> mainMethodNames = List.of("main", "<clinit>");
        for ( int i=stackTrace.length-1; i>=0; i-- ) {
            StackTraceElement element = stackTrace[i];
            if ( !mainMethodNames.contains(element.getMethodName()) ) continue;
            try {
                Class<?> clazz = Class.forName(element.getClassName());
                Method mainMethod = clazz.getMethod("main", String[].class);
                if ( mainMethod==null ) continue;
                int mods = mainMethod.getModifiers();
                if ( Modifier.isPublic(mods) && Modifier.isStatic(mods) ) return clazz;
            } catch ( Throwable tryNext ) {}
        }
        return null;
    }
    @SuppressWarnings({"unused"})
    private static boolean findExecutingMainClass_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        findExecutingMainClass();
        return true;
    }



    /**
     * Gets the main executable file
     */
    public static File getMainExeFile() {
        Class<?> mainClass = findExecutingMainClass();
        if ( mainClass==null ) return null;
        String path = mainClass.getResource(mainClass.getSimpleName() + ".class").toString();
        if ( !path.startsWith("jar:") ) return null;
        String jarPath = path.substring(4, path.lastIndexOf("!"));
        if ( jarPath.startsWith("file:") ) jarPath = jarPath.substring(5);
        File f = new File(jarPath);
        return f.isFile() ? f : null;
    }
    @SuppressWarnings({"unused"})
    private static boolean getMainExeFile_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        getMainExeFile();
        return true;
    }



    public static Class<?> getCallingClass() {
        try {
            return Class.forName( getCallingClassName() );
        } catch ( ClassNotFoundException cnfe ) {
            throw new RuntimeException(cnfe);
        }
    }
    public static String getCallingClassName() {
        StackTraceElement[] stak = new Throwable().getStackTrace();
        String thisClassName = stak[0].getClassName();
        // skip all frames from this class
        for (int i=0; i<stak.length; i++) {
            String className = stak[i].getClassName();
            if ( className.equals(thisClassName) ) continue;
            thisClassName = className;
            break;
        }
        return thisClassName;
    }



    /**
     * Tests all methods in a class that end with _TEST_
     */
    public static boolean testClass() {
        Class<?> clas = getCallingClass();
        return testClass(clas);
    }
    public static boolean testClass( Class<?> claz ) {
        Class<?> clas = claz==null ? getCallingClass() : claz;
        archiveLogFiles();
        System.out.println("Testing class: " + clas.getName());
        class MethodInfo implements Comparable<MethodInfo> {
            public final Method method;
            public int methodLineNumber = Integer.MAX_VALUE;
            public StackTraceElement[] errLoc = null;
            public String errMsg = null;
            MethodInfo( Method m ) { this.method = m; }
            public void setMethodLineNumber( Throwable t ) {
                StackTraceElement[] methodLocation = t.getStackTrace();
                String methNam = method.getName();
                for (StackTraceElement ste : methodLocation) {
                    if ( ste.getClassName().equals(clas.getName()) && ste.getMethodName().equals(methNam) ) {
                        methodLineNumber = ste.getLineNumber();
                        return;
                    }
                }
            }
            public void setErrorTrace( Throwable t ) {
                StackTraceElement[] methodLocation = t.getStackTrace();
                // filter out anything not from clas
                errLoc = Arrays.stream(methodLocation).filter(
                    ste -> ste.getClassName().equals(clas.getName())
                ).toArray(StackTraceElement[]::new);
            }
            @Override
            public int compareTo(MethodInfo other) {
                int lineCompare = Integer.compare( this.methodLineNumber, other.methodLineNumber );
                if (lineCompare != 0) return lineCompare;
                return this.method.getName().compareTo(other.method.getName());
            }
            @Override
            public String toString() {
                String s = (
                    clas.getSimpleName() + "." + method.getName() +
                    ( methodLineNumber==Integer.MAX_VALUE ? "" : ":"+ methodLineNumber )
                );
                if (errMsg!=null) s += "\n   "+errMsg;
                if (errLoc!=null) {
                    for (StackTraceElement ste : errLoc) s += "\n   "+ste.toString();
                }
                return s;
            }
        }
        // Collect and sort methods
        List<MethodInfo> testMethods = new ArrayList<>();
        for (Method m : clas.getDeclaredMethods()) {
            String methNam = m.getName();
            if (!methNam.endsWith("_TEST_")) continue;
            MethodInfo mi = new MethodInfo(m);
            testMethods.add(mi);
            if ( m.getParameterCount()==1 && m.getParameterTypes()[0]==boolean.class ) {
                try {
                    m.setAccessible(true);
                    try { m.invoke(null, true); } catch (InvocationTargetException e) {
                        mi.setMethodLineNumber( e.getTargetException() );
                        continue;
                    }
                } catch ( Throwable fallThrough ) {}
            }
        }
        Collections.sort(testMethods);
        // Run tests in order of line number
        List<MethodInfo> failedMethods = new ArrayList<>();
        for (MethodInfo methodInfo : testMethods) {
            Method m = methodInfo.method;
            System.out.println( "Running test: " + methodInfo.toString() );
            try {
                m.setAccessible(true);
                Object res;
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    throw new RuntimeException( "not static" );
                }
                if (m.getParameterCount()==1 && m.getParameterTypes()[0] == boolean.class) {
                    res = m.invoke(null, false);  // not finding line number this time
                } else res = m.invoke(null);
                if ( Boolean.FALSE.equals(res) ) {
                    String failMsg = "Returned false: "+ methodInfo;
                    System.out.println(failMsg);
                    failedMethods.add(methodInfo);
                }
            } catch (Throwable t) {
                failedMethods.add(methodInfo);
                if ( t instanceof InvocationTargetException ite ) t = ite.getTargetException();
                String failMsg = "Fail in method "+methodInfo+": "+Lib.dblQuot( t.getMessage() );
                System.out.println(failMsg);
                methodInfo.setErrorTrace(t);
                methodInfo.errMsg = t.getMessage();
            }
        }
        if ( failedMethods.isEmpty() ) {
            System.out.println( "All tests PASS in class "+clas.getName() );
            return true;
        }
        System.out.println( "Failed tests in class "+clas.getName()+":" );
        for ( MethodInfo mi : failedMethods ) System.out.println(mi);
        return false;
    }



    /**
     * Wraps text to a specified width.
     */
    public static String[] wrapText( String text, int width ) { return wrapText(text, width, true); }
    /**
     * Wraps text to a specified width, optionally forcing long words to be split.
     */
    public static String[] wrapText( String text, int width, boolean force ) {
        String[] words = text.trim().split("\\s+");
        if ( force ) {
            List<String> newWords = new ArrayList<>();
            for ( String word:words ) {
                while ( word.length()>width ) {
                    newWords.add(word.substring(0, width));
                    word = word.substring(width);
                }
                if ( !word.isBlank() ) newWords.add(word);
            }
            words = newWords.toArray(new String[0]);
        }

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for ( String word:words ) {
            boolean needSpace = line.length()>0;
            if ( line.length() + word.length() + (needSpace ? 1 : 0) > width ) {
                if ( !line.isEmpty() ) lines.add(line.toString());
                line.setLength(0);
                needSpace = false;
            }
            if ( needSpace ) line.append(' ');
            line.append(word);
        }
        if ( !line.isEmpty() ) lines.add(line.toString());
        return lines.toArray(new String[0]);
    }
    @SuppressWarnings({"unused"})
    private static boolean wrapText_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        String input = """
            This is a long string of text, which needs to \t be wrapped,\n
            IncludingThisVeryLongWord AndThisLongWordAlso.
        """;
        int lineLen = 10;

        String[] output = wrapText(input, lineLen, true);
        String[] expected = {
            "This is a", "long", "string of", "text,", "which", "needs to", "be",
            "wrapped,", "IncludingT", "hisVeryLon", "gWord", "AndThisLon", "gWordAlso."
        };
        asrtEQ(Arrays.asList(expected), Arrays.asList(output));

        output = wrapText(input, lineLen, false);
        expected = new String[]{
            "This is a", "long", "string of", "text,", "which", "needs to", "be",
            "wrapped,", "IncludingThisVeryLongWord", "AndThisLongWordAlso."
        };
        asrtEQ(Arrays.asList(expected), Arrays.asList(output));
        return true;
    }



    /**
     * Creates a deep copy of an array, including nested arrays.
     */
    public static Object deepCopyArray( Object arr ) {
        int len = Array.getLength(arr);
        Object result = Array.newInstance(arr.getClass().getComponentType(), len);
        for ( int i=0; i<len; i++ ) {
            Object o = Array.get(arr, i);
            if ( o!=null && o.getClass().isArray() ) o = deepCopyArray(o);
            Array.set(result, i, o);
        }
        return result;
    }
    @SuppressWarnings({"unused"})
    private static boolean deepCopyArray_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        int[] arrA = {1, 2, 3};
        int[][] arrB = {arrA, {4, 5, 6}};
        int[][] arrC = (int[][])deepCopyArray(arrB);
        arrA[0] = 9;
        asrt(arrB[0][0]==9, "Original array should be modified");
        asrt(arrC[0][0]==1, "Copied array should not be modified");
        arrA[0] = 1;
        asrtEQ(Arrays.deepToString(arrB), Arrays.deepToString(arrC), "Arrays should be equal after resetting");
        return true;
    }



    /**
     * Pads a string on the right with a specified padding string.
     */
    public static String rpad( String str, int len, String pad ) {
        if ( str==null ) str = "";
        if ( pad==null ) pad = " ";
        if ( str.length()==len ) return str;

        StringBuffer buf = new StringBuffer(len);
        for ( int i=0; i<str.length() && buf.length()<len; i++ ) buf.append(str.charAt(i));

        while ( buf.length()<len ) {
            for ( int i=0; i<pad.length() && buf.length()<len; i++ ) buf.append(pad.charAt(i));
        }
        return buf.toString();
    }
    @SuppressWarnings({"unused"})
    private static boolean rpad_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        asrtEQ("abc  ", rpad("abc", 5, " "), "String should be padded with spaces");
        asrtEQ("abc", rpad("abc", 3, " "), "String should not be padded if already at length");
        asrtEQ("ab", rpad("abc", 2, " "), "String should be truncated if longer than length");
        asrtEQ("abc--", rpad("abc", 5, "-"), "String should be padded with specified character");
        asrtEQ("abc-+-", rpad("abc", 6, "-+"), "String should be padded with specified pattern");
        return true;
    }



    /**
     * Centers a string in a field of specified width.
     */
    public static String centerPad( String str, int len ) {
        if ( str==null ) str = "";
        int strLen = str.length();
        int prefixLen = (len - strLen) / 2;
        int suffixLen = prefixLen;
        if ( prefixLen + strLen + suffixLen < len ) suffixLen++;
        if ( prefixLen + strLen + suffixLen < len ) prefixLen++;
        if ( prefixLen + strLen + suffixLen > len ) suffixLen--;
        if ( prefixLen + strLen + suffixLen > len ) prefixLen--;

        StringBuffer buf = new StringBuffer();
        for ( int i=0; i<prefixLen; i++ ) buf.append(' ');
        buf.append(str);
        for ( int i=0; i<suffixLen; i++ ) buf.append(' ');

        if ( prefixLen<0 ) {
            buf.setLength(0);
            buf.append(str.substring(-prefixLen));
        }
        if ( suffixLen<0 ) buf.setLength(buf.length() + suffixLen);

        return buf.toString();
    }
    @SuppressWarnings({"unused"})
    private static boolean centerPad_TEST_( boolean findLineNumber ) {
        if ( findLineNumber ) throw new RuntimeException();
        asrtEQ(" FAR ", centerPad("FAR", 5), "String should be centered");
        asrtEQ("FAR ", centerPad("FAR", 4), "String should be right-padded if odd length difference");
        asrtEQ("FAR", centerPad("FAR", 3), "String should not be padded if already at length");
        asrtEQ("FA", centerPad("FAR", 2), "String should be truncated if longer than length");
        asrtEQ(" FARX ", centerPad("FARX", 6), "String should be centered");
        asrtEQ("FARX ", centerPad("FARX", 5), "String should be right-padded if odd length difference");
        asrtEQ("FARX", centerPad("FARX", 4), "String should not be padded if already at length");
        asrtEQ("FAR", centerPad("FARX", 3), "String should be truncated if longer than length");
        asrtEQ("AR", centerPad("FARX", 2), "String should be truncated if longer than length");
        return true;
    }



    /**
    * Shows a dialog and ignores the user's response.
    *
    * Any program that uses this method can set the default look and feel using
    * something like this:
    * UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
    **/
    public static void dialog( String message, boolean isError, int closeAfterSeconds ) {
        String title = isError ? "ERROR" : "Info";
        Object[] options = isError ? new Object[]{"Abort"} : new Object[]{"OK"};
        boolean isInfo = !isError;
        dialog(title,message,options,0,isInfo,false,false,isError,null,closeAfterSeconds);
    }
    /**
     * Shows a dialog, and returns the user's response.
     *
     * Any program that uses this method can set the default look and feel using
     * something like this: UIManager.setLookAndFeel(
     * UIManager.getSystemLookAndFeelClassName() );
     **/
    public static Object dialog(String title, String message, Object[] options,
        int defaultOptionIndex, boolean isInfo, boolean isQuestion,
        boolean isWarn, boolean isError, javax.swing.JFrame optionalFrame,
        int closeAfterSeconds
    ) {
        if (closeAfterSeconds<=0) closeAfterSeconds = 24*60*60; // 1 day
        final javax.swing.JFrame frame = (
            optionalFrame == null ?
            new javax.swing.JFrame( "dialog@" + System.currentTimeMillis() ) : optionalFrame
        );
        frame.setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
        Object result = null;
        try {
            int msgType = javax.swing.JOptionPane.PLAIN_MESSAGE;
            if (isError) {
                msgType = javax.swing.JOptionPane.ERROR_MESSAGE;
            } else if (isWarn) {
                msgType = javax.swing.JOptionPane.WARNING_MESSAGE;
            } else if (isQuestion) {
                msgType = javax.swing.JOptionPane.QUESTION_MESSAGE;
            } else if (isInfo) {
                msgType = javax.swing.JOptionPane.INFORMATION_MESSAGE;
            }
            Object defaultOption = null;
            if (options != null && defaultOptionIndex >= 0
                    && defaultOptionIndex < options.length) {
                defaultOption = options[defaultOptionIndex];
            }
            final long[] stopAfter = new long[]{ System.currentTimeMillis() + closeAfterSeconds*1000 };
            Thread timeoutCounter = new Thread() {
                public void run() {
                    while (true) {
                        try { Thread.sleep(500); }catch(InterruptedException ie){ break; }
                        synchronized( stopAfter ){
                            if ( System.currentTimeMillis() >= stopAfter[0] ) break;
                        }
                    }
                    frame.dispose();
                }
            };
            timeoutCounter.setDaemon(false);
            timeoutCounter.start();
            int selIdx = javax.swing.JOptionPane.showOptionDialog(
                frame, message, title, javax.swing.JOptionPane.YES_NO_CANCEL_OPTION, msgType,
                null, options, defaultOption
            );
            synchronized(stopAfter){ stopAfter[0] = -1; }
            if (selIdx>=0 && options!=null && selIdx<=options.length) {
                result = options[selIdx];
            }
        } finally {
            try{ frame.dispose(); } catch ( Throwable ignore ) {}
        }
        return result;
    }
    /**
     * Shows a dialog, and returns the user's response.
     *
     * Any program that uses this method can set the default look and feel using
     * something like this: UIManager.setLookAndFeel(
     * UIManager.getSystemLookAndFeelClassName() );
     * @return the user's response, or null if the dialog was cancelled, closed, or timed out.
     **/
    public static String dialog(
        String title, String message, String defaultValue, javax.swing.JFrame optionalFrame, int closeAfterSeconds
    ) {
        if (closeAfterSeconds <= 0) closeAfterSeconds = 24 * 60 * 60; // 1 day
        final javax.swing.JFrame frame = (
            optionalFrame == null ?
            new javax.swing.JFrame("dialog@" + System.currentTimeMillis()) : optionalFrame
        );
        frame.setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
        String userResponse = null;
        try {
            final long[] stopAfter = new long[]{System.currentTimeMillis() + closeAfterSeconds * 1000};
            Thread timeoutCounter = new Thread() {
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            break;
                        }
                        synchronized (stopAfter) {
                            if (System.currentTimeMillis() >= stopAfter[0]) break;
                        }
                    }
                    frame.dispose();
                }
            };
            timeoutCounter.setDaemon(true);
            timeoutCounter.start();
            javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout());
            panel.add(new javax.swing.JLabel(message), java.awt.BorderLayout.CENTER);
            javax.swing.JTextField textField = new javax.swing.JTextField(defaultValue);
            panel.add(textField, java.awt.BorderLayout.SOUTH);
            String[] options = {"OK","Cancel"};
            int result = javax.swing.JOptionPane.showOptionDialog(
                frame, panel, title, javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]
            );
            synchronized (stopAfter) { stopAfter[0] = -1; }
            if (result == 0) userResponse = textField.getText();
        } finally {
            try { frame.dispose(); } catch (Throwable ignore) {}
        }
        return userResponse;
    }
    // Commented out for headless environments
    // @SuppressWarnings("unused")
    // private static boolean dialog_TEST_() {
    //     String userResponse = dialog( "Test", "Enter something:", "ok", null, 1 );
    //     System.out.println( "userResponse: " + userResponse );
    //     return true;
    // }



    public static String getCanonicalPath( File f ) {
        try { return f.getCanonicalPath(); }
        catch ( IOException e ) { return f.getAbsolutePath(); }
    }



    public static Process osCmd( List<String> cmd, Map<String,String> envChanges, File cwd ) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        Map<String,String> environment = processBuilder.environment();
        if (envChanges==null) envChanges = Collections.emptyMap();
        for ( Map.Entry<String,String> entry : envChanges.entrySet() ) {
            environment.put(entry.getKey(), entry.getValue());
        }
        if (cwd!=null) processBuilder.directory(cwd);
        return processBuilder.start();
    }



    /**
     * Pushes and pulls bytes into and out of a Process.
     * @return The Process finish status code.
     */
    public static int OSProcIO(
        Process process, InputStream stdin,
        OutputStream stdout, OutputStream stderr
    ) {
        final Process proc = process;
        final InputStream finp = stdin;
        final OutputStream fout = stdout;
        final OutputStream ferr = stderr;
        final StringBuffer extraErrors = new StringBuffer();
        Thread inpThread = new Thread() {
            public void run() {
                try {
                    OutputStream out = proc.getOutputStream();
                    byte[] buf = new byte[1024];
                    while (true) {
                        int bytCount = finp==null ? -1 : finp.read(buf);
                        if (bytCount<0) break;
                        out.write(buf,0,bytCount);
                    }
                    out.close();
                } catch (Throwable e) {
                    synchronized (extraErrors) {
                        extraErrors.append( e.toString() ).append("\n");
                    }
                }
            }
        };
        Thread outThread = new Thread() {
            public void run() {
                try {
                    InputStream inp = proc.getInputStream();
                    byte[] buf = new byte[1024];
                    while (true) {
                        int bytCount = inp.read(buf);
                        if (bytCount<0) break;
                        if (fout!=null) fout.write(buf,0,bytCount);
                    }
                    inp.close();
                } catch (Throwable e) {
                    synchronized (extraErrors) {
                        extraErrors.append( e.toString() ).append("\n");
                    }
                }
            }
        };
        Thread errThread = new Thread() {
            public void run() {
                try {
                    InputStream inp = proc.getErrorStream();
                    byte[] buf = new byte[1024];
                    while (true) {
                        int bytCount = inp.read(buf);
                        if (bytCount<0) break;
                        if (ferr!=null) ferr.write(buf,0,bytCount);
                    }
                    inp.close();
                } catch (Throwable e) {
                    synchronized (extraErrors) {
                        extraErrors.append( e.toString() ).append("\n");
                    }
                }
            }
        };
        inpThread.start();
        outThread.start();
        errThread.start();
        try {
            inpThread.join();
            outThread.join();
            errThread.join();
            int result = proc.waitFor();
            synchronized (extraErrors) {
                if (ferr!=null && extraErrors.length() > 0) ferr.write(extraErrors.toString().getBytes());
            }
            return result;
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        } catch (IOException io) {
            throw new RuntimeException(io);
        }
    }



    public static void main( String[] args ) throws Exception { Lib.testClass(); }
}
