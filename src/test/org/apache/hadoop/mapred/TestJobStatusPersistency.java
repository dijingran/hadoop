package org.apache.hadoop.mapred;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.fs.Path;

import java.io.*;
import java.util.Iterator;
import java.util.Properties;

public class TestJobStatusPersistency extends ClusterMapReduceTestCase {

  public static class EchoMap implements Mapper {

    public void configure(JobConf conf) {
    }

    public void close() {
    }

    public void map(WritableComparable key, Writable value,
                    OutputCollector collector, Reporter reporter) throws IOException {
      collector.collect(key, value);
    }
  }

  public static class EchoReduce implements Reducer {

    public void configure(JobConf conf) {
    }

    public void close() {
    }

    public void reduce(WritableComparable key, Iterator values,
                       OutputCollector collector, Reporter reporter) throws IOException {
      while (values.hasNext()) {
        Writable value = (Writable) values.next();
        collector.collect(key, value);
      }
    }

  }

  private String runJob() throws Exception {
    OutputStream os = getFileSystem().create(new Path(getInputDir(), "text.txt"));
    Writer wr = new OutputStreamWriter(os);
    wr.write("hello1\n");
    wr.write("hello2\n");
    wr.write("hello3\n");
    wr.write("hello4\n");
    wr.close();

    JobConf conf = createJobConf();
    conf.setJobName("mr");

    conf.setInputFormat(TextInputFormat.class);

    conf.setMapOutputKeyClass(LongWritable.class);
    conf.setMapOutputValueClass(Text.class);

    conf.setOutputFormat(TextOutputFormat.class);
    conf.setOutputKeyClass(LongWritable.class);
    conf.setOutputValueClass(Text.class);

    conf.setMapperClass(TestJobStatusPersistency.EchoMap.class);
    conf.setReducerClass(TestJobStatusPersistency.EchoReduce.class);

    conf.setInputPath(getInputDir());

    conf.setOutputPath(getOutputDir());

    return JobClient.runJob(conf).getJobID();
  }

  public void testNonPersistency() throws Exception {
    String jobId = runJob();
    JobClient jc = new JobClient(createJobConf());
    RunningJob rj = jc.getJob(jobId);
    assertNotNull(rj);
    stopCluster();
    startCluster(false, null);
    jc = new JobClient(createJobConf());
    rj = jc.getJob(jobId);
    assertNull(rj);
  }

  public void testPersistency() throws Exception {
    Properties config = new Properties();
    config.setProperty("mapred.job.tracker.persist.jobstatus.active", "true");
    config.setProperty("mapred.job.tracker.persist.jobstatus.hours", "1");
    stopCluster();
    startCluster(false, config);
    String jobId = runJob();
    JobClient jc = new JobClient(createJobConf());
    RunningJob rj0 = jc.getJob(jobId);
    assertNotNull(rj0);
    boolean sucessfull0 = rj0.isSuccessful();
    String jobName0 = rj0.getJobName();
    Counters counters0 = rj0.getCounters();
    TaskCompletionEvent[] events0 = rj0.getTaskCompletionEvents(0);

    stopCluster();
    startCluster(false, config);
     
    jc = new JobClient(createJobConf());
    RunningJob rj1 = jc.getJob(jobId);
    assertNotNull(rj1);
    assertEquals(sucessfull0, rj1.isSuccessful());
    assertEquals(jobName0, rj0.getJobName());
    assertEquals(counters0.size(), rj1.getCounters().size());

    TaskCompletionEvent[] events1 = rj1.getTaskCompletionEvents(0);
    assertEquals(events0.length, events1.length);    
    for (int i = 0; i < events0.length; i++) {
      assertEquals(events0[i].getTaskId(), events1[i].getTaskId());
      assertEquals(events0[i].getTaskStatus(), events1[i].getTaskStatus());
    }
  }

}