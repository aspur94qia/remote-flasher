package com.asppur.flasher
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
object SoundManager {
    fun click(v: View){
        try {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            v.playSoundEffect(SoundEffectConstants.CLICK)
        } catch (e: Exception) { }
    }
}
