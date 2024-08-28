package gg.mew.lassy;

import co.aikar.commands.PaperCommandManager;
import gg.mew.lassy.command.LassyCommand;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.permission.Permission;
import org.bukkit.plugin.java.annotation.permission.Permissions;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

import static gg.mew.lassy.Lassy.PERMISSION_RELOAD;
import static gg.mew.lassy.Lassy.PERMISSION_USE;

@Getter
@Plugin(name = "Lassy", version = "1.2")
@ApiVersion(ApiVersion.Target.v1_20)
@Permissions(value = {
        @Permission(name = PERMISSION_RELOAD, desc = "Allows the holder to reload the Lassy config"),
        @Permission(name = PERMISSION_USE, desc = "Allows the holder to leash additional allowed entities")
})
public final class Lassy extends JavaPlugin implements Listener {

    public static final String PERMISSION_RELOAD = "lassy.reload";
    public static final String PERMISSION_USE = "lassy.use";

    private YamlConfiguration messages;

    private final Map<UUID, UUID> leashed = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.reload(false);

        this.messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        final var commandManager = new PaperCommandManager(this);

        commandManager.registerCommand(new LassyCommand(this));

        getServer().getPluginManager().registerEvents(this, this);

        final var taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            try {
                for (final var it = this.leashed.entrySet().iterator(); it.hasNext(); ) {
                    final var entry = it.next();
                    final var entity = Bukkit.getEntity(entry.getKey());

                    if (entity == null || (entity instanceof LivingEntity e && (!e.isLeashed() || e.getLeashHolder().getType() != EntityType.PLAYER)))
                        it.remove();
                }
            } catch (final Exception e) {
                getLogger().log(Level.SEVERE, "Error while cleaning up cache, this can lead to memory issues if not addressed", e);
            }
        }, 0, getConfig().getInt("cleanup-cache-every"));

        if (taskId == -1) {
            getLogger().warning("Cache cleanup task couldn't be started, disabling plugin.");
            Bukkit.getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this);
    }

    public void reload(final boolean reloadConfig) {
        if (reloadConfig)
            reloadConfig();

        this.messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean canLeash(final Player interactedBy, final LivingEntity interactedOn) {
        if (!interactedBy.hasPermission(PERMISSION_USE))
            return false;

        final var itemInMainHand = interactedBy.getInventory().getItemInMainHand();
        final var itemInOffHand = interactedBy.getInventory().getItemInOffHand();

        if (interactedOn.isLeashed())
            return false;

        if (itemInMainHand.getType() != Material.LEAD && itemInOffHand.getType() != Material.LEAD)
            return false;

        return true;
    }

    private boolean isAllowed(final LivingEntity interactedOn) {
        final var allowList = getConfig().getStringList("allow");

        return allowList.contains(interactedOn.getType().getKey().asString());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        final var interactedById = event.getPlayer().getUniqueId();
        final var interactedOnId = event.getRightClicked().getUniqueId();

        if (event.getRightClicked() instanceof LivingEntity e) {
            if (!canLeash(event.getPlayer(), e))
                return;

            if (!isAllowed(e)) {
                return;
            }

            //NOTE: Stop trading when attempting to leash
            if (e instanceof Villager)
                event.setCancelled(true);

            Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                final var interactedBy = Bukkit.getPlayer(interactedById);
                final var interactedOn = (LivingEntity) Bukkit.getEntity(interactedOnId);

                if (interactedBy == null || interactedOn == null)
                    return;

                if (!canLeash(interactedBy, interactedOn))
                    return;

                interactedBy.getInventory().removeItemAnySlot(new ItemStack(Material.LEAD, 1));

                interactedOn.setLeashHolder(interactedBy);

                if (getConfig().getBoolean("notify.allowed")) {
                    event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(this.messages.getString("allowed")),
                            Placeholder.unparsed("entity", interactedOn.getType().getKey().asString())));
                }

                leashed.put(interactedOnId, interactedById);
            }, 1);
        } else if (event.getRightClicked() instanceof LeashHitch e) {
            event.setCancelled(true);

            Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                final var interactedBy = Bukkit.getPlayer(interactedById);
                final var interactedOn = Bukkit.getEntity(interactedOnId);

                if (interactedBy == null || interactedOn == null)
                    return;

                final var nearbyEntities = e.getNearbyEntities(10, 10, 10);

                for (final var entity : nearbyEntities) {
                    if (!(entity instanceof LivingEntity living))
                        continue;

                    if (!living.isLeashed() || !living.getLeashHolder().equals(interactedOn)) {
                        continue;
                    }

                    living.setLeashHolder(interactedBy);

                    leashed.put(living.getUniqueId(), interactedById);
                }

                interactedOn.remove();
            }, 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPlayerLeashEntity(final PlayerLeashEntityEvent event) {
        if (!event.getPlayer().equals(event.getLeashHolder()))
            return;

        leashed.put(event.getPlayer().getUniqueId(), event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPlayerTeleport(final PlayerTeleportEvent event) {
        if (!getConfig().getBoolean("teleport-with-entities"))
            return;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE || event.getCause() == PlayerTeleportEvent.TeleportCause.EXIT_BED)
            return;

        final var nearbyEntities = event.getPlayer().getLocation().getNearbyLivingEntities(10);

        for (final var entity : nearbyEntities) {
            if (!entity.isLeashed() || !entity.getLeashHolder().equals(event.getPlayer()))
                continue;

            entity.setLeashHolder(null);
            entity.teleport(event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onEntityUnleash(final EntityUnleashEvent event) {
        if (!getConfig().getBoolean("teleport-with-entities"))
            return;

        if (!(event.getEntity() instanceof LivingEntity e))
            return;

        //NOTE: Teleport results in UNKNOWN reason.
        if (event.getReason() != EntityUnleashEvent.UnleashReason.UNKNOWN) {
            leashed.remove(e.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onEntityDeath(final EntityDeathEvent event) {
        this.leashed.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onEntityTeleport(final EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof LivingEntity e))
            return;

        final var entityId = e.getUniqueId();

        final var leashHolderId = leashed.get(entityId);

        if (leashHolderId == null)
            return;

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            final var leashHolder = Bukkit.getPlayer(leashHolderId);

            if (leashHolder == null) {
                leashed.remove(entityId);
                return;
            }

            e.setLeashHolder(leashHolder);
        }, 1);
    }

}
