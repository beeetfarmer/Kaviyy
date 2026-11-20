package com.nl.kaviyy.feature.filter

import com.nl.kaviyy.core.data.Comparison
import com.nl.kaviyy.core.data.FieldKind
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.FilterRule
import com.nl.kaviyy.core.data.RuleValue
import java.time.LocalDate
import java.time.format.DateTimeParseException

private const val MAX_PERCENT = 100.0
private val NUMERIC_KINDS = setOf(FieldKind.Number, FieldKind.Percent, FieldKind.Year, FieldKind.Date)

/** Why a draft can't be saved yet. */
enum class DraftError { Empty, NotANumber, OutOfRange, NotADate }

/** A rule being added or edited. [text] is the typed value for non-option kinds. */
data class RuleDraft(
    val field: FilterField,
    val comparison: Comparison = field.kind.comparisons.first(),
    val selected: List<RuleValue> = emptyList(),
    val index: Int? = null,
) {
    /** Options kinds pick from a list; "at most / at least" pick exactly one. */
    val usesOptions: Boolean get() = this.field.source != null

    val singleChoice: Boolean
        get() = comparison == Comparison.LessThanEqual || comparison == Comparison.GreaterThanEqual

    /** Date fields take a date for before/after and a number of days for in-last. */
    val expectsDate: Boolean
        get() =
            this.field.kind == FieldKind.Date &&
                (comparison == Comparison.IsBefore || comparison == Comparison.IsAfter)

    fun withComparison(c: Comparison): RuleDraft =
        copy(comparison = c, selected = if (c.isSingle()) selected.take(1) else selected)

    fun toggle(value: RuleValue): RuleDraft =
        copy(
            selected =
                when {
                    value in selected -> selected - value
                    singleChoice -> listOf(value)
                    else -> selected + value
                },
        )

    /** The finished rule, or why not. Booleans and options use [selected]; others validate [text]. */
    fun build(text: String): Result<FilterRule> {
        val values = if (usesOptions || field.kind == FieldKind.Boolean) selected else listOf(RuleValue(text.trim()))
        val error =
            if (values.isEmpty() ||
                values.all { it.code.isBlank() }
            ) {
                DraftError.Empty
            } else {
                validate(values.first().code)
            }
        return if (error == null) {
            Result.success(FilterRule(field, comparison, values))
        } else {
            Result.failure(DraftException(error))
        }
    }

    private fun validate(code: String): DraftError? {
        val kind = field.kind
        val n = code.toDoubleOrNull()
        return when {
            expectsDate -> DraftError.NotADate.takeUnless { isDate(code) }

            kind !in NUMERIC_KINDS -> null

            n == null -> DraftError.NotANumber

            // In-last takes whole days.
            kind == FieldKind.Date && (n < 1 || n % 1 != 0.0) -> DraftError.OutOfRange

            kind == FieldKind.Percent && n !in 0.0..MAX_PERCENT -> DraftError.OutOfRange

            else -> null
        }
    }

    private fun Comparison.isSingle() = this == Comparison.LessThanEqual || this == Comparison.GreaterThanEqual

    private fun isDate(code: String) =
        try {
            LocalDate.parse(code)
            true
        } catch (_: DateTimeParseException) {
            false
        }

    companion object {
        fun from(
            rule: FilterRule,
            index: Int,
        ) = RuleDraft(rule.field, rule.comparison, rule.values, index)
    }
}

class DraftException(
    val error: DraftError,
) : IllegalArgumentException(error.name)
