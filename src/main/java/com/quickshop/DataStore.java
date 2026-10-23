package com.quickshop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class DataStore {
    private final QuickShopPlugin plugin;
    private final File file;
    private final Map<UUID, Map<Integer, QuickEntry>> quick = new HashMap<>();
    private final Map<UUID, Integer> shards = new HashMap<>();
    private boolean dirty;

    public DataStore(QuickShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sh = y.getConfigurationSection("shards");
        if (sh != null) for (String k : sh.getKeys(false)) shards.put(UUID.fromString(k), sh.getInt(k));
        ConfigurationSection q = y.getConfigurationSection("quick");
        if (q != null) for (String u : q.getKeys(false)) {
            ConfigurationSection us = q.getConfigurationSection(u);
            Map<Integer, QuickEntry> m = new HashMap<>();
            for (String s : us.getKeys(false)) {
                ConfigurationSection e = us.getConfigurationSection(s);
                try {
                    m.put(Integer.parseInt(s), new QuickEntry(QuickEntry.Type.valueOf(e.getString("type")),
                            e.getString("key"), e.getInt("level"), e.getInt("amount", 1)));
                } catch (Exception ignored) {}
            }
            quick.put(UUID.fromString(u), m);
        }
    }

    public void save() {
        if (!dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        shards.forEach((u, v) -> y.set("shards." + u, v));
        quick.forEach((u, m) -> m.forEach((slot, e) -> {
            String p = "quick." + u + "." + slot;
            y.set(p + ".type", e.type().name());
            y.set(p + ".key", e.key());
            y.set(p + ".level", e.level());
            y.set(p + ".amount", e.amount());
        }));
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException ex) { plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage()); }
    }

    public QuickEntry get(Player p, int slot) {
        Map<Integer, QuickEntry> m = quick.get(p.getUniqueId());
        return m == null ? null : m.get(slot);
    }

    public Map<Integer, QuickEntry> all(Player p) {
        return quick.getOrDefault(p.getUniqueId(), Collections.emptyMap());
    }

    public void set(Player p, int slot, QuickEntry e) {
        quick.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(slot, e);
        dirty = true;
    }

    public void remove(Player p, int slot) {
        Map<Integer, QuickEntry> m = quick.get(p.getUniqueId());
        if (m != null && m.remove(slot) != null) dirty = true;
    }

    public int shards(Player p) { return shards(p.getUniqueId()); }

    public void addShards(Player p, int amount) { addShards(p.getUniqueId(), amount); }

    public int shards(UUID id) { return shards.getOrDefault(id, 0); }

    public void setShards(UUID id, int amount) {
        shards.put(id, Math.max(0, amount));
        dirty = true;
    }

    public void addShards(UUID id, int amount) { setShards(id, shards(id) + amount); }
}
