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

package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.javadoc.Groovydoc;

/**
 * <p>A {@link Plugin} which extends the {@link JavaPlugin} to provide support for compiling and documenting Groovy
 * source files.</p>
 *
 * @author Hans Dockter
 */
public class GroovyPlugin implements Plugin<Project> {
    public static final String GROOVYDOC_TASK_NAME = "groovydoc";

    public void use(Project project) {
        project.getPlugins().usePlugin(GroovyBasePlugin.class);
        project.getPlugins().usePlugin(JavaPlugin.class);

        configureGroovydoc(project);
    }

    private void configureGroovydoc(final Project project) {
        project.getTasks().withType(Groovydoc.class).allTasks(new Action<Groovydoc>() {
            public void execute(Groovydoc groovydoc) {
                groovydoc.getConventionMapping().map("defaultSource", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return mainGroovy(convention).getGroovy();
                    }
                });
            }
        });
        project.getTasks().add(GROOVYDOC_TASK_NAME, Groovydoc.class).setDescription("Generates the groovydoc for the source code.");
    }
    
    private SourceSet main(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    }

    private GroovySourceSet mainGroovy(Convention convention) {
        return ((DynamicObjectAware) main(convention)).getConvention().getPlugin(GroovySourceSet.class);
    }

}
