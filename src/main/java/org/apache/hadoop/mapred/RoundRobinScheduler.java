package org.apache.hadoop.mapred;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * assign task in round robin fashion
 * 
 * @author <a href="mailto:zhizhong.qiu@happyelements.com">kevin</a>
 */
public class RoundRobinScheduler extends TaskScheduler {

	private static final Log LOGGER = LogFactory.getLog(RoundRobinScheduler.class);

	private Map<JobInProgress, JobInProgress> jobs = new ConcurrentHashMap<JobInProgress, JobInProgress>();

	/**
	 * {@inheritDoc}
	 * 
	 * @see org.apache.hadoop.mapred.TaskScheduler#start()
	 */
	@Override
	public void start() throws IOException {
		super.start();
		RoundRobinScheduler.LOGGER.info("start round robin scheduler");
		this.taskTrackerManager.addJobInProgressListener(new JobInProgressListener() {
			@Override
			public void jobUpdated(JobChangeEvent event) {
				RoundRobinScheduler.LOGGER.info("get an event:" + event);
			}

			@Override
			public void jobRemoved(JobInProgress job) {
				RoundRobinScheduler.LOGGER.info("remove job	" + job);
				RoundRobinScheduler.this.jobs.remove(job);
			}

			@Override
			public void jobAdded(JobInProgress job) throws IOException {
				RoundRobinScheduler.LOGGER.info("add job	" + job);
				if (job != null) {
					RoundRobinScheduler.this.jobs.put(job, job);
				}
			}
		});
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Task> assignTasks(TaskTrackerStatus status) throws IOException {
		RoundRobinScheduler.LOGGER.info("assign tasks for " + status);
		final List<Task> assigned = new ArrayList<Task>();
		final int task_tracker = this.taskTrackerManager.getClusterStatus().getTaskTrackers();
		final int uniq_hosts = this.taskTrackerManager.getNumberOfUniqueHosts();

		// assign map task
		int map_capacity = status.getMaxMapTasks() - status.countMapTasks();
		while (map_capacity > 0) {
			Iterator<Entry<JobInProgress, JobInProgress>> round_roubin = this.jobs.entrySet().iterator();
			while (round_roubin.hasNext() && map_capacity > 0) {
				JobInProgress job = round_roubin.next().getValue();
				if (job.getStatus().getRunState() == JobStatus.RUNNING) {
					Task task = job.obtainNewMapTask(status, task_tracker, uniq_hosts);
					if (task == null) {
						assigned.add(task);
						map_capacity--;
					}
				}
			}
		}

		// assign reduce task
		int reduce_capacity = status.getMaxMapTasks() - status.countMapTasks();
		while (reduce_capacity > 0) {
			Iterator<Entry<JobInProgress, JobInProgress>> round_roubin = this.jobs.entrySet().iterator();
			while (round_roubin.hasNext() && reduce_capacity > 0) {
				JobInProgress job = round_roubin.next().getValue();
				if (job.getStatus().getRunState() == JobStatus.RUNNING) {
					Task task = job.obtainNewReduceTask(status, task_tracker, uniq_hosts);
					if (task != null) {
						assigned.add(task);
						reduce_capacity--;
					}
				}
			}
		}

		RoundRobinScheduler.LOGGER.info("assign task:" + assigned.size());
		return assigned;
	}

	@Override
	public Collection<JobInProgress> getJobs(String identity) {
		RoundRobinScheduler.LOGGER.info("get jobs");
		return new CopyOnWriteArraySet<JobInProgress>(this.jobs.keySet());
	}
}
