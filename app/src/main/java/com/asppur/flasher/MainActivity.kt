package com.asppur.flasher

import android.app.AlertDialog
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.topjohnwu.superuser.Shell
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val backupBase = "/sdcard/FlasherBackup"
    private val parts = listOf("boot","recovery","vbmeta","dtbo","super","persist")
    private lateinit var logView: TextView
    private lateinit var statusDevice: TextView
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private var selectedFileMap = mutableMapOf<String, String>() // part -> file path

    private fun log(msg:String){
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        runOnUiThread{ logView.append("\n[$t] $msg") }
    }
    fun soundStart(){ try{tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)}catch(_:Exception){} }
    fun soundOk(){ try{tone.startTone(ToneGenerator.TONE_PROP_ACK, 150)}catch(_:Exception){} }
    fun soundDone(){ try{tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)}catch(_:Exception){} }
    fun soundFail(){ try{tone.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 400)}catch(_:Exception){} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logView = findViewById(R.id.logView)
        statusDevice = findViewById(R.id.statusDevice)
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
        val isRoot = Shell.isAppGrantedRoot()?: false
        statusDevice.text = "Model: ${android.os.Build.MODEL}\nRoot: ${if(isRoot) "Tersedia" else "Tidak Ada"} | File Dipilih: ${selectedFileMap.size}"
        findViewById<LinearLayout>(R.id.btnThis).setOnClickListener { showThis() }
        findViewById<LinearLayout>(R.id.btnOther).setOnClickListener { showOther() }
    }

    fun getImgFiles(): List<File> {
        val dirs = listOf(File("/sdcard/Download"), File("/sdcard/Documents"), File("/sdcard/"))
        val all = mutableListOf<File>()
        for(d in dirs){ if(d.exists()) d.listFiles{ f-> f.extension=="img" }?.let{ all.addAll(it) } }
        return all.sortedByDescending{ it.lastModified() }
    }

    fun pickFileFor(part:String, onPicked: (String)->Unit){
        val files = getImgFiles()
        if(files.isEmpty()){
            Toast.makeText(this,"Tidak ada.img di /sdcard/Download",1).show()
            log("❌ Tidak ada file.img ditemukan")
            soundFail(); return
        }
        val names = files.map{ "${it.name} - ${it.length()/1024/1024}MB" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Pilih file untuk $part").setItems(names){ _, i->
            val f = files[i].absolutePath
            selectedFileMap[part] = f
            statusDevice.text = "File dipilih: $part -> ${files[i].name}"
            log("📁 Dipilih untuk $part: $f")
            soundOk()
            onPicked(f)
        }.setNeutralButton("Browse Manual"){ _,_ ->
            val input = EditText(this); input.hint="/sdcard/Download/boot.img"
            AlertDialog.Builder(this).setTitle("Path manual").setView(input)
               .setPositiveButton("OK"){ _,_->
                    val p = input.text.toString(); if(File(p).exists()){ selectedFileMap[part]=p; onPicked(p); soundOk() } else { soundFail() }
                }.show()
        }.show()
    }

    fun showThis(){
        if(Shell.getShell().isRoot==false){ soundFail(); Toast.makeText(this,"ROOT tidak ada!",1).show(); return }
        AlertDialog.Builder(this).setTitle("THIS DEVICE - Backup Wajib").setItems(arrayOf(
            "1. BACKUP SEMUA",
            "2. PILIH FILE & FLASH boot (Backup dulu)",
            "3. PILIH FILE & FLASH recovery (Backup dulu)",
            "4. PILIH FILE & FLASH URUTAN boot>vbmeta>recovery>dtbo",
            "5. RESTORE DARI BACKUP"
        )){ _, i->
            when(i){
                0->doBackup(null)
                1->pickFileFor("boot"){ doBackupAndFlash(listOf("boot")) }
                2->pickFileFor("recovery"){ doBackupAndFlash(listOf("recovery")) }
                3->{
                    AlertDialog.Builder(this).setTitle("Pilih file urutan?").setMessage("Pilih file untuk tiap partisi? Atau pakai 1 folder yang namanya sama dengan partisi?")
                       .setPositiveButton("Pilih Satu2"){ _,_->
                            pickFileFor("boot"){ pickFileFor("vbmeta"){ pickFileFor("recovery"){ pickFileFor("dtbo"){ doBackupAndFlash(listOf("boot","vbmeta","recovery","dtbo")) } } } }
                        }
                       .setNegativeButton("Auto dari Download"){ _,_-> doBackupAndFlash(listOf("boot","vbmeta","recovery","dtbo")) }
                       .show()
                }
                4->doRestore()
            }
        }.show()
    }

    fun showOther(){
        AlertDialog.Builder(this).setTitle("OTHER DEVICE OTG - No Backup").setItems(arrayOf(
            "Flash boot.img (Pilih file)",
            "Flash recovery.img (Pilih file)",
            "Flash Urutan boot+vbmeta+recovery (Pilih file)",
            "Flash super.img (Pilih file)"
        )){ _, j->
            val cmds = when(j){0->listOf("boot");1->listOf("recovery");2->listOf("boot","vbmeta","recovery");else->listOf("super")}
            if(cmds.size==1){
                pickFileFor(cmds[0]){ file->
                    log("OTG READY: fastboot flash ${cmds[0]} $file")
                    log("Colok HP target FASTBOOT via OTG dulu!")
                    soundDone()
                }
            } else {
                pickFileFor("boot"){ pickFileFor("vbmeta"){ pickFileFor("recovery"){
                    log("OTG URUTAN READY: ${selectedFileMap}")
                    soundDone()
                } } }
            }
        }.show()
    }

    fun doBackup(only:List<String>?){
        soundStart()
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = "$backupBase/$ts"
        Shell.cmd("mkdir -p $dir").exec()
        val target = only?: parts
        log("BACKUP KE $dir")
        for(p in target){
            val src="/dev/block/by-name/$p"
            val r=Shell.cmd("dd if=$src of=$dir/${p}.img bs=4096 2>&1; echo EXIT:$?").exec()
            log("$p: ${r.out.joinToString().take(100)}")
        }
        soundDone(); log("✅ BACKUP SELESAI $dir")
    }
    fun doBackupAndFlash(order:List<String>){
        doBackup(order)
        log("--- MULAI FLASH URUTAN ${order} ---"); soundStart()
        for(p in order){
            val src = selectedFileMap[p]?: "/sdcard/Download/${p}.img"
            if(File(src).exists()){
                val r=Shell.cmd("dd if=$src of=/dev/block/by-name/$p bs=4096; echo OK").exec()
                if(r.out.toString().contains("OK")){ log("Flash $p OK dari $src"); soundOk() } else { log("Flash $p GAGAL"); soundFail(); return }
            } else { log("SKIP $p: $src tidak ada"); soundFail() }
        }
        soundDone(); log("🎉 SEMUA FLASH SELESAI - Reboot?")
    }
    fun doRestore(){
        val base=File(backupBase); val dirs=base.listFiles()?.sortedByDescending{it.name}?: emptyList()
        if(dirs.isEmpty()){ soundFail(); Toast.makeText(this,"Tidak ada backup",1).show(); return }
        AlertDialog.Builder(this).setTitle("Pilih Backup").setItems(dirs.map{it.name}.toTypedArray()){ _,i->
            val dir=dirs[i].absolutePath
            AlertDialog.Builder(this).setItems(arrayOf("Restore boot","Restore recovery","Restore Semua")){ _,j->
                val list=when(j){0->listOf("boot");1->listOf("recovery");else->parts}
                soundStart()
                for(p in list){ val img="$dir/${p}.img"; if(File(img).exists()) Shell.cmd("dd if=$img of=/dev/block/by-name/$p").exec() }
                soundDone(); log("Restore selesai $dir")
            }.show()
        }.show()
    }
}
