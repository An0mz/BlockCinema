package dev.anomz.blockcinema.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.BlockCinemaRegistries;
import dev.anomz.blockcinema.block.TvBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Opens a TV's menu from a distance. Sneak + right-click a TV to link it (saved on the item, so it works
 * on the server); right-click anywhere in range to open its menu, handled on the client. Right-clicking
 * any TV with it opens that TV's menu.
 */
public class RemoteItem extends Item {
    public RemoteItem(Properties properties) {
        super(properties);
    }

    /** The TV this remote is linked to (any TV of its screen), or null. */
    @Nullable
    public static GlobalPos link(ItemStack remote) {
        return remote.get(BlockCinemaRegistries.REMOTE_LINK);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.isSecondaryUseActive() || !(level.getBlockEntity(pos) instanceof TvBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            context.getItemInHand().set(BlockCinemaRegistries.REMOTE_LINK, GlobalPos.of(level.dimension(), pos));
            player.displayClientMessage(Component.translatable("message." + BlockCinema.MODID + ".remote_linked",
                    pos.getX(), pos.getY(), pos.getZ()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Consume rather than success: same handling, but without the arm swing. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos link = link(stack);
        if (link == null) {
            tooltip.add(Component.translatable("item." + BlockCinema.MODID + ".remote.unlinked").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos pos = link.pos();
            tooltip.add(Component.translatable("item." + BlockCinema.MODID + ".remote.linked", pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
