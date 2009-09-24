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

package org.gradle.invocation;

import groovy.lang.Closure;
import org.gradle.StartParameter;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.project.*;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.execution.DefaultTaskExecuter;
import org.gradle.util.GradleVersion;
import org.gradle.util.HelperUtil;
import org.gradle.util.TestClosure;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;

@RunWith(JUnit4.class)
public class DefaultGradleTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final StartParameter parameter = new StartParameter() {{
        setPluginPropertiesFile(new File("plugin.properties"));
    }};
    private final ScriptHandler scriptHandlerMock = context.mock(ScriptHandler.class);
    private final ServiceRegistryFactory serviceRegistryFactoryMock = context.mock(ServiceRegistryFactory.class);
    private final ServiceRegistry serviceRegistryMock = context.mock(ServiceRegistry.class);
    private final StandardOutputRedirector standardOutputRedirectorMock = context.mock(StandardOutputRedirector.class);
    
    private DefaultGradle gradle;

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            one(serviceRegistryFactoryMock).createForBuild(with(any(DefaultGradle.class)));
            will(returnValue(serviceRegistryMock));
            allowing(serviceRegistryMock).get(ScriptHandler.class);
            will(returnValue(scriptHandlerMock));
            allowing(serviceRegistryMock).get(ScriptClassLoaderProvider.class);
            will(returnValue(context.mock(ScriptClassLoaderProvider.class)));
        }});
        gradle = new DefaultGradle(parameter, null, serviceRegistryFactoryMock, standardOutputRedirectorMock,
                                   new DefaultListenerManager());
    }

    @Test
    public void usesGradleVersion() {
        assertThat(gradle.getGradleVersion(), equalTo(new GradleVersion().getVersion()));
    }

    @Test
    public void usesStartParameterForDirLocations() throws IOException {
        parameter.setGradleHomeDir(new File("home"));
        parameter.setGradleUserHomeDir(new File("user"));

        assertThat(gradle.getGradleHomeDir(), equalTo(new File("home").getCanonicalFile()));
        assertThat(gradle.getGradleUserHomeDir(), equalTo(new File("user").getCanonicalFile()));
    }

    @Test
    public void createsADefaultProjectRegistry() {
        assertTrue(gradle.getProjectRegistry().getClass().equals(DefaultProjectRegistry.class));
    }

    @Test
    public void createsATaskGraph() {
        assertTrue(gradle.getTaskGraph().getClass().equals(DefaultTaskExecuter.class));
    }

    @Test
    public void createsAPluginRegistry() {
        assertTrue(gradle.getPluginRegistry().getClass().equals(DefaultPluginRegistry.class));
    }

    @Test
    public void broadcastsProjectEventsToListeners() {
        final ProjectEvaluationListener listener = context.mock(ProjectEvaluationListener.class);
        final Project project = context.mock(Project.class);
        final RuntimeException failure = new RuntimeException();
        context.checking(new Expectations() {{
            one(listener).beforeEvaluate(project);
            one(listener).afterEvaluate(project, failure);
        }});

        gradle.addListener(listener);

        gradle.getProjectEvaluationBroadcaster().beforeEvaluate(project);
        gradle.getProjectEvaluationBroadcaster().afterEvaluate(project, failure);
    }

    @Test
    public void broadcastsBeforeProjectEvaluateEventsToClosures() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Project project = context.mock(Project.class);
        context.checking(new Expectations(){{
            one(closure).call(project);
        }});

        gradle.beforeProject(HelperUtil.toClosure(closure));

        gradle.getProjectEvaluationBroadcaster().beforeEvaluate(project);
    }

    @Test
    public void broadcastsAfterProjectEvaluateEventsToClosures() {
        final TestClosure closure = context.mock(TestClosure.class);
        final Project project = context.mock(Project.class);
        context.checking(new Expectations(){{
            one(closure).call(project);
        }});

        gradle.afterProject(HelperUtil.toClosure(closure));

        gradle.getProjectEvaluationBroadcaster().afterEvaluate(project, null);
    }

    @Test
    public void testScriptClasspath() {
        final TestClosure testClosure = context.mock(TestClosure.class);
        context.checking(new Expectations(){{
            one(testClosure).call(null);
            one(scriptHandlerMock).getRepositories();
        }});

        Closure closure = HelperUtil.toClosure(testClosure);
        gradle.initscript(closure);

        assertThat(closure.getDelegate(), instanceOf(ScriptHandler.class));
        assertThat((ScriptHandler)closure.getDelegate(), equalTo(scriptHandlerMock));
      }

    @Test
    public void captureStdOut() {
        context.checking(new Expectations(){{
            one(standardOutputRedirectorMock).on(LogLevel.DEBUG);
        }});
        gradle.captureStandardOutput(LogLevel.DEBUG);
    }

    @Test
    public void disableStdOutCapture() {
        context.checking(new Expectations(){{
            one(standardOutputRedirectorMock).flush();
            one(standardOutputRedirectorMock).off();
        }});
        gradle.disableStandardOutputCapture();
    }

    @Test
    public void getSettings() {
        final SettingsInternal settingsStub = context.mock(SettingsInternal.class);
        final ScriptSource scriptSourceDummy = context.mock(ScriptSource.class);
        context.checking(new Expectations() {{
            allowing(settingsStub).getSettingsScript();
            will(returnValue(scriptSourceDummy));
        }});
        gradle.getBuildListenerBroadcaster().settingsEvaluated(settingsStub);
        assertThat(gradle.getSettingsSource(), sameInstance(scriptSourceDummy));
    }

    @Test
    public void addInitScriptSource() {
        final ScriptSource initScriptSource1 = context.mock(ScriptSource.class, "initScript1");
        final ScriptSource initScriptSource2 = context.mock(ScriptSource.class, "initScript2");
        assertThat(gradle.getInitScriptSources().isEmpty(), equalTo(true));
        gradle.addInitScriptSource(initScriptSource1);
        assertThat(gradle.getInitScriptSources(), equalTo(WrapUtil.toList(initScriptSource1)));
        gradle.addInitScriptSource(initScriptSource2);
        assertThat(gradle.getInitScriptSources(), equalTo(WrapUtil.toList(initScriptSource1, initScriptSource2)));
    }
    
    @Test
    public void haveScriptsChangedWithUnchangedScripts() {
        prepareScriptSources(false, false, false);
        assertThat(gradle.haveScriptsChanged(), equalTo(false));
    }

    private void prepareScriptSources(final boolean init, final boolean settings, final boolean project) {
        final ScriptSource initScriptSource = context.mock(ScriptSource.class, "init");
        final ScriptSource settingsScriptSource = context.mock(ScriptSource.class, "settings");
        final ScriptSource projectScriptSource = context.mock(ScriptSource.class, "project");
        final ProjectInternal projectStub = context.mock(ProjectInternal.class);
        final IProjectRegistry projectRegistry = context.mock(IProjectRegistry.class);
        final SettingsInternal settingsStub = context.mock(SettingsInternal.class);
        gradle.setProjectRegistry(projectRegistry);
        gradle.getBuildListenerBroadcaster().settingsEvaluated(settingsStub);
        gradle.addInitScriptSource(initScriptSource);
        context.checking(new Expectations() {{
            allowing(settingsStub).getSettingsScript();
            will(returnValue(settingsScriptSource));
            allowing(projectRegistry).getAllProjects();
            will(returnValue(WrapUtil.toSet(projectStub)));
            allowing(projectStub).getBuildScriptSource();
            will(returnValue(projectScriptSource));
            allowing(initScriptSource).hasChanged();
            will(returnValue(init));
            allowing(settingsScriptSource).hasChanged();
            will(returnValue(settings));
            allowing(projectScriptSource).hasChanged();
            will(returnValue(project));
        }});
    }

    @Test
    public void haveScriptsChangedWithChangedInitScript() {
        prepareScriptSources(true, false, false);
        assertThat(gradle.haveScriptsChanged(), equalTo(true));
    }

    @Test
    public void haveScriptsChangedWithChangedSettingsScript() {
        prepareScriptSources(false, true, false);
        assertThat(gradle.haveScriptsChanged(), equalTo(true));
    }

    @Test
    public void haveScriptsChangedWithChangedBuildScript() {
        prepareScriptSources(false, false, true);
        assertThat(gradle.haveScriptsChanged(), equalTo(true));
    }
}
