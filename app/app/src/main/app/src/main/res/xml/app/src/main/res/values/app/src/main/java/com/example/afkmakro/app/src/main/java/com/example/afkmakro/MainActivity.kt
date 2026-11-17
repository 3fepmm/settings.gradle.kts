package com.example.afkmakro

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Store.prefs(this)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(40, 100, 40, 40)

        val info = TextView(this)
        info.textSize = 15f
        info.text = "1) Aşağıdan Erişilebilirlik ayarlarını aç, AFK Makro'yu etkinleştir.\n" +
            "2) Roblox'u aç. Ekranda küçük bir panel görünür.\n" +
            "3) Panelde Kayıt'a bas, oyunda dokunacağın yerlere sırayla dokun, Bitir'e bas.\n" +
            "4) Oynat'a bas. Dur ile durdur."
        box.addView(info)

        val open = Button(this)
        open.text = "Erişilebilirlik ayarlarını aç"
        open.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        box.addView(open)

        val keys = listOf(
            Triple("jitter", 20, "Bekleme sapması (%)"),
            Triple("rounds", 0, "Tur sayısı (0 = sınırsız)"),
            Triple("afkMin", 10, "Anti-AFK dokunuşu aralığı (dk, 0 = kapalı)"),
            Triple("stopMin", 0, "Kaç dakika sonra dursun (0 = durmasın)"),
            Triple("startMin", 0, "Kaç dakika sonra başlasın (0 = hemen)")
        )
        val fields = mutableMapOf<String, EditText>()
        for ((key, def, label) in keys) {
            val t = TextView(this)
            t.text = label
            t.setPadding(0, 24, 0, 0)
            box.addView(t)
            val e = EditText(this)
            e.inputType = InputType.TYPE_CLASS_NUMBER
            e.setText(p.getInt(key, def).toString())
            box.addView(e)
            fields[key] = e
        }

        val save = Button(this)
        save.text = "Kaydet"
        save.setOnClickListener {
            val ed = p.edit()
            for ((k, e) in fields) {
                val d = keys.first { it.first == k }.second
                ed.putInt(k, e.text.toString().toIntOrNull() ?: d)
            }
            ed.apply()
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
        }
        box.addView(save)

        val sv = ScrollView(this)
        sv.addView(box)
        setContentView(sv)
    }
}
