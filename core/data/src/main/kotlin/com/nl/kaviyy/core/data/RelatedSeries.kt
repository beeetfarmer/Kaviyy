package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.network.RelatedSeriesDto
import com.nl.kaviyy.core.network.SeriesDto

/** Kavita `RelationKind`, in the order the Related tab lists them. */
enum class Relation {
    Parent,
    Prequel,
    Sequel,
    SpinOff,
    SideStory,
    Adaptation,
    AlternativeSetting,
    AlternativeVersion,
    Edition,
    Annual,
    Contains,
    Character,
    Doujinshi,
    Other,
}

data class RelatedSeries(
    val relation: Relation,
    val series: SeriesEntity,
)

internal fun RelatedSeriesDto.flatten(): List<Pair<Relation, SeriesDto>> =
    listOf(
        Relation.Parent to parent,
        Relation.Prequel to prequels,
        Relation.Sequel to sequels,
        Relation.SpinOff to spinOffs,
        Relation.SideStory to sideStories,
        Relation.Adaptation to adaptations,
        Relation.AlternativeSetting to alternativeSettings,
        Relation.AlternativeVersion to alternativeVersions,
        Relation.Edition to editions,
        Relation.Annual to annuals,
        Relation.Contains to contains,
        Relation.Character to characters,
        Relation.Doujinshi to doujinshis,
        Relation.Other to others,
    ).flatMap { (relation, list) -> list.map { relation to it } }
