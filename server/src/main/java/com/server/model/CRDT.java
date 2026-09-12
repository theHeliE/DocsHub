package com.server.model;

import java.util.*;

public class CRDT {
    private final CharacterNode root;
    private final Map<String, CharacterNode> nodeMap; // nodeId -> node

    /**
     * Creates a new CRDT instance
     * Initializes the root node and the node map.
     */
    public CRDT() {
        this.root = new CharacterNode("0", "0", '0', null);
        this.nodeMap = new HashMap<>();
        this.nodeMap.put(root.getId(), root);
    }

    /**
     * Insert a character
     * @param userId the id of the user making the insertion
     * @param clock the timestamp representing the real clock time
     * @param value the character to insert
     * @param parentId the ID of the parent node
     * @return true if the insertion was successful, false otherwise
     */
    public boolean insertCharacter(String userId, String clock, String value, String parentId) {
        CharacterNode parentNode = nodeMap.get(parentId);


        if (userId == null || clock == null || value == null || parentNode == null) {
            return false;
        }


        if (userId.isEmpty() || clock.isEmpty()) {
            return false;
        }

        long baseTime = Long.valueOf(clock);

        for (int i =0; i< value.length();i++){
            String c = String.valueOf(baseTime + i);
            CharacterNode newNode = new CharacterNode(userId, c, value.charAt(i), parentNode.getId());
            parentNode.addChild(newNode);
            nodeMap.put(newNode.getId(), newNode);
            parentNode = newNode;
        }


        return true;
    }

    /**
     * Mark a character as deleted by its ID
     * @param nodeId the ID of the node to delete
     * @return true if the node was found and deleted, false otherwise
     */
    public boolean deleteCharacterById(String nodeId) {
        CharacterNode nodeToDelete = nodeMap.get(nodeId);

        if (nodeToDelete == null || nodeToDelete == root) {
            return false;
        }

        nodeToDelete.setDeleted(true);
        return true;
    }

    /**
     * Import content into the CRDT
     * @param userId the id of the user the imported characters are attributed to
     * @param content the text content to import
     */
    public void importContent(String userId, String content) {
        long baseTime = System.currentTimeMillis();
        CharacterNode currentNode = root;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            String clock = String.valueOf(baseTime + i);

            CharacterNode newNode = new CharacterNode(userId, clock, c, currentNode.getId());
            currentNode.addChild(newNode);
            nodeMap.put(newNode.getId(), newNode);

            currentNode = newNode;
        }
    }

    public List<Map<String, Object>> serialize() {
        List<Map<String, Object>> result = new ArrayList<>();
        Queue<CharacterNode> queue = new LinkedList<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            CharacterNode node = queue.poll();
            result.add(node.serialize());
            queue.addAll(node.getChildren());
        }

        return result;
    }

    public Map<String, CharacterNode> getNodes() {
        return nodeMap;
    }
}