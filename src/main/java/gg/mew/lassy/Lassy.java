package gg.mew.lassy;

import co.aikar.commands.PaperCommandManager;
import gg.mew.lassy.command.LassyCommand;
import lombok.Getter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.permission.Permission;
import org.bukkit.plugin.java.annotation.permission.Permissions;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Plugin;

import java.io.File;
import java.util.Objects;

import static gg.mew.lassy.Lassy.PERMISSION_RELOAD;
import static gg.mew.lassy.Lassy.PERMISSION_USE;

@Getter
@Plugin(name = "Lassy", version = "1.0-SNAPSHOT")
@ApiVersion(ApiVersion.Target.v1_20)
@Permissions(value = {
        @Permission(name = PERMISSION_RELOAD, desc = "Allows the holder to reload the Lassy config"),
        @Permission(name = PERMISSION_USE, desc = "Allows the holder to leash additional allowed entities")
})
public final class Lassy extends JavaPlugin implements Listener {

    public static final String PERMISSION_RELOAD = "lassy.reload";
    public static final String PERMISSION_USE = "lassy.use";

    private YamlConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.reload(false);

        this.messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        final var commandManager = new PaperCommandManager(this);

        commandManager.registerCommand(new LassyCommand(this));

        getServer().getPluginManager().registerEvents(this, this);
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
        if (!(event.getRightClicked() instanceof LivingEntity e))
            return;

        if (!canLeash(event.getPlayer(), e))
            return;

        if (!isAllowed(e)) {
            return;
        }

        //NOTE: Stop trading when attempting to leash
        if (e instanceof Villager)
            event.setCancelled(true);

        final var interactedByID = event.getPlayer().getUniqueId();
        final var interactedOnID = e.getUniqueId();

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            final var interactedBy = Bukkit.getPlayer(interactedByID);
            final var interactedOn = (LivingEntity) Bukkit.getEntity(interactedOnID);

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
        }, 1);
    }

}
