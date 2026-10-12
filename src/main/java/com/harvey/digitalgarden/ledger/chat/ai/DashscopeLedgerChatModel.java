package com.harvey.digitalgarden.ledger.chat.ai;

import com.fasterxml.jackson.databind.*;
import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.ledger.chat.ChatImageNormalizer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DashscopeLedgerChatModel implements LedgerChatModel {
    private final RestClient client;
    private final ObjectMapper json;
    private final String key,model,prompt;
    private final Semaphore slots=new Semaphore(2);
    public DashscopeLedgerChatModel(ObjectMapper json,@Value("${app.dashscope.api-key:}") String key,
        @Value("${app.dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String url,
        @Value("${app.ledger-chat.model:}") String model) throws java.io.IOException {
        this.json=json;this.key=key;this.model=model;
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(Duration.ofSeconds(10));factory.setReadTimeout(Duration.ofSeconds(45));
        this.client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.prompt=new ClassPathResource("prompts/ledger-chat-system.txt").getContentAsString(StandardCharsets.UTF_8);
    }
    @Override public boolean available() {return !key.isBlank() && !model.isBlank();}
    @Override public JsonNode parse(Input input) {
        if(!available())throw BusinessException.badRequest("记账助手尚未配置");
        if(!slots.tryAcquire())throw BusinessException.conflict("记账助手正在忙，请稍后重试");
        try {
            List<Map<String,Object>> content=new ArrayList<>();
            String context=json.writeValueAsString(Map.of("text",input.text(),"receivedAt",input.receivedAt(),"timezone","Asia/Shanghai","categories",input.categories(),"activeDraft",input.draft()==null?Map.of():input.draft(),"history",input.history().stream().map(m->Map.of("role",m.role(),"text",m.text().substring(0,Math.min(600,m.text().length())))).toList(),"attachmentIds",input.attachmentIds()));
            content.add(Map.of("type","text","text",context));
            for(var image:input.images()) content.add(Map.of("type","image_url","image_url",Map.of("url","data:image/png;base64,"+Base64.getEncoder().encodeToString(ChatImageNormalizer.forModel(image.bytes())))));
            var body=Map.of("model",model,"stream",false,"temperature",0,"max_tokens",8192,"messages",List.of(Map.of("role","system","content",prompt),Map.of("role","user","content",content)));
            JsonNode response=client.post().uri("/chat/completions").header(HttpHeaders.AUTHORIZATION,"Bearer "+key).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            if(response==null || !response.path("choices").isArray() || response.path("choices").isEmpty())throw new IllegalArgumentException();
            JsonNode choice=response.path("choices").get(0);
            if("length".equals(choice.path("finish_reason").asText()))throw new IllegalArgumentException();
            String raw=choice.path("message").path("content").asText().strip();
            if(raw.startsWith("```"))raw=raw.replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");
            JsonNode result=json.readTree(raw);
            if(!result.isObject() || !result.has("action"))throw new IllegalArgumentException();
            return result;
        } catch(BusinessException ex) {throw ex;}
        catch(Exception ex) {throw BusinessException.badRequest("识别服务暂时不可用，原消息已保留，请重试");}
        finally {slots.release();}
    }
}
