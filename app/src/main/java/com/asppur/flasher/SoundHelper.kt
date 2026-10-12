package com.asppur.flasher
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
object SoundHelper {
    private var pool: SoundPool? = null
    private var click=0; var ok=0; var cancel=0; var error=0
    fun init(c: Context){
        if(pool!=null) return
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()).build()
        // pakai tone bawaan system (fallback) - kita pakai resource id 1 sebagai click
        click = pool!!.load(c, android.R.raw.notification, 1)
    }
    fun playClick(){ try{ pool?.play(click,1f,1f,1,0,1f) }catch(_:Exception){} }
    fun playOk(){ try{ pool?.play(click,1f,1f,1,0,1.5f) }catch(_:Exception){} }
    fun playCancel(){ try{ pool?.play(click,1f,1f,1,0,0.7f) }catch(_:Exception){} }
}
