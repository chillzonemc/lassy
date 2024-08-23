package gg.mew.lassy.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import gg.mew.lassy.Lassy;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.Objects;

@RequiredArgsConstructor
@CommandAlias("lassy")
public final class LassyCommand extends BaseCommand {

    private final Lassy lassy;

    @Subcommand("reload")
    @CommandPermission(Lassy.PERMISSION_RELOAD)
    private void onReload(final CommandSender sender) {
        this.lassy.reload(true);

        sender.sendMessage(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(this.lassy.getMessages().getString("reload"))));
    }

}
