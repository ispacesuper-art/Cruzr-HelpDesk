package com.usc.cruzr.helpdesk;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Loads canned help-desk topics from assets and matches user speech by keywords.
 */
public class HelpDeskEngine {

    public static class Topic {
        public final String id;
        public final String label;
        public final boolean quickAsk;
        private final List<String> keywords;
        private final List<String> responses;

        Topic(String id, String label, List<String> keywords, List<String> responses, boolean quickAsk) {
            this.id = id;
            this.label = label;
            this.keywords = keywords;
            this.responses = responses;
            this.quickAsk = quickAsk;
        }

        /** Picks one answer at random when several are defined for this topic. */
        String pickResponse(Random random) {
            if (responses.isEmpty()) {
                return "";
            }
            if (responses.size() == 1) {
                return responses.get(0);
            }
            return responses.get(random.nextInt(responses.size()));
        }
    }

    public static class MatchResult {
        public final Topic topic;
        public final String response;
        public final boolean exactTopicMatch;

        MatchResult(Topic topic, String response, boolean exactTopicMatch) {
            this.topic = topic;
            this.response = response;
            this.exactTopicMatch = exactTopicMatch;
        }
    }

    private static final String DEFAULT_RESPONSE =
            "Sorry, I did not understand that. You can ask about opening hours, directions, "
                    + "Wi-Fi, IT support, parking, or the library. Tap a topic button or try again.";

    private static final String WELCOME_RESPONSE =
            "Hello! I am your campus help desk assistant. Tap Listen and ask a question, "
                    + "or choose a topic below.";

    private final List<Topic> topics = new ArrayList<>();
    private final Random random = new Random();

    public HelpDeskEngine(Context context) {
        loadTopics(context);
    }

    public List<Topic> getTopics() {
        return Collections.unmodifiableList(topics);
    }

    public List<Topic> getQuickAskTopics() {
        List<Topic> quickAskTopics = new ArrayList<>();
        for (Topic topic : topics) {
            if (topic.quickAsk) {
                quickAskTopics.add(topic);
            }
        }
        return Collections.unmodifiableList(quickAskTopics);
    }

    public String getWelcomeResponse() {
        return WELCOME_RESPONSE;
    }

    public MatchResult match(String userText) {
        if (TextUtils.isEmpty(userText)) {
            return new MatchResult(null, DEFAULT_RESPONSE, false);
        }

        String normalized = normalize(userText);
        Topic bestTopic = null;
        int bestScore = 0;

        for (Topic topic : topics) {
            int score = scoreTopic(normalized, topic);
            if (score > bestScore) {
                bestScore = score;
                bestTopic = topic;
            }
        }

        if (bestTopic != null && bestScore > 0) {
            return new MatchResult(bestTopic, bestTopic.pickResponse(random), bestScore >= 100);
        }

        return new MatchResult(null, DEFAULT_RESPONSE, false);
    }

    public MatchResult matchTopicId(String topicId) {
        for (Topic topic : topics) {
            if (topic.id.equals(topicId)) {
                return new MatchResult(topic, topic.pickResponse(random), true);
            }
        }
        return new MatchResult(null, DEFAULT_RESPONSE, false);
    }

    private int scoreTopic(String normalizedInput, Topic topic) {
        int score = 0;
        for (String keyword : topic.keywords) {
            String normalizedKeyword = normalize(keyword);
            if (normalizedInput.equals(normalizedKeyword)) {
                return 100;
            }
            if (normalizedInput.contains(normalizedKeyword)) {
                score += Math.max(10, normalizedKeyword.length());
            }
        }
        return score;
    }

    private static String normalize(String text) {
        return text.trim()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9\\s']", " ")
                .replaceAll("\\s+", " ");
    }

    private void loadTopics(Context context) {
        try {
            InputStream inputStream = context.getAssets().open("help_topics.json");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            reader.close();

            JSONArray array = new JSONArray(builder.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                String id = object.getString("id");
                String label = object.getString("label");
                List<String> responses = loadResponses(object);

                JSONArray keywordArray = object.getJSONArray("keywords");
                List<String> keywords = new ArrayList<>();
                for (int k = 0; k < keywordArray.length(); k++) {
                    keywords.add(keywordArray.getString(k));
                }

                topics.add(new Topic(
                        id,
                        label,
                        keywords,
                        responses,
                        object.optBoolean("quickAsk", false)
                ));
            }
        } catch (Exception e) {
            topics.clear();
            topics.add(new Topic(
                    "fallback",
                    "Help",
                    Collections.singletonList("help"),
                    Collections.singletonList(DEFAULT_RESPONSE),
                    false
            ));
        }
    }

    private static List<String> loadResponses(JSONObject object) throws Exception {
        List<String> responses = new ArrayList<>();
        if (object.has("responses")) {
            JSONArray responseArray = object.getJSONArray("responses");
            for (int r = 0; r < responseArray.length(); r++) {
                String line = responseArray.getString(r).trim();
                if (!line.isEmpty()) {
                    responses.add(line);
                }
            }
        } else if (object.has("response")) {
            String line = object.getString("response").trim();
            if (!line.isEmpty()) {
                responses.add(line);
            }
        }
        if (responses.isEmpty()) {
            throw new IllegalArgumentException("Topic must have response or responses");
        }
        return responses;
    }
}
