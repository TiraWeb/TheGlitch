package com.theglitch.glitchhealthbar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class HealthBarManager {

    private final GlitchHealthBar plugin;
    private final Map<UUID, BarEntry> bars = new HashMap<>();

    private record BarEntry(LivingEntity target, TextDisplay display) {
    }

    public HealthBarManager(GlitchHealthBar plugin) {
        this.plugin = plugin;
    }

    public void attach(LivingEntity mob) {
        if (!mob.isValid() || mob.isDead()) return;
        if (bars.containsKey(mob.getUniqueId())) return;

        TextDisplay display = mob.getWorld().spawn(barLocation(mob), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThroughBlocks(true);
            d.setShadowed(true);
            d.setPersistent(false);
        });

        bars.put(mob.getUniqueId(), new BarEntry(mob, display));
        refresh(mob);
    }

    public void refresh(LivingEntity mob) {
        BarEntry entry = bars.get(mob.getUniqueId());
        if (entry == null || !mob.isValid() || mob.isDead()) return;
        entry.display().teleport(barLocation(mob));
        entry.display().text(barText(mob));
    }

    public void remove(LivingEntity mob) {
        BarEntry entry = bars.remove(mob.getUniqueId());
        if (entry != null) {
            entry.display().remove();
        }
    }

    public void tick() {
        Iterator<Map.Entry<UUID, BarEntry>> it = bars.entrySet().iterator();
        while (it.hasNext()) {
            BarEntry entry = it.next().getValue();
            LivingEntity target = entry.target();
            if (!target.isValid() || target.isDead()
                    || !target.getWorld().isChunkLoaded(target.getLocation())) {
                entry.display().remove();
                it.remove();
                continue;
            }
            entry.display().teleport(barLocation(target));
            entry.display().text(barText(target));
        }
    }

    public void clearAll() {
        for (BarEntry entry : bars.values()) {
            entry.display().remove();
        }
        bars.clear();
    }

    private Location barLocation(LivingEntity mob) {
        return mob.getLocation().add(0, mob.getHeight() * plugin.offsetFraction() + 0.4, 0);
    }

    private Component barText(LivingEntity mob) {
        double hp = Math.max(0, mob.getHealth());
        double max = Math.max(1, mob.getMaxHealth());
        double fraction = hp / max;
        int length = plugin.barLength();
        int filled = (int) Math.round(fraction * length);
        if (filled > length) filled = length;

        TextColor fillColor = fraction >= 0.5 ? plugin.colorHigh()
                : fraction >= 0.25 ? plugin.colorMid()
                : plugin.colorLow();

        Component bar = Component.text("█".repeat(filled), fillColor)
                .append(Component.text("░".repeat(length - filled), plugin.colorEmpty()));
        if (plugin.showNumbers()) {
            bar = bar.append(Component.text(" " + (int) hp + "/" + (int) max, TextColor.color(0xFFFFFF)));
        }
        return bar;
    }
}
