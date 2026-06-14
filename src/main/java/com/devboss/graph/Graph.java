package com.devboss.graph;

import java.util.LinkedHashMap;
import java.util.Map;

/** 图编排引擎：按 DAG 注册并调度 Node 节点顺序执行 */
public class Graph {

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private String startNodeId;

    public void addNode(String id, Node node) {
        nodes.put(id, node);
    }

    public void setStartNode(String id) {
        this.startNodeId = id;
    }

    public String getStartNodeId() {
        return startNodeId;
    }

    public Node getNode(String id) {
        return nodes.get(id);
    }

    public boolean hasNode(String id) {
        return nodes.containsKey(id);
    }

    public static boolean isEnd(String nodeId) {
        return nodeId == null || nodeId.equals("END") || nodeId.equals("FAILED");
    }
}
