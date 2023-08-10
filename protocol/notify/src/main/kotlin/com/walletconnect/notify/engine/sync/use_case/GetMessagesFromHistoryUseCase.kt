@file:JvmSynthetic

package com.walletconnect.notify.engine.sync.use_case

import com.walletconnect.android.archive.ArchiveInterface
import com.walletconnect.android.archive.network.model.messages.MessagesParams
import com.walletconnect.android.internal.common.model.AccountId
import com.walletconnect.android.sync.client.Sync
import com.walletconnect.android.sync.client.SyncInterface
import com.walletconnect.foundation.util.Logger
import com.walletconnect.notify.engine.sync.NotifySyncStores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class GetMessagesFromHistoryUseCase(
    private val archiveInterface: ArchiveInterface,
    private val syncClient: SyncInterface,
    private val logger: Logger,
) {

    suspend operator fun invoke(accountId: AccountId, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        getMessagesForNotifyStores(accountId, onSuccess, onError)
    }

    private suspend fun getMessagesForNotifyStores(accountId: AccountId, onMessagesFetched: () -> Unit, onError: (Throwable) -> Unit) {
        val countDownLatch = CountDownLatch(NotifySyncStores.values().size)

        // Note: When I tried registering all stores simultaneously I had issues with getting right values, when doing it sequentially it works
        NotifySyncStores.values().forEach { store ->
            syncClient.getStoreTopic(Sync.Params.GetStoreTopics(accountId, store.value))?.let { topic ->
                archiveInterface.getAllMessages(
                    MessagesParams(topic.value, null, ArchiveInterface.DEFAULT_BATCH_SIZE, null),
                    onError = { error -> onError(error.throwable) },
                    onSuccess = {
                        logger.log("Fetched ${it.size} for ${store.value}")
                        countDownLatch.countDown()
                    }
                )
            }
        }

        if (!withContext(Dispatchers.IO) { countDownLatch.await(5, TimeUnit.SECONDS) }) {
            onError(Throwable("Required Notify Stores initialization timeout"))
        } else {
            onMessagesFetched()
        }
    }
}