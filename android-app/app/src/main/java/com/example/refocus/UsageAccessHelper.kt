package com.example.refocus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

object UsageAccessHelper {

    fun tienePermiso(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val modo = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return modo == AppOpsManager.MODE_ALLOWED
    }

    fun abrirConfiguracion(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun paqueteEnPrimerPlano(context: Context): String? {
        if (!tienePermiso(context)) return null

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val fin = System.currentTimeMillis()
        val inicio = fin - 10_000L
        val eventos = usageStatsManager.queryEvents(inicio, fin)
        val evento = UsageEvents.Event()

        var ultimoPaquete: String? = null
        var ultimoTiempo = 0L

        while (eventos.hasNextEvent()) {
            eventos.getNextEvent(evento)

            val esPrimerPlano =
                evento.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        evento.eventType == UsageEvents.Event.ACTIVITY_RESUMED

            if (esPrimerPlano && evento.timeStamp >= ultimoTiempo) {
                ultimoTiempo = evento.timeStamp
                ultimoPaquete = evento.packageName
            }
        }

        return ultimoPaquete
    }
}
