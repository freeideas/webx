import jLib.LibEmail;

public class TestEmail {
    public static void main(String[] args) {
        try {
            // Send test email to carl@freeideas.com
            LibEmail.sendEmail(
                "ai@ironmedia.com",
                "carl@freeideas.com", 
                "Test Email from Claude",
                "Hello Carl,\n\nThis is a test email sent via the Java email API using Claude.\n\nBest regards,\nClaude (via ai@ironmedia.com)",
                "text/plain"
            );
            System.out.println("Test email sent successfully to carl@freeideas.com");
        } catch (Exception e) {
            System.err.println("Error sending email: " + e.getMessage());
            e.printStackTrace();
        }
    }
}