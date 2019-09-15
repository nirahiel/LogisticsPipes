package logisticspipes.blocks;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import drawer.NbtFormatKt;
import lombok.Getter;
import net.fabricmc.fabric.api.util.NbtType;

import logisticspipes.interfaces.IGuiOpenController;
import logisticspipes.interfaces.IGuiTileEntity;
import logisticspipes.items.ItemLogisticsProgrammer;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractguis.CoordinatesGuiProvider;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.guis.block.ProgramCompilerGui;
import logisticspipes.network.packets.block.CompilerStatusPacket;
import logisticspipes.pipes.PipeItemsBasicLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.SimpleStackInventory;
import network.rs485.logisticspipes.world.CoordinateUtils;
import network.rs485.logisticspipes.world.DoubleCoordinates;

public class LogisticsProgramCompilerTileEntity extends LogisticsSolidTileEntity implements IGuiTileEntity, IGuiOpenController {

	public static class ProgramCategories {

		public static final Identifier BASIC = new Identifier("logisticspipes", "compilercategory.basic");
		public static final Identifier TIER_2 = new Identifier("logisticspipes", "compilercategory.tier_2");
		public static final Identifier FLUID = new Identifier("logisticspipes", "compilercategory.fluid");
		public static final Identifier TIER_3 = new Identifier("logisticspipes", "compilercategory.tier_3");
		public static final Identifier CHASSIS = new Identifier("logisticspipes", "compilercategory.chassis");
		public static final Identifier CHASSIS_2 = new Identifier("logisticspipes", "compilercategory.chassis_2");
		public static final Identifier CHASSIS_3 = new Identifier("logisticspipes", "compilercategory.chassis_3");
		public static final Identifier MODDED = new Identifier("logisticspipes", "compilercategory.modded");

		static {
			//Force the order of keys
			programByCategory.put(BASIC, new HashSet<>());
			programByCategory.put(TIER_2, new HashSet<>());
			programByCategory.put(FLUID, new HashSet<>());
			programByCategory.put(TIER_3, new HashSet<>());
			programByCategory.put(CHASSIS, new HashSet<>());
			programByCategory.put(CHASSIS_2, new HashSet<>());
			programByCategory.put(CHASSIS_3, new HashSet<>());
			programByCategory.put(MODDED, new HashSet<>());
		}
	}

	public static final Map<Identifier, Set<Identifier>> programByCategory = new LinkedHashMap<>();
	private final PlayerCollectionList playerList = new PlayerCollectionList();
	private String taskType = "";
	@Getter
	private Identifier currentTask = null;
	@Getter
	private double taskProgress = 0;
	@Getter
	private boolean wasAbleToConsumePower = false;

	@Getter
	private SimpleStackInventory inventory = new SimpleStackInventory(2, "programcompilerinv", 64);

	@Override
	public CoordinatesGuiProvider getGuiProvider() {
		return NewGuiHandler.getGui(ProgramCompilerGui.class);
	}

	public ListTag getNBTTagListForKey(String key) {
		CompoundTag nbt = this.getInventory().getStackInSlot(0).getTag();
		if (nbt == null) {
			this.getInventory().getStackInSlot(0).setTag(new CompoundTag());
			nbt = this.getInventory().getStackInSlot(0).getTag();
		}

		if (!nbt.containsKey(key)) {
			ListTag list = new ListTag();
			nbt.put(key, list);
		}
		return nbt.getList(key, NbtType.STRING);
	}

	public void triggerNewTask(Identifier category, String taskType) {
		if (currentTask != null) return;
		this.taskType = taskType;
		currentTask = category;
		taskProgress = 0;
		wasAbleToConsumePower = true;
		updateClient();
	}

	@Override
	public void guiOpenedByPlayer(PlayerEntity player) {
		playerList.add(player);
		MainProxy.sendPacketToPlayer(getClientUpdatePacket(), player);
	}

	private CoordinatesPacket getClientUpdatePacket() {
		return PacketHandler.getPacket(CompilerStatusPacket.class)
				.setCategory(currentTask)
				.setProgress(taskProgress)
				.setWasAbleToConsumePower(wasAbleToConsumePower)
				.setDisk(getInventory().getStackInSlot(0))
				.setProgrammer(getInventory().getStackInSlot(1))
				.setTilePos(this);
	}

	@Override
	public void guiClosedByPlayer(PlayerEntity player) {
		playerList.remove(player);
	}

	@Override
	public void update() {
		super.update();
		if (MainProxy.isServer(world)) {
			if (currentTask != null) {
				wasAbleToConsumePower = false;
				for (Direction dir : Direction.values()) {
					if (dir == Direction.UP) continue;
					DoubleCoordinates pos = CoordinateUtils.add(new DoubleCoordinates(this), dir);
					BlockEntity tile = pos.getBlockEntity(getWorld());
					if (!(tile instanceof LogisticsTileGenericPipe)) {
						continue;
					}
					LogisticsTileGenericPipe tPipe = (LogisticsTileGenericPipe) tile;
					if (!(tPipe.pipe.getClass() == PipeItemsBasicLogistics.class)) {
						continue;
					}
					CoreRoutedPipe pipe = (CoreRoutedPipe) tPipe.pipe;
					if (pipe.useEnergy(10)) {
						if (taskType.equals("category")) {
							taskProgress += 0.0005;
						} else if (taskType.equals("program")) {
							taskProgress += 0.0025;
						} else if (taskType.equals("flash")) {
							taskProgress += 0.01;
						} else {
							taskProgress += 1;
						}
						wasAbleToConsumePower = true;
					}
				}
				if (taskProgress >= 1) {
					if (taskType.equals("category")) {
						NBTTagList list = getNBTTagListForKey("compilerCategories");
						list.appendTag(new NBTTagString(currentTask.toString()));
					} else if (taskType.equals("program")) {
						NBTTagList list = getNBTTagListForKey("compilerPrograms");
						list.appendTag(new NBTTagString(currentTask.toString()));
					} else if (taskType.equals("flash")) {
						if (!getInventory().getStackInSlot(1).isEmpty()) {
							ItemStack programmer = getInventory().getStackInSlot(1);
							if (!programmer.hasTag()) {
								programmer.setTagCompound(new CompoundTag());
							}
							programmer.getTag().setString(ItemLogisticsProgrammer.RECIPE_TARGET, currentTask.toString());
						}
					} else {
						throw new UnsupportedOperationException(taskType);
					}

					taskType = "";
					currentTask = null;
					taskProgress = 0;
					wasAbleToConsumePower = false;
				}
				updateClient();
			}
		}
	}

	public void updateClient() {
		MainProxy.sendToPlayerList(getClientUpdatePacket(), playerList);
	}

	@Override
	public void onBlockBreak() {
		inventory.dropContents(world, getPos());
	}

	public void setStateOnClient(CompilerStatusPacket compilerStatusPacket) {
		getInventory().setInventorySlotContents(0, compilerStatusPacket.getDisk());
		getInventory().setInventorySlotContents(1, compilerStatusPacket.getProgrammer());
		currentTask = compilerStatusPacket.getCategory();
		taskProgress = compilerStatusPacket.getProgress();
		wasAbleToConsumePower = compilerStatusPacket.isWasAbleToConsumePower();
	}

	@Override
	public void readFromNBT(CompoundTag nbt) {
		inventory.readFromNBT(nbt, "programcompilerinv");
		super.readFromNBT(nbt);
	}

	@Override
	public CompoundTag writeToNBT(CompoundTag nbt) {
		inventory.writeToNBT(nbt, "programcompilerinv");
		return super.writeToNBT(nbt);
	}

	@Override
	public void fromTag(CompoundTag compoundTag_1) {
		super.fromTag(compoundTag_1);
		inventory.read
	}

	@Override
	public CompoundTag toTag(CompoundTag compoundTag_1) {
		return super.toTag(compoundTag_1);
	}
}
