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



    @Override
    public HttpResponse handle( HttpRequest req ) {
        String command = Lib.nvl(req.allParms.get("command"), "");
        if (command == null) command = "";
        switch ( command.toLowerCase() ) {
            case "sendemail": return handleSendEmail(req);
            case "validatelogincode": return handleValidateLoginCode(req);
            case "useremail": return handleUserEmail(req);
            default: return jsonError("Invalid command. Use: sendEmail, validateLoginCode, or userEmail");
        }
    }



    private HttpResponse handleSendEmail( HttpRequest req ) {
        String email = Lib.nvl(req.allParms.get("email"), "");
        if (Lib.isEmpty(email)) return jsonError("Missing email parameter");
        String loginCode = Lib.randToken( "", 6, true );
        Object loginCodesObj = Jsonable.get( persistentMap, List.of("usr",email,"loginCode") );
        Map<?,?> loginCodes = loginCodesObj instanceof Jsonable j ? (Map<?,?>) j.get() : (Map<?,?>) loginCodesObj;
        if (loginCodes!=null) loginCodes.clear();
        Lib.put( persistentMap, List.of("usr",email,"loginCode",loginCode), Lib.timeStamp() );        
        
        Email emailObj = new Email();
        Result<Boolean,Exception> result = emailObj.sendEmail(
            email, "Your "+appName+" login code", Lib.unindent(
                String.format("""
                <html>
                <body>
                    Your login code is: <b>"%s"</b>. <br/>
                    Enter this code on the login page to access your account.
                </body>
                </html>
                """, loginCode )
            ), null, "text/html"
        );
        
        if ( result.isOk() ) {
            return jsonSuccess("Email sent successfully");
        } else {
            Lib.log( "Failed to send email to " + email + ": " + result.err().getMessage() );
            result.err().printStackTrace();
            return jsonError("Failed to send email: " + result.err().getMessage());
        }
    }



    private HttpResponse handleValidateLoginCode( HttpRequest req ) {
        String email = Lib.nvl(req.allParms.get("email"), "");
        String loginCode = Lib.nvl(req.allParms.get("loginCode"), "");
        
        if (Lib.isEmpty(email) || Lib.isEmpty(loginCode)) {
            return jsonError("Missing email or loginCode parameter");
        }
        
        Object stampObj = Jsonable.get( persistentMap, List.of("usr",email,"loginCode",loginCode) );
        Object unwrappedStamp = stampObj instanceof Jsonable j ? j.get() : stampObj;
        String stamp = Lib.nvl( unwrappedStamp, "20010101235959999" );
        long stampMicros = Lib.microsSinceEpoch(stamp);
        long nowMicros = Lib.currentTimeMicros();
        
        if ( nowMicros-stampMicros > loginCodeLifespanMicros ) {
            return jsonError("Login code expired or invalid");
        }
        
        AuthToken tok = AuthToken.newAuthToken(email);
        HttpHeaderBlock resHead = new HttpHeaderBlock("HTTP/1.1", "200", new HashMap<>())
            .withAddHeader("Content-Type", "application/json")
            .withAddHeader("Set-Cookie", "Authorization="+tok.toJson() )
        ;
        
        Map<String,Object> response = new HashMap<>();
        response.put("success", true);
        response.put("authToken", tok.toJson());
        response.put("email", email);
        
        return new HttpResponse( resHead, JsonEncoder.encode(response).getBytes() );
    }



    private HttpResponse handleUserEmail( HttpRequest req ) {
        String email = getEmailAddress(req);
        if (email == null) {
            return jsonError("No valid authentication token found");
        }
        Map<String,Object> response = new HashMap<>();
        response.put("success", true);
        response.put("email", email);
        return jsonResponse(response);
    }



    private HttpResponse jsonSuccess( String message ) {
        Map<String,Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        return jsonResponse(response);
    }



    private HttpResponse jsonError( String message ) {
        Map<String,Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return jsonResponse(response);
    }



    private HttpResponse jsonResponse( Map<String,Object> data ) {
        HttpHeaderBlock resHead = new HttpHeaderBlock("HTTP/1.1", "200", new HashMap<>())
            .withAddHeader("Content-Type", "application/json")
        ;
        return new HttpResponse( resHead, JsonEncoder.encode(data).getBytes() );
    }



    @SuppressWarnings({"unused","unchecked"})
    private static boolean validLoginFlow_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        String originalAppName = HttpLoginHandler.appName;
        HttpLoginHandler.appName = "TestApp";
        HttpLoginHandler handler = new HttpLoginHandler();
        try {
            String testEmail = "test@example.com";
            String loginCode = Lib.randToken("", 6, true);
            Lib.put(persistentMap, List.of("usr", testEmail, "loginCode", loginCode), Lib.timeStamp());
            Map<String,String> headers = new HashMap<>();
            HttpHeaderBlock headerBlock = new HttpHeaderBlock("POST", "/login?command=validateLoginCode&email=" + testEmail + "&loginCode=" + loginCode, headers);
            HttpRequest validateReq = new HttpRequest(headerBlock, new byte[0]);
            HttpResponse response = handler.handle(validateReq);
            Lib.asrt(response.headerBlock.firstLine.contains("200"));
            String responseBody = new String(response.body);
            Map<String,Object> responseData = (Map<String,Object>) JsonDecoder.decode(responseBody);
            Lib.asrt(responseData.get("success").equals(true));
            Lib.asrt(responseData.get("email").equals(testEmail));
            Lib.asrt(responseData.get("authToken") != null);
            String setCookieHeader = response.headerBlock.headers.get("Set-Cookie");
            Lib.asrt(setCookieHeader != null);
            Lib.asrt(setCookieHeader.startsWith("Authorization="));
            return true;
        } finally {
            HttpLoginHandler.appName = originalAppName;
        }
    }



    public static void main( String[] args ) throws Exception { Lib.testClass(); }
}