package fr.enchantlimit;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.Map;

public class LimitListener implements Listener {

    private final LimitManager limits;

    public LimitListener(LimitManager limits) {
        this.limits = limits;
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        Iterator<Map.Entry<Enchantment, Integer>> it = e.getEnchantsToAdd().entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Integer lim = limits.getLimit(e.getItem().getType(), entry.getKey());
            if (lim != null && entry.getValue() > lim) {
                if (lim <= 0) it.remove();
                else entry.setValue(lim);
            }
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack result = e.getResult();
        if (result != null && limits.clamp(result)) {
            e.setResult(result);
        }
    }

    /** Amelioration diamant -> netherite : on redescend aux limites de la version netherite. */
    @EventHandler
    public void onSmithing(PrepareSmithingEvent e) {
        ItemStack result = e.getResult();
        if (result != null && limits.clamp(result)) {
            e.setResult(result);
        }
    }
}
