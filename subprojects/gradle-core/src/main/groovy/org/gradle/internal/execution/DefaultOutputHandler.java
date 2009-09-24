/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.execution;

import org.gradle.api.Task;
import org.gradle.api.GradleException;

/**
 * @author Hans Dockter
 */
public class DefaultOutputHandler implements OutputHandler {
    private OutputTimestampReader outputTimestampReader = new DefaultOutputTimestampReader();
    private OutputTimestampWriter outputTimestampWriter = new DefaultOutputTimestampWriter();
    private Task task;

    public DefaultOutputHandler(Task task) {
        this.task = task;
    }

    public void writeTimestamp(boolean successful) {
        if (successful) {
            outputTimestampWriter.taskSuccessfullyExecuted(task);
        } else {
            outputTimestampWriter.taskFailed(task);
        }
    }

    public OutputTimestampReader getOutputHistoryReader() {
        return outputTimestampReader;
    }

    public void setOutputHistoryReader(OutputTimestampReader outputTimestampReader) {
        this.outputTimestampReader = outputTimestampReader;
    }

    public OutputTimestampWriter getOutputHistoryWriter() {
        return outputTimestampWriter;
    }

    public void setOutputHistoryWriter(OutputTimestampWriter outputTimestampWriter) {
        this.outputTimestampWriter = outputTimestampWriter;
    }

    public boolean wasNotCreatedOrIsStale(Task... associatedTasks) {
        if (!task.doesOutputExists()) {
            return true;
        }
        long lastModifiedTimestamp = getLastModified();
        if (lastModifiedTimestamp == 0) {
            return true;
        }
        for (Task associatedTask : associatedTasks) {
            long associatedTaskLastModifiedTimestamp = associatedTask.getOutputLastModified();
            if (!(associatedTaskLastModifiedTimestamp > 0)) {
                throw new GradleException(String.format(
                        "Task %s depends on another tasks (%s) output that was not generated. ", task, associatedTask));
            }
            if (lastModifiedTimestamp < associatedTaskLastModifiedTimestamp) {
                System.out.println("Task " + associatedTask + " is newer");
                return true;
            }
        }
        return false;
    }

    public long getLastModified() {
        return outputTimestampReader.readTimestamp(task);
    }
}
