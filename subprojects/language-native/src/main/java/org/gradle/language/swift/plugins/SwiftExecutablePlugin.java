/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.plugins;

import com.google.common.collect.Lists;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;

import java.util.Collections;

/**
 * <p>A plugin that produces a native executable from Swift source.</p>
 *
 * <p>Assumes the source files are located in `src/main/swift`.</p>
 *
 * @since 4.1
 */
public class SwiftExecutablePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        // Add a compile task
        SwiftCompile compile = project.getTasks().create("compileSwift", SwiftCompile.class);

        compile.includes(project.getConfigurations().getByName(SwiftBasePlugin.SWIFT_IMPORT_PATH));
        compile.lib(project.getConfigurations().getByName(CppBasePlugin.NATIVE_LINK));

        ConfigurableFileTree sourceTree = project.fileTree("src/main/swift");
        sourceTree.include("**/*.swift");
        compile.source(sourceTree);

        compile.setCompilerArgs(Lists.newArrayList(
            "-sdk", "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.12.sdk",
            "-emit-executable"));
        compile.setMacros(Collections.<String, String>emptyMap());

        // TODO - should reflect changes to build directory
        compile.setObjectFileDir(project.file("build/main/objs"));

        DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
        compile.setTargetPlatform(currentPlatform);

        // TODO - make this lazy
        NativeToolChain toolChain = ((ProjectInternal) project).getModelRegistry().realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
        compile.setToolChain(toolChain);

        String exeName = ((NativeToolChainInternal) toolChain).select(currentPlatform).getExecutableName("build/exe/" + project.getName());
        compile.setOutputFile(project.file(exeName));

        // Add an install task
        final InstallExecutable install = project.getTasks().create("installMain", InstallExecutable.class);
        install.setPlatform(currentPlatform);
        install.setToolChain(toolChain);
        // TODO - should reflect changes to build directory
        // TODO - need to set basename
        install.setDestinationDir(project.file("build/install/" + project.getName()));
        // TODO - should reflect changes to task output
        install.setExecutable(compile.getOutputFile());
        // TODO - infer this
        install.dependsOn(compile);
        // TODO - and this
        install.onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return install.getExecutable().exists();
            }
        });
        install.lib(project.getConfigurations().getByName(CppBasePlugin.NATIVE_RUNTIME));

        project.getTasks().getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(install);
    }
}