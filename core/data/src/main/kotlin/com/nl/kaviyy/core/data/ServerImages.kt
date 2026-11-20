package com.nl.kaviyy.core.data

import android.content.Context
import coil3.ImageLoader
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ServerHttpClients
import com.nl.kaviyy.core.network.ServerImageLoaders
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** One Coil [ImageLoader] per server: its own authenticated client and disk cache (`cacheDir/images/{id}`). */
@Singleton
class ServerImages
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val clients: ServerHttpClients,
        private val session: AuthSession,
    ) {
        private val loaders = ConcurrentHashMap<Server, ImageLoader>()

        fun loader(server: Server): ImageLoader =
            loaders.getOrPut(server) {
                ServerImageLoaders.create(context, server, clients.create(server, session.credentialSource(server.id)))
            }
    }
