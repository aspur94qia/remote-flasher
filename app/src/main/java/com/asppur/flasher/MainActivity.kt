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
    private var selectedFileOtg: String? = null
    private var cancelFlag = false
    private var progressDialog: Dialog? = null
    private var currentPath = File("/storage/emulated/0")
    private fun log(msg:String){ try { val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()); runOnUiThread { logView.append("\n[$t] $msg") } } catch(_:Exception){} }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SoundHelper.init(this)
        logView = findViewById(R.id.logView); statusDevice = findViewById(R.id.statusDevice)
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(15))
        Thread {
            val isRoot = Shell.isAppGrantedRoot()?: false
            runOnUiThread { statusDevice.text = if(isRoot) "ROOT MODE" else "NO ROOT" }
            val res = Shell.cmd("ls /dev/block/by-name/ 2>/dev/null || ls /dev/block/bootdevice/by-name/ 2>/dev/null").exec()
            val parts = res.out.flatMap { it.split("\\s+".toRegex()) }.filter { it.isNotBlank() }.distinct().sorted()
            detectedParts.clear(); if(parts.isNotEmpty()) detectedParts.addAll(parts) else detectedParts.addAll(listOf("boot","recovery","vbmeta","dtbo","super"))
            log("Partisi auto: $detectedParts")
        }.start()
        findViewById<LinearLayout>(R.id.btnThis).setOnClickListener { SoundHelper.playClick(); showThisDeviceDialog() }
        findViewById<LinearLayout>(R.id.btnOther).setOnClickListener { SoundHelper.playClick(); showOtherDeviceDialog() }
        log("Ready - Internal + SD Card picker aktif")
    }
    fun showFilePicker(onPicked:(String)->Unit){
        SoundHelper.playClick()
        val dialog = Dialog(this); dialog.setContentView(R.layout.dialog_picker)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val container = dialog.findViewById<LinearLayout>(R.id.listContainer)
        val txtPath = dialog.findViewById<TextView>(R.id.txtPath)
        val inflater = LayoutInflater.from(this)
        fun listDir(dir: File){
            currentPath = dir; txtPath.text = dir.absolutePath; container.removeAllViews()
            val files = try { dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList() } catch(_:Exception){ emptyList() }
            if(files.isEmpty()){
                val t = TextView(this); t.text = "Folder kosong / No permission"; t.setTextColor(0xFF8892B0.toInt()); container.addView(t)
            }
            for(f in files){
                if(f.isHidden) continue
                if(f.isDirectory){
                    val v = inflater.inflate(R.layout.item_file, container, false)
                    v.findViewById<TextView>(R.id.nameFile).text = "📁 ${f.name}"
                    v.findViewById<TextView>(R.id.sizeFile).text = "Folder"
                    v.setOnClickListener { SoundHelper.playClick(); listDir(f) }
                    container.addView(v)
                } else if(f.extension.lowercase() in listOf("img","zip","bin")){
                    val v = inflater.inflate(R.layout.item_file, container, false)
                    v.findViewById<TextView>(R.id.nameFile).text = f.name
                    v.findViewById<TextView>(R.id.sizeFile).text = "${f.length()/1024/1024}MB"
                    v.setOnClickListener { SoundHelper.playOk(); dialog.dismiss(); onPicked(f.absolutePath) }
                    container.addView(v)
                }
            }
        }
        dialog.findViewById<TextView>(R.id.btnInternal).setOnClickListener { listDir(File("/storage/emulated/0")) }
        dialog.findViewById<TextView>(R.id.btnSdCard).setOnClickListener {
            val sd = File("/storage").listFiles()?.firstOrNull { it.name.contains("-") || it.name.lowercase().contains("sd") } ?: File("/storage")
            listDir(sd)
        }
        dialog.findViewById<TextView>(R.id.btnUp).setOnClickListener { currentPath.parentFile?.let { listDir(it) } }
        dialog.findViewById<TextView>(R.id.btnCancel).setOnClickListener { SoundHelper.playCancel(); dialog.dismiss() }
        listDir(currentPath)
        dialog.show()
    }
    fun showProgress(title:String){ cancelFlag=false; progressDialog = Dialog(this).apply { setContentView(R.layout.dialog_progress); window?.setBackgroundDrawableResource(android.R.color.transparent); setCancelable(false); findViewById<TextView>(R.id.txtProgressTitle).text=title; findViewById<TextView>(R.id.btnCancel).setOnClickListener { SoundHelper.playCancel(); cancelFlag=true; log("Batal"); dismiss() }; show() } }
    fun updateProgress(p:Int,m:String){ runOnUiThread { progressDialog?.findViewById<TextView>(R.id.txtProgressTitle)?.text=m; progressDialog?.findViewById<ProgressBar>(R.id.progBar)?.progress=p } }
    fun hideProgress(){ runOnUiThread { progressDialog?.dismiss() } }
    fun showThisDeviceDialog(){
        SoundHelper.playClick()
        val d = Dialog(this); d.setContentView(R.layout.dialog_this_device); d.window?.setBackgroundDrawableResource(android.R.color.transparent); d.show()
        val containerPart = d.findViewById<LinearLayout>(R.id.containerPartisi); containerPart.removeAllViews()
        val cbList = mutableListOf<Pair<String, CheckBox>>()
        for(part in detectedParts){ val cb=CheckBox(this); cb.text=if(part=="super") "$part [auto-detected]" else part; cb.setTextColor(-1); cb.isChecked=part.contains("boot")||part.contains("vbmeta")||part.contains("recovery")||part=="super"; containerPart.addView(cb); cbList.add(part to cb) }
        val btnFile = d.findViewById<TextView>(R.id.btnPilihFile); val spin = d.findViewById<Spinner>(R.id.spinPartisi); spin.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,detectedParts)
        d.findViewById<TextView>(R.id.btnClose).setOnClickListener { SoundHelper.playCancel(); d.dismiss() }
        d.findViewById<TextView>(R.id.btnMulaiBackup).setOnClickListener { SoundHelper.playClick(); val sel=cbList.filter{it.second.isChecked}.map{it.first}; if(sel.isEmpty()){Toast.makeText(this,"Pilih partisi",Toast.LENGTH_SHORT).show(); SoundHelper.playError(); return@setOnClickListener}; d.dismiss(); doBackupWithProgress(sel) }
        btnFile.setOnClickListener { showFilePicker { path-> selectedFile=path; btnFile.text=File(path).name; log("File: $path") } }
        d.findViewById<TextView>(R.id.btnMulaiFlash).setOnClickListener { SoundHelper.playClick(); val part=spin.selectedItem as String; val file=selectedFile; if(file==null){Toast.makeText(this,"Pilih file dulu",Toast.LENGTH_SHORT).show(); SoundHelper.playError(); return@setOnClickListener}; d.dismiss(); doFlashWithProgress(file,part) }
    }
    fun showOtherDeviceDialog(){
        SoundHelper.playClick()
        val d = Dialog(this); d.setContentView(R.layout.dialog_other_device); d.window?.setBackgroundDrawableResource(android.R.color.transparent); d.show()
        val spin = d.findViewById<Spinner>(R.id.spinPartisiOtg); spin.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,detectedParts)
        val btnFile = d.findViewById<TextView>(R.id.btnPilihFileOtg); val progBar = d.findViewById<ProgressBar>(R.id.progBarOtg); val txtPercent = d.findViewById<TextView>(R.id.txtPercentOtg)
        d.findViewById<TextView>(R.id.btnCloseOther).setOnClickListener { SoundHelper.playCancel(); d.dismiss() }
        btnFile.setOnClickListener { showFilePicker { path-> selectedFileOtg=path; btnFile.text=File(path).name; log("OTG File: $path") } }
        var cancelOtg=false; d.findViewById<TextView>(R.id.btnCancelOtg).setOnClickListener { SoundHelper.playCancel(); cancelOtg=true; txtPercent.text="Batal"; log("OTG Batal") }
        d.findViewById<TextView>(R.id.btnMulaiFlashOtg).setOnClickListener { SoundHelper.playClick(); val part=spin.selectedItem as String; val file=selectedFileOtg; if(file==null){Toast.makeText(this,"Pilih file dulu",Toast.LENGTH_SHORT).show(); SoundHelper.playError(); return@setOnClickListener}; Thread { for(i in 0..100 step 5){ if(cancelOtg) break; runOnUiThread{progBar.progress=i; txtPercent.text="$i%"}; Thread.sleep(120)}; if(!cancelOtg){ val r=Shell.cmd("fastboot flash $part $file 2>&1 || echo butuh fastboot binary").exec(); log("OTG Flash $part: ${r.out.joinToString()}"); SoundHelper.playOk(); runOnUiThread{Toast.makeText(this@MainActivity,"OTG Flash $part selesai",Toast.LENGTH_SHORT).show()}}}.start() }
    }
    fun doBackupWithProgress(parts:List<String>){ Thread { val ts=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date()); val dir="$backupBase/$ts"; Shell.cmd("mkdir -p $dir").exec(); runOnUiThread{showProgress("Backup ke $dir")}; log("Backup: $dir"); var ok=0; for((idx,p) in parts.withIndex()){ if(cancelFlag) break; updateProgress((idx*100)/parts.size,"Backup $p"); val cmd="src=$(ls /dev/block/by-name/$p 2>/dev/null || ls /dev/block/bootdevice/by-name/$p 2>/dev/null || find /dev/block -name $p | head -n1); if [ -z \"\$src\" ]; then echo FAIL; else dd if=\$src of=$dir/${p}.img bs=4096 2>&1; ls -lh $dir/${p}.img; echo OK; fi"; val r=Shell.cmd(cmd).exec(); val out=r.out.joinToString("\n"); log("$p: $out"); if(out.contains("OK")) ok++ }; hideProgress(); SoundHelper.playOk(); val check=Shell.cmd("ls -lh $dir; du -sh $dir").exec(); log("HASIL:\n${check.out.joinToString("\n")}"); runOnUiThread{Toast.makeText(this@MainActivity,"Backup $ok file di $dir",Toast.LENGTH_LONG).show()}}.start() }
    fun doFlashWithProgress(filePath:String,partition:String){ Thread { runOnUiThread{showProgress("Flash $partition...")}; for(i in 0..100 step 20){ if(cancelFlag) break; updateProgress(i,"Flash $partition ($i%)"); Thread.sleep(200)}; if(!cancelFlag){ val cmd="dst=$(ls /dev/block/by-name/$partition 2>/dev/null || ls /dev/block/bootdevice/by-name/$partition 2>/dev/null || find /dev/block -name $partition | head -n1); if [ -z \"\$dst\" ]; then echo FAIL; else dd if=$filePath of=\$dst bs=4096 2>&1; echo OK; fi"; val r=Shell.cmd(cmd).exec(); hideProgress(); log("Flash $partition: ${r.out.joinToString()}"); SoundHelper.playOk() } else hideProgress() }.start() }
}
