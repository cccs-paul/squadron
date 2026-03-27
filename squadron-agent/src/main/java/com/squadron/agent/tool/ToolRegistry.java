package com.squadron.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that collects all {@link AgentTool} beans and provides
 * lookup by tool name.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> toolList) {
        this.tools = new HashMap<>();
        for (AgentTool tool : toolList) {
            tools.put(tool.getName(), tool);
            log.info("Registered agent tool: {}", tool.getName());
        }
    }

    /**
     * Returns the tool for the given name.
     *
     * @param name the tool name
     * @return the agent tool
     * @throws IllegalArgumentException if no tool is registered with that name
     */
    public AgentTool getTool(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("No tool registered with name: " + name);
        }
        return tool;
    }

    /**
     * Returns definitions for all registered tools.
     */
    public List<ToolDefinition> getAllToolDefinitions() {
        return tools.values().stream()
                .map(AgentTool::getDefinition)
                .toList();
    }

    /**
     * Returns the names of all registered tools.
     */
    public List<String> getToolNames() {
        return List.copyOf(tools.keySet());
    }

    /**
     * Returns {@code true} if a tool with the given name is registered.
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
}
