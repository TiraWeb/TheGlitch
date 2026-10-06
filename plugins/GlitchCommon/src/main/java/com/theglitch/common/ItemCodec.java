package com.theglitch.common;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Base64 item encoding used by the stash and insurance save files.
 * <p>
 * Uses Bukkit object streams, which Paper deprecates. The format is kept on purpose:
 * every existing player file is written in it. Moving to
 * {@code ItemStack#serializeAsBytes} would need a read-both migration first.
 */
@SuppressWarnings("deprecation")
public final class ItemCodec {

    private ItemCodec() {
    }

    /** Encodes one stack; {@code null} for a null stack or an encoding failure. */
    public static String encode(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeObject(item);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    /** Decodes one stack; {@code null} for blank input or corrupt data. */
    public static ItemStack decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            return (ItemStack) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    /** Encodes an array slot-for-slot (empty slots become {@code null} entries). */
    public static List<String> encodeAll(ItemStack[] items) {
        List<String> result = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            result.add(encode(item));
        }
        return result;
    }

    /** Decodes a list produced by {@link #encodeAll(ItemStack[])}. */
    public static ItemStack[] decodeAll(List<String> encoded) {
        ItemStack[] items = new ItemStack[encoded.size()];
        for (int i = 0; i < encoded.size(); i++) {
            items[i] = decode(encoded.get(i));
        }
        return items;
    }
}
