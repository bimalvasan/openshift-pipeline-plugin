package com.openshift.jenkins.plugins.pipeline;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.internal.restclient.model.ReplicationController;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

import javax.servlet.ServletException;

import java.io.IOException;

public class OpenShiftScaler extends OpenShiftBaseStep {

	protected final static String DISPLAY_NAME = "Scale OpenShift Deployment";
	
    protected String depCfg = "frontend";
    protected String replicaCount = "0";
    protected String verifyReplicaCount = "false";
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftScaler(String apiURL, String depCfg, String namespace, String replicaCount, String authToken, String verbose, String verifyReplicaCount) {
        this.apiURL = apiURL;
        this.depCfg = depCfg;
        this.namespace = namespace;
        this.replicaCount = replicaCount;
        this.authToken = authToken;
        this.verbose = verbose;
        this.verifyReplicaCount = verifyReplicaCount;
}

	public String getDepCfg() {
		return depCfg;
	}

	public String getReplicaCount() {
		return replicaCount;
	}
	
	public String getVerifyReplicaCount() {
		return verifyReplicaCount;
	}
	
	public boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env) {
		boolean chatty = Boolean.parseBoolean(verbose);
    	boolean checkCount = Boolean.parseBoolean(verifyReplicaCount);
    	listener.getLogger().println(String.format("\n\nStarting the \"%s\" step with deployment config \"%s\" from the project \"%s\".", DISPLAY_NAME, depCfg, namespace));
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME);
    	
    	if (client != null) {
        	IReplicationController rc = null;
        	IDeploymentConfig dc = null;
        	long currTime = System.currentTimeMillis();
        	// in testing with the jenkins-ci sample, the initial deploy after
        	// a build is kinda slow ... gotta wait more than one minute
        	if (chatty)
        		listener.getLogger().println("\nOpenShiftScaler wait " + getDescriptor().getWait());
        	
        	listener.getLogger().println(String.format("  Scaling to \"%s\" replicas%s...", replicaCount, checkCount ? " and verifying the replica count is reached." : ""));        	
        	
        	// do the oc scale ... may need to retry        	
        	boolean scaleDone = false;
        	while (System.currentTimeMillis() < (currTime + getDescriptor().getWait())) {
        		dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, depCfg, namespace);
        		if (dc == null) {
			    	listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully; no deployment config named \"%s\" found.", DISPLAY_NAME, depCfg));
	    			return false;
        		}
        		
        		if (dc.getLatestVersionNumber() > 0)
        			rc = this.getLatestReplicationController(dc, client);
            	if (rc == null) {
            		//TODO if not found, and we are scaling down to zero, don't consider an error - this may be safety
            		// measure to scale down if exits ... perhaps we make this behavior configurable over time, but for now.
            		// we refrain from adding yet 1 more config option
            		if (replicaCount.equals("0")) {
        		    	listener.getLogger().println(String.format("\n\nExiting \"%s\" successfully; no deployments for \"%s\" were found, so a replica count of \"0\" already exists.", DISPLAY_NAME, depCfg));
            			return true;
            		}
            	} else {
            		int count = Integer.decode(replicaCount);
    	        	rc.setDesiredReplicaCount(count);
    	        	if (chatty)
    	        		listener.getLogger().println("\nOpenShiftScaler setting desired replica count of " + replicaCount + " on " + rc.getName());
    	        	try {
    	        		rc = client.update(rc);
    	        		if (chatty)
    	        			listener.getLogger().println("\nOpenShiftScaler rc returned from update current replica count " + rc.getCurrentReplicaCount() + " desired count " + rc.getDesiredReplicaCount());
						scaleDone = this.isReplicationControllerScaledAppropriately(rc, checkCount, count);
    	        	} catch (Throwable t) {
    	        		if (chatty)
    	        			t.printStackTrace(listener.getLogger());
    	        	}
            	}
            	
				
				if (scaleDone) {
					break;
				} else {
					if (chatty) listener.getLogger().println("\nOpenShiftScaler will wait 10 seconds, then try to scale again");
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
					}
				}
	    	}
        	
        	if (!scaleDone) {
        		if (!checkCount) {
        	    	listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully; the call to \"%s\" failed.", DISPLAY_NAME, apiURL));        			
        		} else {
        	    	listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully; the deployment \"%s\" did not reach \"%s\" replica(s) in time.", DISPLAY_NAME, rc.getName(), replicaCount));
        		}
        		return false;
        	}
        	
	    	listener.getLogger().println(String.format("\n\nExiting \"%s\" successfully%s.", DISPLAY_NAME, checkCount ? String.format(", where the deployment \"%s\" reached \"%s\" replicas", rc.getName(), replicaCount) : ""));
        	return true;
        	        	
    	} else {
	    	listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully; a client connection to \"%s\" could not be obtained.", DISPLAY_NAME, apiURL));
    		return false;
    	}
	}

    
    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link OpenShiftScaler}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	private long wait = 180000;
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the various fields.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckApiURL(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckApiURL(value);
        }

        public FormValidation doCheckDepCfg(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckDepCfg(value);
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckNamespace(value);
        }
        
        
        public FormValidation doCheckReplicaCount(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckReplicaCountRequired(value);
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return DISPLAY_NAME;
        }
        
        public long getWait() {
        	return wait;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
        	wait = formData.getLong("wait");
            save();
            return super.configure(req,formData);
        }

    }

}

