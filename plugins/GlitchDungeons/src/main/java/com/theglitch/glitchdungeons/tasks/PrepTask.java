package com.theglitch.glitchdungeons.tasks;

import com.theglitch.glitchdungeons.ColorUtil;
import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.models.DungeonRun;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PrepTask {
    private final GlitchDungeons plugin;
    private final DungeonRun run;
    private BossBar bossBar;
    private int taskId = -1;

    public PrepTask(GlitchDungeons plugin, DungeonRun run) {
        this.plugin = plugin;
        this.run = run;
    }

    public void start() {
        int prepTime = plugin.getDungeonConfig().getPrepTime();

        // Create boss bar
        bossBar = Bukkit.createBossBar(
            ColorUtil.colorize("&ePreparing... " + prepTime + "s"),
            BarColor.YELLOW,
            BarStyle.SOLID
        );
        for (UUID member : run.getParty().getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                bossBar.addPlayer(player);
                player.sendMessage(ColorUtil.colorize("&eDungeon prep phase! Get ready in " + prepTime + "s!"));
            }
        }

        final int[] remaining = {prepTime};
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (run.getState() != DungeonRun.State.PREP) {
                cancel();
                bossBar.removeAll();
                return;
            }

            remaining[0]--;
            bossBar.setTitle(ColorUtil.colorize("&ePreparing... " + remaining[0] + "s"));
            bossBar.setProgress((double) remaining[0] / prepTime);

            if (remaining[0] <= 0) {
                cancel();
                bossBar.removeAll();
                // Start active phase
                run.setState(DungeonRun.State.ACTIVE);

                // Start first wave
                plugin.getWaveManager().startNextWave(run);

                // Start timer task with new boss bar
                new TimerTask(plugin, run).start();
            }
        }, 20L, 20L).getTaskId();
    }

    public void cancel() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

}
