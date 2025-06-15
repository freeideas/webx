package http;
import java.io.*;

import jLib.Lib;
import jLib.LibTest;
import jLib.Result;



public class HttpRequest extends HttpMessage {



    public HttpRequest( HttpHeaderBlock headerBlock, byte[] body ) {
        super(headerBlock, body);
    }



    public static HttpRequest newHttpRequest( HttpMessage msg ) {
        return new HttpRequest( msg.headerBlock, msg.body );
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        String rawHeader = Lib.unindent("""
            GET / HTTP/1.1
            Date: Mon, 27 Jul 2009 12:28:53 GMT
            Server: Apache/2.2.14 (Win32)
            Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT
            Content-Length: 10
            Content-Type: text/html

            1234567890
        """).trim();
        rawHeader = rawHeader.replaceAll( "(?<!\\r)\\n", "\r\n" ); // convert to CRLF
        Result<HttpMessage,Exception> result = HttpMessage.readHttpMessage( new ByteArrayInputStream(rawHeader.getBytes()) );
        if (! result.isOk() ) throw result.err();
        HttpMessage msg = result.ok();
        Lib.asrt( msg.body.length == 10 );
        Lib.asrt( msg.body[0] == (int)'1' );
        return true;
    }



    public static void main( String[] args ) throws Exception { LibTest.testClass(); }
}
