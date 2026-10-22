package net.iegy.hisn.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        FolderEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        ShortcutEntity::class,
        DownloadEntity::class,
        SavedTabEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HisnDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun downloadDao(): DownloadDao
    abstract fun savedTabDao(): SavedTabDao

    companion object {
        const val FILE_NAME = "hisn.db"

        fun create(context: Context, name: String = FILE_NAME): HisnDatabase =
            Room.databaseBuilder(context.applicationContext, HisnDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()

        /**
         * كل تغيير في الجداول (جدول جديد، عمود جديد...) يتطلب: رفع `version` أعلاه، وإضافة Migration هنا.
         * بدون ذلك يتعطّل التطبيق عند من حدّث فوق نسخة قديمة. مثال لنسخة 2 تضيف جدول قائمة القراءة:
         *
         *   val MIGRATION_1_2 = object : Migration(1, 2) {
         *       override fun migrate(db: SupportSQLiteDatabase) {
         *           db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY NOT NULL, url TEXT NOT NULL)")
         *       }
         *   }
         *
         * ثم تُضاف إلى المصفوفة أدناه. ملفات المخطط تُولَّد في app/schemas ويجب رفعها على git.
         * لا نستخدم fallbackToDestructiveMigration عمداً: لأنه يمسح الإشارات المرجعية والسجل.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
