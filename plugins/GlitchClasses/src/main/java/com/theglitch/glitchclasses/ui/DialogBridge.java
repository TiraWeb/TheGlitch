package com.theglitch.glitchclasses.ui;

/**
 * Honest runtime probe for Paper/Purpur native client dialogs. The compile
 * API (paper-api 1.21.4) does not ship the dialog registry, so this bridge
 * only detects — it never constructs dialog instances. v2 flows planned.
 */
public final class DialogBridge {

    private static final boolean PRESENT = probe();

    private DialogBridge() {
    }

    private static boolean probe() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class.forName("io.papermc.paper.registry.data.dialog.type.NoticeTypeImpl");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean dialogsRuntime() {
        return PRESENT;
    }

    public static String runtimeSummary() {
        return dialogsRuntime()
                ? "modern-ui active (holographic layer) | native client dialogs runtime: PRESENT (v2 flows planned)"
                : "modern-ui active (holographic layer) | native client dialogs runtime: NOT DETECTED (legacy inventory UI)";
    }
}
