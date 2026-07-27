package com.theglitch.glitchdungeons.tasks;

import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.models.DungeonRun;
import org.bukkit.Bukkit;

public class WaveTask {
    private final GlitchDungeons plugin;
    private final DungeonRun run;
    private int taskId = -1;

    public WaveTask(GlitchDungeons plugin, DungeonRun run) {
        this.plugin = plugin;
        this.run = run;
    }

    public void start(int delaySeconds) {
        taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (run.getState() == DungeonRun.State.ACTIVE) {
                plugin.getWaveManager().startNextWave(run);
            }
        }, delaySeconds * 20L).getTaskId();
    }

    public void cancel() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
