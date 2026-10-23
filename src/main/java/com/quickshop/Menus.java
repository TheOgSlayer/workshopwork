package com.quickshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Chest-GUI screens used to add things to Quick Buy. */
public final class Menus {
    private final QuickShopPlugin plugin;

    public Menus(QuickShopPlugin plugin) { this.plugin = plugin; }

    // ---------- helpers ----------

    private void go(Runnable r) { Bukkit.getScheduler().runTask(plugin, r); }

    private static Component line(String s, TextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack make(ItemStack base, String name, TextColor color, List<Component> lore) {
        ItemMeta meta = base.getItemMeta();
        meta.displayName(line(name, color));
        meta.lore(lore);
        base.setItemMeta(meta);
        return base;
    }

    private static void fill(Menu m) {
        ItemStack f = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = f.getItemMeta();
        meta.setHideTooltip(true);
        f.setItemMeta(meta);
        Inventory inv = m.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, f.clone());
    }

    private Menu grid(String title, int count) {
        int rows = Math.max(3, Math.min(6, (int) Math.ceil(count / 7.0) + 2));
        return new Menu(Component.text(title), rows);
    }

    private static int slotFor(int i) { return (i / 7 + 1) * 9 + 1 + (i % 7); }

    private void back(Menu m, Runnable target) {
        int slot = m.getInventory().getSize() - 5;
        m.set(slot, make(new ItemStack(Material.BARRIER), "Back", NamedTextColor.RED, List.of()), t -> go(target));
    }

    private void pick(Player p, int slot, QuickEntry tpl, Runnable back) {
        if (plugin.registry().maxAmount(tpl) <= 1) save(p, slot, tpl, 1); // unstackable: no amount screen
        else openAmount(p, slot, tpl, back);
    }

    private void save(Player p, int slot, QuickEntry tpl, int amount) {
        plugin.data().set(p, slot, new QuickEntry(tpl.type(), tpl.key(), tpl.level(), amount));
        Messages.added(p, plugin.registry().name(tpl));
        go(() -> plugin.gui().open(p));
    }

    // ---------- screens ----------

    public void openCategories(Player p, int slot) {
        ShopRegistry reg = plugin.registry();
        Menu m = grid("Add to Quick Buy", reg.categories().size() + 2);
        int i = 0;
        for (ShopRegistry.Category c : reg.categories().values()) {
            m.set(slotFor(i++), make(new ItemStack(c.icon()), c.title(), Messages.BLUE,
                    List.of(line("Click to open", NamedTextColor.GRAY))), t -> go(() -> openItems(p, slot, c)));
        }
        m.set(slotFor(i++), make(new ItemStack(Material.SPAWNER), "Shards Shop", Messages.BLUE,
                List.of(line("Click to open", NamedTextColor.GRAY))), t -> go(() -> openShards(p, slot)));
        m.set(slotFor(i), make(new ItemStack(Material.ENCHANTED_BOOK), "Books Shop", Messages.BLUE,
                List.of(line("Click to open", NamedTextColor.GRAY))), t -> go(() -> openBooks(p, slot)));
        back(m, () -> plugin.gui().open(p));
        fill(m);
        m.open(p);
    }

    private void openItems(Player p, int slot, ShopRegistry.Category c) {
        ShopRegistry reg = plugin.registry();
        Menu m = grid(c.title(), c.items().size());
        int i = 0;
        for (Material mat : c.items().keySet()) {
            QuickEntry tpl = new QuickEntry(QuickEntry.Type.ITEM, mat.name(), 0, 1);
            List<Component> lore = List.of(reg.priceLine(tpl, 1), Component.empty(),
                    line("Click to add to Quick Buy", NamedTextColor.YELLOW));
            m.set(slotFor(i++), make(new ItemStack(mat), ShopRegistry.cap(ShopRegistry.pretty(mat.name())), Messages.BLUE, lore),
                    t -> go(() -> pick(p, slot, tpl, () -> openItems(p, slot, c))));
        }
        back(m, () -> openCategories(p, slot));
        fill(m);
        m.open(p);
    }

    private void openShards(Player p, int slot) {
        ShopRegistry reg = plugin.registry();
        Menu m = grid("Shards Shop", reg.shards().size());
        int i = 0;
        for (String mob : reg.shards().keySet()) {
            QuickEntry tpl = new QuickEntry(QuickEntry.Type.SHARD, mob, 0, 1);
            List<Component> lore = List.of(reg.priceLine(tpl, 1), Component.empty(),
                    line("Click to add to Quick Buy", NamedTextColor.YELLOW));
            m.set(slotFor(i++), make(new ItemStack(Material.SPAWNER), ShopRegistry.cap(mob) + " Spawner", Messages.BLUE, lore),
                    t -> go(() -> pick(p, slot, tpl, () -> openShards(p, slot))));
        }
        back(m, () -> openCategories(p, slot));
        fill(m);
        m.open(p);
    }

    private void openBooks(Player p, int slot) {
        ShopRegistry reg = plugin.registry();
        Menu m = grid("Books Shop", reg.books().size());
        int i = 0;
        for (String ench : reg.books().keySet()) {
            List<Component> lore = List.of(line("Click to choose a level", NamedTextColor.YELLOW));
            m.set(slotFor(i++), make(new ItemStack(Material.ENCHANTED_BOOK), ShopRegistry.cap(ShopRegistry.pretty(ench)),
                    Messages.BLUE, lore), t -> go(() -> openBookLevels(p, slot, ench)));
        }
        back(m, () -> openCategories(p, slot));
        fill(m);
        m.open(p);
    }

    private void openBookLevels(Player p, int slot, String ench) {
        ShopRegistry reg = plugin.registry();
        TreeMap<Integer, Double> levels = reg.books().get(ench);
        Menu m = grid(ShopRegistry.cap(ShopRegistry.pretty(ench)) + " Books", levels.size());
        int i = 0;
        for (Map.Entry<Integer, Double> en : levels.entrySet()) {
            QuickEntry tpl = new QuickEntry(QuickEntry.Type.BOOK, ench, en.getKey(), 1);
            List<Component> lore = List.of(reg.priceLine(tpl, 1), Component.empty(),
                    line("Click to add to Quick Buy", NamedTextColor.YELLOW));
            m.set(slotFor(i++), make(reg.icon(tpl), ShopRegistry.cap(ShopRegistry.pretty(ench)) + " " + ShopRegistry.roman(en.getKey()),
                    Messages.BLUE, lore), t -> go(() -> pick(p, slot, tpl, () -> openBookLevels(p, slot, ench))));
        }
        back(m, () -> openBooks(p, slot));
        fill(m);
        m.open(p);
    }

    /** Amount picker: red panes (-) on the left, green panes (+) on the right. */
    private void openAmount(Player p, int slot, QuickEntry tpl, Runnable backTarget) {
        ShopRegistry reg = plugin.registry();
        int max = reg.maxAmount(tpl);
        int[] amt = {1};
        Menu m = new Menu(Component.text("Select Amount"), 3);
        Inventory inv = m.getInventory();
        String name = ShopRegistry.cap(reg.name(tpl));

        Runnable refresh = () -> {
            ItemStack preview = reg.icon(tpl);
            preview.setAmount(amt[0]);
            inv.setItem(13, make(preview, name, Messages.BLUE, List.of(
                    line("Amount: " + amt[0] + " / " + max, NamedTextColor.GRAY),
                    reg.priceLine(tpl, amt[0]))));
        };

        int[] deltas = {-64, -16, -1, 1, 16, 64};
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int k = 0; k < deltas.length; k++) {
            int d = deltas[k];
            boolean plus = d > 0;
            ItemStack pane = new ItemStack(plus ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                    Math.abs(d));
            make(pane, (plus ? "+" : "-") + Math.abs(d), plus ? NamedTextColor.GREEN : NamedTextColor.RED, List.of());
            m.set(slots[k], pane, t -> {
                amt[0] = Math.max(1, Math.min(max, amt[0] + d));
                refresh.run(); // updates in place, nothing reopens
            });
        }
        refresh.run();

        m.set(22, make(new ItemStack(Material.LIME_CONCRETE), "Confirm", NamedTextColor.GREEN,
                List.of(line("Add to Quick Buy", NamedTextColor.GRAY))), t -> save(p, slot, tpl, amt[0]));
        m.set(18, make(new ItemStack(Material.BARRIER), "Back", NamedTextColor.RED, List.of()), t -> go(backTarget));
        fill(m);
        m.open(p);
    }
}
