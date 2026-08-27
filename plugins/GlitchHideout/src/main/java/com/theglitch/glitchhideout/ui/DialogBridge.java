package com.theglitch.glitchhideout.ui;

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
                ? "modern-ui active (holographic layer) | native client dialogs runtime: PRESENT"
                : "modern-ui active (holographic layer) | native client dialogs runtime: NOT DETECTED (legacy inventory UI)";
    }
}
