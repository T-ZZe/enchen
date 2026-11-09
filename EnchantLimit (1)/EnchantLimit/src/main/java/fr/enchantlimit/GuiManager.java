package fr.enchantlimit;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GuiManager implements Listener {

    public enum Category { ITEMS, ARMOR }
    public enum View { MAIN, LIST, ENCHANTS }

    public static class Holder implements InventoryHolder {
        final View view;
        final Category category;
        final int page;
        final Material material;
        Inventory inventory;

        Holder(View view, Category category, int page, Material material) {
            this.view = view;
            this.category = category;
            this.page = page;
            this.material = material;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private static final int PAGE_SIZE = 45;

    private final LimitManager limits;
    private final List<Material> items = new ArrayList<>();
    private final List<Material> armors = new ArrayList<>();
    private final List<Enchantment> allEnchants = new ArrayList<>();

    public GuiManager(LimitManager limits) {
        this.limits = limits;
        for (Enchantment e : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
            allEnchants.add(e);
        }
        allEnchants.sort(Comparator.comparing(e -> e.getKey().getKey()));

        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem() || m.isAir() || m == Material.ENCHANTED_BOOK) continue;
            if (!isEnchantable(m)) continue;
            (isArmor(m) ? armors : items).add(m);
        }
    }

    private boolean isArmor(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    private boolean isEnchantable(Material m) {
        ItemStack s = new ItemStack(m);
        for (Enchantment e : allEnchants) {
            if (e.equals(Enchantment.BINDING_CURSE) || e.equals(Enchantment.VANISHING_CURSE)) continue;
            if (e.canEnchantItem(s)) return true;
        }
        return false;
    }

    private List<Enchantment> enchantsFor(Material m) {
        ItemStack s = new ItemStack(m);
        List<Enchantment> list = new ArrayList<>();
        for (Enchantment e : allEnchants) {
            if (e.canEnchantItem(s)) list.add(e);
        }
        return list;
    }

    private List<Material> listOf(Category c) {
        return c == Category.ITEMS ? items : armors;
    }

    // ------------------------------------------------------------------ helpers

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack icon(Material mat, String name, NamedTextColor color, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plain(name, color));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(plain(s, NamedTextColor.GRAY));
        meta.lore(l);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private Inventory create(Holder h, int size, Component title) {
        Inventory inv = Bukkit.createInventory(h, size, title);
        h.inventory = inv;
        return inv;
    }

    // ------------------------------------------------------------------ menus

    public void openMain(Player p) {
        Holder h = new Holder(View.MAIN, null, 0, null);
        Inventory inv = create(h, 27, Component.text("Enchantements"));
        inv.setItem(11, icon(Material.DIAMOND_PICKAXE, "Items", NamedTextColor.AQUA,
                "Outils, armes et autres objets enchantables"));
        inv.setItem(15, icon(Material.NETHERITE_CHESTPLATE, "Armures", NamedTextColor.LIGHT_PURPLE,
                "Casques, plastrons, jambieres et bottes"));
        p.openInventory(inv);
    }

    public void openList(Player p, Category cat, int page) {
        List<Material> list = listOf(cat);
        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));

        Holder h = new Holder(View.LIST, cat, page, null);
        Inventory inv = create(h, 54, Component.text(cat == Category.ITEMS ? "Items" : "Armures")
                .append(Component.text(" (" + (page + 1) + "/" + pages + ")")));

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < list.size(); i++) {
            Material m = list.get(start + i);
            ItemStack it = new ItemStack(m);
            ItemMeta meta = it.getItemMeta();
            int n = limits.countRestrictions(m);
            meta.lore(List.of(
                    plain("Clique pour modifier les enchantements", NamedTextColor.GRAY),
                    plain(n == 0 ? "Aucune restriction" : n + " restriction(s)",
                            n == 0 ? NamedTextColor.GREEN : NamedTextColor.RED)));
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
            inv.setItem(i, it);
        }
        if (page > 0) inv.setItem(45, icon(Material.ARROW, "Page precedente", NamedTextColor.YELLOW));
        if (page < pages - 1) inv.setItem(53, icon(Material.ARROW, "Page suivante", NamedTextColor.YELLOW));
        inv.setItem(49, icon(Material.BARRIER, "Retour", NamedTextColor.RED));
        p.openInventory(inv);
    }

    public void openEnchants(Player p, Category cat, int listPage, Material mat) {
        Holder h = new Holder(View.ENCHANTS, cat, listPage, mat);
        Inventory inv = create(h, 54, Component.translatable(mat.translationKey()));
        List<Enchantment> list = enchantsFor(mat);
        for (int i = 0; i < list.size() && i < PAGE_SIZE; i++) {
            inv.setItem(i, book(mat, list.get(i)));
        }
        inv.setItem(49, icon(Material.BARRIER, "Retour", NamedTextColor.RED));
        inv.setItem(53, icon(Material.TNT, "Tout reinitialiser", NamedTextColor.RED,
                "Remet les valeurs vanilla pour cet equipement"));
        p.openInventory(inv);
    }

    private ItemStack book(Material mat, Enchantment e) {
        int max = e.getMaxLevel();
        int cur = limits.getEffective(mat, e);
        ItemStack it = new ItemStack(cur == 0 ? Material.BARRIER : Material.ENCHANTED_BOOK);
        it.setAmount(Math.max(1, cur));
        ItemMeta meta = it.getItemMeta();

        Component name = cur == 0
                ? Component.translatable(e.translationKey(), NamedTextColor.RED)
                : e.displayName(cur);
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(plain("Maximum autorise : " + (cur == 0 ? "INTERDIT" : cur),
                cur == 0 ? NamedTextColor.RED : (cur < max ? NamedTextColor.GOLD : NamedTextColor.GREEN)));
        lore.add(plain("Maximum vanilla : " + max, NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(plain("Clic gauche : baisser (boucle)", NamedTextColor.GRAY));
        lore.add(plain("Clic droit : monter", NamedTextColor.GRAY));
        lore.add(plain("Shift + clic : vanilla", NamedTextColor.GRAY));
        meta.lore(lore);
        if (cur < max) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ events

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission("enchantlimit.admin")) {
            p.closeInventory();
            return;
        }
        int slot = e.getSlot();

        switch (h.view) {
            case MAIN -> {
                if (slot == 11) openList(p, Category.ITEMS, 0);
                else if (slot == 15) openList(p, Category.ARMOR, 0);
            }
            case LIST -> {
                if (slot == 49) openMain(p);
                else if (slot == 45 && h.page > 0) openList(p, h.category, h.page - 1);
                else if (slot == 53) openList(p, h.category, h.page + 1);
                else if (slot >= 0 && slot < PAGE_SIZE) {
                    List<Material> list = listOf(h.category);
                    int idx = h.page * PAGE_SIZE + slot;
                    if (idx < list.size()) openEnchants(p, h.category, h.page, list.get(idx));
                }
            }
            case ENCHANTS -> {
                if (slot == 49) {
                    openList(p, h.category, h.page);
                } else if (slot == 53) {
                    limits.reset(h.material);
                    openEnchants(p, h.category, h.page, h.material);
                } else if (slot >= 0 && slot < PAGE_SIZE) {
                    List<Enchantment> list = enchantsFor(h.material);
                    if (slot >= list.size()) return;
                    Enchantment ench = list.get(slot);
                    int max = ench.getMaxLevel();
                    int cur = limits.getEffective(h.material, ench);
                    ClickType click = e.getClick();
                    int next;
                    if (click.isShiftClick()) {
                        next = max;
                    } else if (click.isRightClick()) {
                        next = cur + 1 > max ? 0 : cur + 1;
                    } else {
                        // Clic gauche : 4 -> 3 -> 2 -> 1 -> 0 -> 4 ...
                        next = cur - 1 < 0 ? max : cur - 1;
                    }
                    limits.setLimit(h.material, ench, next);
                    top.setItem(slot, book(h.material, ench));
                }
            }
        }
    }
}
