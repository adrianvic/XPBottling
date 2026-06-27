package me.honeyberries.xpbottling;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Objects;

/**
 * A lightweight Bukkit plugin that lets players bottle their experience.
 * <p>
 * Core behavior:
 * - When a player right-clicks an enchanting table with a glass bottle (main hand),
 *   consume a fixed amount of experience and give back one experience bottle.
 * - The action requires a specific permission and provides feedback via chat messages and sound.
 * <p>
 * Permissions:
 * - xpbottling.use — required to perform the bottling action.
 * <p>
 * Notes:
 * - The interaction event is canceled to prevent the enchanting table UI from opening.
 * - Experience is removed using the same cost value that is granted to the bottle.
 * - Feedback messages are sent using Adventure Components.
 */
public final class XPBottling extends JavaPlugin implements Listener {

    private static final String PERMISSION_USE = "xpbottling.use";
    private static final float SOUND_VOLUME = 1.0f;
    private static final float SOUND_PITCH = 1.25f;

    private final NamespacedKey namespacedKey = new NamespacedKey(this, "xp");

    /**
     * Plugin boot hook.
     * - Registers this class as an event listener.
     * - Emits a log line for server administrators.
     */
    @Override
    public void onEnable() {
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("XP Bottling has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("XP Bottling has been disabled!");
    }

    /**
     * Handles right-click interactions to perform the bottling logic.
     * <p>
     * Flow:
     * 1) Only proceed for main-hand right-click on a block.
     * 2) Require the clicked block to be an enchanting table.
     * 3) Require the player to be holding a glass bottle in the main hand.
     * 4) Cancel the event to prevent the enchanting interface from opening.
     * 5) Check permission and experience; if valid, process bottling.
     * <p>
     * This handler is intentionally strict to avoid false positives and
     * to keep the action explicit and predictable for players.
     *
     * @param event the interaction event fired by Bukkit
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.ENCHANTING_TABLE) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.GLASS_BOTTLE && player.getInventory().getItemInMainHand().getType() != Material.EXPERIENCE_BOTTLE) {
            return;
        }

        event.setCancelled(true);

        if (!player.hasPermission(PERMISSION_USE)) {
            return;
        }

        int playerXP = player.getTotalExperience();
        if (playerXP <= 0) {
            return;
        }

        int xpToTake = playerXP;
        if (player.isSneaking()) {
            xpToTake = Math.min(100, playerXP);
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        ItemMeta meta = item.getItemMeta();
        Integer xp = 0;
        if (meta != null) {
            Integer storedXP = meta.getPersistentDataContainer().get(namespacedKey, PersistentDataType.INTEGER);
            if (storedXP != null) {
                xp = storedXP;
            }
        }

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        processBottling(xpToTake, xp, player);
    }

    @EventHandler
    public void onExpBottle(ExpBottleEvent e) {
        ThrownExpBottle bottle = e.getEntity();

        ItemStack item = bottle.getItem();
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return;

        Integer xp = meta.getPersistentDataContainer().get(namespacedKey, PersistentDataType.INTEGER);

        if (xp != null) {
            e.setExperience(xp);
        }
    }

    /**
     * Completes the bottling operation for a valid interaction:
     * - Removes experience from the player.
     * - Adds a single experience bottle to the player's inventory.
     * - Plays a short confirmation sound at the player's location.
     * <p>
     * This method assumes all preconditions (permission, target block, held item,
     * and sufficient experience) have already been validated.
     *
     * @param player the player receiving the bottled experience
     */
    private void processBottling(int xpToTake, Integer initialXP, Player player) {
        ItemStack bottle = new ItemStack(Material.EXPERIENCE_BOTTLE, 1);
        ItemMeta meta = bottle.getItemMeta();

        int totalXP = xpToTake + initialXP;

        meta.lore(List.of(
                Component.text("%s XP".formatted(totalXP))
        ));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(namespacedKey, PersistentDataType.INTEGER, totalXP);
        bottle.setItemMeta(meta);

        player.giveExp(-xpToTake);

        player.getInventory().addItem(bottle);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL_DRAGONBREATH, SOUND_VOLUME, SOUND_PITCH);
    }
}
