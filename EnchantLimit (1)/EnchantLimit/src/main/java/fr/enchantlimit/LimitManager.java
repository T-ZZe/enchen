package fr.enchantlimit;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Stocke, par materiau, le niveau maximum autorise de chaque enchantement. */
public class LimitManager {

    private final EnchantLimitPlugin plugin;
    private final File file;
    // Materiau -> (cle enchantement -> niveau max autorise). Seules les restrictions sont stockees.
    private final Map<Material, Map<String, Integer>> limits = new EnumMap<>(Material.class);

    public LimitManager(EnchantLimitPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "limits.yml");
    }

    public void load() {
        limits.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String matName : cfg.getKeys(false)) {
            Material mat = Material.matchMaterial(matName);
            ConfigurationSection sec = cfg.getConfigurationSection(matName);
            if (mat == null || sec == null) continue;
            Map<String, Integer> map = new HashMap<>();
            for (String ench : sec.getKeys(false)) {
                map.put(ench, sec.getInt(ench));
            }
            if (!map.isEmpty()) limits.put(mat, map);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (var e : limits.entrySet()) {
            for (var l : e.getValue().entrySet()) {
                cfg.set(e.getKey().name() + "." + l.getKey(), l.getValue());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder limits.yml : " + ex.getMessage());
        }
    }

    /** Limite explicite, ou null si aucune restriction. */
    public Integer getLimit(Material mat, Enchantment ench) {
        Map<String, Integer> m = limits.get(mat);
        return m == null ? null : m.get(ench.getKey().getKey());
    }

    /** Niveau max actuellement autorise (vanilla si pas de restriction). */
    public int getEffective(Material mat, Enchantment ench) {
        Integer l = getLimit(mat, ench);
        return l == null ? ench.getMaxLevel() : l;
    }

    public void setLimit(Material mat, Enchantment ench, int level) {
        if (level >= ench.getMaxLevel()) {
            Map<String, Integer> m = limits.get(mat);
            if (m != null) {
                m.remove(ench.getKey().getKey());
                if (m.isEmpty()) limits.remove(mat);
            }
        } else {
            limits.computeIfAbsent(mat, k -> new HashMap<>()).put(ench.getKey().getKey(), Math.max(0, level));
        }
        save();
    }

    public void reset(Material mat) {
        limits.remove(mat);
        save();
    }

    public int countRestrictions(Material mat) {
        Map<String, Integer> m = limits.get(mat);
        return m == null ? 0 : m.size();
    }

    /** Rabaisse les enchantements de l'item aux limites. Retourne true si l'item a ete modifie. */
    public boolean clamp(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        Map<String, Integer> m = limits.get(item.getType());
        if (m == null || m.isEmpty()) return false;
        boolean changed = false;
        for (var e : new HashMap<>(item.getEnchantments()).entrySet()) {
            Integer lim = m.get(e.getKey().getKey().getKey());
            if (lim != null && e.getValue() > lim) {
                item.removeEnchantment(e.getKey());
                if (lim > 0) item.addUnsafeEnchantment(e.getKey(), lim);
                changed = true;
            }
        }
        return changed;
    }
}
