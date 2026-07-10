package com.istitutiverona.conteggioore.drive

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.istitutiverona.conteggioore.data.AppDatabase
import java.io.File

// Import SOSTITUTIVO (no merge). Riconosce DB Android (tabella 'persone')
// e DB Windows (tabella 'students'), convertendo il secondo.
object ImportExport {

    enum class TipoDb { ANDROID, WINDOWS, SCONOSCIUTO }

    fun tipoDb(file: File): TipoDb = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            fun ha(t: String) = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(t)
            ).use { it.moveToFirst() }
            when {
                ha("persone") -> TipoDb.ANDROID
                ha("students") -> TipoDb.WINDOWS
                else -> TipoDb.SCONOSCIUTO
            }
        }
    } catch (_: Exception) { TipoDb.SCONOSCIUTO }

    /** Import DB Android: backup locale, chiude Room, sostituisce il file. Poi serve riavvio app. */
    fun importaAndroid(context: Context, sorgente: File) {
        Backup.snapshot(context)                     // backup locale prima di toccare
        AppDatabase.chiudi()
        val dbPath = context.getDatabasePath("conteggio_ore.db")
        File(dbPath.path + "-wal").delete()
        File(dbPath.path + "-shm").delete()
        sorgente.copyTo(dbPath, overwrite = true)
    }

    /** Import DB Windows: backup locale, svuota tutto e converte. Room resta aperto. */
    fun importaWindows(context: Context, sorgente: File, includiPresenze: Boolean) {
        Backup.snapshot(context)
        val win = SQLiteDatabase.openDatabase(sorgente.path, null, SQLiteDatabase.OPEN_READONLY)
        val room = AppDatabase.get(context)
        val db = room.openHelper.writableDatabase

        win.use {
            room.runInTransaction {
                listOf("presenze", "turni_abituali", "percorsi", "persone", "turni", "corsi")
                    .forEach { t -> db.execSQL("DELETE FROM $t") }

                // Corsi (salta il corso di sistema __LIBERO__)
                val liberoIds = mutableSetOf<Long>()
                win.rawQuery("SELECT id, name, total_hours FROM courses", null).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0); val nome = c.getString(1)
                        if (nome == "__LIBERO__") { liberoIds += id; continue }
                        db.execSQL(
                            "INSERT INTO corsi (id, nome, ore, attivo) VALUES (?,?,?,1)",
                            arrayOf(id, nome, c.getDouble(2))
                        )
                    }
                }

                // Turni (stessi id delle sessions Windows, così le presenze mappano dirette)
                win.rawQuery("SELECT id, day_of_week, slot, default_hours FROM sessions", null).use { c ->
                    while (c.moveToNext()) db.execSQL(
                        "INSERT INTO turni (id, giorno, fascia, oreDefault) VALUES (?,?,?,?)",
                        arrayOf(c.getLong(0), c.getInt(1), c.getString(2), c.getDouble(3))
                    )
                }

                // Allievi → persone + percorsi. Nessun turno abituale (→ anagrafiche incomplete).
                // percorsoId = studentId (mappa 1:1, comodo per le presenze).
                win.rawQuery(
                    """SELECT s.id, s.name, s.course_id, s.enrollment_date, s.active,
                              s.custom_hours, c.total_hours
                       FROM students s JOIN courses c ON c.id = s.course_id""", null
                ).use { c ->
                    while (c.moveToNext()) {
                        val sid = c.getLong(0)
                        val senzaCorso = c.getLong(2) in liberoIds
                        val monte = if (c.isNull(5)) c.getDouble(6) else c.getDouble(5)
                        db.execSQL(
                            "INSERT INTO persone (id, nome, etichetta, attiva) VALUES (?,?,NULL,?)",
                            arrayOf(sid, c.getString(1), c.getInt(4))
                        )
                        db.execSQL(
                            """INSERT INTO percorsi (id, personaId, corsoId, oreMonte, giaAvviato,
                               note, dataInizio, stato) VALUES (?,?,?,?,?,?,?,'ATTIVO')""",
                            arrayOf(
                                sid, sid, if (senzaCorso) null else c.getLong(2), monte,
                                if (senzaCorso) 1 else 0, "Importato da Windows", c.getString(3)
                            )
                        )
                    }
                }

                if (includiPresenze) {
                    win.rawQuery(
                        "SELECT student_id, session_id, date, hours_attended, notes FROM attendance", null
                    ).use { c ->
                        while (c.moveToNext()) db.execSQL(
                            "INSERT INTO presenze (percorsoId, turnoId, data, ore, note) VALUES (?,?,?,?,?)",
                            arrayOf(c.getLong(0), c.getLong(1), c.getString(2), c.getDouble(3), c.getString(4))
                        )
                    }
                }
            }
        }
    }
}
