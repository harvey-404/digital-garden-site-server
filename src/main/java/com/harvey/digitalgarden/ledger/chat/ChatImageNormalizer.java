package com.harvey.digitalgarden.ledger.chat;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.ledger.chat.ChatDtos.StoredImage;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Set;
import javax.imageio.*;

public final class ChatImageNormalizer {
    private ChatImageNormalizer() {}
    public static StoredImage normalize(byte[] bytes) {
        if(bytes==null || bytes.length==0 || bytes.length>10*1024*1024)throw BusinessException.badRequest("每张图片最多10MB");
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw BusinessException.badRequest("请选择JPEG、PNG或WebP图片，HEIC请先转为JPEG");
            var reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if(!Set.of("jpeg","jpg","png","webp").contains(format))throw BusinessException.badRequest("请选择JPEG、PNG或WebP图片");
                reader.setInput(stream,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<=0 || h<=0 || (long)w*h>20_000_000)throw BusinessException.badRequest("图片分辨率过大，请压缩后上传");
                BufferedImage image=reader.read(0);
                String target=format.equals("jpeg") || format.equals("jpg")?"jpeg":"png";
                var output=new ByteArrayOutputStream();
                if(!ImageIO.write(image,target,output))throw new IOException();
                if(output.size()>30*1024*1024)throw BusinessException.badRequest("图片解码后过大，请压缩后上传");
                return new StoredImage(output.toByteArray(),"image/"+target,w,h,LedgerChatService.sha256(bytes));
            } finally {reader.dispose();}
        } catch(BusinessException ex) {throw ex;}
        catch(IOException|RuntimeException ex) {throw BusinessException.badRequest("图片无法读取，请换一张或转为JPEG");}
    }
    public static byte[] forModel(byte[] bytes) throws IOException {
        BufferedImage original=ImageIO.read(new ByteArrayInputStream(bytes));
        if(original==null)throw new IOException();
        double ratio=Math.min(1,2048.0/Math.max(original.getWidth(),original.getHeight()));
        BufferedImage small=new BufferedImage(Math.max(1,(int)(original.getWidth()*ratio)),Math.max(1,(int)(original.getHeight()*ratio)),BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=small.createGraphics();
        try {graphics.setColor(Color.WHITE);graphics.fillRect(0,0,small.getWidth(),small.getHeight());graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);graphics.drawImage(original,0,0,small.getWidth(),small.getHeight(),null);} finally {graphics.dispose();}
        var out=new ByteArrayOutputStream();ImageIO.write(small,"png",out);return out.toByteArray();
    }
}
