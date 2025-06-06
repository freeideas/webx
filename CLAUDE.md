# CRITICAL: CODE FORMATTING
ALL code changes must strictly follow CODE_GUIDELINES.md:
- Brevity above all concerns
- Spaces inside parentheses with punctuation: `if ( condition )`
- No spaces around operators: `a==b && b==c`
- Early returns to avoid nesting
- 3 blank lines between methods
- Single-line methods: `public void method() { callMethod(); }`

<system-reminder>
MANDATORY: Every code edit must follow CODE_GUIDELINES.md exactly. Check:
- Parentheses spacing: `methodName( arg1, arg2 )` vs `methodName()`
- Operator spacing: `a==b && b==c`
- 3 blank lines between methods
- Early returns instead of nesting
- Single-line methods when possible
</system-reminder>

## Build Commands

All scripts mentioned below can be run from any directory - they automatically change to the project root.

### Java

#### Compile all Java files
```bash
./java/javac.sh
```
```powershell
powershell.exe -File ./java/javac.ps1
```

#### Compile specific file or directory
```bash
# Compile a single file
./java/javac.sh java/src/HelloRoot.java

# Compile a directory
./java/javac.sh java/src/appz/sside
```
```powershell
# Compile a single file
powershell.exe -File ./java/javac.ps1 java/src/HelloRoot.java

# Compile a directory
powershell.exe -File ./java/javac.ps1 java/src/appz/sside
```

#### Run Java applications
Replace `[ClassName]` with the fully qualified class name (e.g., `appz.sside.http.HttpMessage`):
```bash
./java/java.sh [ClassName]
```
```powershell
powershell.exe -File ./java/java.ps1 [ClassName]
```

#### Examples
```bash
# Compile and run HelloRoot
./java/javac.sh java/src/HelloRoot.java
./java/java.sh HelloRoot

# Compile and run a package class
./java/javac.sh java/src/appz/sside
./java/java.sh appz.sside.http.HttpMessage
```
```powershell
# Compile and run HelloRoot
powershell.exe -File ./java/javac.ps1 java/src/HelloRoot.java
powershell.exe -File ./java/java.ps1 HelloRoot

# Compile and run a package class
powershell.exe -File ./java/javac.ps1 java/src/appz/sside
powershell.exe -File ./java/java.ps1 appz.sside.http.HttpMessage
```

### Download Maven dependencies
```bash
./java/java.sh DownloadJars
```
```powershell
powershell.exe -File ./java/java.ps1 DownloadJars
```

## Project Architecture

This is a multi-language project with both Java and Python components, organized around shared utilities and specialized applications.

### Application Structure

**Java Applications (`java/src/appz/`)**
- Each subdirectory contains a complete application
- Apps follow consistent patterns: Main class, configuration, utilities
- Examples: `kitchensync` (file sync), `aiassist` (AI tools), `play` (web apps)

### Key Patterns

**Java**
- Minimal testing framework using `_TEST_` method naming
- Early returns and flat code structure (per CODE_GUIDELINES.md)

**Python**
- Module path setup via `find_mylibs()` function
- Type hints with forward references
- Class-based organization with utility mixins

### Data Storage
- Logs and temporary files stored in `log/` directory
- Test files and assets in `datafiles/files4testing/`

### Code Style
**MANDATORY**: Follow the strict formatting guidelines in CODE_GUIDELINES.md:
- Brevity above all other concerns
- Early returns to avoid nesting
- Minimal comments (usually only when marked with NOTE; usually only for overview and surprises)
- Specific spacing rules for parentheses and operators
- 120 character line limit

**⚠️ REMINDER: Always check CODE_GUIDELINES.md before writing any code ⚠️**

### Testing
- Java: Methods ending in `_TEST_` that return boolean
- Use `Lib.asrt()` and `Lib.asrtEQ()` for assertions
- Run tests via `Lib.testClass()` method
- **IMPORTANT**: After modifying any Java file containing `_TEST_` methods and a `main` method with `Lib.testClass()`, always run the class to verify tests still pass. Replace `[ClassName]` with the fully qualified class name:
  ```bash
  ./java/javac.sh && ./java/java.sh [ClassName]
  ```
  ```powershell
  powershell.exe -File ./java/javac.ps1; powershell.exe -File ./java/java.ps1 [ClassName]
  ```

### Dependencies
- Java uses Maven configuration in `java/mvn_config.cfg`
- Python dependencies managed per-module