/** 
 * Copyright (c) Krapht, 2011
 * 
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public 
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */

package logisticspipes.routing;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import logisticspipes.interfaces.routing.IRelayItem;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.items.LogisticsLiquidContainer;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.ItemIdentifierStack;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import buildcraft.BuildCraftCore;
import buildcraft.api.core.Position;
import buildcraft.api.transport.IPipedItem;
import buildcraft.core.EntityPassiveItem;
import buildcraft.core.proxy.CoreProxy;
import buildcraft.transport.PipeTransportItems;
import buildcraft.transport.TileGenericPipe;

public class RoutedEntityItem extends EntityPassiveItem implements IRoutedItem{

	int destinationint = -1;
	UUID destinationUUID;
	
	boolean _doNotBuffer;
	
	int bufferCounter = 0;
	
	boolean arrived;
	
	LinkedList<Integer> relays = new LinkedList<Integer>();
	
	TransportMode _transportMode = TransportMode.Unknown;
	
	List<Integer> jamlist = new ArrayList<Integer>();
	
	public RoutedEntityItem(World world, IPipedItem entityItem) {
		super(world, entityItem.getEntityId());
		container = entityItem.getContainer();
		position = entityItem.getPosition();
		speed = entityItem.getSpeed();
		item = entityItem.getItemStack();
		if(entityItem.getContribution("routingInformation") == null) {
			this.addContribution("routingInformation", new RoutedEntityItemSaveHandler(this));
		} else {
			RoutedEntityItemSaveHandler settings = (RoutedEntityItemSaveHandler) entityItem.getContribution("routingInformation");
			this.destinationUUID=settings.destinationUUID;
			this.checkIDFromUUID();

			bufferCounter = settings.bufferCounter;
			arrived = settings.arrived;
			_transportMode = settings.transportMode;
			this.addContribution("routingInformation", new RoutedEntityItemSaveHandler(this));
		}
	}
	
	@Override
	public EntityItem toEntityItem(ForgeDirection dir) {
		if (!CoreProxy.proxy.isRenderWorld(worldObj)) {
			if (getItemStack().stackSize <= 0) {
				return null;
			}

			if(getItemStack().getItem() instanceof LogisticsLiquidContainer) {
				remove();
				return null;
			}

			//detect items spawning in the center of pipes and move them to the exit side
			if(position.x == container.xCoord + 0.5 && position.y == container.yCoord + 0.25 && position.z == container.zCoord + 0.5) {
				position.orientation = dir;
				if(dir == ForgeDirection.DOWN) {
					position.moveForwards(0.25);
				} else  if(dir == ForgeDirection.UP) {
					position.moveForwards(0.75);
				} else {
					position.moveForwards(0.5);
				}
			}

			Position motion = new Position(0, 0, 0, dir);
			motion.moveForwards(0.1 + getSpeed() * 2F);

			EntityItem entityitem = new EntityItem(worldObj, position.x, position.y, position.z, getItemStack());

			entityitem.lifespan = BuildCraftCore.itemLifespan;
			entityitem.delayBeforeCanPickup = 10;

			float f3 = worldObj.rand.nextFloat() * 0.01F - 0.02F;
			entityitem.motionX = (float) worldObj.rand.nextGaussian() * f3 + motion.x;
			entityitem.motionY = (float) worldObj.rand.nextGaussian() * f3 + motion.y;
			entityitem.motionZ = (float) worldObj.rand.nextGaussian() * f3 + motion.z;
			worldObj.spawnEntityInWorld(entityitem);
			remove();

			return entityitem;
		} else {
			return null;
		}
	}
	
	@Override
	public void clearDestination() {
		if (destinationint >= 0 && SimpleServiceLocator.routerManager.isRouter(destinationint)){
			IRouter destinationRouter = SimpleServiceLocator.routerManager.getRouter(destinationint); 
			if (destinationRouter.getPipe() != null && destinationRouter.getPipe().logic instanceof IRequireReliableTransport){
				((IRequireReliableTransport)destinationRouter.getPipe().logic).itemLost(ItemIdentifierStack.GetFromStack(item));
			}
		}
		//keep buffercounter and jamlist
		destinationint = -1;
		destinationUUID = null;
		_doNotBuffer = false;
		arrived = false;
		relays.clear();
		_transportMode = TransportMode.Unknown;
	}
	
	@Override
	public void remove() {
		if(MainProxy.isClient()) return;
		if (destinationint >= 0 && SimpleServiceLocator.routerManager.isRouter(destinationint)){
			IRouter destinationRouter = SimpleServiceLocator.routerManager.getRouter(destinationint); 
			if (!arrived && destinationRouter.getPipe() != null && destinationRouter.getPipe().logic instanceof IRequireReliableTransport){
				((IRequireReliableTransport)destinationRouter.getPipe().logic).itemLost(ItemIdentifierStack.GetFromStack(item));
			}
		}
		super.remove();
	}

	@Override
	public int getDestination() {
		if(relays.isEmpty()) {
			return this.destinationint;
		} else {
			return relays.getLast();
		}
	}

	@Override
	public ItemStack getItemStack() {
		return this.item;
	}

	@Override
	public void setItemStack(ItemStack item) {
		this.item = item;
	}

	@Override
	public void setDestination(int destination) {
		this.destinationint = destination;
		IRouter router = SimpleServiceLocator.routerManager.getRouter(destination);
		if(router != null) {
			this.destinationUUID = router.getId();
		} else {
			this.destinationUUID = null;
		}
	}

	@Override
	public void setDoNotBuffer(boolean isBuffered) {
		_doNotBuffer = isBuffered;
	}

	@Override
	public boolean getDoNotBuffer() {
		return _doNotBuffer;
	}

	@Override
	public EntityPassiveItem getEntityPassiveItem() {
		return this;
	}

	@Override
	@Deprecated
	public void setArrived(boolean flag) {
		this.arrived = flag;
	}

	@Override
	public boolean getArrived() {
		return this.arrived;
	}

	@Override
	public void split(World worldObj, int itemsToTake, ForgeDirection orientation) {
		if(getItemStack().getItem() instanceof LogisticsLiquidContainer) {
			throw new UnsupportedOperationException("Can't split up a LiquidContainer");
		}
		EntityPassiveItem newItem = new EntityPassiveItem(worldObj);
		newItem.setPosition(position.x, position.y, position.z);
		newItem.setSpeed(this.speed);
		newItem.setItemStack(this.item.splitStack(this.item.stackSize - itemsToTake));
		
		if (this.container instanceof TileGenericPipe && ((TileGenericPipe)this.container).pipe.transport instanceof PipeTransportItems){
			if (((TileGenericPipe)this.container).pipe instanceof PipeLogisticsChassi){
				PipeLogisticsChassi chassi = (PipeLogisticsChassi) ((TileGenericPipe)this.container).pipe;
				chassi.queueRoutedItem(SimpleServiceLocator.buildCraftProxy.CreateRoutedItem(worldObj, newItem), orientation);
			} else {
				((PipeTransportItems)((TileGenericPipe)this.container).pipe.transport).entityEntering(newItem, orientation.getOpposite());
			}
		}
	}

	@Override
	public void SetPosition(double x, double y, double z) {
		this.position = new Position(x,y,z);
	}

	@Override
	public void setTransportMode(TransportMode transportMode) {
		this._transportMode = transportMode;
		
	}

	@Override
	public TransportMode getTransportMode() {
		return this._transportMode;
	}

	@Override
	public EntityPassiveItem getNewEntityPassiveItem() {
		if(getItemStack().getItem() instanceof LogisticsLiquidContainer) {
			throw new UnsupportedOperationException("Can't change LiquidContainer to EntityPassiveItem");
		}
		EntityPassiveItem Entityitem = new EntityPassiveItem(worldObj, entityId);
		Entityitem.setContainer(container);
		Entityitem.setPosition(position.x, position.y, position.z);
		Entityitem.setSpeed(speed);
		Entityitem.setItemStack(item);
		return Entityitem;
	}

	@Override
	public void addToJamList(IRouter router) {
		jamlist.add(router.getSimpleID());
	}

	@Override
	public List<Integer> getJamList() {
		return jamlist;
	}

	@Override
	public int getBufferCounter() {
		return bufferCounter;
	}

	@Override
	public void setBufferCounter(int counter) {
		bufferCounter = counter;
	}

	@Override
	public IRoutedItem getCopy() {
		EntityPassiveItem Entityitem = new EntityPassiveItem(worldObj, entityId);
		Entityitem.setContainer(container);
		Entityitem.setPosition(position.x, position.y, position.z);
		Entityitem.setSpeed(speed);
		Entityitem.setItemStack(item.copy());
		RoutedEntityItem routed = new RoutedEntityItem(worldObj, Entityitem);
		routed.destinationint = destinationint;
		routed._doNotBuffer = _doNotBuffer;
		routed.bufferCounter = bufferCounter;
		routed.arrived = arrived;
		routed._transportMode = _transportMode;
		routed.jamlist.addAll(jamlist);
		routed.relays.addAll(relays);
		return routed;
	}

	@Override
	public void addRelayPoints(List<IRelayItem> relays) {
		if(relays != null) {
			for(IRelayItem relay:relays) {
				this.relays.add(relay.getSimpleID());
			}
		}
	}

	@Override
	public void itemRelayed() {
		relays.removeLast();
	}

	@Override
	public boolean isItemRelayed() {
		return !relays.isEmpty();
	}

	@Override
	public void replaceRelayID(int newId) {
		relays.removeLast();
		relays.addLast(newId);
	}

	@Override
	public UUID getDestinationUUID() {
		return this.destinationUUID;
	}

	@Override
	public void checkIDFromUUID() {	
		IRouterManager rm = SimpleServiceLocator.routerManager;
		IRouter router = rm.getRouter(destinationint);
		if(router==null || destinationUUID!=router.getId()) {
			destinationint=rm.getIDforUUID(destinationUUID);
		}		
	}
}
