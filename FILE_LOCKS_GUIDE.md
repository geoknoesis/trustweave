# File Lock Issues on Windows - Causes and Solutions

## What Causes File Locks?

File locks on Windows occur when a process has an open handle to a file or directory. For Gradle builds, the most common causes are:

### 1. **IDE (Cursor/IntelliJ IDEA) - Most Common Cause**
   - **Why**: IDEs keep file handles open for:
     - Indexing and code analysis
     - File watchers for changes
     - Classpath scanning
     - Background compilation
   - **Solution**: Close the IDE completely, or exclude `build/` directories from indexing

### 2. **Gradle Daemon**
   - **Why**: Gradle daemon keeps JAR files in memory/classpath
   - **Solution**: Run `./gradlew --stop` to stop all daemons

### 3. **Java Processes**
   - **Why**: Running applications or tests may have JARs loaded in classpath
   - **Solution**: Kill Java processes (be careful - some may be IDE processes)

### 4. **Windows Explorer**
   - **Why**: Having the build folder open in File Explorer can lock files
   - **Solution**: Close File Explorer windows showing the build directory

### 5. **Antivirus Software**
   - **Why**: Real-time scanning locks files during scan
   - **Solution**: Add build directories to antivirus exclusions

### 6. **Windows Search/Indexing Service**
   - **Why**: Windows Search indexes files, keeping handles open
   - **Solution**: Exclude build directories from Windows Search indexing

### 7. **File System Caching**
   - **Why**: Windows caches file handles for performance
   - **Solution**: Wait a few seconds, or use robocopy trick to clear locks

## Solutions

### Quick Fix: Skip Clean Task
```powershell
./gradlew build -x clean
```
This builds without cleaning, avoiding the file lock issue entirely.

### Solution 1: Stop Gradle Daemons
```powershell
./gradlew --stop
```
Then wait a few seconds and try cleaning again.

### Solution 2: Use Robocopy Trick
```powershell
$buildDir = "C:\Users\steph\work\vericore\vericore-anchor\build"
$emptyDir = "$env:TEMP\empty_$(Get-Random)"
New-Item -ItemType Directory -Path $emptyDir -Force | Out-Null
robocopy $emptyDir $buildDir /MIR /R:0 /W:0 /NFL /NDL /NJH /NJS | Out-Null
Remove-Item $emptyDir -Force
Remove-Item $buildDir -Recurse -Force
```
Robocopy can sometimes delete locked directories that PowerShell cannot.

### Solution 3: Close IDE and Clean
1. Close Cursor IDE completely
2. Close any File Explorer windows showing the build folder
3. Run `./gradlew clean build`

### Solution 4: Configure IDE to Exclude Build Directories
**For IntelliJ/Cursor:**
- File → Settings → Project Structure → Excluded Folders
- Add `build/` directories to exclusions
- This prevents IDE from indexing/locking build files

### Solution 5: Use Handle.exe (Sysinternals)
If you have Handle.exe from Sysinternals:
```powershell
handle.exe vericore-anchor-1.0.0-SNAPSHOT.jar
```
This shows which process has the file locked.

### Solution 6: Restart Windows Explorer
```powershell
taskkill /f /im explorer.exe
start explorer.exe
```
This releases any locks held by File Explorer.

## Prevention

### 1. Exclude Build Directories in IDE
Add to `.idea/workspace.xml` or IDE settings:
```xml
<excluded>
  <directory url="file://$PROJECT_DIR$/build" />
</excluded>
```

### 2. Add to .gitignore
Build directories should already be in `.gitignore`, which helps IDEs ignore them.

### 3. Use Gradle Build Cache
Configure Gradle to use build cache instead of always rebuilding:
```gradle
buildCache {
    local {
        enabled = true
    }
}
```

### 4. Configure Antivirus Exclusions
Add these paths to your antivirus exclusions:
- `C:\Users\steph\work\vericore\**\build\`
- `C:\Users\steph\.gradle\`

## Current Workaround in VeriCore

The `SharedConfig.kt` file already includes a workaround:
- JAR tasks try to rename locked files to `.bak` instead of deleting
- Tasks skip if files are up-to-date
- This reduces but doesn't eliminate file lock issues

## Summary

**Most Common Cause**: IDE (Cursor/IntelliJ) keeping files open for indexing

**Quickest Fix**: `./gradlew build -x clean` (skip clean task)

**Best Long-term Solution**: Exclude `build/` directories from IDE indexing

**If Still Locked**: Close IDE completely, wait 5 seconds, then try again

