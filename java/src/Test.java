import java.util.*;
import jLib.Lib;

public class Test {

    public static void main(String[] args) {
        String[] classes = {
            "http.AuthToken",
            "http.HttpErrorHandler",
            "http.HttpErrorResponse",
            "http.HttpFileHandler",
            "http.HttpHandler",
            "http.HttpHeaderBlock",
            "http.HttpJsonHandler",
            "http.HttpLoginHandler",
            "http.HttpMessage",
            "http.HttpProxyHandler",
            "http.HttpRequest",
            "http.HttpResponse",
            "http.HttpServer",
            "jLib.JsonDecoder",
            "jLib.JsonEncoder",
            "jLib.JsonSerializable",
            "jLib.Lib",
            "jLib.LruCache",
            "jLib.ParseArgs",
            "jLib.Result",
            "persist.PersistentData",
            "persist.PersistentList",
            "persist.PersistentMap",
            "build.Build",
            "build.DownloadJars",
            "build.HelloRoot"
        };

        int totalTests = 0;
        int passedClasses = 0;
        int failedClasses = 0;
        List<String> failedClassNames = new ArrayList<>();

        for (String className : classes) {
            try {
                Class<?> clazz = Class.forName(className);
                System.out.println("\n=== Testing " + className + " ===");
                boolean testsPassed = Lib.testClass(clazz);
                if (testsPassed) {
                    totalTests++;
                    passedClasses++;
                    System.out.println("✓ Tests passed");
                } else {
                    failedClasses++;
                    failedClassNames.add(className);
                    System.out.println("✗ Tests failed");
                }
            } catch (Exception e) {
                failedClasses++;
                failedClassNames.add(className);
                System.out.println("\n=== Testing " + className + " ===");
                System.out.println("✗ Failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (Error e) {
                failedClasses++;
                failedClassNames.add(className);
                System.out.println("\n=== Testing " + className + " ===");
                System.out.println("✗ Failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        System.out.println("\n" + "=".repeat(50));
        System.out.println("SUMMARY:");
        System.out.println("Total classes tested: " + totalTests);
        System.out.println("Classes with passing tests: " + passedClasses);
        System.out.println("Classes with failures: " + failedClasses);
        if (!failedClassNames.isEmpty()) {
            System.out.println("Failed classes: " + String.join(", ", failedClassNames));
        }
        System.out.println("=".repeat(50));
    }
}