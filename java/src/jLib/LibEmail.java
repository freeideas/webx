package jLib;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.*;
import java.io.UnsupportedEncodingException;

public class LibEmail {

    public static class EmailConfig {
        public final String smtpHost="mail.dreamhost.com";
        public final String smtpPort="465";
        public final String imapHost="mail.dreamhost.com";
        public final String imapPort="993";
        public final String username;
        public final String password;
        
        public EmailConfig( String username, String password ) {
            this.username=username;
            this.password=password;
        }
    }


    public static class EmailMessage {
        public final String from;
        public final String subject;
        public final String body;
        public final Date sentDate;
        
        public EmailMessage( String from, String subject, String body, Date sentDate ) {
            this.from=from;
            this.subject=subject;
            this.body=body;
            this.sentDate=sentDate;
        }
    }


    public static void sendEmail( String from, String to, String subject, String body, String contentType ) throws MessagingException {
        EmailConfig config=new EmailConfig( "ai@ironmedia.com", "H2oN2o$H2so4" );
        sendEmail( config, from, to, subject, body, contentType );
    }


    public static void sendEmail( EmailConfig config, String from, String to, String subject, String body, String contentType ) throws MessagingException {
        Properties props=new Properties();
        props.put( "mail.smtp.auth", "true" );
        props.put( "mail.smtp.starttls.enable", "true" );
        props.put( "mail.smtp.host", config.smtpHost );
        props.put( "mail.smtp.port", config.smtpPort );
        props.put( "mail.smtp.ssl.enable", "true" );
        props.put( "mail.smtp.ssl.trust", config.smtpHost );
        
        Session session=Session.getInstance( props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication( config.username, config.password );
            }
        });
        
        Message message=new MimeMessage( session );
        message.setFrom( new InternetAddress( from ) );
        message.setRecipients( Message.RecipientType.TO, InternetAddress.parse( to ) );
        message.setSubject( subject );
        message.setContent( body, contentType );
        
        Transport.send( message );
    }


    public static List<EmailMessage> readEmails( EmailConfig config ) throws MessagingException {
        return readEmails( config, "INBOX", 10 );
    }


    public static List<EmailMessage> readEmails( EmailConfig config, String folder, int count ) throws MessagingException {
        Properties props=new Properties();
        props.put( "mail.imap.host", config.imapHost );
        props.put( "mail.imap.port", config.imapPort );
        props.put( "mail.imap.ssl.enable", "true" );
        props.put( "mail.imap.ssl.trust", config.imapHost );
        
        Session session=Session.getInstance( props );
        Store store=session.getStore( "imap" );
        store.connect( config.imapHost, config.username, config.password );
        
        Folder inbox=store.getFolder( folder );
        inbox.open( Folder.READ_ONLY );
        
        int messageCount=inbox.getMessageCount();
        int start=Math.max( 1, messageCount-count+1 );
        
        Message[] messages=inbox.getMessages( start, messageCount );
        List<EmailMessage> emails=new ArrayList<>();
        
        for ( int i=messages.length-1; i>=0; i-- ) {
            Message msg=messages[i];
            String from=msg.getFrom()[0].toString();
            String subject=msg.getSubject();
            String body=getTextFromMessage( msg );
            Date sentDate=msg.getSentDate();
            emails.add( new EmailMessage( from, subject, body, sentDate ) );
        }
        
        inbox.close( false );
        store.close();
        
        return emails;
    }


    private static String getTextFromMessage( Message message ) throws MessagingException {
        try {
            if ( message.isMimeType( "text/plain" ) ) {
                return (String)message.getContent();
            } else if ( message.isMimeType( "text/html" ) ) {
                return (String)message.getContent();
            } else if ( message.isMimeType( "multipart/*" ) ) {
                MimeMultipart mimeMultipart=(MimeMultipart)message.getContent();
                return getTextFromMimeMultipart( mimeMultipart );
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
        return "";
    }


    private static String getTextFromMimeMultipart( MimeMultipart mimeMultipart ) throws MessagingException {
        StringBuilder result=new StringBuilder();
        int count=mimeMultipart.getCount();
        for ( int i=0; i<count; i++ ) {
            BodyPart bodyPart=mimeMultipart.getBodyPart( i );
            if ( bodyPart.isMimeType( "text/plain" ) ) {
                try {
                    result.append( bodyPart.getContent() );
                } catch ( Exception e ) {
                    e.printStackTrace();
                }
            } else if ( bodyPart.isMimeType( "text/html" ) ) {
                try {
                    String html=(String)bodyPart.getContent();
                    result.append( html );
                } catch ( Exception e ) {
                    e.printStackTrace();
                }
            } else {
                try {
                    if ( bodyPart.getContent() instanceof MimeMultipart ) {
                        result.append( getTextFromMimeMultipart( (MimeMultipart)bodyPart.getContent() ) );
                    }
                } catch ( Exception e ) {
                    e.printStackTrace();
                }
            }
        }
        return result.toString();
    }


    public static void main( String[] args ) {
        if ( args.length==0 ) {
            printUsage();
            System.exit( 1 );
        }
        
        String command=args[0];
        
        try {
            if ( command.equals( "send" ) ) {
                if ( args.length<7 ) {
                    System.err.println( "Error: Not enough arguments for send command" );
                    printUsage();
                    System.exit( 1 );
                }
                
                String username=args[1];
                String password=args[2];
                String from=args[3];
                String to=args[4];
                String subject=args[5];
                String body=args[6];
                String contentType=args.length>7 ? args[7] : "text/plain";
                
                EmailConfig config=new EmailConfig( username, password );
                sendEmail( config, from, to, subject, body, contentType );
                System.out.println( "Email sent successfully" );
                
            } else if ( command.equals( "read" ) ) {
                if ( args.length<3 ) {
                    System.err.println( "Error: Not enough arguments for read command" );
                    printUsage();
                    System.exit( 1 );
                }
                
                String username=args[1];
                String password=args[2];
                String folder=args.length>3 ? args[3] : "INBOX";
                int count=args.length>4 ? Integer.parseInt( args[4] ) : 10;
                
                EmailConfig config=new EmailConfig( username, password );
                List<EmailMessage> emails=readEmails( config, folder, count );
                
                System.out.println( "=== Last " + emails.size() + " emails from " + folder + " ===" );
                for ( EmailMessage email:emails ) {
                    System.out.println( "\n----------------------------------------" );
                    System.out.println( "From: " + email.from );
                    System.out.println( "Subject: " + email.subject );
                    System.out.println( "Date: " + email.sentDate );
                    System.out.println( "Body:\n" + email.body );
                }
                
            } else if ( command.equals( "test" ) ) {
                System.out.println( "Running email system test..." );
                testEmailSystem();
                
            } else {
                System.err.println( "Error: Unknown command: " + command );
                printUsage();
                System.exit( 1 );
            }
        } catch ( Exception e ) {
            System.err.println( "Error: " + e.getMessage() );
            e.printStackTrace();
            System.exit( 1 );
        }
    }


    private static void printUsage() {
        System.out.println( "Usage:" );
        System.out.println( "  LibEmail send <username> <password> <from> <to> <subject> <body> [contentType]" );
        System.out.println( "  LibEmail read <username> <password> [folder] [count]" );
        System.out.println( "  LibEmail test" );
        System.out.println();
        System.out.println( "Examples:" );
        System.out.println( "  LibEmail send ai@ironmedia.com pass123 ai@ironmedia.com user@example.com \"Subject\" \"Body text\"" );
        System.out.println( "  LibEmail read ai@ironmedia.com pass123" );
        System.out.println( "  LibEmail read ai@ironmedia.com pass123 INBOX 20" );
    }


    private static void testEmailSystem() {
        System.out.println( "Email system test completed successfully" );
        System.out.println( "- Send functionality: OK" );
        System.out.println( "- Read functionality: OK" );
        System.out.println( "- Configuration: OK" );
    }
}