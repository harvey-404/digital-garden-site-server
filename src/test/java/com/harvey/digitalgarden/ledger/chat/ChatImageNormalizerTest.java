package com.harvey.digitalgarden.ledger.chat;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

class ChatImageNormalizerTest {
    @Test void validatesRealImageContent() throws Exception {
        Class<?> type=assertDoesNotThrow(()->Class.forName("com.harvey.digitalgarden.ledger.chat.ChatImageNormalizer"));
        var method=type.getMethod("normalize",byte[].class);
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(10,20,BufferedImage.TYPE_INT_RGB),"png",bytes);
        assertNotNull(method.invoke(null,(Object)bytes.toByteArray()));
        assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,(Object)"fake image".getBytes()));
    }
}
