package com.FrogPlay.sablemobbreaker.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockHealthClientData {

    public record Key(UUID subLevelId, int x, int y, int z) {}

    public record Entry(int hp, int maxHp) {}

    private static final Map<Key, Entry> DATA = new ConcurrentHashMap<>();

    private BlockHealthClientData() {}

    public static void put(UUID subLevelId, int x, int y, int z, int hp, int maxHp) {
        DATA.put(new Key(subLevelId, x, y, z), new Entry(hp, maxHp));
    }

    public static Entry get(UUID subLevelId, int x, int y, int z) {
        return DATA.get(new Key(subLevelId, x, y, z));
    }

    public static Map<Key, Entry> getAll() {
        return DATA;
    }

    /** Удаляет запись (используется для очистки устаревших суб-уровней). */
    public static void remove(Key key) {
        DATA.remove(key);
    }

    public static void clear() {
        DATA.clear();
    }
}