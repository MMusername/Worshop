package cp2022.solution;

import java.util.concurrent.Semaphore;

public class WorkplaceQueue {
	private final Semaphore enterQueue = new Semaphore(0, true);
	private final Semaphore switchToQueue = new Semaphore(0, true);
	
	public long wantsToLeave;
	public boolean doesSomeoneWantsToLeave = false;
	
	private int value = 1;
	private int onSwitchTo = 0;
	private int onEnter = 0;
	
	
	public void acquireEnter() throws InterruptedException {
		if (value == 0) {
			onEnter++;
			enterQueue.acquire();
		} else {
			value--;
		}
	}
	
	public void acquireSwitchTo() throws InterruptedException {
		if (value == 0) {
			onSwitchTo++;
			switchToQueue.acquire();
		} else {
			value--;
		}
	}
	
	public void release() {
		if (value == 0) {
			/* someone is waiting on the semaphore */
			if (onSwitchTo > 0) {
				onSwitchTo--;
				switchToQueue.release();
			} else if (onEnter > 0) {
				onEnter--;
				enterQueue.release();
			} else {
				value++;
			}
		} else {
			value++;
		}
	}

}
