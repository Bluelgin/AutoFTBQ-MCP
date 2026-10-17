package dev.autoftbq.mcp.design;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Version-independent, deterministic analysis of a quest progression graph.
 * Describes how an author teaches, branches, gates and rewards, not merely icon placement.
 * Read-only: all recommendations must be reviewed/applied through existing revision-guarded tools.
 */
public final class QuestDesignAnalyzer {
    private static final int MAX_QUESTS = 512;
    private static final int MAX_EDGES = 4096;
    private static final int MAX_ISSUES = 80;
    private static final int MAX_STAGE_SAMPLES = 8;

    private QuestDesignAnalyzer() {}

    public static JsonObject analyze(JsonObject snapshot, double minimumSpacing) {
        if (snapshot == null || !snapshot.has("quests") || !snapshot.get("quests").isJsonArray()) {
            throw new IllegalArgumentException("snapshot must contain a quests array");
        }
        JsonArray array = snapshot.getAsJsonArray("quests");
        if (array.size() > MAX_QUESTS) throw new IllegalArgumentException("chapter exceeds 512 quests");
        if (!Double.isFinite(minimumSpacing) || minimumSpacing <= 0.0 || minimumSpacing > 64.0) {
            throw new IllegalArgumentException("minimum_spacing must be in (0,64]");
        }

        Map<String, Node> nodes = new LinkedHashMap<>();
        int totalTasks = 0, totalRewards = 0;
        Map<String, Integer> taskKinds = new LinkedHashMap<>();
        Map<String, Integer> rewardKinds = new LinkedHashMap<>();
        for (JsonElement element : array) {
            JsonObject q = element.getAsJsonObject();
            String id = string(q, "id");
            if (id.isBlank() || nodes.containsKey(id)) throw new IllegalArgumentException("quest IDs must be unique and nonempty");
            double x = number(q, "x"), y = number(q, "y");
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("invalid quest coordinates: " + id);
            JsonArray prerequisites = q.has("dependencies") && q.get("dependencies").isJsonArray()
                    ? q.getAsJsonArray("dependencies") : new JsonArray();
            JsonArray tasks = q.has("tasks") && q.get("tasks").isJsonArray() ? q.getAsJsonArray("tasks") : new JsonArray();
            JsonArray rewards = q.has("rewards") && q.get("rewards").isJsonArray() ? q.getAsJsonArray("rewards") : new JsonArray();
            totalTasks += tasks.size();
            totalRewards += rewards.size();
            for (JsonElement t : tasks) countType(taskKinds, t);
            for (JsonElement r : rewards) countType(rewardKinds, r);
            nodes.put(id, new Node(id, string(q, "title"), string(q, "data_snbt"), x, y, prerequisites, tasks, rewards));
        }

        int externalPrerequisites = 0;
        int edges = 0;
        for (Node node : nodes.values()) {
            Set<String> seen = new HashSet<>();
            for (JsonElement element : node.dependencies) {
                if (!element.isJsonPrimitive()) continue;
                String dep = element.getAsString();
                if (!seen.add(dep)) continue;
                if (nodes.containsKey(dep)) {
                    node.parents.add(dep);
                    nodes.get(dep).children.add(node.id);
                    edges++;
                } else {
                    externalPrerequisites++;
                }
                if (edges + externalPrerequisites > MAX_EDGES) throw new IllegalArgumentException("too many dependencies");
            }
        }

        Map<String, Integer> indegrees = new HashMap<>();
        ArrayDeque<String> ready = new ArrayDeque<>();
        for (Node node : nodes.values()) {
            indegrees.put(node.id, node.parents.size());
            if (node.parents.isEmpty()) ready.add(node.id);
        }
        int processed = 0;
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            Node node = nodes.get(id);
            processed++;
            for (String childId : node.children) {
                Node child = nodes.get(childId);
                child.stage = Math.max(child.stage, node.stage + 1);
                int remaining = indegrees.merge(childId, -1, Integer::sum);
                if (remaining == 0) ready.addLast(childId);
            }
        }
        boolean cyclic = processed != nodes.size();

        List<Node> ordered = new ArrayList<>(nodes.values());
        ordered.sort(Comparator.comparingInt((Node q) -> q.stage).thenComparing(q -> q.id));
        int entrances = 0, exits = 0, branches = 0, merges = 0, gates = 0;
        int rewardedQuests = 0, maxStage = 0;
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        Map<Integer, List<Node>> stages = new LinkedHashMap<>();
        for (Node node : ordered) {
            if (node.parents.isEmpty()) entrances++;
            if (node.children.isEmpty()) exits++;
            if (node.children.size() >= 2) branches++;
            if (node.parents.size() >= 2) merges++;
            if (node.children.size() >= 2 || node.parents.size() >= 2) gates++;
            if (node.rewards > 0) rewardedQuests++;
            maxStage = Math.max(maxStage, node.stage);
            minX = Math.min(minX, node.x); maxX = Math.max(maxX, node.x);
            minY = Math.min(minY, node.y); maxY = Math.max(maxY, node.y);
            stages.computeIfAbsent(node.stage, ignored -> new ArrayList<>()).add(node);
        }

        JsonObject profile = new JsonObject();
        profile.addProperty("status", "ok");
        copy(snapshot, profile, "chapter_id");
        copy(snapshot, profile, "chapter_title");
        copy(snapshot, profile, "book_revision");
        profile.addProperty("source", "live_quest_dependencies_and_tasks");
        profile.addProperty("acyclic", !cyclic);
        profile.addProperty("quest_count", nodes.size());
        profile.addProperty("internal_dependency_count", edges);
        profile.addProperty("external_prerequisite_count", externalPrerequisites);

        JsonObject progression = new JsonObject();
        progression.addProperty("structure", classify(entrances, exits, branches, merges, nodes.size()));
        progression.addProperty("entry_quests", entrances);
        progression.addProperty("terminal_quests", exits);
        progression.addProperty("branch_points", branches);
        progression.addProperty("convergence_points", merges);
        progression.addProperty("decision_or_gate_points", gates);
        progression.addProperty("longest_prerequisite_chain", cyclic ? -1 : (nodes.isEmpty() ? 0 : maxStage + 1));
        progression.addProperty("parallel_start", entrances > 1);
        progression.addProperty("has_explicit_final_convergence", ordered.stream().anyMatch(n -> n.children.isEmpty() && n.parents.size() > 1));
        progression.addProperty("interpretation", "Topology is evidence of authored progression, not proof of intended gameplay or survival obtainability.");
        profile.add("progression", progression);

        JsonArray phases = new JsonArray();
        for (Map.Entry<Integer, List<Node>> entry : stages.entrySet()) {
            if (phases.size() >= 256) break;
            JsonObject phase = new JsonObject();
            phase.addProperty("depth", entry.getKey());
            phase.addProperty("quest_count", entry.getValue().size());
            JsonArray sample = new JsonArray();
            JsonArray roleSamples = new JsonArray();
            int phaseRewards = 0, phaseTasks = 0;
            for (Node node : entry.getValue()) {
                phaseRewards += node.rewards;
                phaseTasks += node.tasks;
                if (sample.size() < MAX_STAGE_SAMPLES) {
                    JsonObject item = new JsonObject();
                    item.addProperty("id", node.id);
                    item.addProperty("title", node.title);
                    item.addProperty("role", role(node));
                    item.addProperty("task_count", node.tasks);
                    item.addProperty("reward_count", node.rewards);
                    item.addProperty("quest_data_excerpt", excerpt(node.data, 320));
                    item.add("task_examples", sampleTypes(node.taskDetails, 2));
                    item.add("reward_examples", sampleTypes(node.rewardDetails, 2));
                    JsonArray dependencies = new JsonArray();
                    node.parents.stream().limit(8).forEach(dependencies::add);
                    item.add("local_prerequisites", dependencies);
                    sample.add(item);
                }
                if (node.parents.size() >= 2 || node.children.size() >= 2 || node.children.isEmpty()) {
                    if (roleSamples.size() < MAX_STAGE_SAMPLES) roleSamples.add(node.id);
                }
            }
            phase.addProperty("task_count", phaseTasks);
            phase.addProperty("reward_count", phaseRewards);
            phase.add("sample_objectives", sample);
            phase.add("milestone_quest_ids", roleSamples);
            phases.add(phase);
        }
        profile.add("progression_phases", phases);
        profile.addProperty("phases_truncated", cyclic || stages.size() > 256);

        JsonObject pacing = new JsonObject();
        pacing.addProperty("task_count", totalTasks);
        pacing.addProperty("reward_count", totalRewards);
        pacing.addProperty("rewarded_quest_count", rewardedQuests);
        pacing.addProperty("rewarded_quest_fraction", nodes.isEmpty() ? 0 : round((double) rewardedQuests / nodes.size()));
        pacing.addProperty("rewards_per_task", totalTasks == 0 ? 0 : round((double) totalRewards / totalTasks));
        pacing.add("task_type_counts", counts(taskKinds));
        pacing.add("reward_type_counts", counts(rewardKinds));
        profile.add("objectives_and_rewards", pacing);

        JsonObject geometry = new JsonObject();
        geometry.addProperty("min_x", nodes.isEmpty() ? 0 : minX);
        geometry.addProperty("max_x", nodes.isEmpty() ? 0 : maxX);
        geometry.addProperty("min_y", nodes.isEmpty() ? 0 : minY);
        geometry.addProperty("max_y", nodes.isEmpty() ? 0 : maxY);
        geometry.addProperty("primary_axis", (maxX - minX) >= (maxY - minY) ? "horizontal" : "vertical");
        geometry.addProperty("minimum_spacing_requested", minimumSpacing);
        profile.add("geometry", geometry);

        JsonArray issues = new JsonArray();
        int colliding = 0, crowded = 0, backwards = 0, crossed = 0;
        List<Node> byId = new ArrayList<>(nodes.values());
        for (int i = 0; i < byId.size(); i++) {
            Node a = byId.get(i);
            for (int j = i + 1; j < byId.size(); j++) {
                Node b = byId.get(j);
                double distance = Math.hypot(a.x - b.x, a.y - b.y);
                if (distance < 0.8) {
                    colliding++;
                    issue(issues, "overlap", a.id, b.id, "Quest positions overlap or nearly coincide.");
                } else if (distance < minimumSpacing) {
                    crowded++;
                    issue(issues, "tight_spacing", a.id, b.id, "Quest nodes are closer than requested spacing.");
                }
            }
        }

        List<Edge> lineSegments = new ArrayList<>();
        for (Node node : nodes.values()) {
            for (String parentId : node.parents) {
                Node parent = nodes.get(parentId);
                lineSegments.add(new Edge(parent, node));
                boolean horizontal = (maxX - minX) >= (maxY - minY);
                double step = horizontal ? node.x - parent.x : node.y - parent.y;
                if (step < -0.01) {
                    backwards++;
                    issue(issues, "reverse_progression", parent.id, node.id, "Dependency opposes the chapter's dominant visual direction.");
                }
            }
        }
        // Bounded crossing checks: shared endpoints are intentional and don't count.
        if (lineSegments.size() <= 200) {
            for (int i = 0; i < lineSegments.size(); i++) {
                Edge a = lineSegments.get(i);
                for (int j = i + 1; j < lineSegments.size(); j++) {
                    Edge b = lineSegments.get(j);
                    if (a.sharesEndpoint(b)) continue;
                    if (intersects(a.start, a.end, b.start, b.end)) {
                        crossed++;
                        issue(issues, "crossed_dependency", a.end.id, b.end.id, "Two dependency lines cross away from shared endpoints.");
                    }
                }
            }
        }
        JsonObject assessment = new JsonObject();
        assessment.addProperty("overlap_pairs", colliding);
        assessment.addProperty("tight_spacing_pairs", crowded);
        assessment.addProperty("backwards_dependency_edges", backwards);
        assessment.addProperty("crossed_dependency_pairs", crossed);
        assessment.addProperty("crossings_checked", lineSegments.size() <= 200);
        assessment.addProperty("issues_truncated", colliding + crowded + backwards + crossed > issues.size());
        assessment.add("issues", issues);
        profile.add("layout_assessment", assessment);

        JsonArray guidance = new JsonArray();
        guidance.add("Match the reference's prerequisite teaching sequence and actual milestones before matching coordinates.");
        guidance.add("Preserve the intended entry gates, parallel specialization and convergence decisions; do not blindly copy quest or item IDs.");
        guidance.add("Use live registry/recipe evidence for the target modpack; registry existence alone does not prove obtainability.");
        guidance.add("Inspect actual quest/task titles and detailed contents before transferring the author's gameplay philosophy.");
        guidance.add("Check the proposed book's revision, dependency validity and screenshot before accepting any visual edit.");
        profile.add("agent_guidance", guidance);
        return profile;
    }

    public static JsonObject compare(JsonObject reference, JsonObject candidate, double spacing) {
        JsonObject source = analyze(reference, spacing);
        JsonObject target = analyze(candidate, spacing);
        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.add("reference_profile", source);
        result.add("candidate_profile", target);
        JsonArray observations = new JsonArray();
        JsonObject s = source.getAsJsonObject("progression");
        JsonObject t = target.getAsJsonObject("progression");
        for (String key : List.of("entry_quests", "terminal_quests", "branch_points", "convergence_points", "longest_prerequisite_chain")) {
            int before = s.get(key).getAsInt(), after = t.get(key).getAsInt();
            if (before != after) observations.add(key + ": reference " + before + ", candidate " + after
                    + ". Determine whether the difference is justified by the new chapter's objectives.");
        }
        double before = source.getAsJsonObject("objectives_and_rewards").get("rewarded_quest_fraction").getAsDouble();
        double after = target.getAsJsonObject("objectives_and_rewards").get("rewarded_quest_fraction").getAsDouble();
        if (Math.abs(before - after) > 0.20) {
            observations.add("Reward pacing differs substantially (" + before + " vs " + after + "). Review milestone motivation.");
        }
        if (!t.get("has_explicit_final_convergence").getAsBoolean()
                && s.get("has_explicit_final_convergence").getAsBoolean()) {
            observations.add("The reference converges into a final gate, but the candidate does not.");
        }
        if (target.getAsJsonObject("layout_assessment").get("overlap_pairs").getAsInt() > 0) {
            observations.add("The candidate has overlapping nodes; inspect the screenshot.");
        }
        result.add("differences_to_review", observations);
        result.addProperty("judgement", "comparison_only_no_automatic_gameplay_or_visual_equivalence_claim");
        return result;
    }

    private static JsonArray sampleTypes(JsonArray details, int count) {
        JsonArray examples = new JsonArray();
        for (int i = 0; i < Math.min(count, details.size()); i++) {
            JsonElement element = details.get(i);
            if (!element.isJsonObject()) continue;
            JsonObject v = element.getAsJsonObject();
            JsonObject summary = new JsonObject();
            summary.addProperty("type_id", string(v, "type_id"));
            summary.addProperty("title", string(v, "title"));
            summary.addProperty("data_excerpt", excerpt(string(v, "data_snbt"), 180));
            examples.add(summary);
        }
        return examples;
    }
    private static String excerpt(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...[truncated]";
    }

    private static String role(Node n) {
        if (n.parents.isEmpty()) return "entry";
        if (n.children.isEmpty()) return "terminal";
        if (n.parents.size() >= 2) return "convergence_gate";
        if (n.children.size() >= 2) return "branch_gate";
        return "progression";
    }
    private static String classify(int entry, int exit, int branches, int merges, int count) {
        if (count == 0) return "empty";
        if (branches == 0 && merges == 0 && entry == 1 && exit == 1) return "linear";
        if (branches > 0 && merges > 0) return "branch_and_converge";
        if (branches > 0) return "branching";
        if (merges > 0 || entry > 1) return "parallel_and_converge";
        return "mixed";
    }
    private static void countType(Map<String, Integer> result, JsonElement element) {
        if (!element.isJsonObject()) return;
        JsonObject data = element.getAsJsonObject();
        String type = string(data, "type_id");
        if (!type.isBlank()) result.merge(type, 1, Integer::sum);
    }
    private static JsonObject counts(Map<String, Integer> values) {
        JsonObject result = new JsonObject();
        values.forEach(result::addProperty);
        return result;
    }
    private static void issue(JsonArray issues, String code, String a, String b, String message) {
        if (issues.size() >= MAX_ISSUES) return;
        JsonObject issue = new JsonObject();
        issue.addProperty("code", code);
        issue.addProperty("first_quest_id", a);
        issue.addProperty("second_quest_id", b);
        issue.addProperty("message", message);
        issues.add(issue);
    }
    private static boolean intersects(Node a, Node b, Node c, Node d) {
        double d1 = direction(a, b, c), d2 = direction(a, b, d);
        double d3 = direction(c, d, a), d4 = direction(c, d, b);
        return d1 * d2 < -1e-8 && d3 * d4 < -1e-8;
    }
    private static double direction(Node a, Node b, Node c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }
    private static String string(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
    private static double number(JsonObject o, String k) {
        if (!o.has(k)) throw new IllegalArgumentException("missing coordinate: " + k);
        return o.get(k).getAsDouble();
    }
    private static void copy(JsonObject from, JsonObject to, String key) {
        if (from.has(key)) to.add(key, from.get(key).deepCopy());
    }
    private static double round(double d) { return Math.round(d * 1000.0) / 1000.0; }

    private static final class Node {
        final String id, title, data;
        final double x, y;
        final JsonArray dependencies, taskDetails, rewardDetails;
        final int tasks, rewards;
        final List<String> parents = new ArrayList<>();
        final List<String> children = new ArrayList<>();
        int stage;
        Node(String id, String title, String data, double x, double y, JsonArray dependencies,
             JsonArray taskDetails, JsonArray rewardDetails) {
            this.id = id; this.title = title; this.data = data; this.x = x; this.y = y;
            this.dependencies = dependencies;
            this.taskDetails = taskDetails; this.rewardDetails = rewardDetails;
            this.tasks = taskDetails.size(); this.rewards = rewardDetails.size();
        }
    }
    private record Edge(Node start, Node end) {
        boolean sharesEndpoint(Edge b) {
            return start.id.equals(b.start.id) || start.id.equals(b.end.id)
                    || end.id.equals(b.start.id) || end.id.equals(b.end.id);
        }
    }
}
