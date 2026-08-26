package com.theglitch.glitchshops.ui;

public final class DialogBridge {

    private static volatile Boolean cachedRuntime;

    private DialogBridge() {
    }

    public static boolean dialogsRuntime() {
        Boolean known = cachedRuntime;
        if (known != null) {
            return known;
        }
        boolean present;
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class.forName("io.papermc.paper.registry.data.dialog.type.NoticeTypeImpl");
            present = true;
        } catch (Throwable t) {
            present = false;
        }
        cachedRuntime = present;
        return present;
    }

    public static String runtimeSummary() {
        return dialogsRuntime()
                ? "modern-ui active (holographic layer) | native client dialogs runtime: PRESENT (v2 flows planned)"
                : "modern-ui active (holographic layer) | native client dialogs runtime: NOT DETECTED";
    }
}
