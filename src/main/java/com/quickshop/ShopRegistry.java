package com.quickshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.*;

public final class ShopRegistry {
    public record Category(String id, String title, Material icon, Map<Material, Double> items) {}

    private final QuickShopPlugin plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<String, Double> shards = new LinkedHashMap<>();
    private final Map<String, TreeMap<Integer, Double>> books = new LinkedHashMap<>();

    public ShopRegistry(QuickShopPlugin plugin) { this.plugin = plugin; }

    /** Loads (or reloads) everything from prices.yml */
    public void load(FileConfiguration c) {
        categories.clear(); shards.clear(); books.clear();

        ConfigurationSection shops = c.getConfigurationSection("shops");
        if (shops != null) for (String id : shops.getKeys(false)) {
            ConfigurationSection s = shops.getConfigurationSection(id);
            if (s == null) continue;
            Material icon = Material.matchMaterial(s.getString("icon", "CHEST"));
            Map<Material, Double> items = new LinkedHashMap<>();
            ConfigurationSection is = s.getConfigurationSection("items");
            if (is != null) for (String k : is.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m == null) { plugin.getLogger().warning("Unknown material " + k + " in shop " + id); continue; }
                items.put(m, is.getDouble(k));
            }
            categories.put(id, new Category(id, s.getString("title", id), icon == null ? Material.CHEST : icon, items));
        }

        ConfigurationSection sh = c.getConfigurationSection("shards");
        if (sh != null) for (String k : sh.getKeys(false)) shards.put(k.toLowerCase(), sh.getDouble(k));

        ConfigurationSection bk = c.getConfigurationSection("books");
        if (bk != null) for (String ench : bk.getKeys(false)) {
            if (enchant(ench) == null) { plugin.getLogger().warning("Unknown enchantment " + ench + " (skipped)"); continue; }
            ConfigurationSection lv = bk.getConfigurationSection(ench);
            if (lv == null) continue;
            TreeMap<Integer, Double> m = new TreeMap<>();
            for (String l : lv.getKeys(false)) {
                try { m.put(Integer.parseInt(l), lv.getDouble(l)); } catch (NumberFormatException ignored) {}
            }
            books.put(ench.toLowerCase(), m);
        }
    }

    public Map<String, Category> categories() { return categories; }
    public Map<String, Double> shards() { return shards; }
    public Map<String, TreeMap<Integer, Double>> books() { return books; }

    public static Enchantment enchant(String key) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase()));
    }

    public static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n); };
    }

    public static String pretty(String key) { return key.toLowerCase().replace('_', ' '); }

    public static String cap(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    /** price for ONE unit (money for items/books, shards for spawners) */
    public double price(QuickEntry e) {
        return switch (e.type()) {
            case ITEM -> {
                Material mat = Material.matchMaterial(e.key());
                for (Category c : categories.values()) {
                    Double p = c.items().get(mat);
                    if (p != null) yield p;
                }
                yield Double.MAX_VALUE;
            }
            case SHARD -> shards.getOrDefault(e.key(), Double.MAX_VALUE);
            case BOOK -> {
                TreeMap<Integer, Double> m = books.get(e.key());
                yield m == null ? Double.MAX_VALUE : m.getOrDefault(e.level(), Double.MAX_VALUE);
            }
        };
    }

    public boolean exists(QuickEntry e) { return price(e) != Double.MAX_VALUE; }

    /** Max stack size for the item (ender pearls 16, totems 1, ...). */
    public int maxAmount(QuickEntry e) {
        return switch (e.type()) {
            case BOOK -> 1;
            case SHARD -> 64;
            case ITEM -> {
                Material m = Material.matchMaterial(e.key());
                yield m == null ? 1 : Math.max(1, m.getMaxStackSize());
            }
        };
    }

    public String name(QuickEntry e) {
        return switch (e.type()) {
            case ITEM -> pretty(e.key());
            case SHARD -> e.key() + " spawner";
            case BOOK -> pretty(e.key()) + " " + roman(e.level()) + " book";
        };
    }

    /** "Price: $1,000" (or "Price: 350 shards" for spawners) for the given amount. */
    public Component priceLine(QuickEntry e, int amount) {
        double total = price(e) * amount;
        String val = e.type() == QuickEntry.Type.SHARD ? Messages.fmt(total) + " shards" : "$" + Messages.fmt(total);
        return Component.text("Price: ", NamedTextColor.GRAY)
                .append(Component.text(val, NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false);
    }

    /** What the player actually receives (ITEM / BOOK). */
    public ItemStack product(QuickEntry e) {
        if (e.type() == QuickEntry.Type.BOOK) {
            ItemStack b = new ItemStack(Material.ENCHANTED_BOOK, e.amount());
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) b.getItemMeta();
            meta.addStoredEnchant(enchant(e.key()), e.level(), true);
            b.setItemMeta(meta);
            return b;
        }
        return new ItemStack(Material.matchMaterial(e.key()), e.amount());
    }

    /** Display icon (amount 1). */
    public ItemStack icon(QuickEntry e) {
        return switch (e.type()) {
            case ITEM -> new ItemStack(Material.matchMaterial(e.key()));
            case SHARD -> new ItemStack(Material.SPAWNER);
            case BOOK -> { ItemStack b = product(new QuickEntry(e.type(), e.key(), e.level(), 1)); yield b; }
        };
    }
}
