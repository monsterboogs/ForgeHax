package dev.fiki.forgehax.main.util;

import com.google.common.collect.Lists;
import dev.fiki.forgehax.main.util.entity.LocalPlayerInventory;
import dev.fiki.forgehax.main.util.entity.LocalPlayerUtils;
import dev.fiki.forgehax.main.util.math.VectorUtils;
import dev.fiki.forgehax.main.util.reflection.FastReflection;
import dev.fiki.forgehax.main.util.reflection.ReflectionHelper;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.Direction;
import net.minecraft.util.math.*;
import net.minecraft.util.math.shapes.VoxelShape;

import static dev.fiki.forgehax.main.Common.*;

public class BlockHelper {

  @Deprecated
  public static UniqueBlock newUniqueBlock(Block block, int metadata, BlockPos pos) {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  public static UniqueBlock newUniqueBlock(Block block, int metadata) {
    throw new UnsupportedOperationException();
  }

  public static UniqueBlock newUniqueBlock(BlockPos pos) {
    return new UniqueBlock(getWorld().getBlockState(pos).getBlock(), pos);
  }

  public static BlockTraceInfo newBlockTrace(BlockPos pos, Direction side) {
    return new BlockTraceInfo(pos, side);
  }

  public static List<BlockPos> getBlocksInRadius(Vec3d pos, double radius) {
    List<BlockPos> list = Lists.newArrayList();
    for (double x = pos.x - radius; x <= pos.x + radius; ++x) {
      for (double y = pos.y - radius; y <= pos.y + radius; ++y) {
        for (double z = pos.z - radius; z <= pos.z + radius; ++z) {
          list.add(new BlockPos((int) x, (int) y, (int) z));
        }
      }
    }
    return list;
  }

  @Deprecated
  public static float getBlockHardness(BlockPos pos) {
    BlockState state = getWorld().getBlockState(pos);
    return state.getBlockHardness(getWorld(), pos);
  }

  public static boolean isBlockReplaceable(BlockPos pos) {
    return getWorld().getBlockState(pos).getMaterial().isReplaceable();
  }

  public static boolean isTraceClear(Vec3d start, Vec3d end, BlockPos target) {
    RayTraceContext ctx = new RayTraceContext(start, end,
        RayTraceContext.BlockMode.OUTLINE,
        RayTraceContext.FluidMode.NONE, getLocalPlayer());
    BlockRayTraceResult tr = getWorld().rayTraceBlocks(ctx);
    return tr.getPos().equals(target);
  }

  public static boolean doesTraceHitBlockSide(Vec3d start, Vec3d end, BlockPos target, Direction direction) {
    Vec3d dir = end.subtract(start).normalize();
    // extend the end vector to reach out more so it actually touches the block
    RayTraceContext ctx = new RayTraceContext(start, end.add(dir.scale(2)),
        RayTraceContext.BlockMode.OUTLINE,
        RayTraceContext.FluidMode.NONE, getLocalPlayer());
    BlockRayTraceResult tr = getWorld().rayTraceBlocks(ctx);
    return tr.getPos().equals(target)
        && tr.getFace().equals(direction);
  }

  public static Vec3d getOBBCenter(BlockPos pos) {
    BlockState state = getWorld().getBlockState(pos);
    VoxelShape shape = state.getCollisionShape(getWorld(), pos);
    if (!shape.isEmpty()) {
      AxisAlignedBB bb = shape.getBoundingBox();
      return new Vec3d(
          bb.minX + ((bb.maxX - bb.minX) / 2.D),
          bb.minY + ((bb.maxY - bb.minY) / 2.D),
          bb.minZ + ((bb.maxZ - bb.minZ) / 2.D));
    } else {
      return Vec3d.ZERO;
    }
  }

  public static boolean isBlockPlaceable(BlockPos pos) {
    return !getWorld().getBlockState(pos).getShape(getWorld(), pos).isEmpty();
  }

  public static boolean isItemBlockPlaceable(Item item) {
    if (item instanceof BlockItem) {
      BlockItem blockItem = (BlockItem) item;
      return blockItem.getBlock().getDefaultState().isOpaqueCube(getWorld(), BlockPos.ZERO);
    }
    return false;
  }

  private static BlockTraceInfo getPlaceableBlockSideTrace(Vec3d eyes, Vec3d normal,
      Stream<Direction> stream, BlockPos pos) {
    return stream
        .map(side -> new BlockTraceInfo(pos.offset(side), side))
        .filter(info -> isBlockPlaceable(info.getPos()))
        .filter(info -> LocalPlayerUtils.isInReach(eyes, info.getHitVec()))
        .filter(info -> BlockHelper.doesTraceHitBlockSide(eyes, info.getHitVec(), info.getPos(), info.getOppositeSide()))
        .min(Comparator.<BlockTraceInfo>comparingInt(info -> info.isSneakRequired() ? 1 : 0)
            .thenComparing(info -> VectorUtils.getCrosshairDistance(eyes, normal, info.getCenterPos())))
        .orElse(null);
  }

  public static BlockTraceInfo getPlaceableBlockSideTrace(Vec3d eyes, Vec3d normal,
      EnumSet<Direction> sides, BlockPos pos) {
    return getPlaceableBlockSideTrace(eyes, normal, sides.stream(), pos);
  }

  public static BlockTraceInfo getPlaceableBlockSideTrace(Vec3d eyes, Vec3d normal, BlockPos pos) {
    return getPlaceableBlockSideTrace(eyes, normal, Stream.of(Direction.values()), pos);
  }

  public static BlockTraceInfo getBlockSideTrace(Vec3d eyes, BlockPos pos, Direction side) {
    return Optional.of(newBlockTrace(pos, side))
        .filter(tr -> BlockHelper.doesTraceHitBlockSide(eyes, tr.getHitVec(), tr.getPos(), side.getOpposite()))
        .filter(tr -> LocalPlayerUtils.isInReach(eyes, tr.getHitVec()))
        .orElse(null);
  }

  public static BlockTraceInfo getVisibleBlockSideTrace(Vec3d eyes, Vec3d normal, BlockPos pos) {
    return Arrays.stream(Direction.values())
        .map(side -> BlockHelper.getBlockSideTrace(eyes, pos, side))
        .filter(Objects::nonNull)
        .min(Comparator.comparingDouble(i -> VectorUtils.getCrosshairDistance(eyes, normal, i.getCenterPos())))
        .orElse(null);
  }

  @Getter
  public static class BlockTraceInfo {
    private final BlockPos pos;
    private final Direction side;
    private final Vec3d centerPos;
    private final Vec3d hitVec;

    private BlockTraceInfo(BlockPos pos, Direction side) {
      this.pos = pos;
      this.side = side;
      Vec3d obb = BlockHelper.getOBBCenter(pos);
      this.centerPos = new Vec3d(pos).add(obb);
      this.hitVec = this.centerPos.add(obb.mul(new Vec3d(getOppositeSide().getDirectionVec())));
    }

    public Direction getOppositeSide() {
      return side.getOpposite();
    }

    public BlockState getBlockState() {
      return getWorld().getBlockState(getPos());
    }

    public boolean isPlaceable(LocalPlayerInventory.InvItem inv) {
      return Optional.of(inv.getItem())
          .filter(BlockItem.class::isInstance)
          .map(BlockItem.class::cast)
          .filter(item -> item.getBlock().getDefaultState().isOpaqueCube(getWorld(), getPos()))
          .isPresent();
    }

    public boolean isSneakRequired() {
      return BlockActivationChecker.isOverwritten(getBlockState().getBlock());
    }
  }

  @Getter
  @AllArgsConstructor
  @EqualsAndHashCode
  @ToString
  public static class UniqueBlock {
    private final Block block;
    @EqualsAndHashCode.Exclude
    private final BlockPos pos;

    public Vec3d getCenteredPos() {
      return new Vec3d(getPos()).add(getOBBCenter(getPos()));
    }

    public ItemStack asItemStack() {
      throw new UnsupportedOperationException();
    }

    public boolean isInvalid() {
      return Blocks.AIR.equals(getBlock());
    }

    public boolean isEqual(BlockPos pos) {
      return getWorld().getBlockState(pos).getBlock().equals(block);
    }
  }

  public static class BlockActivationChecker {
    private static final Object2BooleanArrayMap<Class<?>> CACHE = new Object2BooleanArrayMap<>();

    public static boolean isOverwritten(final Block instance) {
      Objects.requireNonNull(instance);
      return CACHE.computeIfAbsent(
          instance.getClass(),
          clazz -> Block.class != ReflectionHelper.getMethodDeclaringClass(
              FastReflection.Methods.Block_onBlockActivated,
              instance)
      );
    }
  }
}
