package com.squadron.workspace.provider;

import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.WorkspaceSpec;

public interface WorkspaceProvider {

    String getProviderType();

    String createContainer(WorkspaceSpec spec);

    void destroyContainer(String containerId);

    ExecResult exec(String containerId, String[] command);

    String getContainerStatus(String containerId);

    void copyToContainer(String containerId, byte[] content, String containerPath);

    byte[] copyFromContainer(String containerId, String containerPath);
}
