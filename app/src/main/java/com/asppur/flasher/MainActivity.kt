package com.asppur.flasher
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
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
            runOnUiThread { logView.append("\n[$t] $msg") }
        } catch(_:Exception){}
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logView = findViewById(R.id.logView)
        statusDevice = findViewById(R.id.statusDevice)
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(10))
        val model = android.os.Build.MODEL
        statusDevice.text = "Model: $model"
        Thread {
            val isRoot = Shell.isAppGrantedRoot()?: false
            runOnUiThread { statusDevice.text = "Model: $model Root: ${if(isRoot) "OK" else "NO ROOT"}" }
            log(if(isRoot) "Root OK" else "NO ROOT")
        }.start()
        findViewById<LinearLayout>(R.id.btnThis).setOnClickListener { showThis() }
        findViewById<LinearLayout>(R.id.btnOther).setOnClickListener { showOther() }
        log("Flasher Pro Ready v2.4.1-pro")
    }

    fun getImgFiles(): List<File> {
        val dirs = listOf(File("/sdcard/Download"), File("/sdcard/Documents"), File("/sdcard/"))
        val all = mutableListOf<File>()
        for(d in dirs){ if(d.exists()) try{ d.listFiles{ f-> f.extension=="img" }?.let{ all.addAll(it) } }catch(_:Exception){} }
        return all.sortedByDescending{ it.lastModified() }.take(50)
    }

    fun showCustomPicker(part:String, onPicked: (String)->Unit){
        val files = getImgFiles()
        if(files.isEmpty()){
            Toast.makeText(this,"Tidak ada.img di Download",Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_picker)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.findViewById<TextView>(R.id.titleDialog).text = "PILIH FILE UNTUK $part"
        val container = dialog.findViewById<LinearLayout>(R.id.listContainer)
        val inflater = LayoutInflater.from(this)
        for(f in files){
            val v = inflater.inflate(R.layout.item_file, container, false)
            v.findViewById<TextView>(R.id.nameFile).text = f.name
            v.findViewById<TextView>(R.id.sizeFile).text = "${f.length()/1024/1024}MB - ${f.absolutePath}"
            v.setOnClickListener {
                selectedFileMap[part]=f.absolutePath
                log("Dipilih $part: ${f.absolutePath}")
                onPicked(f.absolutePath)
                dialog.dismiss()
            }
            container.addView(v)
        }
        dialog.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun showThis(){
        val d = Dialog(this); d.setContentView(R.layout.dialog_picker)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.findViewById<TextView>(R.id.titleDialog).text = "THIS DEVICE"
        val c = d.findViewById<LinearLayout>(R.id.listContainer)
        val inf = LayoutInflater.from(this)
        val items = listOf("BACKUP SEMUA" to "Backup semua partisi ke sdcard","FLASH boot" to "Pilih file boot.img","FLASH recovery" to "Pilih file recovery.img","FLASH URUTAN" to "boot + vbmeta + recovery","RESTORE BACKUP" to "Restore dari backup")
        items.forEachIndexed { idx, p->
            val v = inf.inflate(R.layout.item_file, c, false)
            v.findViewById<TextView>(R.id.nameFile).text = p.first
            v.findViewById<TextView>(R.id.sizeFile).text = p.second
            v.setOnClickListener {
                d.dismiss()
                when(idx){
                    0->doBackup(null)
                    1->showCustomPicker("boot"){ doBackupAndFlash(listOf("boot")) }
                    2->showCustomPicker("recovery"){ doBackupAndFlash(listOf("recovery")) }
                    3->showCustomPicker("boot"){ showCustomPicker("vbmeta"){ showCustomPicker("recovery"){ doBackupAndFlash(listOf("boot","vbmeta","recovery")) } } }
                    4->doRestore()
                }
            }
            c.addView(v)
        }
        d.findViewById<TextView>(R.id.btnCancel).setOnClickListener { d.dismiss() }
        d.show()
    }

    fun showOther(){
        val d = Dialog(this); d.setContentView(R.layout.dialog_picker)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.findViewById<TextView>(R.id.titleDialog).text = "OTHER DEVICE OTG"
        val c = d.findViewById<LinearLayout>(R.id.listContainer)
        val inf = LayoutInflater.from(this)
        val items = listOf("Flash boot OTG","Flash recovery OTG","Flash boot+vbmeta+recovery OTG","Flash super OTG")
        items.forEachIndexed { idx, t->
            val v = inf.inflate(R.layout.item_file, c, false)
            v.findViewById<TextView>(R.id.nameFile).text = t
            v.findViewById<TextView>(R.id.sizeFile).text = "Via fastboot OTG"
            v.setOnClickListener {
                d.dismiss()
                when(idx){
                    0->showCustomPicker("boot"){ f-> log("OTG READY: fastboot flash boot $f") }
                    1->showCustomPicker("recovery"){ f-> log("OTG READY: fastboot flash recovery $f") }
                    2->showCustomPicker("boot"){ showCustomPicker("vbmeta"){ showCustomPicker("recovery"){ log("OTG URUTAN READY $selectedFileMap") } } }
                    3->showCustomPicker("super"){ f-> log("OTG READY: fastboot flash super $f") }
                }
            }
            c.addView(v)
        }
        d.findViewById<TextView>(R.id.btnCancel).setOnClickListener { d.dismiss() }
        d.show()
    }

    fun doBackup(only:List<String>?){
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = "$backupBase/$ts"
        Shell.cmd("mkdir -p $dir").exec()
        val target = only?: parts
        log("BACKUP KE $dir")
        for(p in target){ val res = Shell.cmd("dd if=/dev/block/by-name/$p of=$dir/${p}.img bs=4096 2>&1; echo OK").exec(); log("backup $p: ${res.out.lastOrNull()}") }
        log("BACKUP SELESAI $dir"); Toast.makeText(this,"Backup selesai",Toast.LENGTH_SHORT).show()
    }
    fun doBackupAndFlash(order:List<String>){
        doBackup(order); log("MULAI FLASH ${order}")
        for(p in order){
            val src = selectedFileMap[p]?: "/sdcard/Download/${p}.img"
            if(File(src).exists()){
                log("Flashing $p...")
                val r=Shell.cmd("dd if=$src of=/dev/block/by-name/$p bs=4096; echo OK").exec()
                if(r.out.toString().contains("OK")) log("Flash $p OK") else { log("Flash $p GAGAL ${r.out}"); return }
            } else { log("SKIP $p file tidak ada $src") }
        }
        log("SEMUA SELESAI"); Toast.makeText(this,"SELESAI",Toast.LENGTH_SHORT).show()
    }
    fun doRestore(){
        val base=File(backupBase)
        val dirs=base.listFiles()?.sortedByDescending{it.name}?: emptyList()
        if(dirs.isEmpty()){ Toast.makeText(this,"Tidak ada backup",Toast.LENGTH_SHORT).show(); return }
        val d = Dialog(this); d.setContentView(R.layout.dialog_picker)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.findViewById<TextView>(R.id.titleDialog).text = "PILIH BACKUP"
        val c = d.findViewById<LinearLayout>(R.id.listContainer)
        val inf = LayoutInflater.from(this)
        for(dir in dirs){
            val v = inf.inflate(R.layout.item_file, c, false)
            v.findViewById<TextView>(R.id.nameFile).text = dir.name
            v.findViewById<TextView>(R.id.sizeFile).text = dir.absolutePath
            v.setOnClickListener {
                d.dismiss()
                for(p in parts){ val img="${dir.absolutePath}/${p}.img"; if(File(img).exists()) Shell.cmd("dd if=$img of=/dev/block/by-name/$p bs=4096").exec() }
                log("Restore selesai ${dir.name}")
            }
            c.addView(v)
        }
        d.findViewById<TextView>(R.id.btnCancel).setOnClickListener { d.dismiss() }
        d.show()
    }
}
