package org.admute;

import android.view.accessibility.AccessibilityNodeInfo;
import org.admute.core.AdDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded traversal. Never persists screen text. Incomplete reads cannot prove an ad ended. */
final class NodeSnapshot {
    final List<AdDetector.Label> labels = new ArrayList<>();
    private int visited;
    boolean complete = true;
    private boolean sawVisible;
    static NodeSnapshot read(AccessibilityNodeInfo root) {
        NodeSnapshot result = new NodeSnapshot();
        result.walk(root, false, false, 0);
        result.complete &= result.sawVisible && !result.labels.isEmpty();
        return result;
    }
    private void walk(AccessibilityNodeInfo node, boolean playerParent, boolean excludedParent, int depth) {
        if (node == null) { complete = false; return; }
        if (++visited > 1000 || depth > 40) { complete = false; return; }
        String id = String.valueOf(node.getViewIdResourceName()).toLowerCase(Locale.ROOT);
        String cls = String.valueOf(node.getClassName());
        boolean excluded = excludedParent || id.matches(".*(?:title|description|recommend|thumbnail|search|browse|poster).*");
        boolean player = playerParent || id.matches(".*(?:player|playback|ad_badge|ad_count|ad_label|ad_remaining|skip_ad|advertisement|commercial_break).*")
                || cls.endsWith("SeekBar");
        boolean visible = node.isVisibleToUser();
        if (visible) {
            sawVisible = true;
            boolean control = !excluded && (player || cls.endsWith("Button"));
            if (node.getText() != null) labels.add(new AdDetector.Label(node.getText().toString(), control));
            if (node.getContentDescription() != null) labels.add(new AdDetector.Label(node.getContentDescription().toString(), control));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (visited >= 1000) { complete = false; break; }
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) { complete = false; continue; }
            try { walk(child, player, excluded, depth + 1); }
            finally { child.recycle(); }
        }
    }
}
