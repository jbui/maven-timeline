/**
 * Copyright (C) 2013 david@gageot.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.gageot.maven.buildevents;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import org.apache.maven.execution.*;
import org.apache.maven.plugin.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class BuildEventListener extends AbstractExecutionListener {
  private final File output;
  private final Map<String, Long> startTimes = new ConcurrentHashMap<String, Long>();
  private final Map<String, Long> endTimes = new ConcurrentHashMap<String, Long>();
  private final Map<String, Long> threads = new ConcurrentHashMap<String, Long>();

  public BuildEventListener(File output) {
    this.output = output;
  }

  @Override
  public void mojoStarted(ExecutionEvent event) {
    String key = key(event);
    startTimes.put(key, System.currentTimeMillis());
    threads.put(key, Thread.currentThread().getId());
  }

  @Override
  public void mojoSkipped(ExecutionEvent event) {
    mojoEnd(event);
  }

  @Override
  public void mojoSucceeded(ExecutionEvent event) {
    mojoEnd(event);
  }

  @Override
  public void mojoFailed(ExecutionEvent event) {
    mojoEnd(event);
  }

  private void mojoEnd(ExecutionEvent event) {
    endTimes.put(key(event), System.currentTimeMillis());
  }

  @Override
  public void sessionEnded(ExecutionEvent event) {
    report();
  }

  private String key(ExecutionEvent event) {
    MojoExecution mojo = event.getMojoExecution();
    String id = mojo.getExecutionId();
    String goal = mojo.getGoal();
    String phase = mojo.getLifecyclePhase();
    String group = event.getProject().getGroupId();
    String project = event.getProject().getArtifactId();
    return group + "/" + project + "/" + phase + "/" + goal + "/" +id;
  }

  public void report() {
    long buildStartTime = Long.MAX_VALUE;
    for (Long start : startTimes.values()) {
      buildStartTime = Math.min(buildStartTime, start);
    }

    long buildEndTime = 0;
    for (Long end : endTimes.values()) {
      buildEndTime = Math.max(buildEndTime, end);
    }

    List<HashMap<String, Object>> measureList = new ArrayList<HashMap<String, Object>>();
    for (String key : startTimes.keySet()) {
      String[] keyParts = key.split("/");
      HashMap<String, Object> measure = new HashMap<String, Object>();
      
      measure.put("group", keyParts[0]);
      measure.put("project", keyParts[1]);
      measure.put("phase", keyParts[2]);
      measure.put("goal", keyParts[3]);
      measure.put("id", keyParts[4]);
      measure.put("thread", threads.get(key));
      measure.put("start", startTimes.get(key) - buildStartTime);
      measure.put("end", endTimes.get(key) - buildStartTime);
      measure.put("elapsed", (endTimes.get(key) - buildStartTime) - startTimes.get(key) - buildStartTime);
      measureList.add(measure);
    }

    try {
      write((new JSONArray(measureList)).toString());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  private void write(String message) throws IOException {
    File path = output.getParentFile();
    if (!path.exists()) {
      if (!path.mkdirs()) {
        throw new IOException("Unable to create " + path);
      }
    }

    FileWriter writer = new FileWriter(output);
    writer.write(message);
    writer.close();
  }  
}