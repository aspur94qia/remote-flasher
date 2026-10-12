package com.asppur.flasher
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

object SoundHelper {
    private var tone: ToneGenerator? = null
    fun init(c: Context){
        try { tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80) } catch(_:Exception){}
    }
    fun playClick(){ try{ tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }catch(_:Exception){} }
    fun playOk(){ try{ tone?.startTone(ToneGenerator.TONE_PROP_ACK, 200) }catch(_:Exception){} }
    fun playCancel(){ try{ tone?.startTone(ToneGenerator.TONE_PROP_NACK, 200) }catch(_:Exception){} }
    fun playError(){ try{ tone?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300) }catch(_:Exception){} }
}
