package dev.bhored.bananastack;

import java.util.List;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Everything Banana Stack says to players, in the Banana Sandwich colours. */
final class Messages {
	static final int BANANA = 0xFFD84D;
	static final int CREAM = 0xFBEFB6;
	static final int PEEL = 0xF6AB1A;
	static final int TOAST = 0xD4962F;

	private Messages() {
	}

	static MutableComponent text(String text) {
		return Component.literal(text).withColor(CREAM);
	}

	static MutableComponent name(ServerPlayer player) {
		return Component.literal(player.getPlainTextName()).withColor(BANANA);
	}

	static MutableComponent key(String key) {
		return Component.keybind(key).withColor(BANANA);
	}

	private static MutableComponent dot() {
		return Component.literal("  ·  ").withColor(TOAST);
	}

	private static void actionBar(ServerPlayer player, Component message) {
		if (StackConfig.get().messages) {
			player.sendOverlayMessage(message);
		}
	}

	static void mounted(ServerPlayer rider, ServerPlayer top, ServerPlayer bottom, int size) {
		MutableComponent riding = size <= 2
			? text("Riding ").append(name(top))
			: text("Stacked on ").append(name(top)).append(dot()).append(text(size + " high"));
		actionBar(rider, riding.append(dot()).append(key("key.sneak")).append(text(" to hop off")));

		if (top == bottom) {
			actionBar(top, name(rider).append(text(" hopped on")).append(dot()).append(key("key.sneak")).append(text(" to shake them off")));
			return;
		}
		actionBar(top, name(rider).append(text(" is riding on you")));
		actionBar(bottom, name(rider).append(text(" joined your stack")).append(dot()).append(text(size + " high")));
	}

	/** Told once a session to anyone who gets ridden, so nobody has to put up with it without knowing how to stop it. */
	static void howToStop(ServerPlayer player) {
		if (!StackConfig.get().messages) {
			return;
		}
		MutableComponent off = Component.literal("/stack off").withStyle(style -> style
			.withColor(BANANA)
			.withUnderlined(true)
			.withClickEvent(new ClickEvent.RunCommand("/stack off"))
			.withHoverEvent(new HoverEvent.ShowText(text("Stop people from riding you"))));
		player.sendSystemMessage(Component.literal("Banana Stack ").withColor(PEEL)
			.append(text("» "))
			.append(key("key.sneak"))
			.append(text(" shakes everyone off your head. Rather not be ridden? "))
			.append(off));
	}

	static void refused(ServerPlayer rider, Mounting.Refusal refusal, ServerPlayer other, int maxStackSize) {
		actionBar(rider, switch (refusal) {
			case TURNED_OFF -> name(other).append(text(" has riding turned off"));
			case NOT_ALLOWED -> text("You're not allowed to ride other players");
			case NOT_BEHIND -> text("Get behind ").append(name(other)).append(text(" to hop on"));
			case BUSY -> name(other).append(text(" can't carry anyone right now"));
			case FULL -> text("This stack is full (" + maxStackSize + " max)");
			case NO_ROOM -> text("No room up there");
		});
	}

	static void dropped(List<ServerPlayer> riders, ServerPlayer cause, Stacks.Reason reason) {
		for (ServerPlayer rider : riders) {
			MutableComponent message = switch (reason) {
				case SHAKEN_OFF -> name(cause).append(text(" shook everyone off"));
				case BUMPED -> text("Bonk! No room up there");
				case TURNED_OFF -> name(cause).append(text(" turned riding off"));
				case TELEPORTED -> name(cause).append(text(" teleported away"));
				case LEFT_GAME -> name(cause).append(text(" left the game"));
				case DIED -> name(cause).append(text(" can't carry you any more"));
				case UNAVAILABLE -> text("The stack came apart");
			};
			actionBar(rider, message);
		}
		if (reason == Stacks.Reason.SHAKEN_OFF && !riders.isEmpty()) {
			actionBar(cause, text(riders.size() == 1 ? "Shook off 1 rider" : "Shook off " + riders.size() + " riders"));
		}
	}
}
