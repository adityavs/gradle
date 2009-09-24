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

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.gradle.api.Task;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.util.HelperUtil;
import org.gradle.util.HashUtil;
import org.gradle.util.GFileUtils;
import org.hamcrest.Matchers;
import static org.junit.Assert.assertThat;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultOutputTimestampFilePathCreatorTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    @org.junit.Test
    public void testCreatePath() {
        final Task taskStub = context.mock(Task.class);
        final Project projectStub = context.mock(Project.class);
        final Gradle gradleStub = context.mock(Gradle.class);
        final String taskPath = ":someProjectPath:someTaskName";
        final File rootDir = new File("someRootDir");
        final File gradleUserHomeDir = new File("gradleUserHome");

        context.checking(new Expectations() {{
            allowing(taskStub).getPath();
            will(returnValue(taskPath));
            allowing(taskStub).getProject();
            will(returnValue(projectStub));
            allowing(projectStub).getRootDir();
            will(returnValue(rootDir));
            allowing(projectStub).getGradle();
            will(returnValue(gradleStub));
            allowing(gradleStub).getGradleUserHomeDir();
            will(returnValue(gradleUserHomeDir));
        }});

        File outputTimestampFile = new DefaultOutputTimestampFilePathCreator().createPath(taskStub);
        assertThat(outputTimestampFile.getAbsolutePath(),
                Matchers.equalTo(new File(gradleUserHomeDir, OutputTimestampWriter.HISTORY_DIR_NAME + "/" +
                        HashUtil.createHash(GFileUtils.canonicalise(rootDir).getAbsolutePath()) +
                        taskPath.replace(":", "/")).getAbsolutePath()));
    }
}
