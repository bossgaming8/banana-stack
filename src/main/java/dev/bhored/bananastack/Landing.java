package dev.bhored.bananastack;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Where riders go when they get off, and how much room there is above a head. */
final class Landing {
	/** Directions around the stack, relative to the way its bottom faces: behind first, then the sides, then in front. */
	private static final float[] AROUND = {180.0F, 90.0F, -90.0F, 135.0F, -135.0F, 45.0F, -45.0F, 0.0F};
	/** Gap left between the stack and a rider landing next to it. */
	private static final double SPACING = 0.1;

	private Landing() {
	}

	/**
	 * A safe spot on the ground next to {@code anchor} (the bottom of the stack), so getting off the top of a tall
	 * stack never means a long fall. {@code slot} rotates the preferred side, so several riders dropped at once land
	 * around the stack instead of on top of each other.
	 */
	static Vec3 find(LivingEntity rider, Entity anchor, int slot) {
		Level level = anchor.level();
		for (int i = 0; i < AROUND.length; i++) {
			Vec3 side = besideAnchor(rider, anchor, AROUND[Math.floorMod(i + slot, AROUND.length)]);
			Vec3 spot = onFloor(level, rider, anchor, side.x, side.z);
			if (spot != null) {
				return spot;
			}
		}
		// No floor nearby (the stack is mid-jump or over a drop): land beside it at its height and fall like it would.
		for (int i = 0; i < AROUND.length; i++) {
			Vec3 side = besideAnchor(rider, anchor, AROUND[Math.floorMod(i + slot, AROUND.length)]);
			Vec3 spot = new Vec3(side.x, anchor.getBoundingBox().minY, side.z);
			if (DismountHelper.canDismountTo(level, spot, rider, Pose.STANDING)) {
				return spot;
			}
		}
		Vec3 inside = anchor.position();
		return DismountHelper.canDismountTo(level, inside, rider, Pose.STANDING) ? inside : rider.position();
	}

	private static Vec3 besideAnchor(LivingEntity rider, Entity anchor, float relativeAngle) {
		float angle = (anchor.getYRot() + relativeAngle) * Mth.DEG_TO_RAD;
		double x = -Mth.sin(angle);
		double z = Mth.cos(angle);
		// Scale to the edge of a square so diagonal spots clear the anchor's corners too.
		double scale = Math.max(Math.abs(x), Math.abs(z));
		double distance = (anchor.getBbWidth() + rider.getBbWidth()) / 2.0 + SPACING;
		return new Vec3(anchor.getX() + x * distance / scale, anchor.getY(), anchor.getZ() + z * distance / scale);
	}

	/** Checks the anchor's own level, one block up and one block down for somewhere to stand. */
	private static @Nullable Vec3 onFloor(Level level, LivingEntity rider, Entity anchor, double x, double z) {
		double feet = anchor.getBoundingBox().minY;
		int column = Mth.floor(feet);
		for (int dy : new int[] {0, 1, -1}) {
			BlockPos pos = BlockPos.containing(x, column + dy, z);
			double floor = level.getBlockFloorHeight(pos);
			if (!DismountHelper.isBlockFloorValid(floor)) {
				continue;
			}
			double y = pos.getY() + floor;
			if (y > feet + 1.01 || y < feet - 1.51 || isDangerous(level, rider, pos)) {
				continue;
			}
			Vec3 spot = new Vec3(x, y, z);
			for (Pose pose : rider.getDismountPoses()) {
				if (DismountHelper.canDismountTo(level, spot, rider, pose)) {
					rider.setPose(pose);
					return spot;
				}
			}
		}
		return null;
	}

	private static boolean isDangerous(Level level, LivingEntity rider, BlockPos feet) {
		BlockState at = level.getBlockState(feet);
		return rider.getType().isBlockDangerous(at)
			|| rider.getType().isBlockDangerous(level.getBlockState(feet.below()))
			|| at.is(BlockTags.PORTALS)
			|| level.getBlockState(feet.above()).is(BlockTags.PORTALS);
	}

	/**
	 * How much of {@code wanted} lift fits above a rider whose feet would be at {@code feetY} without any lift, or -1
	 * when not even sitting right on the head below fits.
	 */
	static double headroom(LivingEntity rider, double x, double feetY, double z, double wanted) {
		double half = rider.getBbWidth() / 2.0;
		AABB body = new AABB(x - half, feetY, z - half, x + half, feetY + rider.getBbHeight(), z + half).deflate(1.0E-4);
		AABB reach = body.expandTowards(0.0, wanted, 0.0);
		double ceiling = Double.POSITIVE_INFINITY;
		for (VoxelShape shape : rider.level().getBlockCollisions(rider, reach)) {
			for (AABB part : shape.toAabbs()) {
				if (!part.intersects(reach)) {
					continue;
				}
				if (part.intersects(body)) {
					return -1.0;
				}
				ceiling = Math.min(ceiling, part.minY);
			}
		}
		return Math.max(0.0, Math.min(wanted, ceiling - body.maxY));
	}
}
