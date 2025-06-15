package http;
import java.io.*;

import jLib.Lib;
import jLib.LibFile;
import jLib.Result;



public class HttpFileHandler implements HttpHandler {



    public final File rootDir;
    public final String prefix;



    public HttpFileHandler( String prefix, File rootDir ) {
        this.prefix = prefix;
        this.rootDir = rootDir;
    }



    @Override
    public HttpResponse handle( HttpRequest req ) {
        String reqPath = req.headerBlock.getRequestPath();
        reqPath = reqPath.substring( prefix.length() );
        if ( reqPath.startsWith("/") ) reqPath = reqPath.substring(1);
        File f = new File( rootDir, reqPath );
        if ( f.isDirectory() ) {
            File tryFile = new File( f, "index.html" );
            if ( tryFile.isFile() ) {
                f = tryFile;
            } else {
                return serveDirListing(f);
            }
        }
        if ( !f.exists() ) {
            return new HttpErrorResponse( 404, "Not Found" );
        }
        return new FileResponse(f);
    }



    public HttpResponse serveDirListing( File dir ) {
        StringBuilder dirListing = new StringBuilder();
        dirListing.append( "<html>" );
        for ( File f : dir.listFiles() ) {
            dirListing.append( "<a href=\""+f.getName()+"\">"+f.getName()+"</a><br>" );
        }
        dirListing.append( "</html>" );
        return new HttpResponse( new HttpHeaderBlock( "HTTP/1.1 200 OK", Lib.mapOf(
            "Content-Length", ""+dirListing.length(),
            "Content-Type", "text/html"
        ) ), dirListing.toString().getBytes() );
    }



}



class FileResponse extends HttpResponse {
    private final File file;

    public FileResponse( File f ) {
        super( new HttpHeaderBlock( "HTTP/1.1 200 OK", Lib.mapOf(
            "Content-Length", ""+f.length(),
            "Content-Type", LibFile.getMimeType( f.getName() )
        ) ), null);
        file = f;
    }

    @Override
    public Result<Long,Exception> write( OutputStream out ) {
        Result<Long,Exception> result = headerBlock.write(out);
        if (! result.isOk() ) return result;
        long bytesCopied = result.ok();
        try ( FileInputStream fis = new FileInputStream(file); ) {
            long moreBytesCopied = Lib.copy(fis,out);
            if (moreBytesCopied<0) return Result.err( new IOException("can't read "+file.getName() ) );
            return Result.ok(bytesCopied+moreBytesCopied);
        } catch ( IOException e ) {
            return Result.err(e);
        }
    }

}
