package com.docshub.client.model;

import java.util.*;

public class CRDT {
    private final CharacterNode root;
    private final Map<String, CharacterNode> nodeMap; // nodeId -> node
    private ArrayList <String> nodeIDs = new ArrayList<>();

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

    /**
     * Creates a CRDT instance from serialized data
     * @param serializedData the serialized CRDT data from the serialize() method
     * @return a new CRDT instance with the deserialized data
     */
    public static CRDT fromSerialized(List<Map<String, Object>> serializedData) {
        if (serializedData == null || serializedData.isEmpty()) {
            return new CRDT();
        }

        CRDT crdt = new CRDT();

        // First pass: create all nodes
        for (Map<String, Object> nodeData : serializedData) {
            // Skip the root node which is already created
            if ("0:0".equals(nodeData.get("id"))) {
                continue;
            }

            String id = (String) nodeData.get("id");
            String value = (String) nodeData.get("value");
            boolean deleted = nodeData.get("deleted") != null ? (boolean) nodeData.get("deleted") : false;
            String parentId = (String) nodeData.get("parentId");

            // Extract userId and clock from id (format: "userId:clock")
            String[] idParts = id.split(":");
            if (idParts.length != 2) {
                continue; // Invalid ID format
            }
            String userId = idParts[0];
            String clock = idParts[1];

            // Create and add the character
            if (value != null && value.length() > 0) {
                crdt.insertCharacter(userId, clock, value, parentId);

                // If the node was deleted, mark it as deleted
                if (deleted) {
                    crdt.deleteCharacterById(id);
                }
            }
        }

        return crdt;
    }


    /**
     * Insert a character at a specific position in the document
     * @param userId the user making the insertion
     * @param position the position (index) to insert at
     * @param value the character to insert
     * @param clock the timestamp representing the real clock time
     * @return the ID of the newly inserted node
     */
    public String[] insertCharacterAt(String userId, int position, String value, String clock) {
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative");
        }

        List<CharacterNode> nodes = getInOrderTraversal();

        // The character before the insertion position should be the parent
        CharacterNode parentNode;
        if (position == 0) {
            // If inserting at the beginning, use root as parent
            parentNode = root;
        } else if (position <= nodes.size()) {
            // Get the node BEFORE the insertion position as parent
            parentNode = nodes.get(position - 1);
        } else {
            // If position is beyond the end, use the last node as parent
            parentNode = nodes.get(nodes.size() - 1);
        }

        // Create new node with the determined parent
        long baseTime = Long.valueOf(clock);
        String[] nodeIds = new String[value.length()];

        for (int i =0; i< value.length();i++){
            String c = String.valueOf(baseTime + i);
            CharacterNode newNode = new CharacterNode(userId, c, value.charAt(i), parentNode.getId());
            nodeIds[i] = newNode.getId();
            parentNode.addChild(newNode);
            nodeMap.put(newNode.getId(), newNode);
            parentNode = newNode;
        }
        return nodeIds;
    }


    /**
     * Delete a character at a specific position in the document
     * @param position the position (index) of the character to delete
     * @return the ID of the deleted node
     */
    public String deleteCharacter(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative");
        }

        List<CharacterNode> inOrderNodes = getInOrderTraversal();
        if (position >= inOrderNodes.size()) {
            throw new IndexOutOfBoundsException("Position " + position + " is out of bounds");
        }

        CharacterNode nodeToDelete = inOrderNodes.get(position);
        nodeToDelete.setDeleted(true);

        return nodeToDelete.getId();
    }


    /**
     * Get the current text representation of the document
     * @return the document text
     */
    public String getText() {
        StringBuilder text = new StringBuilder();
        List<CharacterNode> nodes = getInOrderTraversal();

        for (CharacterNode node : nodes) {
            text.append(node.getValue());
            nodeIDs.add(node.getId());
        }

        return text.toString();
    }

    /**
     * Get a list of all visible nodes in the document in order
     * @return a list of nodes in order
     */
    public List<CharacterNode> getInOrderTraversal() {
        List<CharacterNode> result = new ArrayList<>();
        inOrderTraversal(root, result);

        // Debug logging

        return result;
    }


    /**
     * Recursively traverse the tree in order and collect all visible nodes
     * @param node the current node
     * @param result the list to populate with nodes
     */
    private void inOrderTraversal(CharacterNode node, List<CharacterNode> result) {
        if (node != root && !node.isDeleted()) {
            result.add(node);
        }

        // Get children and sort them appropriately for document order
        List<CharacterNode> sortedChildren = new ArrayList<>(node.getChildren());
        sortChildren(sortedChildren);

        for (CharacterNode child : sortedChildren) {
            inOrderTraversal(child, result);
        }
    }


    /**
     * Sort children by descending clock (timestamp), then by ascending user ID
     * @param children the list of children to sort
     */
    private void sortChildren(List<CharacterNode> children) {
        children.sort((node1, node2) -> {
            // First compare clocks in descending order
            int clockCompare = Long.compare(node2.getClock(), node1.getClock());

            // If clocks are equal, compare user IDs
            if (clockCompare == 0) {
                return node1.getUserId().compareTo(node2.getUserId());
            }

            return clockCompare;
        });
    }

    /**
     * Find the path to a specific position in the document
     * @param position the position to find
     * @return the list of nodes representing the path
     */
    private List<CharacterNode> getPathToPosition(int position) {
        if (position <= 0) {
            return Collections.emptyList(); // Return empty path for root
        }

        List<CharacterNode> nodes = getInOrderTraversal();
        if (position > nodes.size()) {
            position = nodes.size();
        }

        // Get the node at the position
        CharacterNode targetNode = position == nodes.size() ?
                nodes.get(position - 1) :
                nodes.get(position);

        // Build the path from the node to the root
        List<CharacterNode> path = new ArrayList<>();
        while (targetNode != root) {
            path.add(targetNode);
            targetNode = nodeMap.get(targetNode.getParentId());
        }

        // Reverse the path to get it from root to the target
        Collections.reverse(path);
        return path;
    }

    public ArrayList<String> getNodeIDs() {
        return nodeIDs;
    }

    public Map<String, CharacterNode> getNodeMap() {
        return nodeMap;
    }
}
