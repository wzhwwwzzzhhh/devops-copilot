package com.devboss.graph;

/** 节点执行结果：封装输出文本与下一跳节点 ID */
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
