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

package org.gradle.api.plugins;

import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.GradleManifest;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * <p>A {@link org.gradle.api.Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 *
 * @author Hans Dockter
 */
public class JavaBasePlugin implements Plugin<Project> {
    public static final String CHECK_TASK_NAME = "check";
    public static final String JAVADOC_TASK_NAME = "javadoc";
    public static final String BUILD_TASK_NAME = "build";
    public static final String BUILD_DEPENDENTS_TASK_NAME = "buildDependents";
    public static final String BUILD_NEEDED_TASK_NAME = "buildNeeded";

    public static final String COMPILE_CONFIGURATION_NAME = "compile";
    public static final String RUNTIME_CONFIGURATION_NAME = "runtime";
    public static final String TEST_RUNTIME_CONFIGURATION_NAME = "testRuntime";
    public static final String TEST_COMPILE_CONFIGURATION_NAME = "testCompile";

    public void use(Project project) {
        project.getPlugins().usePlugin(BasePlugin.class);
        project.getPlugins().usePlugin(ReportingBasePlugin.class);

        JavaPluginConvention javaConvention = new JavaPluginConvention(project);
        project.getConvention().getPlugins().put("java", javaConvention);

        configureConfigurations(project);
        configureCompileDefaults(project, javaConvention);
        configureSourceSetDefaults(javaConvention);

        configureJavaDoc(project);
        configureTest(project);
        configureArchives(project, javaConvention);
        configureBuild(project);
        configureBuildNeeded(project);
        configureBuildDependents(project);
    }

    private void configureSourceSetDefaults(final JavaPluginConvention pluginConvention) {
        pluginConvention.getSourceSets().allObjects(new Action<SourceSet>() {
            public void execute(final SourceSet sourceSet) {
                final Project project = pluginConvention.getProject();
                ConventionMapping conventionMapping = ((IConventionAware) sourceSet).getConventionMapping();

                conventionMapping.map("classesDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        String classesDirName = String.format("classes/%s", sourceSet.getName());
                        return new File(project.getBuildDir(), classesDirName);
                    }
                });
                conventionMapping.map("compileClasspath", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME);
                    }
                });
                conventionMapping.map("runtimeClasspath", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return sourceSet.getClasses().plus(project.getConfigurations().getByName(
                                RUNTIME_CONFIGURATION_NAME));
                    }
                });
                sourceSet.getJava().srcDir(String.format("src/%s/java", sourceSet.getName()));
                sourceSet.getResources().srcDir(String.format("src/%s/resources", sourceSet.getName()));

                Copy processResources = project.getTasks().add(sourceSet.getProcessResourcesTaskName(), Copy.class);
                processResources.setDescription(String.format("Processes the %s.", sourceSet.getResources()));
                conventionMapping = processResources.getConventionMapping();
                conventionMapping.map("defaultSource", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return sourceSet.getResources();
                    }
                });
                conventionMapping.map("destinationDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return sourceSet.getClassesDir();
                    }
                });

                String compileTaskName = sourceSet.getCompileTaskName("java");
                Compile compileJava = project.getTasks().add(compileTaskName, Compile.class);
                configureForSourceSet(sourceSet, compileJava);

                Task classes = project.getTasks().add(sourceSet.getClassesTaskName());
                classes.dependsOn(sourceSet.getProcessResourcesTaskName(), compileTaskName);
                classes.setDescription(String.format("Assembles the %s classes.", sourceSet.getName()));

                sourceSet.compiledBy(sourceSet.getClassesTaskName());
            }
        });
    }

    public void configureForSourceSet(final SourceSet sourceSet, Compile compile) {
        ConventionMapping conventionMapping;
        compile.setDescription(String.format("Compiles the %s.", sourceSet.getJava()));
        conventionMapping = compile.getConventionMapping();
        conventionMapping.map("classpath", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return sourceSet.getCompileClasspath();
            }
        });
        conventionMapping.map("defaultSource", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return sourceSet.getJava();
            }
        });
        conventionMapping.map("destinationDir", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return sourceSet.getClassesDir();
            }
        });
    }

    private void configureCompileDefaults(final Project project, final JavaPluginConvention javaConvention) {
        project.getTasks().withType(Compile.class).allTasks(new Action<Compile>() {
            public void execute(final Compile compile) {
                ConventionMapping conventionMapping = compile.getConventionMapping();
                conventionMapping.map("classpath", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME);
                    }
                });
                conventionMapping.map("dependencyCacheDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return javaConvention.getDependencyCacheDir();
                    }
                });
                conventionMapping.map("sourceCompatibility", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return javaConvention.getSourceCompatibility().toString();
                    }
                });
                conventionMapping.map("targetCompatibility", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return javaConvention.getTargetCompatibility().toString();
                    }
                });
            }
        });
    }

    private void configureJavaDoc(final Project project) {
        project.getTasks().withType(Javadoc.class).allTasks(new Action<Javadoc>() {
            public void execute(Javadoc javadoc) {
                javadoc.getConventionMapping().map("destinationDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new File(convention.getPlugin(JavaPluginConvention.class).getDocsDir(), "javadoc");
                    }
                });
                javadoc.getConventionMapping().map("optionsFile", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new File(project.getBuildDir(), "tmp/javadoc.options");
                    }
                });
                javadoc.getConventionMapping().map("title", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(ReportingBasePluginConvention.class).getApiDocTitle();
                    }
                });
                addDependsOnTaskInOtherProjects(javadoc, true, JAVADOC_TASK_NAME, COMPILE_CONFIGURATION_NAME);
            }
        });
    }

    private void configureArchives(final Project project, final JavaPluginConvention pluginConvention) {
        project.getTasks().withType(Jar.class).allTasks(new Action<Jar>() {
            public void execute(Jar task) {
                task.getConventionMapping().map("manifest", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new GradleManifest(pluginConvention.getManifest().getManifest());
                    }
                });
                task.getMetaInf().from(new Callable() {
                    public Object call() throws Exception {
                        return pluginConvention.getMetaInf();
                    }
                });
            }
        });

        Task checkTask = project.getTasks().add(CHECK_TASK_NAME);
        checkTask.setDescription("Runs all checks.");
    }

    private void configureBuild(Project project) {
        DefaultTask buildTask = project.getTasks().add(BUILD_TASK_NAME, DefaultTask.class);
        buildTask.setDescription("Assembles and tests this project.");
        buildTask.dependsOn(BasePlugin.ASSEMBLE_TASK_NAME);
        buildTask.dependsOn(CHECK_TASK_NAME);
    }

    private void configureBuildNeeded(Project project) {
        DefaultTask buildTask = project.getTasks().add(BUILD_NEEDED_TASK_NAME, DefaultTask.class);
        buildTask.setDescription("Assembles and tests this project and all projects it depends on.");
        buildTask.dependsOn(BUILD_TASK_NAME);
        addDependsOnTaskInOtherProjects(buildTask, true, BUILD_TASK_NAME, TEST_RUNTIME_CONFIGURATION_NAME);
    }

    private void configureBuildDependents(Project project) {
        DefaultTask buildTask = project.getTasks().add(BUILD_DEPENDENTS_TASK_NAME, DefaultTask.class);
        buildTask.setDescription("Assembles and tests this project and all projects that depend on it.");
        buildTask.dependsOn(BUILD_TASK_NAME);
        addDependsOnTaskInOtherProjects(buildTask, false, BUILD_TASK_NAME, TEST_RUNTIME_CONFIGURATION_NAME);
    }

    private void configureTest(final Project project) {
        project.getTasks().withType(AbstractTestTask.class).allTasks(new Action<AbstractTestTask>() {
            public void execute(AbstractTestTask test) {
                test.getConventionMapping().map(GUtil.map("testResultsDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(JavaPluginConvention.class).getTestResultsDir();
                    }
                }, "testReportDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(JavaPluginConvention.class).getTestReportDir();
                    }
                }));
            }
        });
        project.getTasks().withType(Test.class).allTasks(new Action<Test>() {
            public void execute(Test test) {
                test.workingDir(project.getProjectDir());
            }
        });
    }

    void configureConfigurations(final Project project) {
        project.setProperty("status", "integration");
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration compileConfiguration = configurations.add(COMPILE_CONFIGURATION_NAME).setVisible(false)
                .setTransitive(false).
                        setDescription("Classpath for compiling the sources.");
        Configuration runtimeConfiguration = configurations.add(RUNTIME_CONFIGURATION_NAME).setVisible(false)
                .extendsFrom(compileConfiguration).
                        setDescription("Classpath for running the compiled sources.");

        Configuration compileTestsConfiguration = configurations.add(TEST_COMPILE_CONFIGURATION_NAME).setVisible(false)
                .extendsFrom(compileConfiguration).
                        setTransitive(false).setDescription("Classpath for compiling the test sources.");

        configurations.add(TEST_RUNTIME_CONFIGURATION_NAME).setVisible(false).extendsFrom(runtimeConfiguration,
                compileTestsConfiguration).
                setDescription("Classpath for running the test sources.");

        Configuration archivesConfiguration = configurations.add(Dependency.ARCHIVES_CONFIGURATION).
                setDescription("Configuration for the default artifacts.");

        configurations.add(Dependency.DEFAULT_CONFIGURATION).extendsFrom(runtimeConfiguration, archivesConfiguration).
                setDescription("Configuration the default artifacts and its dependencies.");
    }

    /**
     * Adds a dependency on tasks with the specified name in other projects.  The other projects are determined from
     * project lib dependencies using the specified configuration name. These may be projects this project depends on or
     * projects that depend on this project based on the useDependOn argument.
     *
     * @param task Task to add dependencies to
     * @param useDependedOn if true, add tasks from projects this project depends on, otherwise use projects that depend
     * on this one.
     * @param otherProjectTaskName name of task in other projects
     * @param configurationName name of configuration to use to find the other projects
     */
    private void addDependsOnTaskInOtherProjects(final Task task, boolean useDependedOn, String otherProjectTaskName,
                                                 String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(configurationName);
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(useDependedOn, otherProjectTaskName));
    }
}