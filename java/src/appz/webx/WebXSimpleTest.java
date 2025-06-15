package appz.webx;

import java.io.*;
import java.net.*;
import java.sql.*;
import jLib.*;
import http.HttpServer;

public class WebXSimpleTest {

    public static void main(String[] args) throws Exception { LibTest.testClass(); }

    private static synchronized int getNextPort() { return 15000 + (int)(System.currentTimeMillis() % 1000); }

    private static boolean waitForServer(int port, int timeoutSeconds) {
        for (int i = 0; i < timeoutSeconds; i++) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("localhost", port), 1000);
                socket.close();
                return true;
            } catch (Exception e) { }
            try { Thread.sleep(1000); } catch (InterruptedException e) { return false; }
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static boolean proxyEndpointTest_TEST_(boolean findLineNumber) throws Exception {
        if (findLineNumber) throw new RuntimeException();

        int testPort = getNextPort();
        String testDbUrl = "jdbc:hsqldb:mem:dbtest" + testPort;
        String shutdownCode = "SHUTDOWN" + testPort;
        Thread serverThread = null;

        try {
            serverThread = new Thread(() -> {
                try {
                    Main.main(new String[]{
                        "--port=" + testPort,
                        "--db=db@" + testDbUrl,
                        "--shutdown=" + shutdownCode,
                        "--run"
                    });
                } catch (Exception e) { }
            });
            serverThread.start();

            if (!waitForServer(testPort, 30)) return false;

            // Test GET request through proxy
            String getUrl = "http://localhost:" + testPort + "/webx/proxy";
            HttpURLConnection getConn = (HttpURLConnection) URI.create(getUrl).toURL().openConnection();
            getConn.setRequestMethod("GET");
            getConn.setRequestProperty("X-Target-URL", "https://jsonplaceholder.typicode.com/posts/1");

            int getResponseCode = getConn.getResponseCode();
            String getResponse = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                getResponse = sb.toString();
            }
            getConn.disconnect();

            boolean getSuccess = (getResponseCode == 200) && getResponse.contains("\"id\": 1") && getResponse.contains("\"title\":");

            // Test database write
            String testResults = String.format(
                "{\"proxyTest\":{\"timestamp\":\"%s\",\"testType\":\"proxy-endpoint-test\",\"success\":%s}}",
                java.time.Instant.now().toString(), getSuccess
            );

            HttpURLConnection dbConn = (HttpURLConnection) URI.create("http://localhost:" + testPort + "/webx/db").toURL().openConnection();
            dbConn.setRequestMethod("POST");
            dbConn.setRequestProperty("Content-Type", "application/json");
            dbConn.setDoOutput(true);

            try (java.io.OutputStream os = dbConn.getOutputStream()) {
                os.write(testResults.getBytes("UTF-8"));
            }

            int dbResponseCode = dbConn.getResponseCode();
            dbConn.disconnect();

            // Verify database contents
            boolean dbWriteSuccess = false;
            try (Connection conn = DriverManager.getConnection(testDbUrl, "SA", "");
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT * FROM webx_data WHERE key_json LIKE '%proxyTest%'");
                dbWriteSuccess = rs.next();
            }

            return getSuccess && dbResponseCode == 200 && dbWriteSuccess;
        } finally {
            if (serverThread != null && serverThread.isAlive()) {
                try {
                    String timestamp = HttpServer.shutdownTimestamp();
                    URL shutdownUrl = URI.create("http://localhost:" + testPort + "/webx/" + shutdownCode + timestamp).toURL();
                    HttpURLConnection conn = (HttpURLConnection) shutdownUrl.openConnection();
                    conn.getResponseCode();
                    conn.disconnect();
                } catch (Exception e) { serverThread.interrupt(); }
                try { serverThread.join(5000); } catch (InterruptedException e) { }
            }
        }
    }

    @SuppressWarnings("unused")
    private static boolean databaseEndpointTest_TEST_(boolean findLineNumber) throws Exception {
        if (findLineNumber) throw new RuntimeException();

        int testPort = getNextPort();
        String testDbUrl = "jdbc:hsqldb:mem:dbtest" + testPort;
        String shutdownCode = "SHUTDOWN" + testPort;
        Thread serverThread = null;

        try {
            serverThread = new Thread(() -> {
                try {
                    Main.main(new String[]{
                        "--port=" + testPort,
                        "--db=db@" + testDbUrl,
                        "--shutdown=" + shutdownCode,
                        "--run"
                    });
                } catch (Exception e) { }
            });
            serverThread.start();

            if (!waitForServer(testPort, 30)) return false;

            // Test database endpoint with direct HTTP request
            URL dbUrl = URI.create("http://localhost:" + testPort + "/webx/db").toURL();
            HttpURLConnection conn = (HttpURLConnection) dbUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String testJson = "{\"dbTest\":{\"timestamp\":\"2025-05-31T22:30:00.000Z\",\"testType\":\"simple-db-test\"}}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(testJson.getBytes());
            }

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            // Verify database writes
            boolean dbWriteSuccess = false;
            try (Connection dbConn = DriverManager.getConnection(testDbUrl, "SA", "");
                 Statement stmt = dbConn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT * FROM webx_data WHERE key_json = '\"dbTest\"'");
                dbWriteSuccess = rs.next();
            }

            return responseCode == 200 && dbWriteSuccess;
        } finally {
            if (serverThread != null && serverThread.isAlive()) {
                try {
                    String timestamp = HttpServer.shutdownTimestamp();
                    URL shutdownUrl = URI.create("http://localhost:" + testPort + "/webx/" + shutdownCode + timestamp).toURL();
                    HttpURLConnection conn = (HttpURLConnection) shutdownUrl.openConnection();
                    conn.getResponseCode();
                    conn.disconnect();
                } catch (Exception e) { serverThread.interrupt(); }
                try { serverThread.join(5000); } catch (InterruptedException e) { }
            }
        }
    }
}
