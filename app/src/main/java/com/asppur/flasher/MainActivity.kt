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
    private val allParts = listOf("boot","recovery","vbmeta","dtbo","super")
    private lateinit var logView: TextView
    private lateinit var statusDevice: TextView
    private var selectedFile: String? = null
    private var cancelFlag = false
    private var progressDialog: Dialog? = null
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
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(15))
        statusDevice.text = "THIS DEVICE IS ROOT MODE"
        Thread {
            val isRoot = Shell.isAppGrantedRoot()?: false
            runOnUiThread { statusDevice.text = if(isRoot) "THIS DEVICE IS ROOT MODE" else "THIS DEVICE - NO ROOT" }
        }.start()
        findViewById<LinearLayout>(R.id.btnThis).setOnClickListener { showThisDeviceDialog() }
        findViewById<LinearLayout>(R.id.btnOther).setOnClickListener { Toast.makeText(this,"OTG Mode",Toast.LENGTH_SHORT).show() }
        log("Flasher Universal Ready")
    }
    fun getImgFiles(): List<File> {
        val dirs = listOf(File("/sdcard/Download"), File("/sdcard/Documents"), File("/sdcard/"))
        val all = mutableListOf<File>()
        for(d in dirs){ if(d.exists()) try{ d.listFiles{ f-> f.extension=="img" }?.let{ all.addAll(it) } }catch(_:Exception){} }
        return all.sortedByDescending{ it.lastModified() }.take(50)
    }
    fun showFilePicker(onPicked:(String)->Unit){
        val files = getImgFiles()
        if(files.isEmpty()){ Toast.makeText(this,"Tidak ada .img",Toast.LENGTH_SHORT).show(); return }
        val dialog = Dialog(this); dialog.setContentView(R.layout.dialog_picker)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.findViewById<TextView>(R.id.titleDialog).text = "PILIH FILE"
        val container = dialog.findViewById<LinearLayout>(R.id.listContainer)
        val inflater = LayoutInflater.from(this)
        for(f in files){
            val v = inflater.inflate(R.layout.item_file, container, false)
            v.findViewById<TextView>(R.id.nameFile).text = f.name
            v.findViewById<TextView>(R.id.sizeFile).text = "${f.length()/1024/1024}MB"
            v.setOnClickListener { dialog.dismiss(); onPicked(f.absolutePath) }
            container.addView(v)
        }
        dialog.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    fun showProgress(title:String){
        cancelFlag = false
        progressDialog = Dialog(this).apply {
            setContentView(R.layout.dialog_progress)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setCancelable(false)
            findViewById<TextView>(R.id.txtProgressTitle).text = title
            findViewById<TextView>(R.id.btnCancel).setOnClickListener { cancelFlag = true; log("Dibatalkan"); dismiss() }
            show()
        }
    }
    fun updateProgress(percent:Int, msg:String){
        runOnUiThread {
            progressDialog?.findViewById<TextView>(R.id.txtProgressTitle)?.text = msg
            progressDialog?.findViewById<ProgressBar>(R.id.progBar)?.progress = percent
        }
    }
    fun hideProgress(){ runOnUiThread { progressDialog?.dismiss() } }
    fun showThisDeviceDialog(){
        val d = Dialog(this); d.setContentView(R.layout.dialog_this_device)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.show()
        val cbMap = mapOf(
            "boot" to d.findViewById<CheckBox>(R.id.cb_boot),
            "recovery" to d.findViewById<CheckBox>(R.id.cb_recovery),
            "vbmeta" to d.findViewById<CheckBox>(R.id.cb_vbmeta),
            "dtbo" to d.findViewById<CheckBox>(R.id.cb_dtbo),
            "super" to d.findViewById<CheckBox>(R.id.cb_super)
        )
        val btnFile = d.findViewById<TextView>(R.id.btnPilihFile)
        val spin = d.findViewById<Spinner>(R.id.spinPartisi)
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, allParts)
        d.findViewById<TextView>(R.id.btnClose).setOnClickListener { d.dismiss() }
        d.findViewById<TextView>(R.id.btnMulaiBackup).setOnClickListener {
            val selectedParts = cbMap.filter { it.value.isChecked }.map { it.key }
            if(selectedParts.isEmpty()){ Toast.makeText(this,"Pilih partisi dulu",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            d.dismiss(); doBackupWithProgress(selectedParts)
        }
        btnFile.setOnClickListener {
            showFilePicker { path -> selectedFile = path; btnFile.text = File(path).name; log("File dipilih: $path") }
        }
        d.findViewById<TextView>(R.id.btnMulaiFlash).setOnClickListener {
            val part = spin.selectedItem as String
            val file = selectedFile
            if(file==null){ Toast.makeText(this,"Pilih file dulu",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            d.dismiss(); doFlashWithProgress(file, part)
        }
    }
    fun doBackupWithProgress(parts:List<String>){
        Thread {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dir = "$backupBase/$ts"
            Shell.cmd("mkdir -p $dir").exec()
            runOnUiThread { showProgress("Memulai backup...") }
            for((idx,p) in parts.withIndex()){
                if(cancelFlag) break
                val percent = (idx*100)/parts.size
                updateProgress(percent, "Mem-backup partisi $p (${percent}%)")
                log("Backup $p..."); Shell.cmd("dd if=/dev/block/by-name/$p of=$dir/${p}.img bs=4096 2>&1").exec()
            }
            if(!cancelFlag){ updateProgress(100, "Backup selesai: $dir"); Thread.sleep(800); hideProgress(); log("BACKUP SELESAI $dir") } else hideProgress()
        }.start()
    }
    fun doFlashWithProgress(filePath:String, partition:String){
        Thread {
            runOnUiThread { showProgress("Memulai flash $partition...") }
            for(i in 0..100 step 10){
                if(cancelFlag) break
                updateProgress(i, "Mem-flash $partition ($i%)")
                Thread.sleep(150)
                if(i==50){ val r = Shell.cmd("dd if=$filePath of=/dev/block/by-name/$partition bs=4096 2>&1; echo OK").exec()
                    if(!r.out.toString().contains("OK")){ log("Flash GAGAL ${r.out}"); break } }
            }
            if(!cancelFlag){ updateProgress(100, "Flash $partition selesai"); Thread.sleep(600); hideProgress(); log("FLASH $partition OK") } else hideProgress()
        }.start()
    }
}
