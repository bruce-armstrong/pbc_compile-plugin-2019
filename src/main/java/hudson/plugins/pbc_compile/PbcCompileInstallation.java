/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014, Kyle Sweeney, Gregory Boissinot and other contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.pbc_compile;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * @author Gregory Boissinot
 */
public final class PbcCompileInstallation extends ToolInstallation implements NodeSpecific<PbcCompileInstallation>, EnvironmentSpecific<PbcCompileInstallation> {

    /**
	 * 
	 */
	private static final long serialVersionUID = -9032399700618715657L;
	private final String defaultArgs;

    @DataBoundConstructor
    public PbcCompileInstallation(String name, String home, String defaultArgs) {
        super(name, home, null);
        this.defaultArgs = Util.fixEmpty(defaultArgs);
    }

    @Override
    public PbcCompileInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new PbcCompileInstallation(getName(), translateFor(node, log), getDefaultArgs());
    }

    @Override
    public PbcCompileInstallation forEnvironment(EnvVars environment) {
        return new PbcCompileInstallation(getName(), environment.expand(getHome()), getDefaultArgs());
    }

    public String getDefaultArgs() {
        return this.defaultArgs;
    }

    @Extension @Symbol("pbc-compile-2019")
    public static class DescriptorImpl extends ToolDescriptor<PbcCompileInstallation> {

        @Override
        public String getDisplayName() {
            return "PbcCompile 2019";
        }

        @Override
        public PbcCompileInstallation[] getInstallations() {
            return getDescriptor().getInstallations();
        }

        @Override
        public void setInstallations(PbcCompileInstallation... installations) {
            getDescriptor().setInstallations(installations);
        }
        
        private PbcCompileBuilder.DescriptorImpl getDescriptor() {
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins != null && jenkins.getDescriptorByType(PbcCompileBuilder.DescriptorImpl.class) != null) {
                return jenkins.getDescriptorByType(PbcCompileBuilder.DescriptorImpl.class);
            } else {
                // To stick with current behavior and meet findbugs requirements
                throw new NullPointerException(jenkins == null ? "Jenkins instance is null" : "PbcCompileBuilder.DescriptorImpl is null");
            }
        }

    }

}
