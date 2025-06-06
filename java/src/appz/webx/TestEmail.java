package appz.webx;

import jLib.*;
import java.util.Date;

/**
 * Test email functionality by sending a test email
 */
public class TestEmail {



    public static void main( String[] args ) {
        String timestamp = new Date().toString();
        String subject = "Test email from Claude AI - " + timestamp;
        String body = "Hello!\n\nThis is a test email sent from Claude AI using the ai@ironmedia.com account.\n\n" +
                      "The email functionality has been successfully set up and tested.\n\n" + 
                      "Timestamp: " + timestamp + "\n\n" +
                      "Best regards,\nClaude AI";
        
        try {
            LibEmail.sendEmail( "ai@ironmedia.com", "freeideas@gmail.com", subject, body, "text/plain" );
            System.out.println( "Test email sent successfully to freeideas@gmail.com" );
            System.out.println( "Subject: " + subject );
        }
        catch ( Exception e ) {
            System.err.println( "Failed to send email: " + e.getMessage() );
            e.printStackTrace();
        }
    }

}