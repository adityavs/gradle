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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.util.GFileUtils;
import org.gradle.util.HelperUtil;
import org.gradle.internal.execution.DefaultOutputTimestampReader;
import org.gradle.internal.execution.OutputTimestampWriter;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import static org.junit.Assert.assertThat;
import org.junit.Before;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultOutputTimestampReaderTest {
    private JUnit4Mockery context = new JUnit4Mockery();
    private Task taskStub = context.mock(Task.class);
    private File outputTimestampFile = new File(HelperUtil.makeNewTestDir(), "somepath");

    private DefaultOutputTimestampReader outputTimestampReader = new DefaultOutputTimestampReader();
    private OutputTimestampFilePathCreator outputTimestampFilePathCreatorStub = context.mock(OutputTimestampFilePathCreator.class);

    @Before
    public void setUp() {
        outputTimestampReader.setOutputTimestampFilePathCreator(outputTimestampFilePathCreatorStub);
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
    public void testReadHistoryWithExistingHistoryFile() {
        long timestamp = 1111;
        GFileUtils.writeStringToFile(outputTimestampFile, "" + timestamp);
        assertThat(outputTimestampReader.readTimestamp(taskStub), equalTo(timestamp));
    }

    @org.junit.Test
    public void testReadHistoryWithNonExistingHistoryFile() {
        assertThat(outputTimestampReader.readTimestamp(taskStub), equalTo(0L));
    }
}
