package com.theglitch.glitchquests;

import org.bukkit.Material;

/** One quest from the config pool. */
public record QuestDef(String id, Period period, String name, String description,
                       QuestType type, int amount, Material icon, Reward reward) {

    public enum Period { DAILY, WEEKLY }

    public enum QuestType {
        KILL_MOB, KILL_GLITCH_MOB, KILL_PLAYER, EXTRACT,
        MINE_BLOCK, MINE_ORE, LOOT_CONTAINER, PLAYTIME,
        TRAVEL, FISH, EAT, CRAFT, COMPLETE_DAILY
    }
}
