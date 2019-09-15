package logisticspipes.ticks;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.Callable;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;

import logisticspipes.proxy.MainProxy;
import logisticspipes.transport.LPTravelingItem;
import logisticspipes.utils.tuples.Tuple2;

public class QueuedTasks {

	@SuppressWarnings("rawtypes")
	private static LinkedList<Callable> queue = new LinkedList<>();

	// called on server shutdown only.
	public static void clearAllTasks() {
		QueuedTasks.queue.clear();
	}

	@SuppressWarnings("rawtypes")
	public static void queueTask(Callable task) {
		synchronized (QueuedTasks.queue) {
			QueuedTasks.queue.add(task);
		}
	}

	@SuppressWarnings({ "rawtypes" })
	@SubscribeEvent
	public void tickEnd(ServerTickEvent event) {
		if (event.phase != Phase.END) {
			return;
		}
		Callable call;
		while (!QueuedTasks.queue.isEmpty()) {
			synchronized (QueuedTasks.queue) {
				call = QueuedTasks.queue.removeFirst();
			}
			if (call != null) {
				try {
					call.call();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		MainProxy.proxy.tick();
		synchronized (LPTravelingItem.forceKeep) {
			Iterator<Tuple2<Integer, Object>> iter = LPTravelingItem.forceKeep.iterator();
			while (iter.hasNext()) {
				Tuple2<Integer, Object> tuple = iter.next();
				tuple.setValue1(tuple.getValue1() - 1);
				if (tuple.getValue1() < 0) {
					iter.remove();
				}
			}
		}
	}
}
