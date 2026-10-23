package com.quickshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class QuickShopPlugin extends JavaPlugin implements Listener {
    private Economy economy;
    private ShopRegistry registry;
    private DataStore data;
    private QuickBuyGui gui;
    private Menus menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "prices.yml").exists()) saveResource("prices.yml", false);

        // Economy is looked up lazily, so load order never stops /shop from registering.
        Bukkit.getScheduler().runTask(this, () -> {
            if (economy() == null)
                getLogger().warning("No Vault economy provider found! /shop works, but buying needs an economy plugin.");
        });

        registry = new ShopRegistry(this);
        loadPrices();
        data = new DataStore(this);
        gui = new QuickBuyGui(this);
        menus = new Menus(this);

        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> data.save(), 20L * 30, 20L * 30);
    }

    @Override
    public void onDisable() {
        if (data != null) data.save();
    }

    private void loadPrices() {
        registry.load(YamlConfiguration.loadConfiguration(new File(getDataFolder(), "prices.yml")));
    }

    public Economy economy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }
    public ShopRegistry registry() { return registry; }
    public DataStore data() { return data; }
    public QuickBuyGui gui() { return gui; }
    public Menus menus() { return menus; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("shop") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("quickshop.reload")) { sender.sendMessage("No permission."); return true; }
            reloadConfig();
            loadPrices();
            sender.sendMessage("QuickShop prices reloaded.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("shards")) {
            shardsCommand(sender, args);
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        gui.open(p);
        return true;
    }

    private static final String ADMIN = "quickshop.shards.admin";

    private void shardsCommand(CommandSender sender, String[] args) {
        // /shards  -> own balance
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Usage: /shards <player|give|take|set>"); return; }
            sender.sendMessage(shardsLine("Shards", data.shards(p)));
            return;
        }
        String sub = args[0].toLowerCase();

        // /shards give|take|set <player> <amount>
        if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
            if (!sender.hasPermission(ADMIN)) { sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED)); return; }
            if (args.length != 3) { sender.sendMessage(Component.text("Usage: /shards " + sub + " <player> <amount>", NamedTextColor.RED)); return; }
            OfflinePlayer target = findPlayer(args[1]);
            if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return; }
            int amount;
            try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { amount = -1; }
            if (amount < 0) { sender.sendMessage(Component.text("Amount must be a whole number, 0 or more.", NamedTextColor.RED)); return; }

            UUID id = target.getUniqueId();
            switch (sub) {
                case "give" -> data.addShards(id, amount);
                case "take" -> data.addShards(id, -amount);
                default -> data.setShards(id, amount);
            }
            String name = target.getName() == null ? args[1] : target.getName();
            sender.sendMessage(shardsLine(name + " now has", data.shards(id)));
            if (target.isOnline() && target.getPlayer() != sender)
                target.getPlayer().sendMessage(shardsLine("Shards", data.shards(id)));
            return;
        }

        // /shards pay <player> <amount>  (everyone)
        if (sub.equals("pay")) {
            if (!(sender instanceof Player payer)) { sender.sendMessage("Players only."); return; }
            if (args.length != 3) { sender.sendMessage(Component.text("Usage: /shards pay <player> <amount>", NamedTextColor.RED)); return; }
            OfflinePlayer target = findPlayer(args[1]);
            if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return; }
            if (target.getUniqueId().equals(payer.getUniqueId())) { sender.sendMessage(Component.text("You can't pay yourself.", NamedTextColor.RED)); return; }
            int amount;
            try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { amount = -1; }
            if (amount <= 0) { sender.sendMessage(Component.text("Amount must be a whole number above 0.", NamedTextColor.RED)); return; }
            if (data.shards(payer) < amount) {
                sender.sendMessage(Component.text("You don't have enough shards. You have ", NamedTextColor.RED)
                        .append(Component.text(Messages.fmt(data.shards(payer)), Messages.BLUE)));
                return;
            }
            data.addShards(payer, -amount);
            data.addShards(target.getUniqueId(), amount);
            String tname = target.getName() == null ? args[1] : target.getName();
            payer.sendMessage(Component.text("You paid ", NamedTextColor.GRAY)
                    .append(Component.text(Messages.fmt(amount) + " shards", Messages.BLUE))
                    .append(Component.text(" to " + tname + ".", NamedTextColor.GRAY)));
            if (target.isOnline())
                target.getPlayer().sendMessage(Component.text(payer.getName() + " paid you ", NamedTextColor.GRAY)
                        .append(Component.text(Messages.fmt(amount) + " shards", Messages.BLUE))
                        .append(Component.text(".", NamedTextColor.GRAY)));
            return;
        }

        // /shards <player> -> check someone else (everyone)
        OfflinePlayer target = findPlayer(args[0]);
        if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return; }
        String name = target.getName() == null ? args[0] : target.getName();
        sender.sendMessage(shardsLine(name + " has", data.shards(target.getUniqueId())));
    }

    private static Component shardsLine(String label, int amount) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(Messages.fmt(amount), Messages.BLUE));
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("shop")) {
            if (args.length == 1 && sender.hasPermission("quickshop.reload")) out.add("reload");
        } else if (cmd.getName().equalsIgnoreCase("shards")) {
            boolean admin = sender.hasPermission(ADMIN);
            if (args.length == 1) {
                out.add("pay");
                if (admin) out.addAll(List.of("give", "take", "set"));
                Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            } else if (args.length == 2) {
                String a = args[0].toLowerCase();
                if (a.equals("pay") || (admin && (a.equals("give") || a.equals("take") || a.equals("set"))))
                    Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        out.removeIf(o -> !o.toLowerCase().startsWith(last));
        return out;
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof ArmorStand) return;
        Player killer = dead.getKiller();
        if (killer == null || killer.equals(dead)) return;
        data.addShards(killer, getConfig().getInt("shards-per-kill", 10));
    }
}
