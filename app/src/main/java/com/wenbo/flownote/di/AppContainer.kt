package com.wenbo.flownote.di

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import com.wenbo.flownote.data.backup.BackupManager
import com.wenbo.flownote.data.backup.RoomBackupSource
import com.wenbo.flownote.data.db.FlowNoteDatabase
import com.wenbo.flownote.data.db.ImageListCodec
import com.wenbo.flownote.data.files.ImageStore
import com.wenbo.flownote.data.repository.MemoRepositoryImpl
import com.wenbo.flownote.data.repository.TagRepositoryImpl
import com.wenbo.flownote.domain.repository.MemoRepository
import com.wenbo.flownote.domain.repository.TagRepository
import com.wenbo.flownote.speech.AndroidSpeechToTextEngine
import com.wenbo.flownote.speech.SpeechToTextEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** 手动 DI 容器：依赖是少量封闭集合，全部单例，在 Application 中构造一次。 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: FlowNoteDatabase = FlowNoteDatabase.create(appContext)
    val imageStore = ImageStore(appContext)
    val memoRepository: MemoRepository = MemoRepositoryImpl(database, imageStore)
    val tagRepository: TagRepository = TagRepositoryImpl(database)
    val backupManager = BackupManager(
        source = RoomBackupSource(database),
        imageStore = imageStore,
        workDir = File(appContext.cacheDir, "backup"),
    )

    /** SpeechRecognizer 有严格生命周期，由各 ViewModel 创建并在 onCleared 时销毁。 */
    fun createSpeechEngine(): SpeechToTextEngine = AndroidSpeechToTextEngine(appContext)

    /** SAF 流收口：备份读写的 uri 授权在会话内一次性消费，不做持久化。 */
    fun openBackupInput(uri: Uri): InputStream? = appContext.contentResolver.openInputStream(uri)

    fun openBackupOutput(uri: Uri): OutputStream? =
        appContext.contentResolver.openOutputStream(uri, "wt")

    /** 启动兜底：清理数据库中已无引用的图片文件（防删除路径中间崩溃残留）。 */
    fun purgeImageOrphansOnStartup() {
        applicationScope.launch {
            val referenced = database.memoDao().getAllImagesJson()
                .flatMapTo(mutableSetOf()) { ImageListCodec.decode(it) }
            imageStore.purgeOrphans(referenced)
        }
    }
}

/** 在 MainActivity setContent 时提供，Composable 层通过它获取容器。 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}
