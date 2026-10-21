package com.aurapay.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/local-image-lab")
@ConditionalOnProperty(name = "aurapay.local-image-lab.enabled", havingValue = "true")
public class LocalImageLabController {
    private static final String COMFY = "http://127.0.0.1:8188";
    private final RestClient http = RestClient.create();
    private final String checkpoint;

    public LocalImageLabController(
            @Value("${aurapay.local-image-lab.checkpoint:v1-5-pruned-emaonly.safetensors}") String checkpoint) {
        this.checkpoint = checkpoint;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        try {
            JsonNode queue = http.get().uri(COMFY + "/queue").retrieve().body(JsonNode.class);
            return Map.of("enabled", true, "connected", queue != null, "comfyUrl", COMFY, "checkpoint", checkpoint);
        } catch (Exception ex) {
            return Map.of("enabled", true, "connected", false, "comfyUrl", COMFY,
                    "checkpoint", checkpoint, "message", "Start ComfyUI locally, then refresh this page.");
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody(required = false) GenerateRequest request) {
        String prompt = request == null || request.prompt() == null ? "" : request.prompt().trim();
        if (prompt.isBlank() || prompt.length() > 1200) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a prompt between 1 and 1200 characters."));
        }
        String negative = request == null || request.negativePrompt() == null ? "blurry, low quality, distorted, watermark"
                : request.negativePrompt().trim();
        try {
            Map<String, Object> body = Map.of("prompt", workflow(prompt, negative), "client_id", UUID.randomUUID().toString());
            JsonNode queued = http.post().uri(COMFY + "/prompt").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
            String promptId = queued == null ? null : queued.path("prompt_id").asText(null);
            if (promptId == null || promptId.isBlank()) {
                return ResponseEntity.status(502).body(Map.of("error", "ComfyUI did not return a prompt ID. Check its console for workflow errors."));
            }

            for (int i = 0; i < 45; i++) {
                Thread.sleep(2000);
                JsonNode history = http.get().uri(COMFY + "/history/" + promptId).retrieve().body(JsonNode.class);
                JsonNode outputs = history == null ? null : history.path(promptId).path("outputs");
                if (outputs != null && outputs.isObject()) {
                    for (var node : outputs) {
                        JsonNode images = node.path("images");
                        if (images.isArray() && !images.isEmpty()) {
                            JsonNode image = images.get(0);
                            String filename = image.path("filename").asText();
                            String subfolder = image.path("subfolder").asText("");
                            String type = image.path("type").asText("output");
                            String uri = UriComponentsBuilder.fromHttpUrl(COMFY + "/view")
                                    .queryParam("filename", filename).queryParam("subfolder", subfolder)
                                    .queryParam("type", type).build().encode().toUriString();
                            byte[] bytes = http.get().uri(uri).retrieve().body(byte[].class);
                            if (bytes == null || bytes.length == 0) {
                                return ResponseEntity.status(502).body(Map.of("error", "ComfyUI returned an empty image."));
                            }
                            return ResponseEntity.ok(Map.of("promptId", promptId, "mimeType", "image/png",
                                    "imageDataUrl", "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes),
                                    "message", "Generated locally by ComfyUI. No hosted image API was used."));
                        }
                    }
                }
            }
            return ResponseEntity.status(504).body(Map.of("error", "Generation timed out after 90 seconds. Check ComfyUI and your GPU memory."));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).body(Map.of("error", "Generation was interrupted."));
        } catch (Exception ex) {
            return ResponseEntity.status(502).body(Map.of("error",
                    "Could not generate the image. Ensure ComfyUI is running at 127.0.0.1:8188 and the configured checkpoint is installed.",
                    "details", safeMessage(ex)));
        }
    }

    private Map<String, Object> workflow(String prompt, String negative) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("1", Map.of("class_type", "CheckpointLoaderSimple", "inputs", Map.of("ckpt_name", checkpoint)));
        graph.put("2", Map.of("class_type", "CLIPTextEncode", "inputs", Map.of("text", prompt, "clip", new String[]{"1", "1"})));
        graph.put("3", Map.of("class_type", "CLIPTextEncode", "inputs", Map.of("text", negative, "clip", new String[]{"1", "1"})));
        graph.put("4", Map.of("class_type", "EmptyLatentImage", "inputs", Map.of("width", 512, "height", 512, "batch_size", 1)));
        graph.put("5", Map.of("class_type", "KSampler", "inputs", Map.of(
                "seed", (UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE), "steps", 15, "cfg", 7.0,
                "sampler_name", "euler", "scheduler", "normal", "denoise", 1.0,
                "model", new String[]{"1", "0"}, "positive", new String[]{"2", "0"},
                "negative", new String[]{"3", "0"}, "latent_image", new String[]{"4", "0"})));
        graph.put("6", Map.of("class_type", "VAEDecode", "inputs", Map.of("samples", new String[]{"5", "0"}, "vae", new String[]{"1", "2"})));
        graph.put("7", Map.of("class_type", "SaveImage", "inputs", Map.of("filename_prefix", "AuraPay_Test", "images", new String[]{"6", "0"})));
        return graph;
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    public record GenerateRequest(String prompt, String negativePrompt) {}
}
