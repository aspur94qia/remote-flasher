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
    private val backupBase = "/storage/emulated/0/Download/FlasherBackup"
    private var detectedParts = mutableListOf<String>()
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
        statusDevice.text = "THIS DEVICE IS ROOT MODE - Detecting..."
        Thread {
            val isRoot = Shell.isAppGrantedRoot()?: false
            if(!isRoot){ runOnUiThread { statusDevice.text = "THIS DEVICE - NO ROOT" }; log("NO ROOT! Backup akan gagal"); return@Thread }
            // AUTO DETECT PARTISI UNIVERSAL
            val res = Shell.cmd("ls /dev/block/by-name/ 2>/dev/null || ls /dev/block/bootdevice/by-name/ 2>/dev/null || ls /dev/block/platform/*/by-name/ 2>/dev/null").exec()
            val parts = res.out.flatMap { it.split("\\s+".toRegex()) }.filter { it.isNotBlank() && !it.contains(":") }.distinct().sorted()
            detectedParts.clear()
            if(parts.isNotEmpty()) detectedParts.addAll(parts)
            else detectedParts.addAll(listOf("boot","recovery","vbmeta","dtbo","super"))
            runOnUiThread { statusDevice.text = "THIS DEVICE IS ROOT MODE (${detectedParts.size} partisi)" }
            log("Partisi terdeteksi: $detectedParts")
        }.start()
        findViewById<LinearLayout>(R.id.btnThis).setOnClickListener { showThisDeviceDialog() }
        findViewById<LinearLayout>(R.id.btnOther).setOnClickListener { Toast.makeText(this,"OTG Mode",Toast.LENGTH_SHORT).show() }
        log("Flasher Universal Ready - Auto Detect")
    }

    fun getImgFiles(): List<File> {
        val dirs = listOf(File("/storage/emulated/0/Download"), File("/sdcard/Download"))
        val all = mutableListOf<File>()
        for(d in dirs){ if(d.exists()) try{ d.listFiles{ f-> f.extension=="img" }?.let{ all.addAll(it) } }catch(_:Exception){} }
        return all.sortedByDescending{ it.lastModified() }.take(50)
    }

    fun showFilePicker(onPicked:(String)->Unit){
        val files = getImgFiles()
        if(files.isEmpty()){ Toast.makeText(this,"Tidak ada .img di Download",Toast.LENGTH_SHORT).show(); return }
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
        if(detectedParts.isEmpty()){ Toast.makeText(this,"Partisi belum terdeteksi, tunggu root",Toast.LENGTH_SHORT).show(); return }
        val d = Dialog(this); d.setContentView(R.layout.dialog_this_device)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.show()
        
        val containerPart = d.findViewById<LinearLayout>(R.id.containerPartisi)
        containerPart.removeAllViews()
        val cbList = mutableListOf<Pair<String, CheckBox>>()
        for(part in detectedParts){
            val cb = CheckBox(this)
            cb.text = part
            cb.setTextColor(0xFFFFFFFF.toInt())
            cb.buttonTintList = android.content.res.ColorStateList.valueOf(0xFF8AFF5A.toInt())
            // auto check yang penting aja
            cb.isChecked = part.contains("boot") || part.contains("vbmeta") || part.contains("recovery") || part=="super"
            containerPart.addView(cb)
            cbList.add(part to cb)
        }

        val btnFile = d.findViewById<TextView>(R.id.btnPilihFile)
        val spin = d.findViewById<Spinner>(R.id.spinPartisi)
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, detectedParts)

        d.findViewById<TextView>(R.id.btnClose).setOnClickListener { d.dismiss() }
        d.findViewById<TextView>(R.id.btnMulaiBackup).setOnClickListener {
            val selectedParts = cbList.filter { it.second.isChecked }.map { it.first }
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
            runOnUiThread { showProgress("Backup ke $dir") }
            log("Backup folder: $dir")
            var successCount = 0
            for((idx,p) in parts.withIndex()){
                if(cancelFlag) break
                val percent = (idx*100)/parts.size
                updateProgress(percent, "Mem-backup $p (${percent}%)")
                log("Backup $p...")
                // coba 3 path block
                val cmd = "src=$(ls /dev/block/by-name/$p 2>/dev/null || ls /dev/block/bootdevice/by-name/$p 2>/dev/null || find /dev/block -name $p | head -n1); if [ -z \"\$src\" ]; then echo FAIL; else dd if=\$src of=$dir/${p}.img bs=4096 2>&1; ls -lh $dir/${p}.img; echo OK; fi"
                val r = Shell.cmd(cmd).exec()
                val out = r.out.joinToString("\n")
                log("$p: $out")
                if(out.contains("OK") && out.contains(".img")) successCount++
                Thread.sleep(300)
            }
            hideProgress()
            val check = Shell.cmd("ls -lh $dir; du -sh $dir").exec()
            log("HASIL BACKUP:\n${check.out.joinToString("\n")}")
            if(successCount==0) log("GAGAL TOTAL! Cek root & partisi") else log("BACKUP SELESAI $successCount/${parts.size} file di $dir - CEK DI FILE MANAGER > Download > FlasherBackup")
            runOnUiThread { Toast.makeText(this@MainActivity, "Backup: $successCount file di Download/FlasherBackup/$ts", Toast.LENGTH_LONG).show() }
        }.start()
    }

    fun doFlashWithProgress(filePath:String, partition:String){
        Thread {
            runOnUiThread { showProgress("Flash $partition...") }
            for(i in 0..100 step 20){
                if(cancelFlag) break
                updateProgress(i, "Mem-flash $partition ($i%)")
                Thread.sleep(200)
            }
            if(!cancelFlag){
                val cmd = "dst=$(ls /dev/block/by-name/$partition 2>/dev/null || ls /dev/block/bootdevice/by-name/$partition 2>/dev/null || find /dev/block -name $partition | head -n1); if [ -z \"\$dst\" ]; then echo FAIL; else dd if=$filePath of=\$dst bs=4096 2>&1; echo OK; fi"
                val r = Shell.cmd(cmd).exec()
                hideProgress()
                log("Flash $partition: ${r.out.joinToString()}")
                if(r.out.toString().contains("OK")) log("FLASH OK") else log("FLASH GAGAL")
            } else hideProgress()
        }.start()
    }
}
