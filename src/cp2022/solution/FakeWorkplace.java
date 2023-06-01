package cp2022.solution;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;

public class FakeWorkplace extends Workplace {

	private final CyclicBarrier barrier;
	private final Workplace trueWorkplace;
	private final String panic;
	
	protected FakeWorkplace(WorkplaceId id, Workplace trueWorkplace, String panic, CyclicBarrier barrier) {
		super(id);
		this.trueWorkplace = trueWorkplace;
		this.barrier = barrier;
		this.panic = panic;
	}

	@Override
	public void use() {
		try {
			barrier.await();
		} catch (InterruptedException | BrokenBarrierException e) {
			throw new RuntimeException(panic);
		}
		trueWorkplace.use();
	}

}
