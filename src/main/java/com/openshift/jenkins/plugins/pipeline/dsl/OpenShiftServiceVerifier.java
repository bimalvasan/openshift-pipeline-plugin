package com.openshift.jenkins.plugins.pipeline.dsl;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepMonitor;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftServiceVerifier;

public class OpenShiftServiceVerifier extends OpenShiftBaseStep implements IOpenShiftServiceVerifier {
	
    protected final String svcName;
    protected String retryCount;
    
    
    @DataBoundConstructor public OpenShiftServiceVerifier(String svcName) {
    	this.svcName = svcName;
	}   
    
	public String getSvcName() {
		return svcName;
	}
	
	public String getSvcName(Map<String,String> overrides) {
		return getSvcName();
	}

	public String getRetryCount() {
		return retryCount;
	}
	
	public String getRetryCount(Map<String, String> overrides) {
		String val = getOverride(getRetryCount(), overrides);
		if (val.length() > 0)
			return val;
		return "100";
	}
	
	@DataBoundSetter public void setRetryCount(String retryCount) {
		this.retryCount = retryCount;
	}
	
	@Override
	public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
		return true;
	}

	@Override
	public Action getProjectAction(AbstractProject<?, ?> project) {
		return null;
	}

	@Override
	public Collection<? extends Action> getProjectActions(
			AbstractProject<?, ?> project) {
		return null;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return null;
	}

    private static final Logger LOGGER = Logger.getLogger(OpenShiftServiceVerifier.class.getName());


	@Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(OpenShiftServiceVerifierExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openShiftVerifyService";
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            if (!arguments.containsKey("serviceName"))
            	throw new IllegalArgumentException("need to specify serviceName");
            OpenShiftServiceVerifier step = new OpenShiftServiceVerifier(arguments.get("serviceName").toString());
            
            if (arguments.containsKey("retryCount")) {
            	Object retryCount = arguments.get("retryCount");
            	if (retryCount != null)
            		step.setRetryCount(retryCount.toString());
            }
            
            ParamVerify.updateDSLBaseStep(arguments, step);
            return step;
        }
    }


}