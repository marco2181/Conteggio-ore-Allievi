package com.istitutiverona.conteggioore.drive

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// Backup: snapshot locale sempre; upload su Drive debounced 2 min via WorkManager
// (constraint rete = retry automatico se offline). Rotazione: ultimi 7 su Drive.
object Backup {
    private const val PREFS = "backup"
    private const val LAVORO = "backup_drive"
    const val MAX_BACKUP = 7

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Stato visibile (Dashboard/Impostazioni) ────────────
    fun ultimoOk(c: Context): String? = prefs(c).getString("ultimo_ok", null)
    fun ultimoErrore(c: Context): String? = prefs(c).getString("ultimo_errore", null)
    fun problema(c: Context): Boolean = ultimoErrore(c) != null

    private fun segnaOk(c: Context, quando: String) =
        prefs(c).edit().putString("ultimo_ok", quando).remove("ultimo_errore").apply()

    private fun segnaErrore(c: Context, msg: String) =
        prefs(c).edit().putString("ultimo_errore", msg).apply()

    // ── Snapshot locale WAL-safe ───────────────────────────
    /** Copia coerente del DB (VACUUM INTO assorbe il WAL). Ritorna il file snapshot. */
    fun snapshot(context: Context): File {
        val dbFile = context.getDatabasePath("conteggio_ore.db")
        val dest = File(context.filesDir, "backup_locale.db")
        dest.delete()
        try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use {
                it.execSQL("VACUUM INTO '${dest.path.replace("'", "''")}'")
            }
        } catch (_: Exception) {
            // ponytail: Android 10 ha SQLite 3.24 (niente VACUUM INTO) → checkpoint + copia.
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c -> c.moveToFirst() }
            }
            dbFile.copyTo(dest, overwrite = true)
        }
        return dest
    }

    fun nomeBackup(): String =
        "conteggio_ore_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".db"

    // ── Pianificazione ─────────────────────────────────────
    /** Chiamare dopo ogni modifica importante: snapshot subito, upload tra 2 min. */
    fun pianifica(context: Context, subito: Boolean = false) {
        runCatching { snapshot(context) }   // salva locale sempre
        if (Drive.tokenDisponibile(context)) accoda(context, if (subito) 0 else 2)
    }

    /** All'uscita/background: tenta l'upload senza aspettare i 2 minuti. */
    fun tentaOra(context: Context) {
        if (Drive.tokenDisponibile(context)) accoda(context, 0)
    }

    private fun accoda(context: Context, ritardoMin: Long) {
        val req = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(ritardoMin, TimeUnit.MINUTES)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(LAVORO, ExistingWorkPolicy.REPLACE, req)
    }

    // ── Upload effettivo ───────────────────────────────────
    suspend fun eseguiUpload(context: Context) = withContext(Dispatchers.IO) {
        val token = Drive.token(context) ?: error("Account Google non connesso")
        val cartella = Drive.cartellaBackups(token)
        val snap = snapshot(context)
        Drive.upload(token, cartella, nomeBackup(), snap)
        // Rotazione: tieni gli ultimi MAX_BACKUP.
        Drive.lista(token, cartella).drop(MAX_BACKUP).forEach { Drive.elimina(token, it.id) }
        segnaOk(context, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
    }

    class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result = try {
            eseguiUpload(applicationContext)
            Result.success()
        } catch (e: Exception) {
            segnaErrore(applicationContext, e.message ?: "errore sconosciuto")
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
