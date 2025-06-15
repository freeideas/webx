package http;

public class HttpErrorHandler implements HttpHandler {

    final int statusCode;
    final String errMsg;

    public HttpErrorHandler( int statusCode, String errMsg ) {
        this.statusCode = statusCode;
        this.errMsg = errMsg;
    }

    public HttpResponse handle( HttpRequest req ) { return new HttpErrorResponse(statusCode,errMsg); }

}
