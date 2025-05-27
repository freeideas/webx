package http;
import java.util.*;
import jLib.*;
import persist.PersistentData;



public class HttpLoginHandler implements HttpHandler {



    @SuppressWarnings({"rawtypes","resource"})
    private static Map persistentMap = new PersistentData().getRootMap();
    public static String appName = null;
    public static String emailFormUrl = null;
    public static String loginCodeUrl = null;
    public static String loggedInUrl = null;
    public static final long loginCodeLifespanMicros = 1000L*1000*60*60; // one hour



    public String getEmailAddress( HttpRequest req ) {
        AuthToken tok = AuthToken.find( req.headerBlock.toString() );
        if ( tok!=null && ! tok.isValid() ) tok = null;
        if (tok!=null) return tok.email.toLowerCase();
        return null;
    }



    /**
     * takes an email address, sends an email;
     * or takes a login code, and redirects to home page, and sets a cookie
     */
    @Override
    public HttpResponse handle( HttpRequest req ) {
        if (!( Lib.isEmpty(req.allParms.get("loginCode")) || Lib.isEmpty(req.allParms.get("email")) )) {
            String email = req.allParms.get("email").toString();
            String loginCode = req.allParms.get("loginCode").toString();
            String stamp = Lib.nvl(
                Lib.get( persistentMap, List.of("usr",email,"loginCode",loginCode) ),
                "20010101235959999"
            );
            long stampMicros = Lib.microsSinceEpoch(stamp);
            long nowMicros = Lib.currentTimeMicros();
            if ( nowMicros-stampMicros <= loginCodeLifespanMicros ) {
                return issueToken(email);
            }
        }
        if (! Lib.isEmpty(req.allParms.get("email")) ) {
            String email = req.allParms.get("email").toString();
            return sendLoginCode(email);
        }
        return sendEmailForm();
    }



    private HttpResponse issueToken( String email ) {
        AuthToken tok = AuthToken.newAuthToken(email);
        HttpHeaderBlock resHead = HttpHeaderBlock.redirect(loggedInUrl)
            .withAddHeader("Set-Cookie", "Authorization="+tok.toJson() )
        ;
        return new HttpResponse( resHead, HttpResponse.redirect(loggedInUrl).body );
    }



    private HttpResponse sendLoginCode( String email ) {
        String loginCode = Lib.randToken( "", 6, true );
        Map<?,?> loginCodes = (Map<?,?>) Lib.get( persistentMap, List.of("usr",email,"loginCode") );
        if (loginCodes!=null) loginCodes.clear();
        Lib.put( persistentMap, List.of("usr",email,"loginCode",loginCode), Lib.timeStamp() );
        LibEmail.sendEmail(
            "admin", email, "Your "+appName+" login code", Lib.unindent(
                String.format("""
                <html>
                <body>
                    Your login code is: <b>"%s"</b>. <br/>
                    Enter this code on the login page to access your account.
                </body>
                </html>
                """, loginCode )
            ), "text/html"
        );
        return HttpResponse.redirect(loginCodeUrl+"?email="+email);
    }



    private HttpResponse sendEmailForm() {
        return HttpResponse.redirect(emailFormUrl);
    }



}