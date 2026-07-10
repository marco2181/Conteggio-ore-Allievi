package com.istitutiverona.conteggioore.pdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.istitutiverona.conteggioore.ui.giornoNome
import com.istitutiverona.conteggioore.ui.oreFmt
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

// PDF nativi Android (android.graphics.pdf), A4 verticale. Nessuna libreria esterna.

private const val PAG_W = 595
private const val PAG_H = 842
private const val MARGINE = 40f

class Cella(val testo: String, val colore: Int = Color.BLACK, val bold: Boolean = false)

private class DocA4 {
    val doc = PdfDocument()
    private var pagina: PdfDocument.Page? = null
    private var n = 0
    var y = MARGINE
    val larghezza = PAG_W - 2 * MARGINE

    val titoloP = Paint().apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
    val sottoP = Paint().apply { textSize = 10f; color = Color.DKGRAY }
    val testoP = Paint().apply { textSize = 9f }
    val boldP = Paint().apply { textSize = 9f; typeface = Typeface.DEFAULT_BOLD }
    val lineaP = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

    fun canvas() = pagina!!.canvas

    fun nuovaPagina() {
        pagina?.let { doc.finishPage(it) }
        n++
        pagina = doc.startPage(PdfDocument.PageInfo.Builder(PAG_W, PAG_H, n).create())
        y = MARGINE
    }

    fun serve(h: Float) { if (pagina == null || y + h > PAG_H - MARGINE) nuovaPagina() }

    fun testo(s: String, p: Paint, spazioDopo: Float = 4f) {
        serve(p.textSize + spazioDopo)
        y += p.textSize
        canvas().drawText(s, MARGINE, y, p)
        y += spazioDopo
    }

    /** Riga di tabella: pesi frazionari, wrap non gestito (testo troncato). */
    fun rigaTabella(celle: List<Cella>, pesi: List<Float>, sfondo: Int? = null) {
        val h = 16f
        serve(h)
        sfondo?.let {
            val bg = Paint().apply { color = it }
            canvas().drawRect(MARGINE, y, MARGINE + larghezza, y + h, bg)
        }
        var x = MARGINE
        val totPesi = pesi.sum()
        celle.forEachIndexed { i, c ->
            val w = larghezza * pesi[i] / totPesi
            val p = Paint(if (c.bold) boldP else testoP).apply { color = c.colore }
            var t = c.testo
            while (t.isNotEmpty() && p.measureText(t) > w - 6) t = t.dropLast(1)
            canvas().drawText(t, x + 3, y + 11.5f, p)
            x += w
        }
        y += h
        canvas().drawLine(MARGINE, y, MARGINE + larghezza, y, lineaP)
    }

    fun chiudi(file: File) {
        pagina?.let { doc.finishPage(it) }
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }
}

private fun coloreCompl(pct: Int): Int = when {
    pct >= 100 -> Color.rgb(0x27, 0xAE, 0x60)
    pct >= 80 -> Color.rgb(0xE6, 0x7E, 0x22)
    else -> Color.rgb(0x34, 0x98, 0xDB)
}

/** "Completato +Xh extra" oltre il monte ore, mai ore negative. */
fun testoCompletamento(fatte: Double, monte: Double): String {
    if (monte <= 0) return "—"
    val pct = (fatte / monte * 100).toInt()
    return if (fatte >= monte) {
        val extra = fatte - monte
        if (extra > 0) "Completato +${oreFmt(extra)}h extra" else "Completato"
    } else "$pct%"
}

fun pctDi(fatte: Double, monte: Double): Int = if (monte > 0) (fatte / monte * 100).toInt() else 0

object PdfReport {

    private fun fileReport(context: Context, nome: String): File {
        val dir = File(context.cacheDir, "report").apply { mkdirs() }
        return File(dir, nome)
    }

    // ── Scheda individuale ─────────────────────────────────
    fun schedaIndividuale(
        context: Context,
        nome: String,
        nomeCorso: String,
        oreMonte: Double,
        giaAvviato: Boolean,
        notePercorso: String?,
        presenze: List<PresenzaPdf>,
    ): File {
        val d = DocA4()
        d.nuovaPagina()
        val fatte = presenze.sumOf { it.ore }
        val rimanenti = (oreMonte - fatte).coerceAtLeast(0.0)
        d.testo("Scheda individuale — $nome", d.titoloP, 8f)
        d.testo("Corso: $nomeCorso", d.sottoP)
        d.testo(
            "Monte ore: ${oreFmt(oreMonte)}h · Fatte: ${oreFmt(fatte)}h · " +
                "Rimanenti: ${oreFmt(rimanenti)}h · ${testoCompletamento(fatte, oreMonte)}",
            d.sottoP
        )
        if (presenze.isNotEmpty())
            d.testo("Periodo: ${presenze.first().data} → ${presenze.last().data}", d.sottoP)
        if (giaAvviato)
            d.testo("Nota: percorso già avviato — ore rimanenti iniziali.", d.sottoP)
        notePercorso?.takeIf { it.isNotBlank() }?.let { d.testo("Note: $it", d.sottoP) }
        d.y += 8f

        val pesi = listOf(1.6f, 1.4f, 1.6f, 0.9f, 2.5f)
        d.rigaTabella(
            listOf("Data", "Giorno", "Turno", "Ore", "Note").map { Cella(it, bold = true) },
            pesi, Color.rgb(0xEE, 0xEE, 0xEE)
        )
        presenze.forEach { p ->
            d.rigaTabella(
                listOf(
                    Cella(p.data), Cella(giornoNome(p.giorno)), Cella(p.fascia),
                    Cella(oreFmt(p.ore)), Cella(p.note ?: "")
                ), pesi
            )
        }
        d.rigaTabella(
            listOf(Cella("Totale", bold = true), Cella(""), Cella(""),
                Cella(oreFmt(fatte), bold = true), Cella("")),
            pesi, Color.rgb(0xF5, 0xF5, 0xF5)
        )

        val f = fileReport(context, "Scheda_${nome.replace(' ', '_')}.pdf")
        d.chiudi(f)
        return f
    }

    // ── Registro mensile ───────────────────────────────────
    fun registroMensile(context: Context, meseLabel: String, righe: List<RigaMensilePdf>): File {
        val d = DocA4()
        d.nuovaPagina()
        d.testo("Registro mensile — $meseLabel", d.titoloP, 8f)
        if (righe.any { it.giaAvviato })
            d.testo("* percorso già avviato (ore rimanenti iniziali)", d.sottoP)
        d.y += 6f

        val pesi = listOf(2.4f, 1.8f, 1f, 1f, 1f, 1.1f, 1.7f)
        d.rigaTabella(
            listOf("Allievo", "Corso", "Mese", "Totali", "Monte", "Rimanenti", "Compl.")
                .map { Cella(it, bold = true) },
            pesi, Color.rgb(0xEE, 0xEE, 0xEE)
        )
        righe.forEach { r ->
            val pct = pctDi(r.oreTotali, r.oreMonte)
            d.rigaTabella(
                listOf(
                    Cella(r.nome + if (r.giaAvviato) " *" else ""),
                    Cella(r.nomeCorso),
                    Cella(oreFmt(r.oreMese)),
                    Cella(oreFmt(r.oreTotali)),
                    Cella(oreFmt(r.oreMonte)),
                    Cella(oreFmt((r.oreMonte - r.oreTotali).coerceAtLeast(0.0))),
                    Cella(testoCompletamento(r.oreTotali, r.oreMonte), coloreCompl(pct), bold = true),
                ), pesi
            )
        }
        val f = fileReport(context, "Registro_${meseLabel.replace(' ', '_')}.pdf")
        d.chiudi(f)
        return f
    }

    // ── Report per corso ───────────────────────────────────
    fun reportCorso(context: Context, nomeCorso: String, righe: List<RigaCorsoPdf>): File {
        val d = DocA4()
        d.nuovaPagina()
        d.testo("Report corso — $nomeCorso", d.titoloP, 8f)
        if (righe.any { it.giaAvviato })
            d.testo("* percorso già avviato (ore rimanenti iniziali)", d.sottoP)
        d.y += 6f

        val pesi = listOf(2.6f, 1.5f, 1.1f, 1.1f, 1.2f, 1.8f)
        d.rigaTabella(
            listOf("Allievo", "Iscrizione", "Fatte", "Monte", "Rimanenti", "Completamento")
                .map { Cella(it, bold = true) },
            pesi, Color.rgb(0xEE, 0xEE, 0xEE)
        )
        righe.forEach { r ->
            val pct = pctDi(r.oreFatte, r.oreMonte)
            d.rigaTabella(
                listOf(
                    Cella(r.nome + if (r.giaAvviato) " *" else ""),
                    Cella(r.dataInizio),
                    Cella(oreFmt(r.oreFatte)),
                    Cella(oreFmt(r.oreMonte)),
                    Cella(oreFmt((r.oreMonte - r.oreFatte).coerceAtLeast(0.0))),
                    Cella(testoCompletamento(r.oreFatte, r.oreMonte), coloreCompl(pct), bold = true),
                ), pesi
            )
        }
        val f = fileReport(context, "Corso_${nomeCorso.replace(' ', '_')}.pdf")
        d.chiudi(f)
        return f
    }

    // ── Azioni: condividi / stampa / salva ────────────────
    fun condividi(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, file.name))
    }

    fun stampa(context: Context, file: File) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(file.name, object : PrintDocumentAdapter() {
            override fun onLayout(
                old: PrintAttributes?, new: PrintAttributes?, sig: CancellationSignal?,
                cb: LayoutResultCallback, extras: android.os.Bundle?,
            ) {
                cb.onLayoutFinished(
                    PrintDocumentInfo.Builder(file.name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),
                    old != new
                )
            }

            override fun onWrite(
                pages: Array<out PageRange>?, dest: ParcelFileDescriptor,
                sig: CancellationSignal?, cb: WriteResultCallback,
            ) {
                FileInputStream(file).use { i ->
                    FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) }
                }
                cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, null)
    }

    /** Salva in Download/ tramite MediaStore (API 29+, nessun permesso). Ritorna true se ok. */
    fun salvaInDownload(context: Context, file: File): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return false
        context.contentResolver.openOutputStream(uri)?.use { o ->
            FileInputStream(file).use { it.copyTo(o) }
        } ?: return false
        return true
    }
}

// DTO minimi per il PDF, disaccoppiati dal layer Room.
class PresenzaPdf(val data: String, val giorno: Int, val fascia: String, val ore: Double, val note: String?)
class RigaMensilePdf(
    val nome: String, val nomeCorso: String, val giaAvviato: Boolean,
    val oreMese: Double, val oreTotali: Double, val oreMonte: Double,
)
class RigaCorsoPdf(
    val nome: String, val dataInizio: String, val giaAvviato: Boolean,
    val oreFatte: Double, val oreMonte: Double,
)
