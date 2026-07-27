package com.theglitch.glitchdungeons.tasks;

import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.models.DungeonRun;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TimerTask {
    private final GlitchDungeons plugin;
    private final DungeonRun run;
    private BossBar bossBar;
    private int taskId = -1;

    public TimerTask(GlitchDungeons plugin, DungeonRun run) {
        this.plugin = plugin;
        this.run = run;
    }

    public void startPrep() {
        new PrepTask(plugin, run).start();
    }

    public void start() {
        // Create boss bar for the active phase
        bossBar = Bukkit.createBossBar(
            colorize("&cFIGHT!"),
            BarColor.RED,
            org.bukkit.boss.BarStyle.SOLID
        );
        for (java.util.UUID member : run.getParty().getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && player.isOnline()) {
                bossBar.addPlayer(player);
            }
        }

        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (run.getState() != DungeonRun.State.ACTIVE) {
                cancel();
                return;
            }

            run.tickTimer();
            int time = run.getRemainingTime();

            if (time <= 0) {
                cancel();
                plugin.getDungeonManager().failDungeon(run, DungeonRun.FailReason.TIMEOUT);
                return;
            }

            // Update boss bar
            int minutes = time / 60;
            int seconds = time % 60;
            String timeStr = String.format("%d:%02d", minutes, seconds);
            bossBar.setTitle(colorize("&c" + timeStr + " remaining"));
            bossBar.setProgress((double) time / run.getMaxTime());

            // Color changes based on time
            if (time <= 60) {
                bossBar.setColor(BarColor.RED);
            } else if (time <= 180) {
                bossBar.setColor(BarColor.ORANGE);
            }

            // Low time warning
            if (time == 60 || time == 30 || time == 10) {
                for (UUID member : run.getParty().getMembers()) {
                    Player player = Bukkit.getPlayer(member);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(colorize("&c&l" + time + " seconds remaining!"));
                    }
                }
            }
        }, 20L, 20L).getTaskId();
    }

    public void cancel() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    private String colorize(String msg) {
        return msg.replaceAll("&([0-9a-fk-or])", "\u00A7$1");
    }
}
