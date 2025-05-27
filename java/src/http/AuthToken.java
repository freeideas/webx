package http;
import java.util.*;
import java.util.regex.*;

import jLib.JsonDecoder;
import jLib.JsonEncoder;
import jLib.JsonSerializable;
import jLib.Lib;
import persist.PersistentData;



public class AuthToken implements JsonSerializable {



    public static final long MAX_AGE_SECONDS = 30*24*60*60; // 30 days
    public static Pattern jsonPattern = Pattern.compile( Lib.nw( """
        "\\{"email":".+@.+","created":20.+,"sig":".+"\\}"
    """ ) );




    @SuppressWarnings({"rawtypes","resource"})
    private static Map persistentMap = new PersistentData().getRootMap();
    public final long createTimeMicros;
    public final String email;
    public final String signature;



    public AuthToken( String email, long createTimeMicros, String signature ) {
        this.createTimeMicros = createTimeMicros;
        this.email = email;
        this.signature = signature;
    }



    @Override
    public String toJson() {
        return JsonEncoder.encode( Lib.mapOf(
            "email", email,
            "created", Lib.timeStamp(createTimeMicros),
            "sig", signature
        ) );
    }



    public static AuthToken fromJson( CharSequence json ) {
        Map<?,?> map = JsonDecoder.decodeMap(json);
        return new AuthToken(
            (String) map.get("email"),
            Lib.toLong( map.get("created") ),
            (String) map.get("sig")
        );
    }



    public static AuthToken find( CharSequence cs ) {
        Matcher mat = jsonPattern.matcher(cs);
        if (! mat.find() ) return null;
        return fromJson( mat.group() );
    }



    public static AuthToken newAuthToken( String email ) {
        long createTimeMicros = Lib.currentTimeMicros();
        email = email.toLowerCase();
        String signature = Lib.hashPassword(email+":"+createTimeMicros);
        Lib.put( persistentMap, List.of("usr",email,"tokenz",createTimeMicros), signature );
        // TODO: remove overage tokens
        return new AuthToken(email,createTimeMicros,signature);
    }



    public boolean invalidate() {
        if (! isValid() ) return false;
        Lib.put( persistentMap, List.of("usr",email,"tokenz",createTimeMicros), null );
        return true;
    }



    /*
     * Logs this user out of all devices
     */
    public boolean invalidateAll() {
        Lib.put( persistentMap, List.of("usr",email,"tokenz"), Map.of() );
        return true;
    }



    public boolean isValid() {
        if ( Lib.currentTimeMicros()-createTimeMicros > MAX_AGE_SECONDS*1000000 ) return false;
        String validSig = (String) Lib.get( persistentMap, List.of("usr",email,"tokenz",createTimeMicros) );
        return signature.equals(validSig);
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        AuthToken tok = newAuthToken("user@host.com");
        Lib.asrt( tok.isValid() );
        Lib.asrt( tok.invalidateAll() );
        Lib.asrt(! tok.isValid() );
        tok = newAuthToken("user@host.com");
        Lib.asrt( tok.isValid() );
        Lib.asrt( tok.invalidate() );
        Lib.asrt(! tok.isValid() );
        return true;
    }



    public static void main( String[] args ) { Lib.testClass(); }
}
