package com.hairsalonproject2.common.integration.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoLocalSearchClientHttpTest {
 private HttpServer server;
 private ExecutorService executor;
 private String baseUrl;

 @BeforeEach
 void startLoopbackServer() throws IOException {
  server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
  executor = Executors.newCachedThreadPool();
  server.setExecutor(executor);
  server.start();
  baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
 }

 @AfterEach
 void stopLoopbackServer() {
  server.stop(0);
  executor.shutdownNow();
 }

 @Test
 void failedAddressLookupIsRetriedAndSuccessfulResultIsCached() {
  AtomicInteger calls = new AtomicInteger();
  server.createContext("/v2/local/search/address.json", exchange -> {
   if (calls.incrementAndGet() == 1) respond(exchange, 503, "{}");
   else respond(exchange, 200, """
           {"documents":[{"address_name":"Seoul","x":"127.1","y":"37.5"}]}
           """);
  });
  KakaoLocalSearchClient client = client(1000);

  assertThat(client.searchAddress("Seoul")).isEmpty();
  assertThat(client.searchAddress("Seoul")).isPresent();
  assertThat(client.searchAddress(" Seoul ")).isPresent();
  assertThat(calls).hasValue(2);
 }

 @Test
 void readTimeoutEndsKeywordRequest() {
  server.createContext("/v2/local/search/keyword.json", exchange -> {
   try {
    Thread.sleep(500);
    respond(exchange, 200, "{\"documents\":[]}");
   } catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
   } finally {
    exchange.close();
   }
  });
  KakaoLocalSearchClient client = client(75);

  assertThatThrownBy(() -> client.searchSalons("Hair", "Seoul", 1, 15))
          .isInstanceOf(ResourceAccessException.class);
 }

 @Test
 void externalIdsDeduplicateResultsWithoutRemovingDifferentBranches() {
  AtomicInteger imageCalls = new AtomicInteger();
  AtomicInteger ratingCalls = new AtomicInteger();
  server.createContext("/v2/local/search/keyword.json", exchange -> respond(exchange, 200, """
          {"documents":[
            {"id":"1","place_name":"Same Hair","address_name":"Seoul"},
            {"id":" 1 ","place_name":"Duplicate","address_name":"Other"},
            {"id":"2","place_name":"Same Hair","address_name":"Seoul"},
            {"id":" ","place_name":"Missing"},
            {"place_name":"Missing"}
          ]}
          """));
  server.createContext("/v2/search/image", exchange -> {
   imageCalls.incrementAndGet();
   respond(exchange, 200, "{\"documents\":[]}");
  });
  server.createContext("/places/", exchange -> {
   ratingCalls.incrementAndGet();
   respond(exchange, 200, "{}");
  });

  var places = client(1000).searchSalons("Hair", "Seoul", 1, 15);

  assertThat(places).extracting(KakaoPlaceSearchResult::getExternalId).containsExactly("1", "2");
  assertThat(imageCalls).hasValue(1);
  assertThat(ratingCalls).hasValue(2);
 }

 private KakaoLocalSearchClient client(int readTimeout) {
  KakaoLocalSearchClient client = new KakaoLocalSearchClient(new ObjectMapper(),
          1000, readTimeout, baseUrl, baseUrl + "/places/");
  ReflectionTestUtils.setField(client, "restApiKey", "test-key");
  return client;
 }

 private void respond(HttpExchange exchange, int status, String body) throws IOException {
  byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
  exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
  exchange.sendResponseHeaders(status, bytes.length);
  try (var output = exchange.getResponseBody()) {
   output.write(bytes);
  }
 }
}
