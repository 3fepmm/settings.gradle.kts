package com.example.afkmakro

import android.content.Context
import android.content.SharedPreferences

data class Step(
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    val dur: Long, val delay: Long
)

object Store {
    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("macro", Context.MODE_PRIVATE)

    fun saveSteps(c: Context, steps: List<Step>) {
        val s = steps.joinToString(";") {
            "${it.x1},${it.y1},${it.x2},${it.y2},${it.dur},${it.delay}"
        }
        prefs(c).edit().putString("steps", s).apply()
    }

    fun loadSteps(c: Context): MutableList<Step> {
        val raw = prefs(c).getString("steps", "") ?: ""
        val out = mutableListOf<Step>()
        if (raw.isBlank()) return out
        for (part in raw.split(";")) {
            val f = part.split(",")
            if (f.size == 6) {
                out.add(
                    Step(
                        f[0].toFloat(), f[1].toFloat(),
                        f[2].toFloat(), f[3].toFloat(),
                        f[4].toLong(), f[5].toLong()
                    )
                )
            }
        }
        return out
    }
}
