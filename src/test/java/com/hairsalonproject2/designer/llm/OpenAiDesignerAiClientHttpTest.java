package com.hairsalonproject2.designer.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hairsalonproject2.designer.service.DesignerAiCandidateService;
import com.hairsalonproject2.designer.service.DesignerAiRecommendationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpenAiDesignerAiClientHttpTest {
 private final ObjectMapper mapper = new ObjectMapper();
 private HttpServer server;
 private ExecutorService executor;
 private String baseUrl;

 @BeforeEach
 void startServer() throws IOException {
  server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
  executor = Executors.newCachedThreadPool();
  server.setExecutor(executor);
  server.start();
  baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
 }

 @AfterEach
 void stopServer() {
  server.stop(0);
  executor.shutdownNow();
 }

 @ParameterizedTest
 @ValueSource(ints = {401, 429, 500, 503})
 void httpFailureReturnsLocalRecommendations(int status) {
  server.createContext("/chat/completions", exchange -> respond(exchange, status, "{}"));
  assertLocalFallback(client(1000));
 }

 @ParameterizedTest
 @ValueSource(strings = {"not-json", "{}", "null", "[]", "{\"choices\":[]}"})
 void invalidEnvelopeReturnsLocalRecommendations(String response) {
  server.createContext("/chat/completions", exchange -> respond(exchange, 200, response));
  assertLocalFallback(client(1000));
 }

 @ParameterizedTest
 @ValueSource(strings = {
  "not-json", "{}", "null", "{\"recommendations\":[]}",
  "{\"recommendations\":[{\"designerId\":999,\"reason\":\"unknown\"}]}",
  "{\"recommendations\":[{\"designerId\":2147483648}]}",
  "{\"recommendations\":[{\"designerId\":1.9}]}",
  "{\"recommendations\":[{\"designerId\":\"1\"}]}"
 })
 void malformedOrOutsideCandidateContentReturnsLocalRecommendations(String content) throws Exception {
  String body = envelope(content);
  server.createContext("/chat/completions", exchange -> respond(exchange, 200, body));
  assertLocalFallback(client(1000));
 }

 @Test
 void readTimeoutReturnsLocalRecommendations() {
  server.createContext("/chat/completions", exchange -> {
   try {
    Thread.sleep(800);
    respond(exchange, 200, "{}");
   } catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
   } finally {
    exchange.close();
   }
  });
  assertLocalFallback(client(75));
 }

 @Test
 void validResponseRejectsUnknownAndDuplicateIdsAndBoundsOutput() throws Exception {
  String body = envelope("""
   {"summary":"추천 결과","recommendations":[
    {"designerId":999,"reason":"invalid"},
    {"designerId":1,"reason":"상담이 꼼꼼합니다","tags":["친절","친절","경력","스타일","추천","초과"]},
    {"designerId":1,"reason":"duplicate"}]}
   """);
  AtomicReference<String> request = new AtomicReference<>();
  server.createContext("/chat/completions", exchange -> {
   request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
   respond(exchange, 200, body);
  });

  var result = client(1000).recommend("염색", List.of(candidate()), 3).orElseThrow();

  assertThat(result.recommendations()).hasSize(1);
  assertThat(result.recommendations().getFirst().designerId()).isEqualTo(1);
  assertThat(result.recommendations().getFirst().tags()).containsExactly("친절", "경력", "스타일", "추천");
  assertThat(mapper.readTree(request.get()).path("messages").path(1).path("content").asText())
   .contains("염색", "\"designerId\":1", "\"limit\":3");
 }

 private void assertLocalFallback(OpenAiDesignerAiClient client) {
  DesignerAiCandidateService candidates = mock(DesignerAiCandidateService.class);
  when(candidates.loadCandidates()).thenReturn(List.of(candidate()));
  var response = new DesignerAiRecommendationService(candidates, client).recommend("염색", 3);
  assertThat(response.isLlmUsed()).isFalse();
  assertThat(response.getSource()).isEqualTo("LOCAL_RULES");
  assertThat(response.getRecommendations()).hasSize(1);
  assertThat(response.getRecommendations().getFirst().getDesignerId()).isEqualTo(1);
 }

 private DesignerAiCandidatePrompt candidate() {
  return new DesignerAiCandidatePrompt(1, 1, "디자이너", "살롱", "COLOR", 5,
   new BigDecimal("4.5"), 10L, 3, "염색", List.of("염색"), List.of());
 }

 private OpenAiDesignerAiClient client(int readTimeout) {
  return new OpenAiDesignerAiClient(mapper, true, "test-key", baseUrl, "test-model", 1000, readTimeout);
 }

 private String envelope(String content) throws Exception {
  return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("content", content)))));
 }

 private void respond(HttpExchange exchange, int status, String body) throws IOException {
  byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
  exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
  exchange.sendResponseHeaders(status, bytes.length);
  try (var output = exchange.getResponseBody()) { output.write(bytes); }
 }
}
