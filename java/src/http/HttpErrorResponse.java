package http;

public class HttpErrorResponse extends HttpResponse {

    public HttpErrorResponse( int statusCode, String errMsg ) {
        super( new HttpHeaderBlock( "HTTP/1.1 "+statusCode+" "+errMsg, null ), errMsg.getBytes() );
    }

}
