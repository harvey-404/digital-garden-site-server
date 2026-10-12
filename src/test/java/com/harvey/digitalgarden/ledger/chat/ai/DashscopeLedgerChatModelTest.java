package com.harvey.digitalgarden.ledger.chat.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DashscopeLedgerChatModelTest {
    @Test
    void hybridVisionModelReceivesNonThinkingJsonContract() throws Exception {
        ObjectMapper json=new ObjectMapper();
        AtomicReference<JsonNode> captured=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat/completions",exchange->{
            JsonNode request=json.readTree(exchange.getRequestBody());captured.set(request);
            boolean compatible=request.has("enable_thinking") && !request.path("enable_thinking").asBoolean()
                    && "json_object".equals(request.path("response_format").path("type").asText());
            // The model endpoint requires explicit non-thinking mode for JSON output.
            byte[] body=json.writeValueAsBytes(compatible?Map.of("choices",List.of(Map.of(
                    "finish_reason","stop","message",Map.of("content","{\"action\":\"CLARIFY\",\"reply\":\"请说明消费\"}"))))
                    :Map.of("error",Map.of("code","invalid_parameter_error")));
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(compatible?200:400,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        try {
            var model=new DashscopeLedgerChatModel(json,"local-fixture-key",
                    "http://127.0.0.1:"+server.getAddress().getPort(),"qwen3.5-plus");
            var result=model.parse(new LedgerChatModel.Input("咖啡16元",1791788400L,
                    List.of(),List.of(),List.of(Map.of("id",1,"name","餐饮")),null,List.of()));
            assertEquals("CLARIFY",result.path("action").asText());
            assertEquals("qwen3.5-plus",captured.get().path("model").asText());
            assertFalse(captured.get().path("stream").asBoolean());
            assertEquals("user",captured.get().path("messages").get(1).path("role").asText());
        } finally {server.stop(0);}
    }
}
