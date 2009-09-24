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
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.HashUtil;
import org.gradle.util.GFileUtils;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultOutputTimestampFilePathCreator implements OutputTimestampFilePathCreator {
    public File createPath(Task task) {
        File taskOutputRootDir = new File(task.getProject().getGradle().getGradleUserHomeDir(), OutputTimestampWriter.HISTORY_DIR_NAME);
        File pathBase = task.getProject().getRootDir();
        String path = HashUtil.createHash(GFileUtils.canonicalise(pathBase).getAbsolutePath());
        return new File(taskOutputRootDir, path + task.getPath().replace(":", "/"));
    }
}
