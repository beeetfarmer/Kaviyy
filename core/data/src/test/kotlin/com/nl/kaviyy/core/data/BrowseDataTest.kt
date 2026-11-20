package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.SeriesMetadataEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.PersonDto
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowseDataTest {
    @Test
    fun rolesMapToTheirFilterFields() {
        assertEquals(FilterField.Writers, personField(ROLE_WRITER))
        assertEquals(FilterField.Penciller, personField(4))
        assertEquals(FilterField.Characters, personField(11))
        assertEquals(FilterField.Location, personField(15))
        assertNull(personField(0))
        assertNull(personField(16))
    }

    @Test
    fun peopleCachedBeforeRolesCountAsWriters() {
        val legacy = SeriesMetadataEntity(1, 7, writersJson = """[{"id":8,"name":"Mira"}]""").toDomain()
        assertEquals(listOf(NamedRef(8, "Mira", ROLE_WRITER)), legacy.writers)
    }

    @Test
    fun anAllLibrariesQueryHasNoLibraryStatement() {
        val rule = FilterRule(FilterField.Genres, Comparison.Contains, listOf(RuleValue("4", "Fantasy")))
        val dto = LibraryQuery(null, SeriesFilter(rules = listOf(rule))).toFilter()
        assertEquals(listOf(FilterField.Genres.api), dto.statements.map { it.field })
        assertEquals(
            listOf(
                FilterField.LIBRARIES_API,
                18,
            ),
            LibraryQuery(3, SeriesFilter(rules = listOf(rule))).toFilter().statements.map {
                it.field
            },
        )
    }

    @Test
    fun blankPersonDescriptionIsNull() =
        runTest {
            var description: String? = "  "
            val api =
                object : FakeKavitaApi() {
                    override suspend fun person(name: String) = PersonDto(1, name, description)
                }
            val repo = KavitaPersonRepository { api }
            val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
            assertEquals(AppResult.Success(null), repo.description(server, "Mira"))
            description = "Writer of Ember Road"
            assertEquals(AppResult.Success("Writer of Ember Road"), repo.description(server, "Mira"))
        }
}
