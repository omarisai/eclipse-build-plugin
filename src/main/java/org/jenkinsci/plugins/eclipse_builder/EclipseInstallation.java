package org.jenkinsci.plugins.eclipse_builder;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
* @author Yasuyuki Saito
*/
public final class EclipseInstallation extends ToolInstallation implements NodeSpecific<EclipseInstallation>, EnvironmentSpecific<EclipseInstallation> {

    /** */
    private transient String pathToEclipse;

    private final String defaultArgs;

    @DataBoundConstructor
    public EclipseInstallation(String name, String home, String defaultArgs) {
        super(name, home, null);
        this.defaultArgs = Util.fixEmptyAndTrim(defaultArgs);
    }

    @Override
    public EclipseInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new EclipseInstallation(getName(), translateFor(node, log), this.defaultArgs);
    }

    @Override
    public ExeInstallation forEnvironment(EnvVars environment) {
        return new EclipseInstallation(getName(), environment.expand(getHome()), this.defaultArgs);
    }

    @Override
    protected Object readResolve() {
        if (this.pathToExe != null) {
            return new EclipseInstallation(this.getName(), this.pathToExe, this.defaultArgs);
        }
        return this;
    }

    public String getDefaultArgs() {
        return this.defaultArgs;
    }

    /**
     * @author ndeloof via git plugin
     */
    public static Node workspaceToNode(FilePath workspace) {
        Jenkins j = Jenkins.getActiveInstance();
        if (workspace != null && workspace.isRemote()) {
            for (Computer c : j.getComputers()) {
                if (c.getChannel() == workspace.getChannel()) {
                    Node n = c.getNode();
                    if (n != null) {
                        return n;
                    }
                }
            }
        }
        return j;
    }
    
    /**
     * @author Yasuyuki Saito
     */
    @Extension
    public static class DescriptorImpl extends ToolDescriptor<EclipseInstallation> {

        @Override
        public String getDisplayName() {
            return Messages.EclipseInstallation_DisplayName();
        }

        @Override
        public EclipseInstallation[] getInstallations() {
            return Jenkins.getInstance().getDescriptorByType(ExeBuilder.DescriptorImpl.class).getInstallations();
        }

        @Override
        public void setInstallations(EclipseInstallation... installations) {
            Jenkins.getInstance().getDescriptorByType(EclipseBuilder.DescriptorImpl.class).setInstallations(installations);
        }

    }
}
