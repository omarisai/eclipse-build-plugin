package org.jenkinsci.plugins.eclipse_builder;

import hudson.AbortException;
import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Plugin;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.lang.StringBuilder;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.jenkinsci.plugins.eclipse_builder.util.StringUtil;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * @author Yasuyuki Saito, Omar Isai Pinales Ayala
 */
public class EclipseBuilder extends Builder implements SimpleBuildStep {

    private final String eclipseInstallationName;
    @CheckForNull
    private String workspacePath;
    private String projectsPaths;
    private String buildList;
    private boolean cleanBuild = DescriptorImpl.DEFAULT_CLEAN_BUILD;
    private boolean failBuild = DescriptorImpl.DEFAULT_FAIL_BUILD;

    /**
     *
     * @param eclipseInstallationName
     * @param workspacePath
     * @param projectsPaths
     * @param buildList
     * @param cleanBuild
     * @param failBuild
     */
    
    @Deprecated
    public EclipseBuilder(String eclipseInstallationName, String workspacePath, String projectsPaths, String buildList,
            boolean cleanBuild, boolean failBuild) {
        this.eclipseInstallationName = eclipseInstallationName;
        this.workspacePath = workspacePath;
        this.projectsPaths = projectsPaths;
        this.buildList = buildList;
        this.cleanBuild = cleanBuild;
        this.failBuild = failBuild;
    }

    @DataBoundConstructor
    public EclipseBuilder(String eclipseInstallationName) {
        this.eclipseInstallationName = eclipseInstallationName;
    }

    public String getEclipseInstallationName() {
        return eclipseInstallationName;
    }

    @CheckForNull
    public String getWorkspacePath() {
        return workspacePath;
    }

    @DataBoundSetter
    public void setWorkspacePath(String args) {
        this.workspacePath = Util.fixEmptyAndTrim(args);
    }
    
    @CheckForNull
    public String getProjectsPaths() {
        return projectsPaths;
    }

    @DataBoundSetter
    public void setProjectsPaths(String args) {
        this.projectsPaths = Util.fixEmptyAndTrim(args);
    }
    
    @CheckForNull
    public String getBuildList() {
        return buildList;
    }

    @DataBoundSetter
    public void setBuildList(String args) {
        this.buildList = Util.fixEmptyAndTrim(args);
    }

    public boolean getCleanBuild() {
        return cleanBuild;
    }

    @DataBoundSetter
    public void setCleanBuild(boolean f) {
        this.cleanBuild = f;
    }
    
    public boolean getFailBuild() {
        return failBuild;
    }

    @DataBoundSetter
    public void setFailBuild(boolean f) {
        this.failBuild = f;
    }

    public EclipseInstallation getEclipseInstallation() {
        if (eclipseInstallationName == null) {
            return null;
        }
        for (EclipseInstallation i : DESCRIPTOR.getInstallations()) {
            if (eclipseInstallationName.equals(i.getName())) {
                return i;
            }
        }
        return null;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener tl) throws InterruptedException, IOException {
        //public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        ArrayList<String> args = new ArrayList<String>();
        EnvVars env = null;
        EclipseInstallation installation = getEclipseInstallation();
        if (installation == null) {
            throw new AbortException("Eclipse Installation not found.");
        }
        installation = installation.forNode(EclipseInstallation.workspaceToNode(workspace), tl);

        if (run instanceof AbstractBuild) {
            env = run.getEnvironment(tl);
            installation = installation.forEnvironment(env);
        }

        // eclipsec path.
        String eclipsecPath = getEclipsecPath(installation, launcher, tl);
        if (StringUtil.isNullOrSpace(eclipsecPath)) {
            throw new AbortException("Eclipse path is blank.");
        }
        args.add(eclipsecPath);

        // Default Arguments
        if (!StringUtil.isNullOrSpace(installation.getDefaultArgs())) {
            args.addAll(getArguments(run, workspace, tl, installation.getDefaultArgs()));
        }

        // Get Command Line String
        StringBuilder builder = new StringBuilder();
        
        //Add workspace
        if (!StringUtil.isNullOrSpace(workspacePath)) {
            builder.append("-data " + workspacePath + "\n");
        }
        
        //Add projects
        if (!StringUtil.isNullOrSpace(projectsPaths)) {
            String projects[] = projectsPaths.split("[\\r\\n]+");
            
            for (int project = 0; project < projects.length; project++) {
                builder.append("-import " + workspacePath + "\\" + projects[project] + "\n");
            }
        }
        
        //Load clean option
        if (cleanBuild) {
            builder.append("-cleanBuild all\n");
        }
        
        //Load projects to build
        if (!StringUtil.isNullOrSpace(buildList)) {
            String projects[] = buildList.split("[\\r\\n]+");
            for (int project = 0; project < projects.length; project++) {
                builder.append("-build " + projects[project] + "\n");
            }
        }
        else {
            builder.append("-build all\n");
        }
        
        //Load all the arguments
        if (!StringUtil.isNullOrSpace(builder.toString())) {
            args.addAll(getArguments(run, workspace, tl, builder.toString()));
        }

        // eclipse run.
        exec(args, run, launcher, tl, env, workspace);
    }

    /**
     *
     * @param installation
     * @param launcher
     * @param tl
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private String getEclipsecPath(EclipseInstallation installation, Launcher launcher, TaskListener tl) throws InterruptedException, IOException {
        String pathToEclipse = installation.getHome() + "\\eclipsec.exe";
        FilePath exec = new FilePath(launcher.getChannel(), pathToEclipse);

        try {
            if (!exec.exists()) {
                tl.fatalError(pathToEclipse + " doesn't exist");
                return null;
            }
        } catch (IOException e) {
            tl.fatalError("Failed checking for existence of " + pathToEclipse);
            return null;
        }

        tl.getLogger().println("Path To eclipse: " + pathToEclipse);
        return StringUtil.appendQuote(pathToEclipse);
    }

    /**
     *
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private List<String> getArguments(Run<?, ?> run, hudson.FilePath workspace, TaskListener tl, String values) throws InterruptedException, IOException {
        ArrayList<String> args = new ArrayList<String>();
        StringTokenizer valuesToknzr = new StringTokenizer(values, " \t\r\n");

        while (valuesToknzr.hasMoreTokens()) {
            String value = valuesToknzr.nextToken();
            if (run instanceof AbstractBuild) {
                Plugin p = Jenkins.getInstance().getPlugin("token-macro");
                if (null != p && p.getWrapper().isActive()) {
                    try {
                        value = TokenMacro.expandAll(run, workspace, tl, value);
                    } catch (MacroEvaluationException ex) {
                        tl.error("TokenMacro was unable to evaluate: " + value + " " + ex.getMessage());
                    }
                } else {
                    EnvVars envVars = run.getEnvironment(tl);
                    value = envVars.expand(value);
                }
            }
            if (!StringUtil.isNullOrSpace(value)) {
                args.add(value);
            }
        }
        return args;
    }

    /**
     *
     * @param args
     * @param build
     * @param launcher
     * @param tl
     * @param env
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private void exec(List<String> args, Run<?, ?> run, Launcher launcher, TaskListener tl, EnvVars env, FilePath workspace) throws InterruptedException, IOException {
        ArgumentListBuilder cmdExecArgs = new ArgumentListBuilder();
        FilePath tmpDir = null;
        //FilePath pwd = run.getWorkspace();

        if (!launcher.isUnix()) {
            tmpDir = workspace.createTextTempFile("exe_runner_", ".bat", StringUtil.concatString(args), false);
            cmdExecArgs.add("cmd.exe", "/C", tmpDir.getRemote(), "&&", "exit", "%ERRORLEVEL%");
        } else {
            for (String arg : args) {
                cmdExecArgs.add(arg);
            }
        }

        tl.getLogger().println("Executing : " + cmdExecArgs.toStringWithQuote());

        try {
            int r;
            if (run instanceof AbstractBuild) {
                r = launcher.launch().cmds(cmdExecArgs).envs(env).stdout(tl).pwd(workspace).join();
            }
            else{
                r = launcher.launch().cmds(cmdExecArgs).stdout(tl).pwd(workspace).join();   //env vars arent available in pipeline
            }

            if (failBuild) {
                if (r != 0) {
                    throw new AbortException("Exited with code: " + r);
                }
            } else {
                if (r != 0) {
                    tl.getLogger().println("Exe exited with code: " + r);
                    run.setResult(Result.UNSTABLE);
                }
            }
        } finally {
            if (tmpDir != null) {
                tmpDir.delete();
            }
        }
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Descriptor should be singleton.
     */
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * @author Yasuyuki Saito
     */
    @Symbol("runexe")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public static final boolean DEFAULT_CLEAN_BUILD = false;
        public static final boolean DEFAULT_FAIL_BUILD = true;
        @CopyOnWrite
        private volatile EclipseInstallation[] installations = new EclipseInstallation[0];

        public DescriptorImpl() {
            super(EclipseBuilder.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.EclipseBuilder_DisplayName();
        }

        public EclipseInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(EclipseInstallation... installations) {
            this.installations = installations;
            save();
        }

        /**
         * Obtains the {@link EclipseInstallation.DescriptorImpl} instance.
         */
        public EclipseInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(EclipseInstallation.DescriptorImpl.class);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
