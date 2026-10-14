package net.satisfy.hearth_and_timber.core.block;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.satisfy.hearth_and_timber.core.registry.ObjectRegistry;

public interface IWeatheringThatch {
    Supplier<Block> getNextBlock();

    default void tickWeathering(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(18000) == 0) {
            BlockState next = getNextState(state);
            if (next != null) level.setBlock(pos, next, 2);
        }
    }

    default BlockState getNextState(BlockState current) {
        Block next = getNextBlock() == null ? null : getNextBlock().get();
        return next == null ? null : copyMatching(current, next.defaultBlockState());
    }

    @SuppressWarnings("deprecation")
    default ItemInteractionResult handleInteraction(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        PotionContents potionData = stack.get(DataComponents.POTION_CONTENTS);
        Optional<Holder<Potion>> optionalPotion = potionData == null ? Optional.empty() : potionData.potion();
        boolean isWaterBottle = optionalPotion.isPresent() && optionalPotion.get().is(Potions.WATER);

        if (isWaterBottle) {
            Block next = getNextBlock() == null ? null : getNextBlock().get();
            if (next != null) {
                BlockState target = copyMatching(state, next.defaultBlockState());
                if (!level.isClientSide) {
                    level.setBlock(pos, target, 2);
                    stack.shrink(1);
                    player.addItem(new ItemStack(Items.GLASS_BOTTLE));
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(Items.WHEAT)) {
            Block base = getVariant(state.getBlock(), 0);
            if (base != null && base != state.getBlock()) {
                BlockState target = copyMatching(state, base.defaultBlockState());
                if (!level.isClientSide) {
                    level.setBlock(pos, target, 2);
                    stack.shrink(1);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(Items.HONEYCOMB)) {
            Block waxed = getWaxed(state.getBlock());
            if (waxed != null) {
                BlockState target = copyMatching(state, waxed.defaultBlockState());
                if (!level.isClientSide) {
                    level.setBlock(pos, target, 11);
                    level.levelEvent(player, 3003, pos, 0);
                    stack.consume(1, player);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.getItem() instanceof HoeItem) {
            Block previous = getPrevious(state.getBlock());
            if (previous != null) {
                BlockState target = copyMatching(state, previous.defaultBlockState());
                if (!level.isClientSide) level.setBlock(pos, target, 2);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static BlockState copyMatching(BlockState from, BlockState to) {
        for (Property<?> property : to.getProperties()) {
            if (from.hasProperty(property)) to = setUnchecked(to, property, from.getValue(property));
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState setUnchecked(BlockState state, Property<T> property, Object value) {
        return state.setValue(property, property.getValueClass().cast(value));
    }

    private static Block[][] variants() {
        return new Block[][]{
                {ObjectRegistry.THATCH.get(), ObjectRegistry.WEATHERED_THATCH.get(), ObjectRegistry.DRYING_THATCH.get(), ObjectRegistry.AGED_THATCH.get()},
                {ObjectRegistry.THATCH_STAIRS.get(), ObjectRegistry.WEATHERED_THATCH_STAIRS.get(), ObjectRegistry.DRYING_THATCH_STAIRS.get(), ObjectRegistry.AGED_THATCH_STAIRS.get()},
                {ObjectRegistry.THATCH_SLAB.get(), ObjectRegistry.WEATHERED_THATCH_SLAB.get(), ObjectRegistry.DRYING_THATCH_SLAB.get(), ObjectRegistry.AGED_THATCH_SLAB.get()}
        };
    }

    private static Block[][] waxedVariants() {
        return new Block[][]{
                {ObjectRegistry.WAXED_THATCH.get(), ObjectRegistry.WAXED_WEATHERED_THATCH.get(), ObjectRegistry.WAXED_DRYING_THATCH.get(), ObjectRegistry.WAXED_AGED_THATCH.get()},
                {ObjectRegistry.WAXED_THATCH_STAIRS.get(), ObjectRegistry.WAXED_WEATHERED_THATCH_STAIRS.get(), ObjectRegistry.WAXED_DRYING_THATCH_STAIRS.get(), ObjectRegistry.WAXED_AGED_THATCH_STAIRS.get()},
                {ObjectRegistry.WAXED_THATCH_SLAB.get(), ObjectRegistry.WAXED_WEATHERED_THATCH_SLAB.get(), ObjectRegistry.WAXED_DRYING_THATCH_SLAB.get(), ObjectRegistry.WAXED_AGED_THATCH_SLAB.get()}
        };
    }

    private static int[] indexOf(Block block) {
        Block[][] variants = variants();
        for (int shape = 0; shape < variants.length; shape++) {
            for (int age = 0; age < variants[shape].length; age++) {
                if (variants[shape][age] == block) return new int[]{shape, age};
            }
        }
        return null;
    }

    private static Block getVariant(Block block, int age) {
        int[] index = indexOf(block);
        return index == null ? null : variants()[index[0]][age];
    }

    private static Block getPrevious(Block block) {
        int[] index = indexOf(block);
        return index == null || index[1] == 0 ? null : variants()[index[0]][index[1] - 1];
    }

    private static Block getWaxed(Block block) {
        int[] index = indexOf(block);
        return index == null ? null : waxedVariants()[index[0]][index[1]];
    }

    default void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> list, TooltipFlag flag) {
        int beige = 0xF5DEB3;
        int gold = 0xFFD700;

        if (!Screen.hasShiftDown()) {
            Component key = Component.literal("[SHIFT]").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(gold)));
            list.add(Component.translatable("tooltip.hearth_and_timber.tooltip_information.hold", key)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(beige))));
            return;
        }

        list.add(Component.translatable("tooltip.hearth_and_timber.thatch.info_0")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(beige))));
        list.add(Component.empty());
        list.add(Component.translatable("tooltip.hearth_and_timber.thatch.info_1")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(beige))));
        list.add(Component.empty());
        list.add(Component.translatable("tooltip.hearth_and_timber.thatch.info_2")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(beige))));
    }
}