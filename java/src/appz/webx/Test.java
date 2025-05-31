package appz.webx;

import java.io.*;
import java.util.*;
import jLib.*;

public class Test {

    public static void main(String[] args) {
        // Automatically discover all classes with _TEST_ methods
        List<String> classNames = discoverTestClasses();
        
        int totalTests = 0;
        int passedClasses = 0;
        int failedClasses = 0;
        List<String> failedClassNames = new ArrayList<>();

        for (String className : classNames) {
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
    
    // Automatically discover classes containing _TEST_ methods
    private static List<String> discoverTestClasses() {
        List<String> testClasses = new ArrayList<>();
        
        // Scan source directories for .java files
        String[] sourceDirs = {"java/src", "src"};
        
        for (String sourceDir : sourceDirs) {
            File dir = new File(sourceDir);
            if (dir.exists() && dir.isDirectory()) {
                scanDirectory(dir, "", testClasses);
            }
        }
        
        Collections.sort(testClasses);
        return testClasses;
    }
    
    private static void scanDirectory(File dir, String packageName, List<String> testClasses) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                String subPackage = packageName.isEmpty() 
                    ? file.getName() 
                    : packageName + "." + file.getName();
                scanDirectory(file, subPackage, testClasses);
            } else if (file.getName().endsWith(".java")) {
                String className = file.getName().substring(0, file.getName().length() - 5);
                String fullClassName = packageName.isEmpty() 
                    ? className 
                    : packageName + "." + className;
                
                // Check if this class has _TEST_ methods by scanning the source file
                if (hasTestMethods(file)) {
                    testClasses.add(fullClassName);
                }
            }
        }
    }
    
    private static boolean hasTestMethods(File javaFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(javaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Look for method signatures containing "_TEST_"
                if (line.trim().contains("_TEST_") && 
                    (line.contains("boolean") || line.contains("void")) &&
                    line.contains("(") && 
                    !line.trim().startsWith("//") &&
                    !line.trim().startsWith("*")) {
                    return true;
                }
            }
        } catch (IOException e) {
            // If we can't read the file, skip it
        }
        return false;
    }
}