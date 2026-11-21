package com.nl.kaviyy.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsSerializer
import com.nl.kaviyy.core.datastore.CredentialFile
import com.nl.kaviyy.core.datastore.CredentialSerializer
import com.nl.kaviyy.core.datastore.TrackerFile
import com.nl.kaviyy.core.datastore.TrackerSerializer
import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    private const val KEYSET_NAME = "credentials_keyset"
    private const val KEYSET_PREFS = "kaviyy_keysets" // holds only the Keystore-encrypted Tink keyset
    private const val MASTER_KEY_URI = "android-keystore://kaviyy_credentials_master"

    @Provides
    @Singleton
    fun credentialAead(
        @ApplicationContext context: Context,
    ): Aead {
        AeadConfig.register()
        return AndroidKeysetManager
            .Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    @Provides
    @Singleton
    fun credentialDataStore(
        @ApplicationContext context: Context,
        aead: Aead,
        @Dispatcher(KaviyyDispatchers.IO) io: CoroutineDispatcher,
    ): DataStore<CredentialFile> =
        DataStoreFactory.create(
            serializer = CredentialSerializer(aead),
            corruptionHandler = ReplaceFileCorruptionHandler { CredentialFile() },
            scope = CoroutineScope(io + SupervisorJob()),
            produceFile = { context.dataStoreFile("credentials.pb") },
        )

    /** Tracker tokens: same Keystore-wrapped key, separate file and associated data. */
    @Provides
    @Singleton
    fun trackerDataStore(
        @ApplicationContext context: Context,
        aead: Aead,
        @Dispatcher(KaviyyDispatchers.IO) io: CoroutineDispatcher,
    ): DataStore<TrackerFile> =
        DataStoreFactory.create(
            serializer = TrackerSerializer(aead),
            corruptionHandler = ReplaceFileCorruptionHandler { TrackerFile() },
            scope = CoroutineScope(io + SupervisorJob()),
            produceFile = { context.dataStoreFile("trackers.pb") },
        )

    @Provides
    @Singleton
    fun appSettingsDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(KaviyyDispatchers.IO) io: CoroutineDispatcher,
    ): DataStore<AppSettings> =
        DataStoreFactory.create(
            serializer = AppSettingsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
            scope = CoroutineScope(io + SupervisorJob()),
            produceFile = { context.dataStoreFile("settings.pb") },
        )
}
