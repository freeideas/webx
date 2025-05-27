package http;



public abstract interface HttpHandler {



    abstract public HttpResponse handle( HttpRequest req );



}
