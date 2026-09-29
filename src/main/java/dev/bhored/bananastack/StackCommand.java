package dev.bhored.bananastack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import org.jspecify.annotations.Nullable;

/** /stack (also /bananastack): how it works, turning riding on and off for yourself, and reloading the config. */
final class StackCommand {
	private StackCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("bananastack")
			.requires(Permissions.require("bananastack.command", true))
			.executes(StackCommand::help)
			.then(Commands.literal("toggle").executes(context -> setRiding(context, null)))
			.then(Commands.literal("on").executes(context -> setRiding(context, true)))
			.then(Commands.literal("off").executes(context -> setRiding(context, false)))
			.then(Commands.literal("reload")
				.requires(Permissions.require("bananastack.command.reload", PermissionLevel.GAMEMASTERS))
				.executes(StackCommand::reload)));

		// The short name is nicer to type, but another mod may already own it.
		if (dispatcher.getRoot().getChild("stack") == null) {
			dispatcher.register(Commands.literal("stack")
				.requires(Permissions.require("bananastack.command", true))
				.executes(StackCommand::help)
				.redirect(root));
		}
	}

	private static int help(CommandContext<CommandSourceStack> context) {
		StackConfig config = StackConfig.get();
		String hand = config.requireEmptyHand ? " with an empty hand" : "";
		String how = config.behindAngle < 180.0F ? "Walk up behind a player and right-click them" : "Right-click a player";
		MutableComponent message = Component.literal("Banana Stack").withColor(Messages.BANANA)
			.append(line(how + hand + " to hop on."))
			.append(line("Right-click the bottom of a stack to climb to the top."))
			.append(Component.literal("\n").append(Messages.key("key.sneak"))
				.append(Messages.text(" hops you off. At the bottom, it shakes everyone off.")));

		ServerPlayer player = context.getSource().getPlayer();
		if (player != null) {
			boolean allowed = RidingPreference.allows(player);
			String other = allowed ? "off" : "on";
			message.append(line("Others riding you: "))
				.append(Component.literal(allowed ? "ON" : "OFF").withColor(allowed ? Messages.BANANA : Messages.PEEL))
				.append(Messages.text("  "))
				.append(Component.literal("[turn " + other + "]").withStyle(style -> style
					.withColor(Messages.TOAST)
					.withClickEvent(new ClickEvent.RunCommand("/bananastack " + other))
					.withHoverEvent(new HoverEvent.ShowText(Messages.text("/stack " + other)))));
		}
		context.getSource().sendSuccess(() -> message, false);
		return 1;
	}

	private static MutableComponent line(String text) {
		return Messages.text("\n" + text);
	}

	private static int setRiding(CommandContext<CommandSourceStack> context, @Nullable Boolean wanted) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		boolean allowed = wanted != null ? wanted : !RidingPreference.allows(player);
		RidingPreference.set(player, allowed);
		if (allowed) {
			context.getSource().sendSuccess(() -> Messages.text("Riding on you is ")
				.append(Component.literal("on").withColor(Messages.BANANA))
				.append(Messages.text(". Players can hop on you again.")), false);
		} else {
			if (Stacks.isCarrying(player)) {
				Stacks.dropEveryoneAbove(player, Stacks.Reason.TURNED_OFF);
			}
			context.getSource().sendSuccess(() -> Messages.text("Riding on you is ")
				.append(Component.literal("off").withColor(Messages.PEEL))
				.append(Messages.text(". Nobody can hop on you until you run "))
				.append(Component.literal("/stack on").withColor(Messages.BANANA))
				.append(Messages.text(".")), false);
		}
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		if (StackConfig.load()) {
			context.getSource().sendSuccess(() -> Messages.text("Reloaded config/bananastack.json"), true);
			return 1;
		}
		context.getSource().sendFailure(Component.literal("Couldn't read config/bananastack.json, so the defaults are in use. The server log has the details."));
		return 0;
	}
}
