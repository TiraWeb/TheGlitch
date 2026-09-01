package com.theglitch.glitchhud;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Resolves dynamic HUD values via PlaceholderAPI when available,
 * with direct Vault/Economy fallback. All methods are null-safe
 * and never throw.
 */
public final class PlaceholderResolver {

    private final GlitchHUD plugin;
    private volatile Object economy; // reflective Vault Economy, if present
    private volatile boolean hasPapi;

    public PlaceholderResolver(GlitchHUD plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        // Try to grab Vault Economy via reflection — avoids compile-time VaultAPI dep
        economy = null;
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object rsp = Bukkit.getServicesManager().getRegistration(econClass);
            if (rsp != null) {
                try {
                    java.lang.reflect.Method getProvider = rsp.getClass().getMethod("getProvider");
                    economy = getProvider.invoke(rsp);
                } catch (Exception ignored) {}
            }
        } catch (ClassNotFoundException ignored) {
            // Vault not present
        } catch (Exception ignored) {}
    }

    public boolean hasPapi() { return hasPapi; }

    /** Resolve a raw placeholder string through PAPI if available (reflective). */
    public String resolve(Player player, String raw) {
        if (raw == null) return "";
        if (!hasPapi || player == null) return raw;
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            java.lang.reflect.Method m = papiClass.getMethod("setPlaceholders", Player.class, String.class);
            Object out = m.invoke(null, player, raw);
            return out == null ? raw : (String) out;
        } catch (Exception e) {
            return raw;
        }
    }

    /** Direct string placeholder query (e.g. "%glitchitems_stacks%"). */
    public String papi(Player player, String placeholderWithPercents) {
        if (placeholderWithPercents == null) return "";
        if (!hasPapi || player == null) return placeholderWithPercents;
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            java.lang.reflect.Method m = papiClass.getMethod("setPlaceholders", Player.class, String.class);
            Object out = m.invoke(null, player, placeholderWithPercents);
            String s = out == null ? "" : (String) out;
            if (s.equals(placeholderWithPercents)) return "";
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    public long getBalance(Player player) {
        if (player == null) return 0;
        if (hasPapi) {
            String v = papi(player, "%vault_eco_balance%");
            if (v != null && !v.isBlank() && !v.contains("%")) {
                try { return Math.round(Double.parseDouble(v.replace(",", ""))); } catch (NumberFormatException ignored) {}
            }
        }
        if (economy != null) {
            try {
                java.lang.reflect.Method m = economy.getClass().getMethod("getBalance", Player.class);
                Object bal = m.invoke(economy, player);
                if (bal instanceof Number n) return Math.round(n.doubleValue());
            } catch (Exception ignored) {
                try {
                    java.lang.reflect.Method m2 = economy.getClass().getMethod("getBalance", org.bukkit.OfflinePlayer.class);
                    Object bal = m2.invoke(economy, (org.bukkit.OfflinePlayer) player);
                    if (bal instanceof Number n) return Math.round(n.doubleValue());
                } catch (Exception ignored2) {}
            }
        }
        return 0;
    }

    public String getGlitchClass(Player player) {
        if (player == null) return "None";
        if (hasPapi) {
            String v = papi(player, "%glitchclasses_class%");
            if (v != null && !v.isBlank() && !v.contains("%")) return capitalize(v);
        }
        return "None";
    }

    public String getGlitchLevel(Player player) {
        if (hasPapi) {
            String v = papi(player, "%glitchclasses_level%");
            if (v != null && !v.isBlank() && !v.contains("%")) return v;
        }
        return "0";
    }

    public int getStacks(Player player) {
        if (hasPapi) {
            String v = papi(player, "%glitchitems_stacks%");
            if (v != null && !v.isBlank() && !v.contains("%")) {
                try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    public int getMaxStacks(Player player) {
        if (hasPapi) {
            String v = papi(player, "%glitchitems_max_stacks%");
            if (v != null && !v.isBlank() && !v.contains("%")) {
                try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return 8;
    }

    public int getPayout(Player player) {
        if (hasPapi) {
            String v = papi(player, "%glitchitems_payout%");
            if (v != null && !v.isBlank() && !v.contains("%")) {
                try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    public int getDmgTaken(Player player) {
        if (hasPapi) {
            String v = papi(player, "%glitchitems_dmg_taken%");
            if (v != null && !v.isBlank() && !v.contains("%")) {
                try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    public boolean isInRaid(Player player) {
        if (hasPapi) {
            String v = papi(player, "%glitchraid_in_raid%");
            if ("true".equalsIgnoreCase(v)) return true;
            if ("false".equalsIgnoreCase(v)) return false;
        }
        return false;
    }

    public String getTimeLeftFormatted(Player player) {
        if (hasPapi) {
            String v = papi(player, "%glitchraid_time_left_formatted%");
            if (v != null && !v.isBlank() && !v.contains("%")) return v;
        }
        return null;
    }

    public int getPing(Player player) {
        if (player == null) return -1;
        if (hasPapi) {
            String[] candidates = {"%player_ping%", "%ping%"};
            for (String ph : candidates) {
                String v = papi(player, ph);
                if (v != null && !v.isBlank() && !v.contains("%")) {
                    try { return Integer.parseInt(v.replace(",", "").trim()); } catch (NumberFormatException ignored) {}
                    try { return (int) Math.round(Double.parseDouble(v.trim())); } catch (NumberFormatException ignored) {}
                }
            }
        }
        // Direct Paper API (1.19.4+)
        try { return player.getPing(); } catch (Exception ignored) {}
        // Reflective fallback (older / Purpur)
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("getPing");
            Object r = m.invoke(player);
            if (r instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return -1;
    }

    public double getTps() {
        // Try PAPI first (Server expansion)
        if (hasPapi) {
            // Use dummy offline player for server placeholders; PAPI can handle null
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method m = papiClass.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
                String[] candidates = {"%server_tps_1%", "%server_tps%", "%tps%"};
                for (String ph : candidates) {
                    try {
                        Object out = m.invoke(null, (org.bukkit.OfflinePlayer) null, ph);
                        if (out instanceof String s && !s.contains("%") && !s.isBlank()) {
                            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        // Direct Paper/Bukkit TPS
        try {
            double[] tps = Bukkit.getTPS();
            if (tps != null && tps.length > 0 && tps[0] > 0) return tps[0];
        } catch (Exception ignored) {}
        try {
            double[] tps = Bukkit.getServer().getTPS();
            if (tps != null && tps.length > 0 && tps[0] > 0) return tps[0];
        } catch (Exception ignored) {}
        // Reflective last resort
        try {
            java.lang.reflect.Method m = Bukkit.class.getMethod("getTPS");
            Object r = m.invoke(null);
            if (r instanceof double[] arr && arr.length > 0) return arr[0];
        } catch (Exception ignored) {}
        return -1;
    }

    public String getWorldShardPlaceholder(Player p) { return "%vault_eco_balance%"; }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
