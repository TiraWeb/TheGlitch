package com.theglitch.glitchtutorial;

/** Tutorial steps in order. The first six happen in the tutorial world. */
enum Step {
    INTRO, CLASS, CRATES, MOBS, ROGUE, EXTRACT, HUB, DUNGEON, DONE;

    boolean inTutorialWorld() {
        return ordinal() <= EXTRACT.ordinal();
    }

    Step next() {
        return values()[Math.min(ordinal() + 1, DONE.ordinal())];
    }

    String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
