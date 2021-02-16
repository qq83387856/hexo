package info.xiaomo.gengine.ai.btree.branch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import info.xiaomo.gengine.ai.btree.BranchTask;
import info.xiaomo.gengine.ai.btree.Task;
import info.xiaomo.gengine.ai.btree.annotation.TaskAttribute;

public class Parallel<E> extends BranchTask<E> {
	private static final long serialVersionUID = 1L;
	/**
	 * Optional task attribute specifying the parallel policy (defaults to
	 * {@link Policy#Sequence})
	 */
	@TaskAttribute
	public Policy policy;
	/**
	 * Optional task attribute specifying the execution policy (defaults to
	 * {@link Orchestrator#Resume})
	 */
	@TaskAttribute
	public Orchestrator orchestrator;

	private boolean noRunningTasks;
	private Boolean lastResult;
	private int currentChildIndex;

	/**
	 * Creates a parallel task with sequence policy, resume orchestrator and no
	 * children
	 */
	public Parallel() {
		this(new ArrayList<>());
	}

	/**
	 * Creates a parallel task with sequence policy, resume orchestrator and the
	 * given children
	 *
	 * @param tasks the children
	 */
	@SuppressWarnings("unchecked")
	public Parallel(Task<E>... tasks) {
		this(Arrays.asList(tasks));
	}

	/**
	 * Creates a parallel task with sequence policy, resume orchestrator and the
	 * given children
	 *
	 * @param tasks the children
	 */
	public Parallel(List<Task<E>> tasks) {
		this(Policy.Sequence, tasks);
	}

	/**
	 * @param policy
	 * @param orchestrator
	 */
	public Parallel(Policy policy, Orchestrator orchestrator) {
		this(policy, orchestrator, new ArrayList<>());
	}

	/**
	 * Creates a parallel task with the given policy, resume orchestrator and no
	 * children
	 *
	 * @param policy the policy
	 */
	public Parallel(Policy policy) {
		this(policy, new ArrayList<Task<E>>());
	}

	/**
	 * Creates a parallel task with the given policy, resume orchestrator and the
	 * given children
	 *
	 * @param policy the policy
	 * @param tasks  the children
	 */
	@SuppressWarnings("unchecked")
	public Parallel(Policy policy, Task<E>... tasks) {
		this(policy, Arrays.asList(tasks));
	}

	/**
	 * Creates a parallel task with the given policy, resume orchestrator and the
	 * given children
	 *
	 * @param policy the policy
	 * @param tasks  the children
	 */
	public Parallel(Policy policy, List<Task<E>> tasks) {
		this(policy, Orchestrator.Resume, tasks);
	}

	/**
	 * Creates a parallel task with the given orchestrator, sequence policy and the
	 * given children
	 *
	 * @param orchestrator the orchestrator
	 * @param tasks        the children
	 */
	public Parallel(Orchestrator orchestrator, List<Task<E>> tasks) {
		this(Policy.Sequence, orchestrator, tasks);
	}

	/**
	 * Creates a parallel task with the given orchestrator, sequence policy and the
	 * given children
	 *
	 * @param orchestrator the orchestrator
	 * @param tasks        the children
	 */
	@SuppressWarnings("unchecked")
	public Parallel(Orchestrator orchestrator, Task<E>... tasks) {
		this(Policy.Sequence, orchestrator, Arrays.asList(tasks));
	}

	/**
	 * Creates a parallel task with the given orchestrator, policy and children
	 *
	 * @param policy       the policy
	 * @param orchestrator the orchestrator
	 * @param tasks        the children
	 */
	public Parallel(Policy policy, Orchestrator orchestrator, List<Task<E>> tasks) {
		super(tasks);
		this.policy = policy;
		this.orchestrator = orchestrator;
		noRunningTasks = true;
	}

	@Override
	public void run() {
		orchestrator.execute(this);
	}

	@Override
	public void childRunning(Task<E> task, Task<E> reporter) {
		noRunningTasks = false;
	}

	@Override
	public void childSuccess(Task<E> runningTask) {
		lastResult = policy.onChildSuccess(this);
	}

	@Override
	public void childFail(Task<E> runningTask) {
		lastResult = policy.onChildFail(this);
	}

	@Override
	public void resetTask() {
		super.resetTask();
		noRunningTasks = true;
	}

	// @Override
	// protected Task<E> copyTo(Task<E> task) {
	// Parallel<E> parallel = (Parallel<E>) task;
	// parallel.policy = policy; // no need to clone since it is immutable
	// parallel.orchestrator = orchestrator; // no need to clone since it is
	// immutable
	// return super.copyTo(task);
	// }

	public void resetAllChildren() {
		for (int i = 0, n = getChildCount(); i < n; i++) {
			Task<E> child = getChild(i);
			child.reset();
		}
	}

	@Override
	public void reset() {
		policy = Policy.Sequence;
		orchestrator = Orchestrator.Resume;
		noRunningTasks = true;
		lastResult = null;
		currentChildIndex = 0;
		super.reset();
	}

	/**
	 * 任务执行协调器<br>
	 * The enumeration of the child orchestrators supported by the {@link Parallel}
	 * task
	 */
	public enum Orchestrator {
		/**
		 * 默认方式，重新调度执行所有子任务<br>
		 * The default orchestrator - starts or resumes all children every single step
		 */
		Resume() {
			@SuppressWarnings({"rawtypes", "unchecked"})
			@Override
			public void execute(Parallel<?> parallel) {
				parallel.noRunningTasks = true;
				parallel.lastResult = null;
				for (parallel.currentChildIndex = 0; parallel.currentChildIndex < parallel.children
						.size(); parallel.currentChildIndex++) {
					Task child = parallel.children.get(parallel.currentChildIndex);
					if (child.getStatus() == Status.RUNNING) {
						child.run();
					} else {
						child.setControl(parallel);
						child.start();
						if (child.checkGuard(parallel))
							child.run();
						else
							child.fail();
					}

					if (parallel.lastResult != null) { // Current child has finished either with success or fail
						parallel.cancelRunningChildren(parallel.noRunningTasks ? parallel.currentChildIndex + 1 : 0);
						if (parallel.lastResult)
							parallel.success();
						else
							parallel.fail();
						return;
					}
				}
				parallel.running();
			}
		},
		/**
		 * 执行一次<br>
		 * Children execute until they succeed or fail but will not re-run until the
		 * parallel task has succeeded or failed
		 */
		Join() {
			@SuppressWarnings({"rawtypes", "unchecked"})
			@Override
			public void execute(Parallel<?> parallel) {
				parallel.noRunningTasks = true;
				parallel.lastResult = null;
				for (parallel.currentChildIndex = 0; parallel.currentChildIndex < parallel.children
						.size(); parallel.currentChildIndex++) {
					Task child = parallel.children.get(parallel.currentChildIndex);

					switch (child.getStatus()) {
						case RUNNING:
							child.run();
							break;
						case SUCCEEDED:
						case FAILED:
							break;
						default:
							child.setControl(parallel);
							child.start();
							if (child.checkGuard(parallel))
								child.run();
							else
								child.fail();
							break;
					}

					if (parallel.lastResult != null) { // Current child has finished either with success or fail
						parallel.cancelRunningChildren(parallel.noRunningTasks ? parallel.currentChildIndex + 1 : 0);
						parallel.resetAllChildren();
						if (parallel.lastResult)
							parallel.success();
						else
							parallel.fail();
						return;
					}
				}
				parallel.running();
			}
		};

		/**
		 * Called by parallel task each run
		 *
		 * @param parallel The {@link Parallel} task
		 */
		public abstract void execute(Parallel<?> parallel);
	}

	/**
	 * 执行结果策略
	 * <br>
	 * The enumeration of the policies supported by the {@link Parallel} task.
	 */
	public enum Policy {
		/**
		 * 一个子任务失败，并行任务即失败<br>
		 * The sequence policy makes the {@link Parallel} task fail as soon as one child
		 * fails; if all children succeed, then the parallel task succeeds. This is the
		 * default policy.
		 */
		Sequence() {
			@Override
			public Boolean onChildSuccess(Parallel<?> parallel) {
				switch (parallel.orchestrator) {
					case Join:
						return parallel.noRunningTasks
								&& parallel.children.get(parallel.children.size() - 1).getStatus() == Status.SUCCEEDED
								? Boolean.TRUE
								: null;
					case Resume:
					default:
						return parallel.noRunningTasks && parallel.currentChildIndex == parallel.children.size() - 1
								? Boolean.TRUE
								: null;
				}
			}

			@Override
			public Boolean onChildFail(Parallel<?> parallel) {
				return Boolean.FALSE;
			}
		},
		/**
		 * 所有子任务执行成功，并行任务才算成功<br>
		 * The selector policy makes the {@link Parallel} task succeed as soon as one
		 * child succeeds; if all children fail, then the parallel task fails.
		 */
		Selector() {
			@Override
			public Boolean onChildSuccess(Parallel<?> parallel) {
				return Boolean.TRUE;
			}

			@Override
			public Boolean onChildFail(Parallel<?> parallel) {
				return parallel.noRunningTasks && parallel.currentChildIndex == parallel.children.size() - 1
						? Boolean.FALSE
						: null;
			}
		};

		/**
		 * Called by parallel task each time one of its children succeeds.
		 *
		 * @param parallel the parallel task
		 * @return {@code Boolean.TRUE} if parallel must succeed, {@code Boolean.FALSE}
		 * if parallel must fail and {@code null} if parallel must keep on
		 * running.
		 */
		public abstract Boolean onChildSuccess(Parallel<?> parallel);

		/**
		 * Called by parallel task each time one of its children fails.
		 *
		 * @param parallel the parallel task
		 * @return {@code Boolean.TRUE} if parallel must succeed, {@code Boolean.FALSE}
		 * if parallel must fail and {@code null} if parallel must keep on
		 * running.
		 */
		public abstract Boolean onChildFail(Parallel<?> parallel);

	}
}