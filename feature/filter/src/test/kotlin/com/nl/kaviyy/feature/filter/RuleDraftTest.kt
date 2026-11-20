package com.nl.kaviyy.feature.filter

import com.nl.kaviyy.core.data.Comparison
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.FilterRule
import com.nl.kaviyy.core.data.RuleValue
import org.junit.Test
import kotlin.test.assertEquals

class RuleDraftTest {
    private fun error(
        draft: RuleDraft,
        text: String = "",
    ) = (draft.build(text).exceptionOrNull() as? DraftException)?.error

    @Test
    fun typedValuesAreValidatedPerKind() {
        assertEquals(DraftError.Empty, error(RuleDraft(FilterField.SeriesName), "  "))
        assertEquals(
            "Ember",
            RuleDraft(FilterField.SeriesName)
                .build(" Ember ")
                .getOrThrow()
                .values
                .single()
                .code,
        )
        assertEquals(DraftError.NotANumber, error(RuleDraft(FilterField.ReleaseYear), "soon"))
        assertEquals(DraftError.OutOfRange, error(RuleDraft(FilterField.ReadProgress), "120"))
        assertEquals(null, error(RuleDraft(FilterField.ReadProgress), "100"))
        val date = RuleDraft(FilterField.ReadingDate, Comparison.IsAfter)
        assertEquals(DraftError.NotADate, error(date, "31/01/2024"))
        assertEquals(null, error(date, "2024-01-31"))
        val inLast = date.withComparison(Comparison.IsInLast)
        assertEquals(DraftError.OutOfRange, error(inLast, "0"))
        assertEquals(null, error(inLast, "30"))
    }

    @Test
    fun optionsToggleAndAtMostKeepsOne() {
        val a = RuleValue("1", "Teen")
        val b = RuleValue("2", "Mature")
        var d = RuleDraft(FilterField.AgeRating).toggle(a).toggle(b)
        assertEquals(listOf(a, b), d.selected)
        d = d.withComparison(Comparison.LessThanEqual)
        assertEquals(listOf(a), d.selected)
        assertEquals(listOf(b), d.toggle(b).selected) // single choice replaces
        assertEquals(DraftError.Empty, error(RuleDraft(FilterField.Genres)))
        assertEquals(FilterRule(FilterField.AgeRating, Comparison.LessThanEqual, listOf(a)), d.build("").getOrThrow())
    }

    @Test
    fun editingStartsFromTheRule() {
        val rule = FilterRule(FilterField.Genres, Comparison.NotContains, listOf(RuleValue("7", "Drama")))
        val d = RuleDraft.from(rule, 2)
        assertEquals(2, d.index)
        assertEquals(rule, d.build("").getOrThrow())
    }
}
