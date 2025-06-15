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
        
        Email email = new Email();
        Result<Boolean,Exception> result = email.sendEmail( "freeideas@gmail.com", subject, body, "ai@ironmedia.com", "text/plain" );
        
        if ( result.isOk() ) {
            System.out.println( "Test email sent successfully to freeideas@gmail.com" );
            System.out.println( "Subject: " + subject );
        }
        else {
            System.err.println( "Failed to send email: " + result.err().getMessage() );
            result.err().printStackTrace();
        }
    }

}