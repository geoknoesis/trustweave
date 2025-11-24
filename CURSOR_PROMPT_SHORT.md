# Cursor.ai Prompt (Short Version)

```
Investigate the bug in Kotlin Gradle Plugin 2.2.21 where `kotlin.build.archivesTaskOutputAsFriendModule=false` property is read correctly by Gradle but ignored by the plugin.

CONTEXT:
- Property is set in gradle.properties: kotlin.build.archivesTaskOutputAsFriendModule=false
- Gradle reads it correctly (verified: project.findProperty returns "false")
- Plugin ignores it: compileKotlin still depends on jar, causing circular dependency
- Circular dependency: compileKotlin → jar → classes → compileJava → compileKotlin
- Documentation: https://kotlinlang.org/docs/gradle-configure-project.html#other-details

TASK:
1. Clone: git clone https://github.com/JetBrains/kotlin.git && cd kotlin && git checkout v2.2.21
2. Navigate to: libraries/tools/gradle-plugin
3. Search for "archivesTaskOutputAsFriendModule" to find where property is read
4. Search for "dependsOn.*jar" or "compileKotlin.*dependsOn" to find where dependency is created
5. Trace the code flow: Does the property value reach the dependency creation code? Is there a conditional check?
6. Compare with v2.2.20: git diff v2.2.20..v2.2.21 -- libraries/tools/gradle-plugin/

FIND THE BUG:
- Where is the property read but not used?
- Where are dependencies created without checking the property?
- Is there a timing issue (dependencies created before property read)?
- Is the conditional logic wrong?

DELIVERABLES:
- Exact file and line number of the bug
- Explanation of what's wrong
- Proposed fix
- Minimal patch

The bug is likely: property read but value not passed to dependency creation, or dependencies created before property check, or missing/incorrect conditional logic.
```

