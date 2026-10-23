package com.quickshop;

/** One saved Quick Buy slot. amount = how many you get per click. */
public record QuickEntry(Type type, String key, int level, int amount) {
    public enum Type { ITEM, SHARD, BOOK }
}
