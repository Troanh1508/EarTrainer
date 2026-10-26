package com.troanhmolo.eartrainer.ui.compare

import com.troanhmolo.eartrainer.exercise.ExerciseKind

/**
 * What to compare, as plain names so it can travel in a navigation route: two answers' stable
 * names (e.g. "MINOR_SIXTH") and the [scope] they were mixed up under, the style, chord target or
 * mode that was being looked at, or null for all of them.
 */
data class ComparisonRequest(
    val exercise: ExerciseKind,
    val scope: String?,
    val first: String,
    val second: String,
) {
    val route: String get() = "compare/${exercise.name}/${scope ?: ALL}/$first/$second"

    companion object {
        const val ROUTE_PATTERN = "compare/{exercise}/{scope}/{first}/{second}"
        private const val ALL = "ALL"

        /** Exercises with a comparison drill. */
        val SUPPORTED: Set<ExerciseKind> = ExerciseKind.entries.toSet()

        /** Null if any part does not name a known value, so a bad route never builds a drill. */
        fun fromRoute(exercise: String?, scope: String?, first: String?, second: String?): ComparisonRequest? {
            val kind = ExerciseKind.entries.find { it.name == exercise }?.takeIf { it in SUPPORTED } ?: return null
            if (first.isNullOrEmpty() || second.isNullOrEmpty() || first == second) return null
            return ComparisonRequest(kind, scope?.takeIf { it != ALL }, first, second)
        }
    }
}
