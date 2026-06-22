package com.usc.cruzr.helpdesk;

import android.util.Log;

import com.ubtrobot.Robot;
import com.ubtrobot.skill.SkillInfo;
import com.ubtrobot.skill.SkillManager;

import java.util.List;
import java.util.Locale;

/**
 * Pauses Cruzr chat/voice assistant skills that compete for the microphone.
 */
final class AssistantSkillPauser {

    private static final String TAG = "AssistantSkillPauser";

    private AssistantSkillPauser() {
    }

    static void pauseAssistantSkills() {
        try {
            SkillManager skillManager = Robot.globalContext().getSystemService(SkillManager.SERVICE);
            if (skillManager == null) {
                return;
            }
            pauseMatchingSkills(skillManager, skillManager.getRunningSkills());
            pauseMatchingSkills(skillManager, skillManager.getStartedSkills());
        } catch (Throwable error) {
            Log.w(TAG, "Could not pause assistant skills", error);
        }
    }

    private static void pauseMatchingSkills(SkillManager skillManager, List<SkillInfo> skills) {
        if (skills == null || skills.isEmpty()) {
            return;
        }
        for (SkillInfo skill : skills) {
            if (skill == null || !isAssistantSkill(skill)) {
                continue;
            }
            String packageName = skill.getPackageName();
            String skillName = skill.getName();
            try {
                skillManager.pauseSkill(packageName, skillName);
                Log.i(TAG, "Paused skill " + packageName + "/" + skillName);
            } catch (Throwable error) {
                try {
                    skillManager.stopSkill(packageName, skillName);
                    Log.i(TAG, "Stopped skill " + packageName + "/" + skillName);
                } catch (Throwable stopError) {
                    Log.w(TAG, "Could not pause skill " + packageName + "/" + skillName, error);
                }
            }
        }
    }

    private static boolean isAssistantSkill(SkillInfo skill) {
        String combined = String.format(
                Locale.US,
                "%s %s %s %s",
                safeLower(skill.getName()),
                safeLower(skill.getPackageName()),
                safeLower(skill.getClassName()),
                safeLower(skill.getCategory())
        );
        return combined.contains("assistant")
                || combined.contains("chat")
                || combined.contains("voice")
                || combined.contains("speech")
                || combined.contains("dialog")
                || combined.contains("wakeup")
                || combined.contains("wake_up")
                || combined.contains("llm");
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
