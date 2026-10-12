package com.asppur.flasher

import android.app.AlertDialog
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Vibrator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.topjohnwu.superuser.Shell
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val backupBase = "/sdcard/FlasherBackup"
    private val parts = listOf("boot","recovery","vbmeta","dtbo","super")
    private lateinit var logView: TextView
    private lateinit var statusDevice: TextView
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private fun log(msg:String){ 
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logView.append("\n[$t] $msg")
    }
    fun soundStart(){ try{tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200)}catch(_:Exception){}; log("▶️ MULAI") }
    fun soundOk(){ try{tone.startTone(ToneGenerator.TONE_PROP_ACK, 200)}catch(_:Exception){}; log("✅ OK") }
    fun soundDone(){ try{tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)}catch(_:Exception){}; log("🎉 SELESAI") }
    fun soundFail(){ try{tone.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500)}catch(_:Exception){}; log("❌ GAGAL") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logView = findViewById(R.id.logView)
        statusDevice = findViewById(R.id.statusDevice)
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
        val isRoot = Shell.isAppGrantedRoot() ?: false
        statusDevice.text = "Perangkat terdeteksi: ${android.os.Build.MODEL} • Kode: ${android.os.Build.DEVICE}\nRoot: ${if(isRoot) "Tersedia" else "Tidak Ada"}"
        log("Flasher Pro v2.4.1 initialized")
        log("Root: $isRoot")
        findViewById<LinearLayout>(R.id.btnThis).setOnClickListener { soundStart(); showThis() }
        findViewById<LinearLayout>(R.id.btnOther).setOnClickListener { soundStart(); showOther() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { logView.text=""; soundOk() }
        findViewById<Button>(R.id.btnReboot).setOnClickListener { Shell.cmd("reboot").exec() }
    }

    fun showThis(){
        if(Shell.getShell().isRoot==false){ soundFail(); Toast.makeText(this,"ROOT tidak ada!",1).show(); return }
        AlertDialog.Builder(this).setTitle("THIS DEVICE - Backup Wajib").setItems(arrayOf(
            "1. BACKUP SEMUA PARTISI",
            "2. BACKUP + FLASH boot.img",
            "3. BACKUP + FLASH recovery.img",
            "4. BACKUP + FLASH URUTAN (boot>vbmeta>recovery>dtbo)",
            "5. RESTORE DARI BACKUP"
        )) { _, i ->
            when(i){0->doBackup(null);1->doBackupAndFlash(listOf("boot"));2->doBackupAndFlash(listOf("recovery"));3->doBackupAndFlash(listOf("boot","vbmeta","recovery","dtbo"));4->doRestore()}
        }.show()
    }
    fun showOther(){
        AlertDialog.Builder(this).setTitle("OTHER DEVICE OTG").setMessage("Tanpa Backup!\nFile harus di /sdcard/Download/*.img\n\nLangsung Flash?").setPositiveButton("LANJUT") { _,_->
            AlertDialog.Builder(this).setItems(arrayOf("Flash boot","Flash recovery","Flash boot+vbmeta+recovery","Flash super")) { _,j->
                val cmds = when(j){0->listOf("boot");1->listOf("recovery");2->listOf("boot","vbmeta","recovery");else->listOf("super")}
                log("OTG FLASH ${cmds} - Pastikan fastboot devices OK")
                soundOk()
            }.show()
        }.setNegativeButton("Batal"){_,_->soundFail()}.show()
    }
    fun doBackup(only:List<String>?){
        soundStart()
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = "$backupBase/$ts"
        Shell.cmd("mkdir -p $dir").exec()
        val target = only ?: parts
        log("BACKUP KE $dir")
        var ok=true
        for(p in target){
            val src = "/dev/block/by-name/$p"
            val r = Shell.cmd("dd if=$src of=$dir/${p}.img bs=4096 2>&1; echo EXIT:$?").exec()
            log("$p: ${r.out.joinToString()}")
            if(r.out.toString().contains("EXIT:0")==false) ok=false
        }
        if(ok){ soundDone(); Toast.makeText(this,"Backup OK: $dir",1).show() } else { soundFail() }
    }
    fun doBackupAndFlash(order:List<String>){
        doBackup(order)
        log("--- MULAI FLASH URUTAN ${order} ---")
        soundStart()
        for(p in order){
            val src="/sdcard/Download/${p}.img"
            if(File(src).exists()){
                val r=Shell.cmd("dd if=$src of=/dev/block/by-name/$p bs=4096; echo OK").exec()
                if(r.out.toString().contains("OK")){ log("Flash $p OK"); soundOk() } else { log("Flash $p GAGAL"); soundFail(); return }
            } else { log("SKIP $p: file $src tidak ada"); soundFail() }
        }
        soundDone()
    }
    fun doRestore(){
        val base=File(backupBase)
        val dirs=base.listFiles()?.sortedByDescending{it.name} ?: emptyList()
        if(dirs.isEmpty()){ soundFail(); Toast.makeText(this,"Tidak ada backup",1).show(); return }
        AlertDialog.Builder(this).setTitle("Pilih Backup").setItems(dirs.map{it.name}.toTypedArray()){_,i->
            val dir=dirs[i].absolutePath
            AlertDialog.Builder(this).setItems(arrayOf("Restore boot","Restore recovery","Restore Semua")){_,j->
                val list=when(j){0->listOf("boot");1->listOf("recovery");else->parts}
                soundStart()
                for(p in list){ val img="$dir/${p}.img"; if(File(img).exists()) Shell.cmd("dd if=$img of=/dev/block/by-name/$p").exec() }
                soundDone(); log("Restore selesai $dir")
            }.show()
        }.show()
    }
}
