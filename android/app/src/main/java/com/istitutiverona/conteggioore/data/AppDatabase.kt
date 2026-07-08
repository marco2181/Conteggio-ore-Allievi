package com.istitutiverona.conteggioore.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Corso::class, Persona::class, Percorso::class, Turno::class, TurnoAbituale::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun corsoDao(): CorsoDao
    abstract fun personaDao(): PersonaDao
    abstract fun turnoDao(): TurnoDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Turni fissi come nell'app Windows (giorno 0=Lun .. 5=Sab).
        // Seminati dal ViewModel al primo avvio se la tabella è vuota.
        val TURNI_DEFAULT = listOf(
            Turno(giorno = 0, fascia = "mattina", oreDefault = 3.0),
            Turno(giorno = 1, fascia = "pomeriggio", oreDefault = 3.0),
            Turno(giorno = 2, fascia = "pomeriggio", oreDefault = 3.0),
            Turno(giorno = 2, fascia = "sera", oreDefault = 2.5),
            Turno(giorno = 3, fascia = "sera", oreDefault = 2.5),
            Turno(giorno = 4, fascia = "mattina", oreDefault = 3.0),
            Turno(giorno = 5, fascia = "mattina", oreDefault = 3.0),
            Turno(giorno = 5, fascia = "pomeriggio", oreDefault = 3.0),
        )

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "conteggio_ore.db"
                )
                    // ponytail: fallback distruttivo — app ancora vuota, nessun dato da migrare.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
