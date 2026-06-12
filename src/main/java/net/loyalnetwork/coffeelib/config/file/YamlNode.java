package net.loyalnetwork.coffeelib.config.file;

import java.util.*;

public final class YamlNode {

    private final Map<String, YamlNode> children = new LinkedHashMap<>();

    private final List<String> comments = new ArrayList<>();

    private Object value;

    public Map<String, YamlNode> children() {
        return Collections.unmodifiableMap(children);
    }

    public List<String> comments() {
        return Collections.unmodifiableList(comments);
    }

    public Object value() {
        return value;
    }

    public void value(Object value) {
        this.value = value;
    }

    public boolean leaf() {
        return children.isEmpty();
    }

    public void addComment(String comment) {
        comments.add(comment);
    }

    public void addComments(String[] comments) {
        this.comments.addAll(Arrays.asList(comments));
    }

    YamlNode getOrCreateChild(String name) {
        return children.computeIfAbsent(name, ignored -> new YamlNode());
    }
}