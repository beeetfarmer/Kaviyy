package com.nl.kaviyy.core.data.di

import com.nl.kaviyy.core.data.AdminRepository
import com.nl.kaviyy.core.data.AnnotationRepository
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.BookFileRepository
import com.nl.kaviyy.core.data.BookmarkRepository
import com.nl.kaviyy.core.data.CachedBookFileRepository
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.ConnectivityNetworkMonitor
import com.nl.kaviyy.core.data.HomeRepository
import com.nl.kaviyy.core.data.ImageReaderRepository
import com.nl.kaviyy.core.data.KavitaAdminRepository
import com.nl.kaviyy.core.data.KavitaImageReaderRepository
import com.nl.kaviyy.core.data.KavitaPersonRepository
import com.nl.kaviyy.core.data.KavitaProfileRepository
import com.nl.kaviyy.core.data.KavitaReviewRepository
import com.nl.kaviyy.core.data.KavitaSearchRepository
import com.nl.kaviyy.core.data.KavitaSmartFilterRepository
import com.nl.kaviyy.core.data.ListsRepository
import com.nl.kaviyy.core.data.NetworkMonitor
import com.nl.kaviyy.core.data.OfflineFirstAnnotationRepository
import com.nl.kaviyy.core.data.OfflineFirstBookmarkRepository
import com.nl.kaviyy.core.data.OfflineFirstCatalogRepository
import com.nl.kaviyy.core.data.OfflineFirstHomeRepository
import com.nl.kaviyy.core.data.OfflineFirstListsRepository
import com.nl.kaviyy.core.data.OfflineFirstProgressRepository
import com.nl.kaviyy.core.data.OfflineFirstReadStateRepository
import com.nl.kaviyy.core.data.OfflineFirstReaderSettingsRepository
import com.nl.kaviyy.core.data.OfflineFirstReadingListRepository
import com.nl.kaviyy.core.data.PersonRepository
import com.nl.kaviyy.core.data.ProfileRepository
import com.nl.kaviyy.core.data.ProgressRepository
import com.nl.kaviyy.core.data.ReadStateRepository
import com.nl.kaviyy.core.data.ReaderDisplayRepository
import com.nl.kaviyy.core.data.ReaderSettingsRepository
import com.nl.kaviyy.core.data.ReadingListRepository
import com.nl.kaviyy.core.data.ReviewRepository
import com.nl.kaviyy.core.data.SearchRepository
import com.nl.kaviyy.core.data.ServerApis
import com.nl.kaviyy.core.data.SessionRepository
import com.nl.kaviyy.core.data.SharedPrefsReaderDisplayRepository
import com.nl.kaviyy.core.data.SmartFilterRepository
import com.nl.kaviyy.core.network.KavitaApis
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun session(repository: SessionRepository): AuthSession

    @Binds
    fun home(repository: OfflineFirstHomeRepository): HomeRepository

    @Binds
    fun catalog(repository: OfflineFirstCatalogRepository): CatalogRepository

    @Binds
    fun readerSettings(repository: OfflineFirstReaderSettingsRepository): ReaderSettingsRepository

    @Binds
    fun imageReader(repository: KavitaImageReaderRepository): ImageReaderRepository

    @Binds
    fun bookmarks(repository: OfflineFirstBookmarkRepository): BookmarkRepository

    @Binds
    fun progress(repository: OfflineFirstProgressRepository): ProgressRepository

    @Binds
    fun lists(repository: OfflineFirstListsRepository): ListsRepository

    @Binds
    fun readingLists(repository: OfflineFirstReadingListRepository): ReadingListRepository

    @Binds
    fun profile(repository: KavitaProfileRepository): ProfileRepository

    @Binds
    fun annotations(repository: OfflineFirstAnnotationRepository): AnnotationRepository

    @Binds
    fun bookFiles(repository: CachedBookFileRepository): BookFileRepository

    @Binds
    fun readerDisplay(repository: SharedPrefsReaderDisplayRepository): ReaderDisplayRepository

    @Binds
    fun search(repository: KavitaSearchRepository): SearchRepository

    @Binds
    fun admin(repository: KavitaAdminRepository): AdminRepository

    @Binds
    fun people(repository: KavitaPersonRepository): PersonRepository

    @Binds
    fun reviews(repository: KavitaReviewRepository): ReviewRepository

    @Binds
    fun readState(repository: OfflineFirstReadStateRepository): ReadStateRepository

    @Binds
    fun smartFilters(repository: KavitaSmartFilterRepository): SmartFilterRepository

    @Binds
    fun network(monitor: ConnectivityNetworkMonitor): NetworkMonitor

    companion object {
        @Provides
        fun serverApis(
            apis: KavitaApis,
            session: AuthSession,
        ): ServerApis = ServerApis { server -> apis.forServer(server, session.credentialSource(server.id)) }

        /** The device clock and time zone; tests pass a fixed one. */
        @Provides
        fun clock(): Clock = Clock.systemDefaultZone()
    }
}
