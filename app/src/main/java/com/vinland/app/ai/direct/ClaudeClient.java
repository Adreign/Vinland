package com.vinland.app.ai.direct;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.vinland.app.ai.model.AiError;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

/**
 * Sends one request to Claude and returns the JSON text of the answer.
 * Blocking: call it from a background thread.
 */
public class ClaudeClient {

    /** To switch models, change only this, e.g. to "claude-sonnet-5-5" (smarter, ~20x the cost). */
    public static final String MODEL = "claude-haiku-5-5";
    private static final long MAX_TOKENS = 8000;

    /** A failed call, already turned into a message the screen can show. */
    public static class ClaudeException extends Exception {
        public final AiError error;

        ClaudeException(AiError error) {
            super(error.message);
            this.error = error;
        }
    }

    private final AnthropicClient client;

    public ClaudeClient(String apiKey) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(45))
                .maxRetries(1)
                .build();
    }

    /** Returns the answer's JSON text, or null if it was cut off or empty (worth one retry). */
    public String generate(String cropBlock, String userBlock) throws ClaudeException {
        Map<String, Object> schema = RecommendationValidator.schema();
        JsonOutputFormat.Schema.Builder schemaBuilder = JsonOutputFormat.Schema.builder();
        for (Map.Entry<String, Object> e : schema.entrySet()) {
            schemaBuilder.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
        }

        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(GardenPrompt.SYSTEM_PROMPT)
                .addUserMessageOfContentBlockParams(Arrays.asList(
                        // The crop list is the same for every user, so it is cached by Claude (cheaper).
                        ContentBlockParam.ofText(TextBlockParam.builder()
                                .text(cropBlock)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(userBlock).build())))
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(JsonOutputFormat.builder().schema(schemaBuilder.build()).build())
                        .build())
                .build();

        Message response;
        try {
            response = client.messages().create(params);
        } catch (RateLimitException e) {
            throw busy(60);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new ClaudeException(new AiError(AiError.Type.UNKNOWN,
                    "The Claude API key is missing or wrong. Check ANTHROPIC_API_KEY in secrets.properties.", 0));
        } catch (AnthropicServiceException e) {
            if (e.statusCode() >= 500) throw busy(30);
            throw new ClaudeException(new AiError(AiError.Type.UNKNOWN,
                    "The AI request failed (" + e.statusCode() + "). Please try again.", 0));
        } catch (AnthropicIoException e) {
            throw busy(0); // network problem or timeout
        }

        String stop = response.stopReason().map(Object::toString).orElse("");
        if (stop.equalsIgnoreCase("refusal") || stop.equalsIgnoreCase("max_tokens")) return null;

        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(t -> text.append(t.text()));
        }
        return text.length() > 0 ? text.toString() : null;
    }

    private static ClaudeException busy(int retryAfter) {
        return new ClaudeException(new AiError(AiError.Type.BUSY,
                "The AI service is busy or unreachable right now. Please try again in a minute.", retryAfter));
    }
}
