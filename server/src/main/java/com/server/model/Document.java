package com.server.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Document {
    // Participant colours are drawn as text on white, so they must meet WCAG AA (4.5:1)
    private static final double MIN_CONTRAST_ON_WHITE = 4.5;
    private static final double GOLDEN_ANGLE = 137.508;
    private static final double SATURATION = 0.65;
    private static final double START_LIGHTNESS = 0.50;

    private final String id;
    private final String name;
    private final CRDT crdt;
    private final String editorCode;
    private final String viewerCode;
    private Integer usersCount; // For userId generation
    private final Set<User> users;
    private final double baseHue; // Random per document, so documents do not all share one palette

    public Document(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.crdt = new CRDT();
        this.editorCode = UUID.randomUUID().toString().substring(0, 8);
        this.viewerCode = UUID.randomUUID().toString().substring(0, 8);
        this.usersCount = 0;
        this.users = new HashSet<>();
        this.baseHue = Math.random() * 360;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CRDT getCrdt() {
        return crdt;
    }

    public String getEditorCode() {
        return editorCode;
    }

    public String getViewerCode() {
        return viewerCode;
    }

    public Set<User> getUsers() {
        return users;
    }

    public boolean isEditor(String code) {
        return editorCode.equals(code);
    }

    public User createNewUser(boolean isEditor) {
        String color = generateColor(usersCount);
        User user = new User(String.valueOf(++usersCount), color, isEditor);
        users.add(user);
        return user;
    }

    public boolean removeUser(String userId) {
        return users.removeIf(user -> user.getId().equals(userId));
    }

    public void importContent(String userId, String content) {
        crdt.importContent(userId, content);
    }

    private String generateColor(int userIndex) {
        // Golden-angle steps put each new user's hue far from the users before them
        double hue = (baseHue + userIndex * GOLDEN_ANGLE) % 360;

        // Darken until the colour is readable on white; yellows and greens need far more
        // darkening than blues, so no single lightness works for every hue
        for (double lightness = START_LIGHTNESS; lightness > 0; lightness -= 0.01) {
            int rgb = hslToRgb(hue, SATURATION, lightness);
            if (contrastOnWhite(rgb) >= MIN_CONTRAST_ON_WHITE) {
                return String.format("#%06X", rgb);
            }
        }
        return "#000000";
    }

    private static int hslToRgb(double hue, double saturation, double lightness) {
        double chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
        double sector = hue / 60;
        double secondary = chroma * (1 - Math.abs(sector % 2 - 1));
        double r = 0, g = 0, b = 0;
        if (sector < 1) {
            r = chroma; g = secondary;
        } else if (sector < 2) {
            r = secondary; g = chroma;
        } else if (sector < 3) {
            g = chroma; b = secondary;
        } else if (sector < 4) {
            g = secondary; b = chroma;
        } else if (sector < 5) {
            r = secondary; b = chroma;
        } else {
            r = chroma; b = secondary;
        }
        double offset = lightness - chroma / 2;
        return (toByte(r + offset) << 16) | (toByte(g + offset) << 8) | toByte(b + offset);
    }

    private static int toByte(double value) {
        return (int) Math.round(Math.max(0, Math.min(1, value)) * 255);
    }

    // WCAG 2 contrast ratio of an sRGB colour against white
    private static double contrastOnWhite(int rgb) {
        double luminance = 0.2126 * linear((rgb >> 16) & 0xFF)
                + 0.7152 * linear((rgb >> 8) & 0xFF)
                + 0.0722 * linear(rgb & 0xFF);
        return 1.05 / (luminance + 0.05);
    }

    private static double linear(int component) {
        double c = component / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
