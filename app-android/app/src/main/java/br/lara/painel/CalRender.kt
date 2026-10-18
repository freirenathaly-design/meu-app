package br.lara.painel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

data class Item(val titulo: String, val ini: String, val fim: String, val cor: Int)

object CalRender {
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    fun key(cal: Calendar): String = fmt.format(cal.time)

    private fun primeiroDia(year: Int, month: Int): Calendar {
        val c = Calendar.getInstance()
        c.clear()
        c.set(year, month, 1, 12, 0, 0)
        return c
    }

    /** Primeiro domingo exibido na grade (pode ser do mês anterior). */
    fun inicioGrade(year: Int, month: Int): Calendar {
        val c = primeiroDia(year, month)
        c.add(Calendar.DAY_OF_MONTH, -(c.get(Calendar.DAY_OF_WEEK) - 1))
        return c
    }

    fun semanas(year: Int, month: Int): Int {
        val c = primeiroDia(year, month)
        val off = c.get(Calendar.DAY_OF_WEEK) - 1
        return ceil((off + c.getActualMaximum(Calendar.DAY_OF_MONTH)) / 7.0).toInt()
    }

    fun fimGrade(year: Int, month: Int): Calendar {
        val c = inicioGrade(year, month)
        c.add(Calendar.DAY_OF_MONTH, semanas(year, month) * 7 - 1)
        return c
    }

    private fun corTexto(cor: Int): Int {
        val r = (cor shr 16) and 0xFF
        val g = (cor shr 8) and 0xFF
        val b = cor and 0xFF
        val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (lum > 0.62) 0xFF1B1B2F.toInt() else 0xFFFFFFFF.toInt()
    }

    private class Seg(val a: Int, val b: Int, val item: Item) { var lane = 0 }

    fun render(c: Context, wPx: Int, hPx: Int, year: Int, month: Int, items: List<Item>, dark: Boolean): Bitmap {
        val d = c.resources.displayMetrics.density
        val w = max(wPx, 80)
        val h = max(hPx, 80)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val textC = if (dark) 0xFFF1F1F8.toInt() else 0xFF1B1B2F.toInt()
        val dimC = if (dark) 0xFF7E7C99.toInt() else 0xFFA09EB5.toInt()
        val lineC = if (dark) 0xFF34334A.toInt() else 0xFFE3E1F0.toInt()
        val accent = if (dark) 0xFFA99DFF.toInt() else 0xFF5B4ED1.toInt()
        val onAccent = if (dark) 0xFF1B1B2F.toInt() else 0xFFFFFFFF.toInt()

        val semanas = semanas(year, month)
        val headerH = 20f * d
        val cellW = w / 7f
        val cellH = (h - headerH) / semanas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.textAlign = Paint.Align.CENTER

        // letras dos dias da semana
        tp.textSize = 11f * d
        tp.color = dimC
        val letras = arrayOf("D", "S", "T", "Q", "Q", "S", "S")
        for (i in 0 until 7) cv.drawText(letras[i], i * cellW + cellW / 2f, headerH * 0.75f, tp)

        // linhas da grade
        paint.color = lineC
        paint.strokeWidth = 1f
        for (r in 0..semanas) {
            val y = headerH + r * cellH
            cv.drawLine(0f, y, w.toFloat(), y, paint)
        }
        for (i in 1 until 7) cv.drawLine(i * cellW, headerH, i * cellW, h.toFloat(), paint)

        val inicio = inicioGrade(year, month)
        val hoje = key(Calendar.getInstance())
        val numH = 22f * d
        val laneH = 15.5f * d
        val barH = 14f * d
        val maisH = 10f * d
        val maxLanes = max(1, floor((cellH - numH - maisH) / laneH).toInt())

        val barText = Paint(Paint.ANTI_ALIAS_FLAG)
        barText.textSize = 9.5f * d
        barText.textAlign = Paint.Align.LEFT
        val num = Paint(Paint.ANTI_ALIAS_FLAG)
        num.textAlign = Paint.Align.CENTER
        num.textSize = 12f * d
        val mais = Paint(Paint.ANTI_ALIAS_FLAG)
        mais.textSize = 9f * d
        mais.color = dimC
        mais.textAlign = Paint.Align.LEFT

        for (wk in 0 until semanas) {
            val y0 = headerH + wk * cellH
            val keys = Array(7) { i ->
                val cc = inicio.clone() as Calendar
                cc.add(Calendar.DAY_OF_MONTH, wk * 7 + i)
                key(cc)
            }
            // números dos dias
            for (i in 0 until 7) {
                val cc = inicio.clone() as Calendar
                cc.add(Calendar.DAY_OF_MONTH, wk * 7 + i)
                val cx = i * cellW + cellW / 2f
                val doMes = cc.get(Calendar.MONTH) == month
                if (keys[i] == hoje) {
                    paint.color = accent
                    paint.style = Paint.Style.FILL
                    cv.drawCircle(cx, y0 + 11f * d, 9.5f * d, paint)
                    num.color = onAccent
                } else {
                    num.color = if (doMes) textC else dimC
                }
                cv.drawText(cc.get(Calendar.DAY_OF_MONTH).toString(), cx, y0 + 15f * d, num)
            }

            // segmentos de eventos desta semana
            val segs = ArrayList<Seg>()
            for (it in items) {
                if (it.fim < keys[0] || it.ini > keys[6]) continue
                val a = if (it.ini <= keys[0]) 0 else keys.indexOf(it.ini)
                val b = if (it.fim >= keys[6]) 6 else keys.indexOf(it.fim)
                if (a < 0 || b < 0) continue
                segs.add(Seg(a, b, it))
            }
            segs.sortWith(compareBy<Seg>({ it.a }, { -(it.b - it.a) }, { it.item.titulo }))
            val ultimo = ArrayList<Int>()
            for (s in segs) {
                var lane = -1
                for (l in ultimo.indices) if (ultimo[l] < s.a) { lane = l; break }
                if (lane == -1) { ultimo.add(s.b); lane = ultimo.size - 1 } else ultimo[lane] = s.b
                s.lane = lane
            }
            val ocultos = IntArray(7)
            for (s in segs) {
                if (s.lane >= maxLanes) {
                    for (col in s.a..s.b) ocultos[col]++
                    continue
                }
                val top = y0 + numH + s.lane * laneH
                val r = RectF(s.a * cellW + 1.5f * d, top, (s.b + 1) * cellW - 1.5f * d, top + barH)
                paint.style = Paint.Style.FILL
                paint.color = s.item.cor
                cv.drawRoundRect(r, 3f * d, 3f * d, paint)
                barText.color = corTexto(s.item.cor)
                cv.save()
                cv.clipRect(r)
                cv.drawText(s.item.titulo, r.left + 3f * d, top + barH * 0.74f, barText)
                cv.restore()
            }
            for (col in 0 until 7) {
                if (ocultos[col] > 0) cv.drawText("+" + ocultos[col], col * cellW + 4f * d, y0 + cellH - 3f * d, mais)
            }
        }
        return bmp
    }
}
