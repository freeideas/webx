package http;
import java.util.*;
import java.util.regex.*;
import jLib.*;
import persist.PersistentData;



public class AuthToken implements JsonSerializable {



    public static final long MAX_AGE_SECONDS = 30*24*60*60; // 30 days
    public static Pattern jsonPattern = Pattern.compile( LibString.nw( """
        \\{"email":".+@.+","created":"20.+","sig":".+"\\}
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
        Object created = map.get("created");
        long createTimeMicros = created instanceof String ? 
            Lib.microsSinceEpoch((String)created) : 
            ((Number)created).longValue();
        return new AuthToken(
            (String) map.get("email"),
            createTimeMicros,
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
        try { // garbage-collect any over-old tokens
            @SuppressWarnings("unchecked")
            Object tokenzObj = Jsonable.get( persistentMap, List.of("usr", email, "tokenz") );
            Map<Object,Object> tokenzMap = tokenzObj instanceof Jsonable j ? (Map<Object,Object>) j.get() : (Map<Object,Object>) tokenzObj;
            if ( tokenzMap == null ) tokenzMap = Map.of();
            long currentTimeMicros = Lib.currentTimeMicros();
            long maxAgeMicros = MAX_AGE_SECONDS * 1000000;
            for ( Map.Entry<Object,Object> entry : tokenzMap.entrySet() ) {
                Object key = entry.getKey();
                if ( !(key instanceof Long tokenTimeMicros) ) continue;
                if ( currentTimeMicros - tokenTimeMicros <= maxAgeMicros ) continue;
                tokenzMap.remove( tokenTimeMicros );
            }
        } catch ( Exception e ) { Log.logException(e); }
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
        Object sigObj = Jsonable.get( persistentMap, List.of("usr",email,"tokenz",createTimeMicros) );
        String validSig = sigObj instanceof Jsonable j ? (String) j.get() : (String) sigObj;
        return signature.equals(validSig);
    }



    @SuppressWarnings("unused")
    private static boolean _TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        AuthToken tok = newAuthToken("user@host.com");
        LibTest.asrt( tok.isValid() );
        LibTest.asrt( tok.invalidateAll() );
        LibTest.asrt(! tok.isValid() );
        tok = newAuthToken("user@host.com");
        LibTest.asrt( tok.isValid() );
        LibTest.asrt( tok.invalidate() );
        LibTest.asrt(! tok.isValid() );
        return true;
    }



    public static void main( String[] args ) { LibTest.testClass(); }
}
