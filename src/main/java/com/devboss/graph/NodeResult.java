package com.devboss.graph;

public class NodeResult {

    private final String output;
    private final String nextNodeId;

    public NodeResult(String output, String nextNodeId) {
        this.output = output;
        this.nextNodeId = nextNodeId;
    }

    public String output() {
        return output;
    }

    public String nextNodeId() {
        return nextNodeId;
    }
}
