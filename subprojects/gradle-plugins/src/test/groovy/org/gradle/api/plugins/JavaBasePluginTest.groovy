/*
 * Copyright 2010 the original author or authors.
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

package gradle.api.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.Compile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.gradle.util.Matchers.builtBy
import static org.gradle.util.Matchers.dependsOn
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */

class JavaBasePluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final JavaBasePlugin javaBasePlugin = new JavaBasePlugin()

    @Test public void appliesBasePluginsAndAddsConventionObject() {
        javaBasePlugin.use(project)

        assertTrue(project.getPlugins().hasPlugin(ReportingBasePlugin))
        assertTrue(project.getPlugins().hasPlugin(BasePlugin))

        assertThat(project.convention.plugins.java, instanceOf(JavaPluginConvention))
    }

    @Test public void addsConfigurationsToTheProject() {
        javaBasePlugin.use(project)

        def configuration = project.configurations.getByName(JavaBasePlugin.COMPILE_CONFIGURATION_NAME)
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.configurations.getByName(JavaBasePlugin.RUNTIME_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaBasePlugin.COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(JavaBasePlugin.TEST_COMPILE_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaBasePlugin.COMPILE_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.configurations.getByName(JavaBasePlugin.TEST_RUNTIME_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(JavaBasePlugin.TEST_COMPILE_CONFIGURATION_NAME, JavaBasePlugin.RUNTIME_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(Dependency.DEFAULT_CONFIGURATION)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(Dependency.ARCHIVES_CONFIGURATION, JavaBasePlugin.RUNTIME_CONFIGURATION_NAME)))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test public void createsTasksAndAppliesMappingsForNewSourceSet() {
        javaBasePlugin.use(project)

        project.sourceSets.add('custom')
        def set = project.sourceSets.custom
        assertThat(set.java.srcDirs, equalTo(toLinkedSet(project.file('src/custom/java'))))
        assertThat(set.resources.srcDirs, equalTo(toLinkedSet(project.file('src/custom/resources'))))
        assertThat(set.compileClasspath, sameInstance(project.configurations.compile))
        assertThat(set.classesDir, equalTo(new File(project.buildDir, 'classes/custom')))
        assertThat(set.classes, builtBy('customClasses'))
        assertThat(set.runtimeClasspath.sourceCollections, hasItem(project.configurations.runtime))
        assertThat(set.runtimeClasspath, hasItem(new File(project.buildDir, 'classes/custom')))

        def task = project.tasks['processCustomResources']
        assertThat(task.description, equalTo('Processes the custom resources.'))
        assertThat(task, instanceOf(Copy))
        assertThat(task, dependsOn())
        assertThat(task.destinationDir, equalTo(project.sourceSets.custom.classesDir))
        assertThat(task.defaultSource, equalTo(project.sourceSets.custom.resources))

        task = project.tasks['compileCustomJava']
        assertThat(task.description, equalTo('Compiles the custom Java source.'))
        assertThat(task, instanceOf(Compile))
        assertThat(task, dependsOn())
        assertThat(task.defaultSource, equalTo(project.sourceSets.custom.java))
        assertThat(task.classpath, sameInstance(project.sourceSets.custom.compileClasspath))
        assertThat(task.destinationDir, equalTo(project.sourceSets.custom.classesDir))

        task = project.tasks['customClasses']
        assertThat(task.description, equalTo('Assembles the custom classes.'))
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn('processCustomResources', 'compileCustomJava'))
    }

    @Test public void createsStandardTasksAndAppliesMappings() {
        javaBasePlugin.use(project)

        def task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn())

        task = project.tasks[JavaBasePlugin.CHECK_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn())

        task = project.tasks["buildArchives"]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn())

        task = project.tasks[JavaBasePlugin.BUILD_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(BasePlugin.ASSEMBLE_TASK_NAME, JavaBasePlugin.CHECK_TASK_NAME))

        task = project.tasks[JavaBasePlugin.BUILD_NEEDED_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaBasePlugin.BUILD_TASK_NAME))

        task = project.tasks[JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME]
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn(JavaBasePlugin.BUILD_TASK_NAME))
    }

    @Test public void appliesMappingsToTasksDefinedByBuildScript() {
        javaBasePlugin.use(project)

        def task = project.createTask('customCompile', type: Compile)
        assertThat(task.classpath, sameInstance(project.configurations.getByName(JavaBasePlugin.COMPILE_CONFIGURATION_NAME)))
        assertThat(task.sourceCompatibility, equalTo(project.sourceCompatibility.toString()))

        task = project.createTask('customTest', type: org.gradle.api.tasks.testing.Test)
        assertThat(task.workingDir, equalTo(project.projectDir))

        task = project.createTask('customJavadoc', type: Javadoc)
        assertThat(task.destinationDir, equalTo((project.file("$project.docsDir/javadoc"))))
        assertThat(task.optionsFile, equalTo(project.file('build/tmp/javadoc.options')))
        assertThat(task.title, equalTo(project.apiDocTitle))
    }

    @Test public void appliesMappingsToCustomJarTasks() {
        javaBasePlugin.use(project)

        def task = project.createTask('customJar', type: Jar)
        assertThat(task, dependsOn())
        assertThat(task.destinationDir, equalTo(project.libsDir))
        assertThat(task.manifest, notNullValue())
    }

    @Test public void buildOtherProjects() {
        DefaultProject commonProject = HelperUtil.createChildProject(project, "common");
        DefaultProject middleProject = HelperUtil.createChildProject(project, "middle");
        DefaultProject appProject = HelperUtil.createChildProject(project, "app");

        javaBasePlugin.use(project);
        javaBasePlugin.use(commonProject);
        javaBasePlugin.use(middleProject);
        javaBasePlugin.use(appProject);

        appProject.dependencies {
            compile project(path: middleProject.path, configuration: 'compile')
        }
        middleProject.dependencies {
            compile project(path: commonProject.path, configuration: 'compile')
        }

        Task task = middleProject.tasks[JavaBasePlugin.BUILD_NEEDED_TASK_NAME];
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, equalTo([':middle:build', ':common:build'] as Set))

        task = middleProject.tasks[JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME];
        assertThat(task.taskDependencies.getDependencies(task)*.path as Set, equalTo([':middle:build', ':app:build'] as Set))
    }
}