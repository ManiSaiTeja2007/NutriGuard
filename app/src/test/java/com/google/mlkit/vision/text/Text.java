package com.google.mlkit.vision.text;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

public class Text {
    public static class TextBlock {
        private String text;
        private Rect bounds;
        private List<Line> lines = new ArrayList<>();

        public TextBlock(String text, Rect bounds) {
            this.text = text;
            this.bounds = bounds;
        }
        public String getText() { return text; }
        public Rect getBoundingBox() { return bounds; }
        public List<Line> getLines() { return lines; }
        public void addLine(Line l) { lines.add(l); }
    }

    public static class Line {
        private String text;
        private Rect bounds;
        private List<Element> elements = new ArrayList<>();

        public Line(String text, Rect bounds) {
            this.text = text;
            this.bounds = bounds;
        }
        public String getText() { return text; }
        public Rect getBoundingBox() { return bounds; }
        public List<Element> getElements() { return elements; }
        public void addElement(Element e) { elements.add(e); }
        public float getConfidence() { return 1.0f; }
    }

    public static class Element {
        private String text;
        private Rect bounds;

        public Element(String text, Rect bounds) {
            this.text = text;
            this.bounds = bounds;
        }
        public String getText() { return text; }
        public Rect getBoundingBox() { return bounds; }
        public float getConfidence() { return 1.0f; }
    }

    private List<TextBlock> blocks = new ArrayList<>();

    public List<TextBlock> getTextBlock() { return blocks; }
    public List<TextBlock> getTextBlocks() { return blocks; }
    public String getText() { return ""; }
    public void addBlock(TextBlock block) {
        blocks.add(block);
    }
}
