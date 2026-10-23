package com.quickshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

public final class Messages {
    public static final TextColor BLUE = TextColor.fromHexString("#00a2ff");
    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL  = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private Messages() {}

    public static String sc(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) {
            int i = NORMAL.indexOf(c);
            sb.append(i >= 0 ? SMALL.charAt(i) : c);
        }
        return sb.toString();
    }

    private static Component t(String s, TextColor c) { return Component.text(sc(s), c); }

    public static void inventoryFull(Player p) {
        p.sendActionBar(t("inventory", BLUE).append(t(" ", NamedTextColor.WHITE)).append(t("full", NamedTextColor.RED)));
    }

    public static void added(Player p, String item) {
        p.sendActionBar(t("added", NamedTextColor.GREEN).append(t(" ", NamedTextColor.WHITE))
                .append(t(item, BLUE)).append(t(" to quick buy", NamedTextColor.WHITE)));
    }

    /** currency = "money" or "shards" */
    public static void notEnough(Player p, String currency, String item, String needed) {
        p.sendActionBar(t("not enough " + currency + " to buy ", NamedTextColor.WHITE)
                .append(t(item, BLUE)).append(t(". need ", NamedTextColor.WHITE))
                .append(t(needed, NamedTextColor.DARK_RED)).append(t(" more", NamedTextColor.WHITE)));
    }

    public static void removed(Player p) {
        p.sendActionBar(t("removed", NamedTextColor.RED).append(t(" ", NamedTextColor.WHITE))
                .append(t("item", BLUE)).append(t(" from quick buy", NamedTextColor.WHITE)));
    }

    public static String fmt(double d) {
        return d == Math.floor(d) ? String.format("%,.0f", d) : String.format("%,.2f", d);
    }
}
