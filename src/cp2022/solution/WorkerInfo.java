package cp2022.solution;

import cp2022.base.WorkplaceId;

public class WorkerInfo {
	private final long threadId;
	private final int number; // value of enter count when Thread threadId called enter or switchTo 
	private WorkplaceId workPlaceID = null;
	private WorkplaceId nextWorkPlaceID = null;
	
	public int cycleLenght = 0;
	public boolean cycleDetector = false;
	
	public WorkerInfo(long threadId, int number) {
		this.threadId = threadId;
		this.number = number;
	}
	
	public WorkerInfo(long threadId, int number, WorkplaceId workPlaceID) {
		this.threadId = threadId;
		this.number = number;
		this.workPlaceID = workPlaceID;
	}

	public long getThreadId() {
		return threadId;
	}

	public int getNumber() {
		return number;
	}

	public WorkplaceId getWorkPlaceID() {
		return workPlaceID;
	}
	
	public void updateWorkPlaceID() {
		workPlaceID = nextWorkPlaceID;
		nextWorkPlaceID = null;
	}

	public WorkplaceId getNextWorkPlaceID() {
		return nextWorkPlaceID;
	}

	public void setNextWorkPlaceID(WorkplaceId nextWorkPlaceID) {
		this.nextWorkPlaceID = nextWorkPlaceID;
	}

}
