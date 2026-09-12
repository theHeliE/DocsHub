package com.server.dto;

/**
 * Data Transfer Object for CRDT operations
 */
public record CrdtOperation(
    String type,     // "insert" or "delete" or "undoDelete"
    String userId,   // ID of the user performing the operation
    String clock,    // Logical clock timestamp
    String[] nodeId,   // Target nodes (delete, undoDelete) or the nodes an insert created
    String parentId, // ID of the parent node (for insert operations)
    String value  // Character value (for insert operations and paste operations)
) {}
