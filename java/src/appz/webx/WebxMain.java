package appz.webx;

import http.*;
import jLib.*;
import java.io.File;

public class WebxMain {
    public static void main(String[] args) {
        System.out.println("Webx Application Starting...");
        
        ParseArgs pa = new ParseArgs(args);
        int port = pa.getInteger("port", 8080, "Server port");
        String rootDir = pa.getString("root", ".", "Root directory to serve");
        
        HttpServer server = new HttpServer(port);
        server.handlers.put("/", new HttpFileHandler("/", new File(rootDir)));
        
        System.out.println("Starting server on port " + port);
        System.out.println("Serving files from: " + new File(rootDir).getAbsolutePath());
        
        server.start();
    }
}