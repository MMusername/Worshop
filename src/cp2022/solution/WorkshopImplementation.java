package cp2022.solution;
import static java.lang.Thread.currentThread;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;

public class WorkshopImplementation implements cp2022.base.Workshop {

	private static final String panic = "panic: unexpected thread interruption";
	
	/* set of work places */
	private Map<WorkplaceId, Workplace> workplaces = new ConcurrentHashMap<>();
	/* all workers currently in workshop */
	private Map<Long, WorkerInfo> workers = new ConcurrentHashMap<>();
	/* set of workers that passed the gate */
	private Map<Long, WorkerInfo> afterGate = new ConcurrentHashMap<>();
	/* gate before workshop to ensure "2N requirement */
	private Semaphore gate = new Semaphore(1, true);
	/* map Semaphores for all work places */
	private Map<WorkplaceId, WorkplaceQueue> workplaceQueues = new ConcurrentHashMap<>();
	/* mutex for data protection */
	private Semaphore mutex = new Semaphore(1);
	/* barrier for blocking use() in case of cycle */
	private CyclicBarrier barrier;
	
	/* how many entered work place */
	private AtomicInteger enterCount = new AtomicInteger(0);
	
	public WorkshopImplementation(Collection<Workplace> workplaces) {
		for (Workplace workplace : workplaces) {
			this.workplaces.put(workplace.getId(), workplace);
			workplaceQueues.put(workplace.getId(), new WorkplaceQueue());
		}
	}
	
	/* opens gate if it is safe */
	public void checkGate() {
		int min = 0;
		for (WorkerInfo worker : afterGate.values()) {
			min = Math.min(min, worker.getNumber());
		}
		int max = enterCount.get() - min;
		if (2 * workplaces.size() - max - enterCount.get() >= 1) {
			gate.release();
		}
	}
	
	@Override
	public Workplace enter(WorkplaceId wid) {
		try {
			gate.acquire();
		} catch (InterruptedException e) {
        	throw new RuntimeException(panic);
		}
		/* after gate */
        try {
        	mutex.acquire();
        	afterGate.put(currentThread().getId(), 
        			new WorkerInfo(currentThread().getId(), enterCount.get()));
        	workers.put(currentThread().getId(), 
        			new WorkerInfo(currentThread().getId(), enterCount.get(), wid));
        	checkGate();
        } catch (InterruptedException e) {
        	throw new RuntimeException(panic);
        } finally {
        	mutex.release();
        }
        /* ready to enter work place queue */
        try {
			workplaceQueues.get(wid).acquireEnter();
		} catch (InterruptedException e1) {
			throw new RuntimeException(panic);
		}
        /* after work place queue */
        try {
        	mutex.acquire();
        	afterGate.remove(currentThread().getId());
        	enterCount.incrementAndGet();
        	checkGate();
        } catch (InterruptedException e) {
        	throw new RuntimeException(panic);
        } finally {
        	mutex.release();
        }
        
        return workplaces.get(wid);
	}

	private int detectCycle() {
		long worker = currentThread().getId();
		long first = worker;
		int lenght = 0;
		/* while I have not found a cycle */
		do {
			WorkplaceId nextWorkplaceID = workers.get(worker).getNextWorkPlaceID();
			if (!workplaceQueues.get(nextWorkplaceID).doesSomeoneWantsToLeave)	return 0;
			lenght++;
			worker = workplaceQueues.get(nextWorkplaceID).wantsToLeave;
		} while (first != worker);
		return lenght;
	}
	
	@Override
	public Workplace switchTo(WorkplaceId wid) {
		/* if wants to switch for same work place */
		if (workers.get(currentThread().getId()).getWorkPlaceID() == wid) {
			return workplaces.get(wid);
		}
				
		/* cycles */
		try {		
			mutex.acquire();
			
			/* reseting cycle length from previous swithTo */
			workers.get(currentThread().getId()).cycleLenght = 0;
			/* for handling cycles */
			workers.get(currentThread().getId()).setNextWorkPlaceID(wid);
			/* telling our current work place that we wants to leave it */
			workplaceQueues.get(workers.get(currentThread().getId()).getWorkPlaceID()).doesSomeoneWantsToLeave = true;
			workplaceQueues.get(workers.get(currentThread().getId()).getWorkPlaceID()).wantsToLeave = currentThread().getId();
			
			/* for 2*N condition */
			afterGate.put(currentThread().getId(), 
					new WorkerInfo(currentThread().getId(), enterCount.get()));
			
			int lenght = detectCycle();
			if (lenght != 0) {
				barrier = new CyclicBarrier(lenght);
				
				long worker = currentThread().getId();
				long first = worker;

				/* while I have not finish the cycle */
				do { 
					/* give every worker in the cycle length of the cycle */
					workers.get(worker).cycleLenght = lenght;
					WorkplaceId nextWorkplaceID = workers.get(worker).getNextWorkPlaceID();
					worker = workplaceQueues.get(nextWorkplaceID).wantsToLeave;
				} while (first != worker);
				
				/* there is a cycle and we are in it so we want to release first queue in the cycle */
				workplaceQueues.get(workers.get(currentThread().getId()).getWorkPlaceID()).release();
				workers.get(currentThread().getId()).cycleDetector = true;
			}
		} catch (InterruptedException e) {
        	throw new RuntimeException(panic);
        } finally {
        	mutex.release();
        }
		
		try {
			workplaceQueues.get(wid).acquireSwitchTo();
		} catch (InterruptedException e) {
			throw new RuntimeException(panic);
		}
		
		try {
			mutex.acquire();
			/* we are after semaphore */
			afterGate.remove(currentThread().getId());
			/* release our old work place */
			workplaceQueues.get(workers.get(currentThread().getId()).getWorkPlaceID()).doesSomeoneWantsToLeave = false;
			workplaceQueues.get(workers.get(currentThread().getId()).getWorkPlaceID()).wantsToLeave = 0;
			if (!workers.get(currentThread().getId()).cycleDetector) {
				workplaceQueues.get(workers.get(currentThread().getId()).getWorkPlaceID()).release();
			} else {
				workers.get(currentThread().getId()).cycleDetector = false;
			}
			checkGate();
			
			/* can switch work place */
			workers.get(currentThread().getId()).updateWorkPlaceID();
		} catch (InterruptedException e) {
			throw new RuntimeException(panic);
		} finally {
			mutex.release();
		}
				
		/* if there was a cycle, we should return fake work place */
		if (workers.get(currentThread().getId()).cycleLenght != 0) {
			return new FakeWorkplace(wid, workplaces.get(wid), panic, barrier);
		}
		return workplaces.get(wid);
	}

	@Override
	public void leave() {
		/* clear data */
		try {
			mutex.acquire();
			
			workplaceQueues.get(workers.get(currentThread().getId()).getWorkPlaceID()).doesSomeoneWantsToLeave = false;
			workplaceQueues.get(workers.get(currentThread().getId()).getWorkPlaceID()).wantsToLeave = 0;
			/* remove from workers map and release work place */
			workplaceQueues.get(workers.get(currentThread().getId()).getWorkPlaceID()).release();
			workers.remove(currentThread().getId());
			checkGate();
		} catch (InterruptedException e) {
			throw new RuntimeException(panic);
		} finally {
			mutex.release();
		}
	}

}
