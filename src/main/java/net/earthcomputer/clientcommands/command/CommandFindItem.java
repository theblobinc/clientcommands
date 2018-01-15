package net.earthcomputer.clientcommands.command;

import java.util.List;

import net.earthcomputer.clientcommands.ClientCommandsMod;
import net.earthcomputer.clientcommands.DelegatingContainer;
import net.earthcomputer.clientcommands.GuiBlocker;
import net.earthcomputer.clientcommands.LongTask;
import net.earthcomputer.clientcommands.Ptr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

public class CommandFindItem extends ClientCommandBase {

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		ClientCommandsMod.INSTANCE.ensureNoTasks();

		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}

		Item item = getItemByText(sender, args[0]);
		int damage = args.length < 2 || "*".equals(args[1]) ? -1 : parseInt(args[1], -1, Short.MAX_VALUE);
		NBTTagCompound nbt = null;
		if (args.length >= 3) {
			try {
				nbt = JsonToNBT.getTagFromJson(getChatComponentFromNthArg(sender, args, 2).getUnformattedText());
			} catch (NBTException e) {
				throw new CommandException("commands.give.tagError", e.getMessage());
			}
		}

		Minecraft mc = Minecraft.getMinecraft();
		World world = mc.world;

		for (int i = 0; i < mc.player.inventory.getSizeInventory(); i++) {
			ItemStack stack = mc.player.inventory.getStackInSlot(i);
			if (stack.getItem() == item && (damage == -1 || stack.getItemDamage() == damage)
					&& (nbt == null || NBTUtil.areNBTEquals(nbt, stack.getTagCompound(), true))) {
				sender.sendMessage(new TextComponentString("It's already in your inventory you dummy!"));
				return;
			}
		}

		if (mc.player.isSneaking()) {
			sender.sendMessage(
					new TextComponentString(TextFormatting.RED + "Can't access inventories while you're sneaking"));
			return;
		}

		float radius = mc.playerController.getBlockReachDistance() + 0.5f;
		double playerx = sender.getPositionVector().x;
		double playery = sender.getPositionVector().y + sender.getCommandSenderEntity().getEyeHeight();
		double playerz = sender.getPositionVector().z;

		Ptr<Boolean> foundItem = new Ptr<>(Boolean.FALSE);

		for (int x = (int) (playerx - radius); x <= playerx + radius; x++) {
			for (int z = (int) (playerz - radius); z <= playerz + radius; z++) {
				for (int y = (int) (playery - radius); y <= playery + radius; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (world.getTileEntity(pos) instanceof IInventory) {
						ClientCommandsMod.INSTANCE
								.addLongTask(new WaitForGuiTask(mc, item, damage, nbt, sender, pos, foundItem));
					}
				}
			}
		}
		ClientCommandsMod.INSTANCE.addLongTask(new LongTask() {
			@Override
			public void start() {
				if (!foundItem.get()) {
					sender.sendMessage(new TextComponentString(TextFormatting.RED + "No matching items found"));
				}
				setFinished();
			}

			@Override
			protected void taskTick() {
			}
		});
	}

	@Override
	public String getName() {
		return "cfinditem";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/cfinditem <item> [damage] [nbt]";
	}

	private static class WaitForGuiTask extends LongTask {
		private Minecraft mc;
		private Item item;
		private int damage;
		private NBTTagCompound nbt;
		private ICommandSender sender;
		private BlockPos pos;
		private Ptr<Boolean> foundItem;

		public WaitForGuiTask(Minecraft mc, Item item, int damage, NBTTagCompound nbt, ICommandSender sender,
				BlockPos pos, Ptr<Boolean> foundItem) {
			this.mc = mc;
			this.item = item;
			this.damage = damage;
			this.nbt = nbt;
			this.sender = sender;
			this.pos = pos;
			this.foundItem = foundItem;
		}

		@Override
		public void start() {
			GuiBlocker blocker = new GuiBlocker() {
				@Override
				public boolean processGui(GuiScreen gui) {
					if (gui instanceof GuiContainer) {
						GuiContainer containerGui = (GuiContainer) gui;
						mc.player.openContainer = new DelegatingContainer(containerGui.inventorySlots) {
							@Override
							public void setAll(List<ItemStack> stacks) {
								for (ItemStack stack : stacks) {
									if (stack.getItem() == item && (damage == -1 || stack.getItemDamage() == damage)
											&& (nbt == null
													|| NBTUtil.areNBTEquals(nbt, stack.getTagCompound(), true))) {
										sender.sendMessage(new TextComponentString(
												String.format("Matching item found at (%d, %d, %d)", pos.getX(),
														pos.getY(), pos.getZ())));
										foundItem.set(Boolean.TRUE);
										break;
									}
								}
								mc.player.closeScreen();
								WaitForGuiTask.this.setFinished();
							}
						};
						setFinished();
						return false;
					} else {
						return true;
					}
				}
			};
			ClientCommandsMod.INSTANCE.addGuiBlocker(blocker);
			boolean success = false;
			for (EnumHand hand : EnumHand.values()) {
				EnumActionResult result = mc.playerController.processRightClickBlock(mc.player, mc.world, pos,
						EnumFacing.DOWN, new Vec3d(pos), hand);
				if (result == EnumActionResult.FAIL) {
					sender.sendMessage(new TextComponentString(TextFormatting.RED + "Unable to open an inventory"));
					break;
				}
				if (result == EnumActionResult.SUCCESS) {
					success = true;
					break;
				}
			}
			if (!success) {
				blocker.setFinished();
			}
		}

		@Override
		protected void taskTick() {
		}
	}

}