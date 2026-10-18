package br.lara.painel

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification

class AvisoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Notif.ACAO_AVISO -> mostrar(c, i)
            Notif.ACAO_SYNC, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val r = goAsync()
                Thread {
                    try {
                        if (i.action == Notif.ACAO_SYNC) Data.fetch(c)
                        Notif.criarCanal(c)
                        Notif.agendar(c)
                    } finally { r.finish() }
                }.start()
            }
        }
    }

    private fun mostrar(c: Context, i: Intent) {
        Notif.criarCanal(c)
        val abrir = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val txt = i.getStringExtra("x") ?: ""
        val n = Notification.Builder(c, Notif.CANAL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(i.getStringExtra("t") ?: "Lembrete")
            .setContentText(txt)
            .setStyle(Notification.BigTextStyle().bigText(txt))
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()
        try {
            c.getSystemService(NotificationManager::class.java).notify(i.getIntExtra("n", 0), n)
        } catch (_: SecurityException) { }
    }
}
