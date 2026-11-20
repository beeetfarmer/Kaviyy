package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.apiCall
import javax.inject.Inject

/** A person page's header (network only). */
interface PersonRepository {
    /** The person's description (server text; sanitize before display), null when none. */
    suspend fun description(
        server: Server,
        name: String,
    ): AppResult<String?>
}

class KavitaPersonRepository
    @Inject
    constructor(
        private val apis: ServerApis,
    ) : PersonRepository {
        override suspend fun description(
            server: Server,
            name: String,
        ): AppResult<String?> =
            apiCall { apis.api(server).person(name) }.map { it.description?.takeIf(String::isNotBlank) }
    }
