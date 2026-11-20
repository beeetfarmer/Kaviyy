package com.nl.kaviyy.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Device connectivity. Interface = test seam. */
fun interface NetworkMonitor {
    fun isOnline(): Flow<Boolean>
}

/** Default network has validated internet. Needs `ACCESS_NETWORK_STATE`. */
class ConnectivityNetworkMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NetworkMonitor {
        override fun isOnline(): Flow<Boolean> =
            callbackFlow {
                val cm = checkNotNull(context.getSystemService(ConnectivityManager::class.java))
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            caps: NetworkCapabilities,
                        ) {
                            trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }
                    }
                val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities)
                trySend(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
                cm.registerDefaultNetworkCallback(callback)
                awaitClose { cm.unregisterNetworkCallback(callback) }
            }.conflate().distinctUntilChanged()
    }
