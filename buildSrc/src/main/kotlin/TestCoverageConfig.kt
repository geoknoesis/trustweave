import org.gradle.api.Project

/**
 * Configuration constants for test coverage using Kover.
 * 
 * These values represent the minimum coverage thresholds recommended for VeriCore plugins.
 * 
 * To configure Kover in your build.gradle.kts, add:
 * ```kotlin
 * kover {
 *     reports {
 *         verify {
 *             rule {
 *                 bound {
 *                     minValue = TestCoverageConfig.MIN_LINE_COVERAGE
 *                     metric = kotlinx.kover.api.CounterType.LINE
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
object TestCoverageConfig {
    
    /**
     * Minimum line coverage threshold (80%).
     */
    const val MIN_LINE_COVERAGE = 0.80
    
    /**
     * Minimum branch coverage threshold (75%).
     */
    const val MIN_BRANCH_COVERAGE = 0.75
    
    /**
     * Minimum method coverage threshold (80%).
     */
    const val MIN_METHOD_COVERAGE = 0.80
    
    /**
     * Configures Kover for a project.
     * This is a placeholder - actual Kover configuration should be done in build.gradle.kts
     * using the constants above.
     */
    fun configure(project: Project) {
        // Kover configuration is done via build.gradle.kts DSL
        // This method exists for future extensibility
    }
}

