/*
THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
*/

package com.xebialabs.xlrelease.plugin.ansible;

import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.overthere.*;
import com.xebialabs.overthere.util.CapturingOverthereExecutionOutputHandler;
import com.xebialabs.overthere.util.OverthereUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import com.google.common.base.Strings;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.xebialabs.overthere.ConnectionOptions.*;
import static com.xebialabs.overthere.OperatingSystemFamily.UNIX;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.WINRM_TIMEMOUT;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.CONNECTION_TYPE;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.SUDO_USERNAME;
import static com.xebialabs.overthere.ssh.SshConnectionType.SUDO;
import static com.xebialabs.overthere.util.CapturingOverthereExecutionOutputHandler.capturingHandler;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by rbroker on 8/17/16.
 */
public class remoteAnsible {
    private static final String SCRIPT_NAME = "uploaded-script";

    private final ConnectionOptions options = new ConnectionOptions();
    private final String protocol;
    private final String remotePath;
    private final String script;
    private final String extension;

    private final CapturingOverthereExecutionOutputHandler stdout = capturingHandler();
    private final CapturingOverthereExecutionOutputHandler stderr = capturingHandler();

    public remoteAnsible( ConfigurationItem hostCI, String script) {
        this.protocol = hostCI.getProperty("protocol");
        copyPropertiesToConnectionOptions(options, hostCI);

        this.script = script;
        this.extension = options.get(OPERATING_SYSTEM, UNIX).getScriptExtension();
        this.remotePath = hostCI.getProperty("remotePath");
    }

    protected void copyPropertiesToConnectionOptions(ConnectionOptions options, ConfigurationItem ci) {
        // support legacy properties
        if(ci.hasProperty("sudo") && (Boolean) (ci.getProperty("sudo"))) {
            ci.setProperty(CONNECTION_TYPE, SUDO);
            ci.setProperty(SUDO_USERNAME, "root");
        }

        // copy all CI properties to connection properties
        for (PropertyDescriptor pd : ci.getType().getDescriptor().getPropertyDescriptors()) {
            if (!pd.getCategory().equals("output")) {
                Object value = pd.get(ci);
                setConnectionOption(options, pd.getName(), value);
            }
        }
    }

    private void setConnectionOption(ConnectionOptions options, String key, Object value) {
        if (key.equals("script") || key.equals("remotePath") || key.equals("scriptLocation")) {
            return;
        }

        if (value == null || value.toString().isEmpty()) {
            return;
        }

        // support legacy properties
        if(key.equals("temporaryDirectoryPath")) {
            key = TEMPORARY_DIRECTORY_PATH;
        } else if(key.equals("timeout")) {
            key = WINRM_TIMEMOUT;
        }

        if (value instanceof Integer && ((Integer) value).intValue() == 0) {
            logger.debug("Activating workaround for DEPLOYITPB-4775: Integer with value of 0 not passed to Overthere.");
            return;
        }

        if (key.equals(JUMPSTATION)) {
            ConfigurationItem item = (ConfigurationItem) value;

            ConnectionOptions jumpstationOptions = new ConnectionOptions();
            copyPropertiesToConnectionOptions(jumpstationOptions, item);
            options.set(key, jumpstationOptions);
        } else {
            options.set(key, value);
        }
    }

    public int execute() {
        try (OverthereConnection connection = Overthere.getConnection(protocol, options)) {
            if(!isNullOrEmpty(remotePath)) {
                connection.setWorkingDirectory(connection.getFile(remotePath));
            }

            // Upload and execute the script
            OverthereFile targetFile = connection.getTempFile(SCRIPT_NAME, extension);
            OverthereUtils.write(script.getBytes(UTF_8), targetFile);
            targetFile.setExecutable(true);

            CmdLine scriptCommand = CmdLine.build(targetFile.getPath());

            return connection.execute(stdout, stderr, scriptCommand);
        } catch (Exception e) {
            StringWriter stacktrace = new StringWriter();
            PrintWriter writer = new PrintWriter(stacktrace, true);
            e.printStackTrace(writer);
            stderr.handleLine(stacktrace.toString());

            return 1;
        }
    }

    public String getStdout() {
        return stdout.getOutput();
    }

    public List<String> getStdoutLines() {
        return stdout.getOutputLines();
    }

    public String getStderr() {
        return stderr.getOutput();
    }

    public List<String> getStderrLines() {
        return stderr.getOutputLines();
    }

    private static Logger logger = LoggerFactory.getLogger(remoteAnsible.class);
}
