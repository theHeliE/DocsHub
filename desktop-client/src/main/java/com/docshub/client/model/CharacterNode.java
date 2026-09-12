package com.docshub.client.model;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CharacterNode {
    private final String id; // "userId:clock"
    private final char value;
    private boolean deleted;
    private final String parentId;
    private final Set<CharacterNode> children;

    public CharacterNode(String userId, String clock, char value, String parentId) {
        this.id = userId + ':' + clock;
        this.value = value;
        this.deleted = false;
        this.parentId = parentId;
        this.children = new HashSet<>();
    }

    public String getId() {
        return id;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void addChild(CharacterNode child) {
        children.add(child);
    }

    public Set<CharacterNode> getChildren() {
        return children;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("value", String.valueOf(value));
        map.put("deleted", deleted);
        map.put("parentId", parentId);
        return map;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public char getValue() {
        return value ;
    }

    public long getClock() {
        return Long.parseLong(id.split(":")[1]);
    }

    public String getUserId() {
        return id.split(":")[0];
    }

    public String getParentId() {
        return parentId;
    }
}
