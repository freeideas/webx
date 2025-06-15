package jLib;
// requires e.g. jsch-0.1.55.jar or above
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import java.io.*;
import java.util.*;

public class SshClient implements AutoCloseable  {

    private Session session = null;
    private ChannelSftp sftpChan = null;

    // Constructor for password authentication
    public SshClient(String sshHost, String sshUser, String sshPassword) throws IOException {
        this(sshHost, sshUser, sshPassword, null);
    }

    // Constructor for private key authentication
    public SshClient(String sshHost, String sshUser, File privateKeyFile) throws IOException {
        this(sshHost, sshUser, null, privateKeyFile);
    }

    // Private constructor to handle both authentication methods
    private SshClient(String sshHost, String sshUser, String sshPassword, File privateKeyFile) throws IOException {
        try {
            JSch jsch = new JSch();
            if (privateKeyFile != null) {
                jsch.addIdentity(privateKeyFile.getAbsolutePath());
            }
            session = jsch.getSession(sshUser, sshHost, 22);
            if (sshPassword != null) {
                session.setPassword(sshPassword);
            }
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
        } catch (JSchException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() throws Exception {
        if (sftpChan != null && sftpChan.isConnected() ) {
            sftpChan.disconnect();
        }
        if (session != null && session.isConnected() ) {
            session.disconnect();
        }
    }

    public long upload( InputStream src, String remoteFilePath, boolean append ) throws IOException {
        try {
            openSftp();
            sftpChan.put( src, remoteFilePath, append ? ChannelSftp.APPEND : ChannelSftp.OVERWRITE );
            return sftpChan.lstat(remoteFilePath).getSize();
        } catch ( IOException ioe ) {
            throw ioe;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public InputStream download( String remoteFilePath, long startPos ) throws IOException {
        try {
            openSftp();
            return sftpChan.get(remoteFilePath,null,startPos);
        } catch ( IOException ioe ) {
            throw ioe;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
    public InputStream download( String remoteFilePath ) throws IOException {
        return download(remoteFilePath,0);
    }

    public boolean rm( String remoteFilePath ) throws IOException {
        try {
            openSftp();
            try{
                sftpChan.rm(remoteFilePath);
                return fileLength(remoteFilePath) < 0;
            } catch( SftpException e ) {
                throw new IOException(e);
            }
        } catch ( IOException ioe ) {
            throw ioe;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public boolean rename( String remoteFilePath, String newRemoteFilePath ) throws IOException {
        try {
            openSftp();
            if ( fileLength(newRemoteFilePath) >= 0 ) throw new IOException("File already exists: "+newRemoteFilePath);
            sftpChan.rename(remoteFilePath,newRemoteFilePath);
            return fileLength(newRemoteFilePath) >= 0;
        } catch ( IOException ioe ) {
            throw ioe;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public long fileLength( String remoteFilePath ) throws IOException {
        try {
            openSftp();
            SftpATTRS attrs = sftpChan.lstat(remoteFilePath);
            return attrs.getSize();
        } catch ( IOException ioe ) {
            throw ioe;
        } catch ( SftpException e ) {
            if ( e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE ) return -1;
            throw new IOException(e);
        }
    }

    public int osCmd( String cmd ) throws IOException {
        return osCmd( cmd, (InputStream)null, (OutputStream)null, (OutputStream)null );
    }

    public int osCmd( String cmd, String stdin, StringBuffer stdout, StringBuffer stderr ) throws IOException {
        try {
            ByteArrayOutputStream baosStdout = stdout==null?null: new ByteArrayOutputStream();
            ByteArrayOutputStream baosStderr = stdout==null?null: new ByteArrayOutputStream();
            byte[] stdinBytes = stdin==null ? null : stdin.getBytes("UTF-8");
            ByteArrayInputStream baisStdin = stdinBytes==null ? null : new ByteArrayInputStream(stdinBytes);
            int exitStatus = osCmd( cmd, baisStdin, baosStdout, baosStderr );
            if (stdout!=null) stdout.append( baosStdout.toString("UTF-8") );
            if (stderr!=null) stderr.append( baosStderr.toString("UTF-8") );
            return exitStatus;
        } catch ( IOException ioe ) {
            throw ioe;
        } catch ( Exception e ) {
            throw new IOException(e);
        }
    }

    public int osCmd(
        String cmd, InputStream stdin, OutputStream stdout, OutputStream stderr
    ) throws IOException {
        try {
            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(cmd);
            if (stdin!=null) channelExec.setInputStream(stdin);
            if (stdout!=null) channelExec.setOutputStream(stdout);
            if (stdout!=null) channelExec.setErrStream(stderr);
            channelExec.connect();
            while ( !channelExec.isClosed() ) Thread.sleep(100);
            int exitStatus = channelExec.getExitStatus();
            channelExec.disconnect();
            return exitStatus;
        } catch ( Exception e ) {
            throw new IOException(e);
        }
    }

    public Lib.Pair<InputStream,InputStream> osCmd( String cmd, InputStream stdin ) throws IOException {
        try {
            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            PipedOutputStream stdoutPipe = new PipedOutputStream();
            PipedOutputStream stderrPipe = new PipedOutputStream();
            PipedInputStream stdoutIn = new PipedInputStream(stdoutPipe);
            PipedInputStream stderrIn = new PipedInputStream(stderrPipe);
            channelExec.setCommand(cmd);
            channelExec.setInputStream(stdin);
            channelExec.setOutputStream(stdoutPipe);
            channelExec.setErrStream(stderrPipe);
            channelExec.connect();
            Thread t = new Thread( () -> {
                try {
                    while ( !channelExec.isClosed() ) Thread.sleep(100);
                    channelExec.disconnect();
                    try{ stdoutPipe.close(); }catch(Throwable ignore){}
                    try{ stderrPipe.close(); }catch(Throwable ignore){}
                } catch (Exception ignore) {}
            });
            t.setName( this.getClass().getSimpleName()+".osCmd_"+Lib.currentTimeMicros() );
            t.setDaemon(true);
            t.start();
            return new Lib.Pair<>( stdoutIn, stderrIn );
        } catch ( JSchException e ) {
            throw new IOException(e);
        }
    }

    private void openSftp() throws IOException {
        if (sftpChan!=null) return;
        try {
            sftpChan = (ChannelSftp) session.openChannel("sftp");
            sftpChan.connect();
        } catch ( JSchException e ) {
            throw new IOException(e);
        }
    }

    private static boolean _TEST_( boolean findLineNumber ) throws Exception {
		if (findLineNumber) throw new RuntimeException();
        Jsonable creds = Lib.loadCreds();
        Object hostObj = creds.get( "LINMAIN/ADDR" );
        String sftpHost = hostObj instanceof Jsonable j ? (String) j.get() : (String) hostObj;
        Object userObj = creds.get( "LINMAIN/USER" );
        String sftpUser = userObj instanceof Jsonable j ? (String) j.get() : (String) userObj;
        Object passObj = creds.get( "LINMAIN/PASS" );
        String sftpPass = passObj instanceof Jsonable j ? (String) j.get() : (String) passObj;
        String localFilePath = "./misc/test.txt";
        String s = """
            The quick brown fox jumps over the lazy dog.
        """;
        File localFile = new File(localFilePath);
        localFile.delete();
        Lib.append2file( localFile, s );
        long fileSize = new File(localFilePath).length();
        Lib.asrt( fileSize > 0 );
        String remoteFilePath = "test.txt";
        try( SshClient sshCli = new SshClient(sftpHost,sftpUser,sftpPass); ) {
            long remoteFileSize = sshCli.upload( new FileInputStream(localFilePath), remoteFilePath, false );
            Lib.asrtEQ(fileSize,remoteFileSize);
            try( InputStream inp = sshCli.download(remoteFilePath); ) {
                byte[] buf = new byte[(int)fileSize];
                int totalRead = 0;
                int read;
                while ( totalRead < buf.length && (read = inp.read(buf, totalRead, buf.length - totalRead)) != -1 ) {
                    totalRead += read;
                }
                String testS = new String(buf);
                Lib.asrtEQ(s,testS);
            }
            Lib.asrt( sshCli.rm(remoteFilePath) );
            Lib.asrt( sshCli.fileLength(remoteFilePath) < 0 );
            Lib.Pair<InputStream,InputStream> pair = sshCli.osCmd( "touch "+remoteFilePath, null );
            int c = pair.a.read();
            Lib.asrt( c == -1 );
            c = pair.b.read();
            Lib.asrt( c == -1 );
            Lib.asrt( sshCli.fileLength("test.txt") == 0 );
            Lib.asrt( sshCli.rm(remoteFilePath) );
            sshCli.osCmd( "touch "+remoteFilePath );
            Lib.asrt( sshCli.fileLength("test.txt") == 0 );
            Lib.asrt( sshCli.rm(remoteFilePath) );
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        Lib.testClass( SshClient.class );
    }
}
