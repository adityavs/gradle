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
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class DefaultOutputHandlerTest {
    private JUnit4Mockery context = new JUnit4Mockery();
    private Task taskStub = context.mock(Task.class);
    private DefaultOutputHandler outputHandler = new DefaultOutputHandler(taskStub);

    @Test
    public void getLastModified() {
        final OutputTimestampReader outputTimestampReaderStub = context.mock(OutputTimestampReader.class);
        outputHandler.setOutputHistoryReader(outputTimestampReaderStub);
        final long someTimestamp = 1111;
        context.checking(new Expectations() {{
            allowing(outputTimestampReaderStub).readTimestamp(taskStub);
            will(returnValue(someTimestamp));
        }});
        assertThat(outputHandler.getLastModified(), Matchers.equalTo(someTimestamp));
    }

    @Test
    public void wasNotCreatedOrIsStaleWithNonExistingLastModifiedInformation() {
        final OutputTimestampReader outputTimestampReaderStub = context.mock(OutputTimestampReader.class);
        outputHandler.setOutputHistoryReader(outputTimestampReaderStub);
        final long someTimestamp = 0;
        context.checking(new Expectations() {{
            allowing(outputTimestampReaderStub).readTimestamp(taskStub);
            will(returnValue(someTimestamp));
            allowing(taskStub).doesOutputExists();
            will(returnValue(true));
        }});
        assertThat(outputHandler.wasNotCreatedOrIsStale(), Matchers.equalTo(true));
    }

    @Test
    public void wasNotCreatedOrIsStaleWithNonExistingTaskOutput() {
        final OutputTimestampReader outputTimestampReaderStub = context.mock(OutputTimestampReader.class);
        outputHandler.setOutputHistoryReader(outputTimestampReaderStub);
        final long someTimestamp = 1111;
        final Task associatedTaskStub = context.mock(Task.class, "associated");
        context.checking(new Expectations() {{
            allowing(outputTimestampReaderStub).readTimestamp(taskStub);
            will(returnValue(someTimestamp));
            allowing(taskStub).doesOutputExists();
            will(returnValue(false));
            allowing(associatedTaskStub).getOutputLastModified();
            will(returnValue(someTimestamp - 1));
        }});
        assertThat(outputHandler.wasNotCreatedOrIsStale(), Matchers.equalTo(true));
    }

    @Test
    public void wasNotCreatedOrIsStaleWithNewerAssociatedTaskOutput() {
        final OutputTimestampReader outputTimestampReaderStub = context.mock(OutputTimestampReader.class);
        outputHandler.setOutputHistoryReader(outputTimestampReaderStub);
        final long someTimestamp = 1111;
        final Task associatedTaskStub = context.mock(Task.class, "associated");
        context.checking(new Expectations() {{
            allowing(outputTimestampReaderStub).readTimestamp(taskStub);
            will(returnValue(someTimestamp));
            allowing(taskStub).doesOutputExists();
            will(returnValue(true));
            allowing(associatedTaskStub).getOutputLastModified();
            will(returnValue(someTimestamp + 1));
        }});
        assertThat(outputHandler.wasNotCreatedOrIsStale(associatedTaskStub), Matchers.equalTo(true));
    }

    @Test
    public void wasNotCreatedOrIsStaleWithUptodateOutput() {
        final OutputTimestampReader outputTimestampReaderStub = context.mock(OutputTimestampReader.class);
        outputHandler.setOutputHistoryReader(outputTimestampReaderStub);
        final long someTimestamp = 1111;
        context.checking(new Expectations() {{
            allowing(outputTimestampReaderStub).readTimestamp(taskStub);
            will(returnValue(someTimestamp));
            allowing(taskStub).doesOutputExists();
            will(returnValue(true));
        }});
        assertThat(outputHandler.wasNotCreatedOrIsStale(), Matchers.equalTo(false));
    }

    @Test
    public void writeHistoryWithExecutionSuccessful() {
        final OutputTimestampWriter outputTimestampWriterMock = context.mock(OutputTimestampWriter.class);
        outputHandler.setOutputHistoryWriter(outputTimestampWriterMock);
        context.checking(new Expectations() {{
            one(outputTimestampWriterMock).taskSuccessfullyExecuted(taskStub);
        }});

        outputHandler.writeTimestamp(true);
    }

    @Test
    public void writeHistoryWithExecutionFailed() {
        final OutputTimestampWriter outputTimestampWriterMock = context.mock(OutputTimestampWriter.class);
        outputHandler.setOutputHistoryWriter(outputTimestampWriterMock);
        context.checking(new Expectations() {{
            one(outputTimestampWriterMock).taskFailed(taskStub);
        }});

        outputHandler.writeTimestamp(false);
    }
}
