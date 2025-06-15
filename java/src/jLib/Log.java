package jLib;
import java.io.*;
import java.util.concurrent.atomic.*;


/**
 * Centralized logging utilities extracted from Lib class
 */
public class Log {
    
    private static final AtomicReference<File> logFile = new AtomicReference<>();
    private static final LruCache<String,Long> logOnceCache = new LruCache<>( -1, 1000*60*60, false );
    
    
    
    /**
     * Log an object to stderr and append to log file
     * @param o Object to log (can be null, Throwable, or any object)
     * @return The same object that was passed in
     */
    public static Object log( Object o ) {
        try {
            String msg = o==null ? "null" : (o instanceof Throwable t) ? Lib.formatException(t) : JsonEncoder.encode(o);
            msg = Lib.timeStamp() + "@" + Thread.currentThread().getName() + ": " + msg;
            System.err.println(msg);
            Lib.append2file( getLogFile(), msg+"\n" );
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return o;
    }
    
    
    
    /**
     * Log a message, but only once per specified time period
     * @param o Object to log
     * @return true if the message was logged, false if it was suppressed
     */
    public static boolean logOnce( Object o ) { 
        return logOnce( o, 5000 ); 
    }
    
    
    
    /**
     * Log a message, but only once per specified time period
     * @param o Object to log
     * @param perMillis Minimum milliseconds between logging the same message
     * @return true if the message was logged, false if it was suppressed
     */
    public static boolean logOnce( Object o, long perMillis ) { 
        return logOnce( null, o, perMillis ); 
    }
    
    
    
    /**
     * Logs a message, but only -- at most -- once per specified time period.
     * Useful for logging potentially over-frequent messages.
     *
     * @param msgID Unique identifier for this message (can be null)
     * @param msg The message to log
     * @param perMillis Minimum milliseconds between logging the same message
     * @return true if the message was logged, false if it was suppressed
     *
     * WARNING: a cache entry per msgID is stored for up to an hour,
     *          so do not use a large number of unique msgIDs.
     * NOTE: perMillis over one hour is treated as one hour,
     *       so we don't need to store more than an hour of entries.
    **/
    public static boolean logOnce( String msgID, Object msg, long perMillis ) {
        if (msgID==null) msgID="";
        if ( perMillis < 1 ) perMillis = 5000;
        if (msg==null) msg=msgID;
        String msgTxt = msg.toString();
        boolean didLog = false;
        long now = System.currentTimeMillis();
        Long lastLogTime = logOnceCache.get(msgID);
        if (lastLogTime==null) {
            lastLogTime = 0L;
        }
        if ( now - lastLogTime > perMillis ) {
            didLog = true;
            log(msgTxt);
            logOnceCache.put(msgID,now);
        }
        return didLog;
    }
    
    
    
    public static Object logException( Object msg ) {
        RuntimeException re = msg instanceof RuntimeException r ? r : null;
        String s = msg instanceof String str ? str : LibString.toString(msg);
        try { if (re==null) throw new RuntimeException(s); } catch ( RuntimeException e ) { re = e; }
        log(re);
        return msg;
    }


    
    /**
     * Get the default log file
     * @return The log file
     */
    public static File getLogFile() {
        File f = logFile.get();
        if (f==null) {
            f = new File( "./log/"+LibApp.getAppName()+".log" );
            logFile.set(f);
        }
        return f;
    }
    
    
    
    /**
     * Set a custom log file
     * @param f The new log file to use
     */
    public static void setLogFile( File f ) {
        logFile.set(f);
    }
    
    
    
    @SuppressWarnings("unused")
    private static boolean logOnce_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        
        // Clear the cache before testing
        logOnceCache.clear();
        
        // Test basic logOnce(Object, long)
        LibTest.asrt(  logOnce( "testing logOnce", 500 ) ); // first time should work
        LibTest.asrt(! logOnce( "testing logOnce", 500 ) ); // second time should be suppressed
        try{ Thread.sleep(501); }catch(InterruptedException ignore){}
        LibTest.asrt( logOnce( "testing logOnce", 500 ) ); // after wait for message to expire, should work again
        
        // Test logOnce(Object) with default 5000ms
        String uniqueMsg = "default timing test " + System.currentTimeMillis();
        LibTest.asrt(  logOnce( uniqueMsg ) );
        LibTest.asrt(! logOnce( uniqueMsg ) );
        
        // Test logOnce with msgID
        LibTest.asrt(  logOnce( "msgID1", "message 1", 100 ) );
        LibTest.asrt(! logOnce( "msgID1", "message 1", 100 ) );
        LibTest.asrt(  logOnce( "msgID2", "message 2", 100 ) ); // different msgID should work
        
        // Test edge cases
        LibTest.asrt(  logOnce( null, "null msgID", 100 ) );
        LibTest.asrt(  logOnce( "msgID3", null, 100 ) ); // null msg should use msgID
        
        return true;
    }
    
    
    
    @SuppressWarnings("unused")
    private static boolean log_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        
        // Test basic logging
        Object result = log("Test message");
        LibTest.asrtEQ(result, "Test message");
        
        // Test exception logging
        Exception e = new Exception("Test exception");
        result = log(e);
        LibTest.asrt(result instanceof Exception);
        
        // Test null logging
        result = log(null);
        LibTest.asrt(result == null);
        
        // Test complex object logging (should use JsonEncoder)
        java.util.Map<String,Object> map = new java.util.HashMap<>();
        map.put("key", "value");
        map.put("number", 42);
        result = log(map);
        LibTest.asrt(result instanceof java.util.Map);
        
        return true;
    }
    
    
    
    @SuppressWarnings("unused")
    private static boolean logFile_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        
        // Test default log file
        File defaultFile = getLogFile();
        LibTest.asrt( defaultFile != null );
        LibTest.asrt( defaultFile.getPath().contains("log/") );
        LibTest.asrt( defaultFile.getPath().endsWith(".log") );
        
        // Test setting custom log file
        File customFile = new File("./log/custom_test.log");
        setLogFile(customFile);
        LibTest.asrtEQ( getLogFile(), customFile );
        
        // Test that logging works with custom file
        log("Testing custom log file");
        
        // Reset to null to test default behavior again
        logFile.set(null);
        File newDefault = getLogFile();
        LibTest.asrt( !newDefault.equals(customFile) );
        
        return true;
    }
    
    
    
    public static void main( String[] args ) throws Exception {
        LibTest.testClass( Log.class );
    }
}