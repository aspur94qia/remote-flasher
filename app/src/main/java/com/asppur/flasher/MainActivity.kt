package com.asppur.flasher

import android.app.AlertDialog
import android.os.Bundle
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
    private var selectedFileMap = mutableMapOf<String, String>()

    private fun log(msg:String){
        try {
            val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            runOnUiThread { if(::logView.isInitialized) logView.append("\n[$t] $msg") }
        } catch(_:Exception){}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            logView = findViewById(R.id.logView)
            statusDevice = findViewById(R.id.statusDevice)

            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(10))

            val model = try { android.os.Build.MODEL } catch(_:Exception){ "Unknown" }
            statusDevice.text = "Model: $model"

            Thread {
                try {
                    val isRoot = Shell.isAppGrantedRoot()?: false
                    runOnUiThread { statusDevice.text = "Model: $model Root: ${if(isRoot) "OK" else "NO ROOT"}" }
                    log(if(isRoot) "Root OK" else "NO ROOT - Flash hanya via OTG")
                } catch(e:Exception){ log("Root check err: ${e.message}") }
            }.start()

            findViewById<LinearLayout>(R.id.btnThis).setOnClickListener { showThis() }
            findViewById<LinearLayout>(R.id.btnOther).setOnClickListener { showOther() }
            log("Flasher Pro Ready")
        } catch(e:Exception){
            Toast.makeText(this, "Crash onCreate: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun getImgFiles(): List<File> {
        val dirs = listOf(File("/sdcard/Download"), File("/sdcard/Documents"), File("/sdcard/"))
        val all = mutableListOf<File>()
        for(d in dirs){ if(d.exists()) try{ d.listFiles{ f-> f.extension=="img" }?.let{ all.addAll(it) } }catch(_:Exception){} }
        return all.sortedByDescending{ it.lastModified() }.take(50)
    }

    fun pickFileFor(part:String, onPicked: (String)->Unit){
        val files = getImgFiles()
        if(files.isEmpty()){
            Toast.makeText(this,"Tidak ada.img di Download",Toast.LENGTH_SHORT).show()
            return
        }
        val names = files.map{ "${it.name} - ${it.length()/1024/1024}MB" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Pilih file untuk $part").setItems(names){ _, i->
            val f = files[i].absolutePath
            selectedFileMap[part]=f
            log("Dipilih $part: $f")
            onPicked(f)
        }.show()
    }

    fun showThis(){
        AlertDialog.Builder(this).setTitle("THIS DEVICE")
           .setItems(arrayOf("BACKUP SEMUA","FLASH boot (pilih file)","FLASH recovery","FLASH URUTAN boot+vbmeta+recovery","RESTORE BACKUP")){ _,i->
            when(i){
                0->doBackup(null)
                1->pickFileFor("boot"){ doBackupAndFlash(listOf("boot")) }
                2->pickFileFor("recovery"){ doBackupAndFlash(listOf("recovery")) }
                3->pickFileFor("boot"){ pickFileFor("vbmeta"){ pickFileFor("recovery"){ doBackupAndFlash(listOf("boot","vbmeta","recovery")) } } }
                4->doRestore()
            }
        }.show()
    }

    fun showOther(){
        AlertDialog.Builder(this).setTitle("OTHER DEVICE OTG")
           .setItems(arrayOf("Flash boot (pilih file)","Flash recovery (pilih file)","Flash boot+vbmeta+recovery","Flash super")){ _,j->
            val cmds = when(j){0->listOf("boot");1->listOf("recovery");2->listOf("boot","vbmeta","recovery");else->listOf("super")}
            if(cmds.size==1) pickFileFor(cmds[0]){ file-> log("OTG READY: fastboot flash ${cmds[0]} $file") }
            else pickFileFor("boot"){ pickFileFor("vbmeta"){ pickFileFor("recovery"){ log("OTG URUTAN READY ${selectedFileMap}") } } }
        }.show()
    }

    fun doBackup(only:List<String>?){
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = "$backupBase/$ts"
        Shell.cmd("mkdir -p $dir").exec()
        val target = only?: parts
        log("BACKUP KE $dir")
        for(p in target){
            val res = Shell.cmd("dd if=/dev/block/by-name/$p of=$dir/${p}.img bs=4096 2>&1; echo OK").exec()
            log("backup $p: ${res.out.lastOrNull()}")
        }
        log("BACKUP SELESAI $dir")
        Toast.makeText(this,"Backup selesai",Toast.LENGTH_SHORT).show()
    }
    fun doBackupAndFlash(order:List<String>){
        doBackup(order)
        log("MULAI FLASH ${order}")
        for(p in order){
            val src = selectedFileMap[p]?: "/sdcard/Download/${p}.img"
            if(File(src).exists()){
                log("Flashing $p...")
                val r=Shell.cmd("dd if=$src of=/dev/block/by-name/$p bs=4096; echo OK").exec()
                if(r.out.toString().contains("OK")) log("Flash $p OK") else { log("Flash $p GAGAL ${r.out}"); return }
            } else { log("SKIP $p file tidak ada $src") }
        }
        log("SEMUA SELESAI")
        Toast.makeText(this,"SELESAI",Toast.LENGTH_SHORT).show()
    }
    fun doRestore(){
        val base=File(backupBase)
        val dirs=base.listFiles()?.sortedByDescending{it.name}?: emptyList()
        if(dirs.isEmpty()){ Toast.makeText(this,"Tidak ada backup",Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Pilih Backup").setItems(dirs.map{it.name}.toTypedArray()){ _,i->
            val dir=dirs[i].absolutePath
            AlertDialog.Builder(this).setTitle("Restore dari ${dirs[i].name}").setItems(arrayOf("Restore boot","Restore recovery","Restore Semua")){ _,j->
                val list=when(j){0->listOf("boot");1->listOf("recovery");else->parts}
                for(p in list){ val img="$dir/${p}.img"; if(File(img).exists()) Shell.cmd("dd if=$img of=/dev/block/by-name/$p bs=4096").exec() }
                log("Restore selesai $dir"); Toast.makeText(this,"Restore selesai",Toast.LENGTH_SHORT).show()
            }.show()
        }.show()
    }
}
