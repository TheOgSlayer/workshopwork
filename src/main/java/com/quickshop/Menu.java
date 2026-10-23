package com.quickshop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** A simple chest menu with a click handler per slot. */
public final class Menu implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, Consumer<ClickType>> handlers = new HashMap<>();

    public Menu(Component title, int rows) { this.inv = Bukkit.createInventory(this, rows * 9, title); }

    @Override public Inventory getInventory() { return inv; }

    public void set(int slot, ItemStack item, Consumer<ClickType> handler) {
        inv.setItem(slot, item);
        if (handler != null) handlers.put(slot, handler); else handlers.remove(slot);
    }

    public void click(int slot, ClickType type) {
        Consumer<ClickType> h = handlers.get(slot);
        if (h != null) h.accept(type);
    }

    public void open(Player p) { p.openInventory(inv); }
}
