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

import org.apache.commons.io.FileUtils;
import org.gradle.api.Task;
import org.gradle.util.GFileUtils;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import static org.junit.Assert.assertThat;
import org.junit.Before;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class DefaultOutputTimestampWriterTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    private File outputTimestampFile = new File(HelperUtil.makeNewTestDir(), "somepath");
    private DefaultOutputTimestampWriter outputTimestampWriter = new DefaultOutputTimestampWriter();
    private Task taskStub = context.mock(Task.class);
    private OutputTimestampFilePathCreator outputTimestampFilePathCreatorStub = context.mock(OutputTimestampFilePathCreator.class);

    @Before
    public void setUp() {
        outputTimestampWriter.setOutputTimestampFilePathCreator(outputTimestampFilePathCreatorStub);
        context.checking(new Expectations() {{
            allowing(outputTimestampFilePathCreatorStub).createPath(taskStub);
            will(returnValue(outputTimestampFile));
        }});
    }
    
    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @org.junit.Test
    public void shouldHaveOutputTimestampFileWhenTaskSuccessfullyExecuted() throws IOException {
        long timestampLowerLimit = System.currentTimeMillis();
        outputTimestampWriter.taskSuccessfullyExecuted(taskStub);
        assertThat(outputTimestampFile.isFile(), equalTo(true));
        long timestamp = Long.parseLong(FileUtils.readFileToString(outputTimestampFile));
        assertThat(timestamp, greaterThanOrEqualTo(timestampLowerLimit));
        assertThat(timestamp, lessThanOrEqualTo(System.currentTimeMillis()));
    }

    @org.junit.Test
    public void shouldNotWriteOutputTimestampFileWhenTaskDidNoWork() throws IOException {
        GFileUtils.writeStringToFile(outputTimestampFile, "1");
        context.checking(new Expectations() {{
            allowing(taskStub).getDidWork();
            will(returnValue(false));
        }});
        outputTimestampWriter.taskSuccessfullyExecuted(taskStub);
        long timestamp = Long.parseLong(FileUtils.readFileToString(outputTimestampFile));
        assertThat(timestamp, equalTo(1L));
    }

    @org.junit.Test
    public void shouldHaveNoOutputTimestampFileWhenTaskFailed() {
        outputTimestampWriter.taskFailed(taskStub);
        assertThat(outputTimestampFile.exists(), equalTo(false));
    }

    @org.junit.Test
    public void shouldDeleteExisitingOutputTimestampFileeWhenTaskFailed() throws IOException {
        FileUtils.touch(outputTimestampFile);
        outputTimestampWriter.taskFailed(taskStub);
        assertThat(outputTimestampFile.exists(), equalTo(false));
    }
}
