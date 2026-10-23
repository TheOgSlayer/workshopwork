package com.quickshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class QuickBuyGui implements Listener {
    public static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final QuickShopPlugin plugin;

    public QuickBuyGui(QuickShopPlugin plugin) { this.plugin = plugin; }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Quick Buy"));
        h.inv = inv;
        for (Map.Entry<Integer, QuickEntry> en : plugin.data().all(p).entrySet()) {
            if (en.getKey() < 0 || en.getKey() >= 54) continue;
            ItemStack it = display(en.getValue());
            if (it != null) inv.setItem(en.getKey(), it);
        }
        p.openInventory(inv);
    }

    /** Item with the stack size = amount you get per click, price in lore. */
    private ItemStack display(QuickEntry e) {
        ShopRegistry reg = plugin.registry();
        if (!reg.exists(e)) return null;
        int amount = Math.max(1, Math.min(e.amount(), reg.maxAmount(e)));
        ItemStack it = reg.icon(e);
        it.setAmount(amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(ShopRegistry.cap(reg.name(e)), Messages.BLUE).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(reg.priceLine(e, amount));
        lore.add(Component.empty());
        lore.add(Component.text("Left click to buy", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Right click to remove", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) return;
        e.setCancelled(true);

        if (e.getClickedInventory() != top) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getSlot();
        ClickType ct = e.getClick();
        QuickEntry entry = plugin.data().get(p, slot);

        if (entry == null) {
            if (ct.isLeftClick()) Bukkit.getScheduler().runTask(plugin, () -> plugin.menus().openCategories(p, slot));
            return;
        }
        if (ct.isRightClick()) {
            plugin.data().remove(p, slot);
            top.setItem(slot, null);
            Messages.removed(p);
        } else if (ct.isLeftClick()) {
            buy(p, entry); // GUI is never closed/reopened by a purchase
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void buy(Player p, QuickEntry e) {
        ShopRegistry reg = plugin.registry();
        if (!reg.exists(e)) return;
        int amount = Math.max(1, Math.min(e.amount(), reg.maxAmount(e)));
        double total = reg.price(e) * amount;
        String name = reg.name(e);

        if (e.type() == QuickEntry.Type.SHARD) {
            int cost = (int) Math.ceil(total);
            int have = plugin.data().shards(p);
            if (have < cost) {
                Messages.notEnough(p, "shards", name, Messages.fmt(cost - have));
                return;
            }
            plugin.data().addShards(p, -cost);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "givespawner " + p.getName() + " " + e.key() + " " + amount);
            return;
        }

        ItemStack stack = reg.product(new QuickEntry(e.type(), e.key(), e.level(), amount));
        if (!canFit(p, stack)) { Messages.inventoryFull(p); return; }

        if (plugin.economy() == null) {
            p.sendActionBar(Component.text(Messages.sc("economy not available"), NamedTextColor.RED));
            return;
        }
        double bal = plugin.economy().getBalance(p);
        if (bal < total) {
            Messages.notEnough(p, "money", name, Messages.fmt(total - bal));
            return;
        }
        EconomyResponse r = plugin.economy().withdrawPlayer(p, total);
        if (!r.transactionSuccess()) return;
        p.getInventory().addItem(stack);
    }

    /** Fast space check (no inventory copies) so spam clicking stays cheap. */
    private static boolean canFit(Player p, ItemStack stack) {
        int remaining = stack.getAmount();
        int max = stack.getMaxStackSize();
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) remaining -= max;
            else if (it.isSimilar(stack)) remaining -= Math.max(0, max - it.getAmount());
            if (remaining <= 0) return true;
        }
        return false;
    }
}
