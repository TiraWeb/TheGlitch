package com.theglitch.glitchdungeons.tasks;

import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.listeners.ExtractionListener;
import com.theglitch.glitchdungeons.models.DungeonRun;
import org.bukkit.Bukkit;

public class ExtractionTask {
    private final GlitchDungeons plugin;
    private final DungeonRun run;
    private final ExtractionListener extractionListener;
    private int taskId = -1;

    public ExtractionTask(GlitchDungeons plugin, DungeonRun run, ExtractionListener extractionListener) {
        this.plugin = plugin;
        this.run = run;
        this.extractionListener = extractionListener;
    }

    public void start() {
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (run.getState() != DungeonRun.State.EXTRACTING) {
                cancel();
                return;
            }
            extractionListener.tickExtraction(run);
        }, 1L, 1L).getTaskId();
    }

    public void cancel() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
