package com.asppur.flasher
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "FLASHER NEON OK - ANTI CRASH\nJika ini muncul, app sudah gak crash"
        tv.textSize = 20f
        tv.setPadding(40,100,40,40)
        setContentView(tv)
    }
}
