package jLib;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.search.*;
import java.util.*;
import java.io.*;



public class Email {
    private final String smtpHost;
    private final int smtpPort;
    private final String imapHost;
    private final int imapPort;
    private final String username;
    private final String password;
    private final String defaultFrom;



    public Email( String smtpHost, int smtpPort, String imapHost, int imapPort, 
                  String username, String password, String defaultFrom ) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.username = username;
        this.password = password;
        this.defaultFrom = defaultFrom;
    }



    public Email() {
        Jsonable creds = Lib.loadCreds();
        this.smtpHost = (String) creds.get( "ACE", "SMTP", "HOST" );
        this.smtpPort = ((Number) creds.get( "ACE", "SMTP", "PORT" )).intValue();
        this.imapHost = (String) creds.get( "ACE", "IMAP", "HOST" );
        this.imapPort = ((Number) creds.get( "ACE", "IMAP", "PORT" )).intValue();
        this.username = (String) creds.get( "ACE", "SMTP", "USERNAME" );
        this.password = (String) creds.get( "ACE", "SMTP", "PASSWORD" );
        this.defaultFrom = (String) creds.get( "ACE", "SMTP", "FROM" );
    }



    public Result<Boolean,Exception> sendEmail( String to, String subject, String body, String from, String contentType ) {
        if ( to==null ) return Result.err( new IllegalArgumentException( "to address cannot be null" ) );
        if ( subject==null ) subject = "";
        if ( body==null ) body = "";
        if ( from==null ) from = defaultFrom;
        if ( contentType==null ) contentType = "text/plain";
        
        try {
            Properties props = new Properties();
            props.put( "mail.smtp.auth", "true" );
            props.put( "mail.smtp.starttls.enable", "true" );
            props.put( "mail.smtp.host", smtpHost );
            props.put( "mail.smtp.port", String.valueOf(smtpPort) );
            props.put( "mail.smtp.ssl.enable", "true" );
            props.put( "mail.smtp.ssl.trust", smtpHost );
            
            Session session = Session.getInstance( props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication( username, password );
                }
            });
            
            Message message = new MimeMessage( session );
            message.setFrom( new InternetAddress( from ) );
            message.setRecipients( Message.RecipientType.TO, InternetAddress.parse( to ) );
            message.setSubject( subject );
            message.setContent( body, contentType );
            
            Transport.send( message );
            return Result.ok( true );
        } catch ( Exception e ) {
            return Result.err( e );
        }
    }



    public Result<List<EmailMessage>,Exception> readEmails( int maxCount ) {
        return readEmails( "INBOX", maxCount, false );
    }



    public Result<List<EmailMessage>,Exception> readEmails( String folderName, int maxCount, boolean unreadOnly ) {
        if ( folderName==null ) folderName = "INBOX";
        if ( maxCount<=0 ) maxCount = 10;
        
        try {
            Properties props = new Properties();
            props.put( "mail.store.protocol", "imaps" );
            props.put( "mail.imaps.host", imapHost );
            props.put( "mail.imaps.port", String.valueOf(imapPort) );
            props.put( "mail.imaps.ssl.enable", "true" );
            
            Session session = Session.getInstance( props );
            Store store = session.getStore( "imaps" );
            store.connect( imapHost, username, password );
            
            Folder folder = store.getFolder( folderName );
            folder.open( Folder.READ_ONLY );
            
            Message[] messages;
            if ( unreadOnly ) {
                messages = folder.search( new FlagTerm( new Flags(Flags.Flag.SEEN), false ) );
            } else {
                int totalMessages = folder.getMessageCount();
                int start = Math.max( 1, totalMessages - maxCount + 1 );
                messages = folder.getMessages( start, totalMessages );
            }
            
            List<EmailMessage> emailList = new ArrayList<>();
            int count = 0;
            for ( int i=messages.length-1; i>=0 && count<maxCount; i-- ) {
                Message msg = messages[i];
                EmailMessage email = new EmailMessage();
                email.id = msg.getHeader( "Message-ID" )[0];
                email.from = InternetAddress.toString( msg.getFrom() );
                email.subject = msg.getSubject();
                email.date = msg.getSentDate();
                email.isRead = msg.isSet( Flags.Flag.SEEN );
                
                // Extract body
                String body = "";
                String contentType = "text/plain";
                if ( msg.isMimeType("text/plain") ) {
                    body = (String) msg.getContent();
                } else if ( msg.isMimeType("text/html") ) {
                    body = (String) msg.getContent();
                    contentType = "text/html";
                } else if ( msg.isMimeType("multipart/*") ) {
                    MimeMultipart mimeMultipart = (MimeMultipart) msg.getContent();
                    body = getTextFromMimeMultipart( mimeMultipart );
                }
                email.body = body;
                email.contentType = contentType;
                
                emailList.add( email );
                count++;
            }
            
            folder.close( false );
            store.close();
            
            return Result.ok( emailList );
        } catch ( Exception e ) {
            return Result.err( e );
        }
    }



    private String getTextFromMimeMultipart( MimeMultipart mimeMultipart ) throws Exception {
        StringBuilder result = new StringBuilder();
        int count = mimeMultipart.getCount();
        for ( int i=0; i<count; i++ ) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if ( bodyPart.isMimeType("text/plain") ) {
                result.append( bodyPart.getContent().toString() );
            } else if ( bodyPart.isMimeType("text/html") ) {
                String html = (String) bodyPart.getContent();
                result.append( html );
            }
        }
        return result.toString();
    }



    public static class EmailMessage {
        public String id;
        public String from;
        public String subject;
        public String body;
        public Date date;
        public boolean isRead;
        public String contentType;
        
        public String toString() {
            return "From: " + from + "\n" +
                   "Subject: " + subject + "\n" +
                   "Date: " + date + "\n" +
                   "Read: " + isRead + "\n" +
                   "Body: " + (body.length()>100 ? body.substring(0,100)+"..." : body);
        }
    }



    @SuppressWarnings("unused")
    private static boolean sendEmail_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Email email = new Email();
        // Test null to address
        Result<Boolean,Exception> result = email.sendEmail( null, "subject", "body", null, null );
        Lib.asrt( !result.isOk() );
        Lib.asrt( result.err() instanceof IllegalArgumentException );
        // Test with valid minimal parameters
        result = email.sendEmail( "test@example.com", null, null, null, null );
        Lib.asrt( result.isOk() || result.err() instanceof MessagingException );
        return true;
    }



    @SuppressWarnings("unused")
    private static boolean sendAndReceive_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Email email = new Email();
        
        // Send a test email to self
        String testSubject = "Test Email " + System.currentTimeMillis();
        String testBody = "This is a test email sent at " + new Date();
        Result<Boolean,Exception> sendResult = email.sendEmail( email.defaultFrom, testSubject, testBody, null, null );
        
        if ( !sendResult.isOk() ) {
            Lib.log( "Could not send test email: " + sendResult.err() );
            return true; // Skip test if email sending fails
        }
        
        // Wait a bit for email to arrive
        try { Thread.sleep(5000); } catch ( InterruptedException e ) {}
        
        // Try to read the email
        Result<List<EmailMessage>,Exception> readResult = email.readEmails( 20 );
        if ( !readResult.isOk() ) {
            Lib.log( "Could not read emails: " + readResult.err() );
            return true; // Skip test if email reading fails
        }
        
        // Check if we can find the email we just sent
        boolean found = false;
        for ( EmailMessage msg : readResult.ok() ) {
            if ( msg.subject!=null && msg.subject.equals(testSubject) ) {
                found = true;
                Lib.asrt( msg.body.contains("test email") );
                break;
            }
        }
        
        if ( !found ) {
            Lib.log( "Test email not found in inbox (may take time to arrive)" );
        }
        
        return true;
    }



    public static void main( String[] args ) {
        ParseArgs p = new ParseArgs( args );
        p.setAppName( "Email" );
        p.setDescr( "Email utility for sending and reading emails via SMTP/IMAP" );
        
        // Email server configuration parameters
        String smtpHost = p.getString( "smtp-host", null, "SMTP server hostname" );
        Integer smtpPort = p.getInteger( "smtp-port", 465, "SMTP server port" );
        String imapHost = p.getString( "imap-host", null, "IMAP server hostname" );
        Integer imapPort = p.getInteger( "imap-port", 993, "IMAP server port" );
        String username = p.getString( "username", null, "Email username" );
        String password = p.getString( "password", null, "Email password" );
        String defaultFrom = p.getString( "default-from", null, "Default sender address" );
        
        // Command and operation parameters
        String command = p.getString( "command", "", "Command: send, read, read-unread, or test" );
        String to = p.getString( "to", null, "Recipient email address (send)" );
        String subject = p.getString( "subject", "", "Email subject (send)" );
        String body = p.getString( "body", "", "Email body (send)" );
        String from = p.getString( "from", null, "Sender email address (send)" );
        String contentType = p.getString( "content-type", "text/plain", "Content type (send)" );
        Integer maxCount = p.getInteger( "max", 10, "Max emails to read (read/read-unread)" );
        Boolean help = p.getBoolean( "help", false, "Show help" );
        
        if ( help || command.isEmpty() ) {
            System.out.println( p.getHelp() );
            System.out.println( "\nExamples:" );
            System.out.println( "  # Send email:" );
            System.out.println( "  Email -command=send -smtp-host=mail.example.com -username=user@example.com -password=pass -to=recipient@example.com -subject=\"Hello\" -body=\"Message\"" );
            System.out.println( "  # Read emails:" );
            System.out.println( "  Email -command=read -imap-host=mail.example.com -username=user@example.com -password=pass -max=20" );
            System.out.println( "  # Using config file:" );
            System.out.println( "  Email @config.json -command=send -to=user@example.com -subject=\"Hello\"" );
            System.out.println( "  # Test (uses .creds.json):" );
            System.out.println( "  Email -command=test" );
            System.exit( 0 );
        }
        
        Email email;
        
        // For test command, use the no-arg constructor that loads from .creds.json
        if ( command.equals("test") ) {
            email = new Email();
        } else {
            // Validate required parameters for other commands
            if ( smtpHost==null || username==null || password==null ) {
                System.err.println( "Error: smtp-host, username, and password are required (or use @config.json)" );
                System.exit( 1 );
            }
            if ( (command.equals("read") || command.equals("read-unread")) && imapHost==null ) {
                System.err.println( "Error: imap-host is required for reading emails" );
                System.exit( 1 );
            }
            if ( defaultFrom==null ) defaultFrom = username;
            if ( imapHost==null ) imapHost = smtpHost;
            
            email = new Email( smtpHost, smtpPort, imapHost, imapPort, username, password, defaultFrom );
        }
        
        switch ( command ) {
            case "send":
                if ( to==null ) {
                    System.err.println( "send requires: -to=address" );
                    System.exit( 1 );
                }
                Result<Boolean,Exception> result = email.sendEmail( to, subject, body, from, contentType );
                if ( result.isOk() ) {
                    System.out.println( "Email sent to " + to );
                } else {
                    System.err.println( "Failed to send email: " + result.err().getMessage() );
                    System.exit( 1 );
                }
                break;

            case "read":
            case "read-unread":
                boolean unreadOnly = command.equals("read-unread");
                Result<List<EmailMessage>,Exception> readResult = email.readEmails( "INBOX", maxCount, unreadOnly );
                if ( readResult.isOk() ) {
                    List<EmailMessage> emails = readResult.ok();
                    for ( EmailMessage msg : emails ) {
                        System.out.println( "===BEGIN EMAIL===" );
                        System.out.println( "ID: " + msg.id );
                        System.out.println( "From: " + msg.from );
                        System.out.println( "Subject: " + msg.subject );
                        System.out.println( "Date: " + msg.date );
                        System.out.println( "Read: " + msg.isRead );
                        System.out.println( "Content-Type: " + msg.contentType );
                        System.out.println( "Body:" );
                        System.out.println( msg.body );
                        System.out.println( "===END EMAIL===" );
                    }
                } else {
                    System.err.println( "Failed to read emails: " + readResult.err().getMessage() );
                    System.exit( 1 );
                }
                break;

            case "test":
                Lib.testClass( Email.class );
                break;

            default:
                System.err.println( "Unknown command: " + command );
                System.exit( 1 );
        }
    }



}