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
    private val parts = listOf("boot","recovery","vbmeta","dtbo","super","persist")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 120, 40, 40)
        }
        val title = TextView(this).apply { text = "FLASHER PRO"; textSize = 28f }
        val sub = TextView(this).apply { text = "Backup dulu baru flash\nThis Device = BISA BACKUP\nOther Device = LANGSUNG FLASH" }
        val btn1 = Button(this).apply { text = "FLASH TO THIS DEVICE (ROOT)\n-> Backup Wajib" }
        val btn2 = Button(this).apply { text = "FLASH TO OTHER DEVICE (OTG)\n-> Langsung Flash, No Backup" }
        val log = TextView(this).apply { text = "Log: ready\nRoot: ${Shell.isAppGrantedRoot()}" }

        btn1.setOnClickListener { showThisMenu(log) }
        btn2.setOnClickListener { showOtherMenu(log) }

        root.addView(title)
        root.addView(sub)
        root.addView(Space(this).apply { minimumHeight = 40 })
        root.addView(btn1)
        root.addView(btn2)
        root.addView(Space(this).apply { minimumHeight = 40 })
        root.addView(log)
        setContentView(root)
    }

    fun showThisMenu(log: TextView) {
        if (Shell.getShell().isRoot == false) {
            Toast.makeText(this, "ROOT tidak ada!", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this).setTitle("THIS DEVICE - Backup Dulu!").setItems(arrayOf(
            "1. BACKUP SEMUA PARTISI ASLI",
            "2. BACKUP + FLASH boot.img",
            "3. BACKUP + FLASH recovery.img",
            "4. BACKUP + FLASH URUTAN (boot > vbmeta > recovery)",
            "5. RESTORE DARI BACKUP"
        )) { _, i ->
            when(i) {
                0 -> doBackup(log, null)
                1 -> doBackupAndFlash(log, listOf("boot"))
                2 -> doBackupAndFlash(log, listOf("recovery"))
                3 -> doBackupAndFlash(log, listOf("boot","vbmeta","recovery","dtbo"))
                4 -> doRestore(log)
            }
        }.show()
    }

    fun showOtherMenu(log: TextView) {
        AlertDialog.Builder(this)
            .setTitle("OTHER DEVICE - WARNING NO BACKUP")
            .setMessage("Mode OTG FASTBOOT tidak bisa backup!\n\nUrutan:\n1. Colok HP target mode FASTBOOT via OTG\n2. Scan Device\n3. Flash Langsung\n\nFile taruh di /sdcard/Download/\nButuh binary fastboot di /sdcard/Download/fastboot")
            .setPositiveButton("LANJUT FLASH OTG") { _, _ ->
                AlertDialog.Builder(this).setTitle("Pilih Flash OTG").setItems(arrayOf(
                    "Flash boot.img",
                    "Flash recovery.img", 
                    "Flash Urutan: boot + vbmeta + recovery",
                    "Flash super.img"
                )) { _, j ->
                    val cmds = when(j) {
                        0 -> listOf("boot")
                        1 -> listOf("recovery")
                        2 -> listOf("boot","vbmeta","recovery")
                        else -> listOf("super")
                    }
                    var out = "OTG FLASH: ${cmds.joinToString()}\n"
                    out += "Pastikan fastboot devices terbaca!\n"
                    // contoh command, nanti pakai binary fastboot ARM
                    for(p in cmds) out += "fastboot flash $p /sdcard/Download/${p}.img\n"
                    log.text = out
                }.show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    fun doBackup(log: TextView, only: List<String>?) {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = "$backupBase/$ts"
        Shell.cmd("mkdir -p $dir").exec()
        val target = only ?: parts
        var out = "BACKUP KE $dir\n"
        for(p in target) {
            val src = if(File("/dev/block/by-name/$p").exists()) "/dev/block/by-name/$p" else "/dev/block/bootdevice/by-name/$p"
            if(File(src).exists() || src.contains("by-name")) {
                val r = Shell.cmd("dd if=$src of=$dir/${p}.img bs=4096 2>&1; ls -lh $dir/${p}.img").exec()
                out += "$p: ${r.out.joinToString()}\n"
            }
        }
        log.text = out
        Toast.makeText(this, "Backup selesai: $dir", Toast.LENGTH_LONG).show()
    }

    fun doBackupAndFlash(log: TextView, order: List<String>) {
        doBackup(log, order)
        var out = log.text.toString() + "\n--- MULAI FLASH URUTAN ---\n"
        for(p in order) {
            val src = "/sdcard/Download/${p}.img"
            if(File(src).exists()) {
                val r = Shell.cmd("dd if=$src of=/dev/block/by-name/$p bs=4096; echo SUCCESS $p").exec()
                out += "Flash $p: ${r.out}\n"
            } else {
                out += "SKIP $p: file $src tidak ada di Download!\n"
            }
        }
        out += "\nSelesai. Reboot?\n"
        log.text = out
    }

    fun doRestore(log: TextView) {
        val base = File(backupBase)
        val dirs = base.listFiles()?.sortedByDescending { it.name } ?: emptyList()
        if(dirs.isEmpty()) { Toast.makeText(this, "Tidak ada backup", Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(this).setTitle("Pilih Backup").setItems(dirs.map{it.name}.toTypedArray()) { _, i ->
            val dir = dirs[i].absolutePath
            AlertDialog.Builder(this).setTitle("Restore dari $dir").setItems(arrayOf("Restore boot","Restore recovery","Restore Semua")) { _, j ->
                val list = when(j) {0->listOf("boot");1->listOf("recovery"); else->parts}
                for(p in list) {
                    val img = "$dir/${p}.img"
                    if(File(img).exists()) Shell.cmd("dd if=$img of=/dev/block/by-name/$p").exec()
                }
                log.text = "Restore selesai dari $dir"
            }.show()
        }.show()
    }
}
