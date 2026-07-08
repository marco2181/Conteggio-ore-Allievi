package com.istitutiverona.conteggioore.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CorsoDao {
    @Query("SELECT * FROM corsi ORDER BY attivo DESC, nome")
    fun tutti(): Flow<List<Corso>>

    @Query("SELECT * FROM corsi WHERE attivo = 1 ORDER BY nome")
    fun attivi(): Flow<List<Corso>>

    @Insert suspend fun inserisci(c: Corso): Long
    @Update suspend fun aggiorna(c: Corso)
    @Delete suspend fun elimina(c: Corso)
}

@Dao
interface AllievoDao {
    @Query("SELECT * FROM allievi WHERE attivo = 1 ORDER BY nome")
    fun attivi(): Flow<List<Allievo>>

    @Query("SELECT * FROM allievi ORDER BY nome")
    fun tutti(): Flow<List<Allievo>>

    @Query("SELECT COUNT(*) FROM allievi WHERE nome = :nome AND id != :escludiId")
    suspend fun contaOmonimi(nome: String, escludiId: Long): Int

    @Insert suspend fun inserisci(a: Allievo): Long
    @Update suspend fun aggiorna(a: Allievo)
    @Delete suspend fun elimina(a: Allievo)
}
