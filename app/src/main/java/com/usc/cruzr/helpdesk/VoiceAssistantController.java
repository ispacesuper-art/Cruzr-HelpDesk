package com.usc.cruzr.helpdesk;

import android.content.Context;
import android.util.Log;

import com.ubtechinc.cruzr.assistant.sdk.AssistantManager;

/**
 * Pauses the Cruzr system voice assistant while Help Desk is in use.
 */
public final class VoiceAssistantController {

    private static final String TAG = "VoiceAssistantController";

    private VoiceAssistantController() {
    }

    public static void disableForHelpDesk(Context context) {
        try {
            AssistantManager manager = AssistantManager.get(context.getApplicationContext());
            manager.switchAssistant(AssistantManager.TYPE_TURN_OFF);
            manager.hideAssistant();
            manager.showOrHidePart(AssistantManager.TYPE_HIDE_PART_WAKEUP);
            manager.showOrHidePart(AssistantManager.TYPE_HIDE_PART_MESSAGE);
        } catch (Throwable error) {
            Log.w(TAG, "Could not disable Cruzr voice assistant", error);
        }
    }

    public static void restoreDefault(Context context) {
        try {
            AssistantManager manager = AssistantManager.get(context.getApplicationContext());
            manager.switchAssistant(AssistantManager.TYPE_TURN_ON);
            manager.showOrHidePart(AssistantManager.TYPE_SHOW_PART_WAKEUP);
            manager.showOrHidePart(AssistantManager.TYPE_SHOW_PART_MESSAGE);
        } catch (Throwable error) {
            Log.w(TAG, "Could not restore Cruzr voice assistant", error);
        }
    }
}
