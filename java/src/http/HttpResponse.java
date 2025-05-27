package http;
import java.io.*;

import jLib.Lib;
import jLib.Result;



public class HttpResponse extends HttpMessage {



    public HttpResponse( HttpHeaderBlock headerBlock, byte[] body ) {
        super(headerBlock, body);
    }



    public static HttpResponse newHttpResponse( HttpMessage msg ) {
        return new HttpResponse( msg.headerBlock, msg.body );
    }



    public static HttpResponse redirect( String uri ) {
        HttpHeaderBlock hed = HttpHeaderBlock.redirect(uri);
        String body = Lib.unindent("""
            <html>
            <head>
                <meta http-equiv="refresh" content="0;URL='""" + uri + """
                '" />
                <title>Redirecting...</title>
                <script> window.location.href = '""" + uri + """
                ';</script>
            </head>
            <body>
                <p>Redirecting to <a href='""" + uri + """
                '>""" + uri + """
                </a></p>
                <p><i>If you are not redirected, click the link above.</i></p>
            </body>
            </html>
        """);
        return new HttpResponse(hed, body.getBytes());
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
        Result<HttpMessage,Exception> msgRes =
            HttpMessage.readHttpMessage( new ByteArrayInputStream(rawHeader.getBytes()) )
        ;
        if (! msgRes.isOk() ) throw msgRes.err();
        HttpMessage msg = msgRes.ok();
        HttpResponse res = newHttpResponse(msg);
        Lib.asrt( res.body.length == 10 );
        Lib.asrt( res.body[0] == (int)'1' );
        return true;
    }



    public static void main( String[] args ) throws Exception { Lib.testClass(); }



}
